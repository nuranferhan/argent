package com.argent.ledger

import com.argent.common.domain.Balance
import com.argent.common.domain.Position
import com.argent.common.events.DomainEvent
import com.argent.common.events.FundsHeld
import com.argent.common.events.FundsReleased
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SchemaUtils
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class LedgerService(
    dataSource: DataSource,
    private val eventBus: (DomainEvent) -> Unit = {}
) {
    data class ReconciliationMismatch(
        val account: String,
        val asset: String,
        val journalNet: String,
        val balanceTotal: String
    )

    data class ReconciliationReport(
        val ok: Boolean,
        val checkedAccountAssets: Int,
        val mismatches: List<ReconciliationMismatch>
    )

    private val log = LoggerFactory.getLogger(LedgerService::class.java)
    private val db = Database.connect(dataSource)

    private val accountQueries by lazy { LedgerAccountQueries(db, ::readBalance, ::readPosition) }

    fun initSchema() = accountQueries.initSchema()
    fun getBalance(userId: String, asset: String): Balance = accountQueries.getBalance(userId, asset)
    fun listBalances(userId: String): List<Balance> = accountQueries.listBalances(userId)
    fun activeHoldOrderIds(): List<String> = accountQueries.activeHoldOrderIds()
    fun getPosition(userId: String, symbol: String): Position = accountQueries.getPosition(userId, symbol)
    fun listPositions(userId: String): List<Position> = accountQueries.listPositions(userId)
    fun verifyInvariant(): Boolean = accountQueries.verifyInvariant()
    fun verifyReconciliation(): Boolean = accountQueries.verifyReconciliation()
    fun reconciliationReport(): ReconciliationReport = accountQueries.reconciliationReport()
    fun journalForTx(txId: String): List<Pair<BigDecimal, BigDecimal>> = accountQueries.journalForTx(txId)

    fun expireWorthlessDerivative(symbol: String): Int = transaction(db) {
        var closed = 0
        Positions.selectAll().where { Positions.symbol eq symbol }.forUpdate().forEach { position ->
            val userId = position[Positions.userId]
            val quantity = position[Positions.quantity]
            if (quantity.signum() == 0) return@forEach

            if (quantity.signum() > 0) {
                val balance = lockBalance(userId, symbol) ?: return@forEach
                if (balance[Balances.available] < quantity || balance[Balances.held].signum() != 0) return@forEach
                writeBalance(balance, balance[Balances.available] - quantity, balance[Balances.held])
                val txId = UUID.randomUUID().toString()
                journal(txId, userId, symbol, quantity, BigDecimal.ZERO, "Derivative expiry writeoff symbol=$symbol")
                journal(txId, "DERIVATIVE_EXPIRY_LOSS", symbol, BigDecimal.ZERO, quantity, "Derivative expiry loss symbol=$symbol")
                assertTxBalanced(txId)
            }

            val entry = position[Positions.avgEntry]
            val realized = position[Positions.realizedPnl] +
                if (quantity.signum() > 0) entry.multiply(quantity).negate() else entry.multiply(quantity.abs())
            Positions.update({ (Positions.userId eq userId) and (Positions.symbol eq symbol) and (Positions.quantity eq quantity) }) {
                it[Positions.quantity] = BigDecimal.ZERO
                it[Positions.avgEntry] = BigDecimal.ZERO
                it[Positions.realizedPnl] = realized
            }
            closed++
        }
        closed
    }

    private val funding by lazy {
        LedgerFundingService(
            db, ::creditAvailable, ::lockBalance, ::writeBalance, ::journal,
            ::assertTxBalanced, ::readBalance, ::storedBalance,
            ::rememberBalance, ::idempotencyFingerprint
        )
    }

    fun deposit(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance =
        funding.deposit(userId, asset, amount, idempotencyKey)

    fun withdraw(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance =
        funding.withdraw(userId, asset, amount, idempotencyKey)

    fun hold(userId: String, asset: String, amount: BigDecimal, orderId: String): Boolean {
        require(amount > BigDecimal.ZERO)
        repeat(4) { attempt ->
            try {
                var created = false
                val ok = transaction(db) {
                    val existing = OrderHolds.selectAll().where { OrderHolds.orderId eq orderId }.firstOrNull()
                    if (existing != null) return@transaction true
                    val current = lockBalance(userId, asset) ?: return@transaction false
                    if (current[Balances.available] < amount) return@transaction false
                    writeBalance(current, current[Balances.available] - amount, current[Balances.held] + amount)
                    val txId = UUID.randomUUID().toString()
                    journal(txId, userId, asset, debit = amount, credit = BigDecimal.ZERO, "Hold available order=$orderId")
                    journal(txId, userId, asset, debit = BigDecimal.ZERO, credit = amount, "Hold reserved order=$orderId")
                    assertTxBalanced(txId)
                    OrderHolds.insert {
                        it[OrderHolds.orderId] = orderId
                        it[OrderHolds.userId] = userId
                        it[OrderHolds.asset] = asset
                        it[remaining] = amount
                    }
                    created = true
                    true
                }
                if (ok && created) {
                    eventBus(FundsHeld(aggregateId = userId, orderId = orderId, asset = asset, amount = amount.toPlainString()))
                }
                return ok
            } catch (e: OptimisticLockException) {
                if (attempt == 3) {
                    log.warn("Hold failed after retries user={} asset={}", userId, asset)
                    throw OptimisticLockException("Could not reserve $asset after concurrent updates")
                }
            } catch (e: Exception) {
                log.warn("Hold failed user={} asset={} amount={}: {}", userId, asset, amount, e.message)
                throw e
            }
        }
        return false
    }

    private val holdRelease by lazy { LedgerHoldReleaseService(db, eventBus, ::releaseLocked) }

    fun releaseRemainingHold(orderId: String) = holdRelease.releaseRemainingHold(orderId)

    fun release(userId: String, asset: String, amount: BigDecimal, orderId: String) =
        holdRelease.release(userId, asset, amount, orderId)

    private val tradeSettlement by lazy {
        LedgerTradeSettlementService(
            db = db,
            eventBus = eventBus,
            consumeHold = ::consumeHold,
            creditAvailable = ::creditAvailable,
            debitAvailable = ::debitAvailable,
            journal = ::journal,
            assertTxBalanced = ::assertTxBalanced,
            applyPosition = ::applyPosition
        )
    }

    fun settleTrade(
        tradeId: String,
        symbol: String,
        buyUserId: String,
        sellUserId: String,
        baseAsset: String,
        quoteAsset: String,
        quantity: BigDecimal,
        price: BigDecimal,
        buyOrderId: String,
        sellOrderId: String
    ) = tradeSettlement.settleTrade(
        tradeId, symbol, buyUserId, sellUserId, baseAsset, quoteAsset,
        quantity, price, buyOrderId, sellOrderId
    )

    fun isTradeSettled(tradeId: String): Boolean = tradeSettlement.isTradeSettled(tradeId)

    fun settleBinanceTestnetFill(
        executionId: String,
        holdId: String,
        userId: String,
        symbol: String,
        side: String,
        baseAsset: String,
        quoteAsset: String,
        quantity: BigDecimal,
        quoteQuantity: BigDecimal,
        commission: BigDecimal,
        commissionAsset: String
    ): Boolean = tradeSettlement.settleBinanceTestnetFill(
        executionId, holdId, userId, symbol, side, baseAsset, quoteAsset,
        quantity, quoteQuantity, commission, commissionAsset
    )

    private fun applyPosition(userId: String, symbol: String, qtyDelta: BigDecimal, price: BigDecimal) {
        val existing = Positions.selectAll()
            .where { (Positions.userId eq userId) and (Positions.symbol eq symbol) }
            .firstOrNull()
        if (existing == null) {
            val qty = qtyDelta
            val avg = if (qty.signum() > 0) price else price
            Positions.insert {
                it[Positions.userId] = userId
                it[Positions.symbol] = symbol
                it[quantity] = qty
                it[avgEntry] = avg
                it[realizedPnl] = BigDecimal.ZERO
            }
            return
        }
        var qty = existing[Positions.quantity]
        var avg = existing[Positions.avgEntry]
        var realized = existing[Positions.realizedPnl]
        when {
            qty.signum() == 0 -> {
                qty = qtyDelta
                avg = price
            }
            qty.signum() == qtyDelta.signum() -> {
                val newQty = qty + qtyDelta
                avg = (avg.multiply(qty) + price.multiply(qtyDelta)).divide(newQty, 18, java.math.RoundingMode.HALF_UP)
                qty = newQty
            }
            else -> {
                val closeQty = qty.abs().min(qtyDelta.abs())
                val sign = if (qty.signum() > 0) BigDecimal.ONE else BigDecimal.ONE.negate()
                realized += (price - avg).multiply(closeQty).multiply(sign)
                qty += qtyDelta
                if (qty.signum() == 0) avg = BigDecimal.ZERO
                else if (qty.signum() != existing[Positions.quantity].signum()) avg = price
            }
        }
        Positions.update({ (Positions.userId eq userId) and (Positions.symbol eq symbol) }) {
            it[quantity] = qty
            it[avgEntry] = avg
            it[realizedPnl] = realized
        }
    }

    private fun consumeHold(orderId: String, userId: String, asset: String, amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "Settlement amount must be positive" }
        val hold = OrderHolds.selectAll().where { OrderHolds.orderId eq orderId }.firstOrNull()
            ?: throw IllegalStateException("Missing funds hold for order $orderId")
        if (hold[OrderHolds.userId] != userId || hold[OrderHolds.asset] != asset) {
            throw IllegalStateException("Funds hold does not match order $orderId owner/asset")
        }
        val left = hold[OrderHolds.remaining]
        if (left < amount) throw IllegalStateException("Hold remaining $left < $amount for $orderId")
        OrderHolds.update({ OrderHolds.orderId eq orderId }) { it[remaining] = left - amount }
        val current = lockBalance(userId, asset) ?: throw IllegalStateException("No balance for $userId $asset")
        if (current[Balances.held] < amount) {
            throw IllegalStateException("Insufficient held $asset for $userId")
        }
        writeBalance(current, current[Balances.available], current[Balances.held] - amount)
    }

    private fun releaseLocked(userId: String, asset: String, amount: BigDecimal, orderId: String): FundsReleased? {
        if (amount <= BigDecimal.ZERO) return null
        val current = lockBalance(userId, asset) ?: return null
        val toRelease = amount.min(current[Balances.held])
        writeBalance(current, current[Balances.available] + toRelease, current[Balances.held] - toRelease)
        val hold = OrderHolds.selectAll().where { OrderHolds.orderId eq orderId }.firstOrNull()
        if (hold != null) {
            val left = hold[OrderHolds.remaining]
            OrderHolds.update({ OrderHolds.orderId eq orderId }) {
                it[remaining] = (left - toRelease).max(BigDecimal.ZERO)
            }
        }
        val txId = UUID.randomUUID().toString()
        journal(txId, userId, asset, toRelease, BigDecimal.ZERO, "Release reserved order=$orderId")
        journal(txId, userId, asset, BigDecimal.ZERO, toRelease, "Release available order=$orderId")
        assertTxBalanced(txId)
        return FundsReleased(aggregateId = userId, orderId = orderId, asset = asset, amount = toRelease.toPlainString())
    }

    private fun creditAvailable(userId: String, asset: String, amount: BigDecimal) {
        val existing = lockBalance(userId, asset)
        if (existing == null) {
            Balances.insert {
                it[Balances.userId] = userId
                it[Balances.asset] = asset
                it[available] = amount
                it[held] = BigDecimal.ZERO
                it[version] = 1
            }
        } else {
            writeBalance(existing, existing[Balances.available] + amount, existing[Balances.held])
        }
    }

    private fun debitAvailable(userId: String, asset: String, amount: BigDecimal) {
        require(amount >= BigDecimal.ZERO) { "Debit amount cannot be negative" }
        if (amount == BigDecimal.ZERO) return
        val current = lockBalance(userId, asset) ?: throw IllegalStateException("No $asset balance for testnet commission")
        if (current[Balances.available] < amount) throw IllegalStateException("Insufficient available $asset for Binance Testnet commission")
        writeBalance(current, current[Balances.available] - amount, current[Balances.held])
    }

    private fun lockBalance(userId: String, asset: String) =
        Balances.selectAll()
            .where { (Balances.userId eq userId) and (Balances.asset eq asset) }
            .forUpdate()
            .firstOrNull()

    private fun writeBalance(current: org.jetbrains.exposed.sql.ResultRow, newAvail: BigDecimal, newHeld: BigDecimal) {
        val updated = Balances.update({
            (Balances.userId eq current[Balances.userId]) and
                (Balances.asset eq current[Balances.asset]) and
                (Balances.version eq current[Balances.version])
        }) {
            it[available] = newAvail
            it[held] = newHeld
            it[version] = current[Balances.version] + 1
        }
        if (updated == 0) throw OptimisticLockException("Concurrent modification of ${current[Balances.userId]} ${current[Balances.asset]}")
    }

    private fun readBalance(userId: String, asset: String): Balance {
        return Balances.selectAll()
            .where { (Balances.userId eq userId) and (Balances.asset eq asset) }
            .map { rowToBalance(it) }
            .firstOrNull() ?: Balance(userId, asset)
    }

    private fun readPosition(userId: String, symbol: String): Position {
        return Positions.selectAll()
            .where { (Positions.userId eq userId) and (Positions.symbol eq symbol) }
            .map { rowToPosition(it) }
            .firstOrNull() ?: Position(userId, symbol)
    }

    private fun rowToBalance(it: org.jetbrains.exposed.sql.ResultRow) = Balance(
        userId = it[Balances.userId],
        asset = it[Balances.asset],
        available = it[Balances.available].stripTrailingZeros().toPlainString(),
        held = it[Balances.held].stripTrailingZeros().toPlainString(),
        version = it[Balances.version]
    )

    private fun rowToPosition(it: org.jetbrains.exposed.sql.ResultRow) = Position(
        userId = it[Positions.userId],
        symbol = it[Positions.symbol],
        quantity = it[Positions.quantity].stripTrailingZeros().toPlainString(),
        avgEntryPrice = it[Positions.avgEntry].stripTrailingZeros().toPlainString(),
        realizedPnl = it[Positions.realizedPnl].stripTrailingZeros().toPlainString()
    )

    private fun journal(
        txId: String,
        account: String,
        asset: String,
        debit: BigDecimal,
        credit: BigDecimal,
        description: String
    ) {
        JournalEntries.insert {
            it[id] = UUID.randomUUID().toString()
            it[JournalEntries.txId] = txId
            it[JournalEntries.account] = account
            it[JournalEntries.asset] = asset
            it[JournalEntries.debit] = debit
            it[JournalEntries.credit] = credit
            it[JournalEntries.description] = description
            it[createdAt] = Instant.now().toEpochMilli()
        }
    }

    private fun assertTxBalanced(txId: String) {
        val rows = JournalEntries.selectAll().where { JournalEntries.txId eq txId }
            .map { it[JournalEntries.debit] to it[JournalEntries.credit] }
        val debit = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.first }
        val credit = rows.fold(BigDecimal.ZERO) { acc, r -> acc + r.second }
        check(debit.compareTo(credit) == 0) { "Unbalanced journal $txId debit=$debit credit=$credit" }
    }

    private fun storedBalance(key: String?, requestHash: String? = null): Balance? {
        if (key == null) return null
        val existing = IdempotencyKeys.selectAll().where { IdempotencyKeys.key eq key }.firstOrNull() ?: return null
        val storedRequestHash = existing[IdempotencyKeys.requestHash]
        if (storedRequestHash != null && requestHash != null && storedRequestHash != requestHash) {
            throw IllegalArgumentException("Idempotency key was already used with a different asset or amount")
        }
        val parts = existing[IdempotencyKeys.result].split('|')
        return Balance(parts[0], parts[1], parts.getOrElse(2) { "0" }, parts.getOrElse(3) { "0" })
    }

    private fun rememberBalance(key: String?, bal: Balance, requestHash: String?) {
        if (key == null) return
        IdempotencyKeys.insert {
            it[IdempotencyKeys.key] = key
            it[result] = "${bal.userId}|${bal.asset}|${bal.available}|${bal.held}"
            it[IdempotencyKeys.requestHash] = requestHash
            it[createdAt] = Instant.now().toEpochMilli()
        }
    }

    private fun idempotencyFingerprint(asset: String, amount: BigDecimal) =
        "$asset:${amount.stripTrailingZeros().toPlainString()}"

    class OptimisticLockException(msg: String) : RuntimeException(msg)
}
