package com.argent.ledger

import com.argent.common.domain.Balance
import com.argent.common.domain.Position
import com.argent.common.events.DomainEvent
import com.argent.common.events.FundsHeld
import com.argent.common.events.FundsReleased
import com.argent.common.events.SettlementCompleted
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
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

    object Balances : Table("balances") {
        val userId = varchar("user_id", 64)
        val asset = varchar("asset", 32)
        val available = decimal("available", 36, 18)
        val held = decimal("held", 36, 18)
        val version = long("version")
        override val primaryKey = PrimaryKey(userId, asset)
    }

    object JournalEntries : Table("journal_entries") {
        val id = varchar("id", 64)
        val txId = varchar("tx_id", 64)
        val account = varchar("account", 64)
        val asset = varchar("asset", 32)
        val debit = decimal("debit", 36, 18)
        val credit = decimal("credit", 36, 18)
        val description = varchar("description", 256)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    object IdempotencyKeys : Table("idempotency_keys") {
        val key = varchar("key", 256)
        val result = text("result")
        val requestHash = varchar("request_hash", 128).nullable()
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(key)
    }

    object OrderHolds : Table("order_holds") {
        val orderId = varchar("order_id", 64)
        val userId = varchar("user_id", 64)
        val asset = varchar("asset", 32)
        val remaining = decimal("remaining", 36, 18)
        override val primaryKey = PrimaryKey(orderId)
    }

    object Positions : Table("positions") {
        val userId = varchar("user_id", 64)
        val symbol = varchar("symbol", 32)
        val quantity = decimal("quantity", 36, 18)
        val avgEntry = decimal("avg_entry", 36, 18)
        val realizedPnl = decimal("realized_pnl", 36, 18)
        override val primaryKey = PrimaryKey(userId, symbol)
    }

    object Settlements : Table("settlements") {
        val tradeId = varchar("trade_id", 64)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(tradeId)
    }

    fun initSchema() {
        val isPostgres = db.vendor.contains("postgres", ignoreCase = true)
        transaction(db) {
            // Older local databases may already have journal rows without the account column.
            // Backfill them explicitly before Exposed attempts to add the new NOT NULL column.
            if (isPostgres) {
                exec("""
                    DO ${'$'}${'$'}
                    BEGIN
                        IF to_regclass('journal_entries') IS NOT NULL THEN
                            ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS account VARCHAR(64);
                            UPDATE journal_entries SET account = 'LEGACY_UNASSIGNED' WHERE account IS NULL;
                            ALTER TABLE journal_entries ALTER COLUMN account SET NOT NULL;
                            IF EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema = current_schema()
                                  AND table_name = 'journal_entries'
                                  AND column_name = 'user_id'
                            ) THEN
                                ALTER TABLE journal_entries ALTER COLUMN user_id DROP NOT NULL;
                            END IF;
                        END IF;
                    END
                    ${'$'}${'$'}
                """.trimIndent())
            }
            SchemaUtils.createMissingTablesAndColumns(Balances, JournalEntries, IdempotencyKeys, OrderHolds, Positions, Settlements)
            runCatching { exec("ALTER TABLE idempotency_keys ALTER COLUMN key TYPE VARCHAR(256)") }
            runCatching { exec("ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS request_hash VARCHAR(128)") }
        }
    }

    fun getBalance(userId: String, asset: String): Balance = transaction(db) {
        readBalance(userId, asset)
    }

    fun listBalances(userId: String): List<Balance> = transaction(db) {
        Balances.selectAll().where { Balances.userId eq userId }.map { rowToBalance(it) }
            .filter { it.availableBd().signum() != 0 || it.heldBd().signum() != 0 }
    }

    fun activeHoldOrderIds(): List<String> = transaction(db) {
        OrderHolds.selectAll()
            .filter { it[OrderHolds.remaining].signum() > 0 }
            .map { it[OrderHolds.orderId] }
    }

    fun getPosition(userId: String, symbol: String): Position = transaction(db) {
        readPosition(userId, symbol)
    }

    fun listPositions(userId: String): List<Position> = transaction(db) {
        Positions.selectAll().where { Positions.userId eq userId }.map { rowToPosition(it) }
            .filter { it.quantityBd().signum() != 0 || it.realizedPnlBd().signum() != 0 }
    }

    fun deposit(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(idempotencyKey == null || idempotencyKey.length in 1..128) { "Idempotency key must contain 1 to 128 characters" }
        val key = idempotencyKey?.let { "deposit:$userId:$asset:$it" }
        val requestHash = idempotencyFingerprint(asset, amount)
        return transaction(db) {
            storedBalance(key, requestHash)?.let { return@transaction it }
            idempotencyKey?.let { legacy ->
                storedBalance("deposit:$userId:$legacy", requestHash)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
                storedBalance("deposit:$legacy")?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
            }
            val txId = UUID.randomUUID().toString()
            creditAvailable(userId, asset, amount)
            journal(txId, "SYSTEM", asset, debit = amount, credit = BigDecimal.ZERO, "Deposit custody")
            journal(txId, userId, asset, debit = BigDecimal.ZERO, credit = amount, "Deposit liability")
            assertTxBalanced(txId)
            val bal = readBalance(userId, asset)
            rememberBalance(key, bal, requestHash)
            bal
        }
    }

    fun withdraw(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(idempotencyKey == null || idempotencyKey.length in 1..128) { "Idempotency key must contain 1 to 128 characters" }
        val key = idempotencyKey?.let { "withdraw:$userId:$asset:$it" }
        val requestHash = idempotencyFingerprint(asset, amount)
        return transaction(db) {
            storedBalance(key, requestHash)?.let { return@transaction it }
            idempotencyKey?.let { legacy ->
                storedBalance("withdraw:$userId:$legacy", requestHash)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
                storedBalance("withdraw:$legacy")?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
            }
            val current = lockBalance(userId, asset) ?: throw IllegalArgumentException("Insufficient $asset")
            if (current[Balances.available] < amount) throw IllegalArgumentException("Insufficient available $asset")
            writeBalance(current, current[Balances.available] - amount, current[Balances.held])
            val txId = UUID.randomUUID().toString()
            journal(txId, userId, asset, debit = amount, credit = BigDecimal.ZERO, "Withdraw liability")
            journal(txId, "SYSTEM", asset, debit = BigDecimal.ZERO, credit = amount, "Withdraw custody")
            assertTxBalanced(txId)
            val bal = readBalance(userId, asset)
            rememberBalance(key, bal, requestHash)
            bal
        }
    }

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

    fun releaseRemainingHold(orderId: String) {
        val released = transaction(db) {
            val hold = OrderHolds.selectAll().where { OrderHolds.orderId eq orderId }.firstOrNull() ?: return@transaction null
            val remaining = hold[OrderHolds.remaining]
            val event = if (remaining > BigDecimal.ZERO) {
                releaseLocked(hold[OrderHolds.userId], hold[OrderHolds.asset], remaining, orderId)
            } else null
            OrderHolds.update({ OrderHolds.orderId eq orderId }) { it[OrderHolds.remaining] = BigDecimal.ZERO }
            event
        }
        released?.let(eventBus)
    }

    fun release(userId: String, asset: String, amount: BigDecimal, orderId: String) {
        val event = transaction(db) { releaseLocked(userId, asset, amount, orderId) }
        event?.let(eventBus)
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
    ) {
        require(quantity > BigDecimal.ZERO) { "Trade quantity must be positive" }
        require(price > BigDecimal.ZERO) { "Trade price must be positive" }
        val notional = quantity.multiply(price)
        val firstTime = transaction(db) {
            val already = Settlements.selectAll().where { Settlements.tradeId eq tradeId }.firstOrNull()
            if (already != null) return@transaction false
            Settlements.insert {
                it[Settlements.tradeId] = tradeId
                it[createdAt] = Instant.now().toEpochMilli()
            }
            consumeHold(buyOrderId, buyUserId, quoteAsset, notional)
            consumeHold(sellOrderId, sellUserId, baseAsset, quantity)
            creditAvailable(buyUserId, baseAsset, quantity)
            creditAvailable(sellUserId, quoteAsset, notional)

            val txId = UUID.randomUUID().toString()
            journal(txId, buyUserId, quoteAsset, notional, BigDecimal.ZERO, "Trade buy quote trade=$tradeId")
            journal(txId, sellUserId, quoteAsset, BigDecimal.ZERO, notional, "Trade sell quote trade=$tradeId")
            journal(txId, sellUserId, baseAsset, quantity, BigDecimal.ZERO, "Trade sell base trade=$tradeId")
            journal(txId, buyUserId, baseAsset, BigDecimal.ZERO, quantity, "Trade buy base trade=$tradeId")
            assertTxBalanced(txId)

            applyPosition(buyUserId, symbol, quantity, price)
            applyPosition(sellUserId, symbol, quantity.negate(), price)
            true
        }
        if (firstTime) {
            eventBus(SettlementCompleted(aggregateId = tradeId, tradeId = tradeId))
        }
    }

    fun verifyInvariant(): Boolean = transaction(db) {
        val totals = JournalEntries.selectAll()
            .groupBy { it[JournalEntries.asset] }
        totals.values.all { rows ->
            val (debit, credit) = rows.fold(BigDecimal.ZERO to BigDecimal.ZERO) { acc, row ->
                acc.first + row[JournalEntries.debit] to acc.second + row[JournalEntries.credit]
            }
            debit.compareTo(credit) == 0
        }
    }

    /**
     * Reconciles each user/asset balance (available + held) against that account's
     * net journal balance. SYSTEM and LEGACY_UNASSIGNED are control accounts and
     * intentionally have no spendable balance row.
     */
    fun verifyReconciliation(): Boolean = reconciliationReport().ok

    fun reconciliationReport(): ReconciliationReport = transaction(db) {
        val controlAccounts = setOf("SYSTEM", "LEGACY_UNASSIGNED")
        val journalNet = JournalEntries.selectAll()
            .filter { it[JournalEntries.account] !in controlAccounts }
            .groupBy { it[JournalEntries.account] to it[JournalEntries.asset] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { total, row ->
                    total + row[JournalEntries.credit] - row[JournalEntries.debit]
                }
            }
        val balanceTotals = Balances.selectAll()
            .associate { row ->
                (row[Balances.userId] to row[Balances.asset]) to
                    (row[Balances.available] + row[Balances.held])
            }

        val keys = journalNet.keys + balanceTotals.keys
        val mismatches = keys.mapNotNull { (account, asset) ->
            val journal = journalNet[account to asset] ?: BigDecimal.ZERO
            val balance = balanceTotals[account to asset] ?: BigDecimal.ZERO
            if (journal.compareTo(balance) == 0) null else ReconciliationMismatch(
                account = account,
                asset = asset,
                journalNet = journal.stripTrailingZeros().toPlainString(),
                balanceTotal = balance.stripTrailingZeros().toPlainString()
            )
        }
        ReconciliationReport(
            ok = mismatches.isEmpty(),
            checkedAccountAssets = keys.size,
            mismatches = mismatches.sortedWith(compareBy({ it.account }, { it.asset }))
        )
    }

    fun journalForTx(txId: String): List<Pair<BigDecimal, BigDecimal>> = transaction(db) {
        JournalEntries.selectAll().where { JournalEntries.txId eq txId }
            .map { it[JournalEntries.debit] to it[JournalEntries.credit] }
    }

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
