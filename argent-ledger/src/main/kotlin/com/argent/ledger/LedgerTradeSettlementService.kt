package com.argent.ledger

import com.argent.common.events.DomainEvent
import com.argent.common.events.SettlementCompleted
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

internal class LedgerTradeSettlementService(
    private val db: Database,
    private val eventBus: (DomainEvent) -> Unit,
    private val consumeHold: (String, String, String, BigDecimal) -> Unit,
    private val creditAvailable: (String, String, BigDecimal) -> Unit,
    private val debitAvailable: (String, String, BigDecimal) -> Unit,
    private val journal: (String, String, String, BigDecimal, BigDecimal, String) -> Unit,
    private val assertTxBalanced: (String) -> Unit,
    private val applyPosition: (String, String, BigDecimal, BigDecimal) -> Unit,
) {
    private val log = LoggerFactory.getLogger(LedgerTradeSettlementService::class.java)

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
            if (hasLegacyTradePostings(
                    tradeId, buyUserId, sellUserId, baseAsset, quoteAsset, quantity, notional
                )
            ) {
                Settlements.insert {
                    it[Settlements.tradeId] = tradeId
                    it[createdAt] = Instant.now().toEpochMilli()
                }
                log.warn("Repaired missing settlement marker from existing ledger postings tradeId={}", tradeId)
                return@transaction true
            }
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

    /**
     * Older releases could persist balanced trade journal postings without a
     * settlement marker. On recovery, recognize only the complete four-leg
     * posting set in one journal transaction; never reapply those balances.
     */
    private fun hasLegacyTradePostings(
        tradeId: String,
        buyUserId: String,
        sellUserId: String,
        baseAsset: String,
        quoteAsset: String,
        quantity: BigDecimal,
        notional: BigDecimal
    ): Boolean {
        val marker = "trade=$tradeId"
        val rows = JournalEntries.selectAll()
            .where { JournalEntries.description like "%$marker%" }
            .toList()
        if (rows.size != 4 || rows.map { it[JournalEntries.txId] }.distinct().size != 1) return false

        fun normalized(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
        data class Posting(val account: String, val asset: String, val debit: String, val credit: String)
        val expected = setOf(
            Posting(buyUserId, quoteAsset, normalized(notional), "0"),
            Posting(sellUserId, quoteAsset, "0", normalized(notional)),
            Posting(sellUserId, baseAsset, normalized(quantity), "0"),
            Posting(buyUserId, baseAsset, "0", normalized(quantity))
        )
        val actual = rows.map { row ->
            Posting(
                account = row[JournalEntries.account],
                asset = row[JournalEntries.asset],
                debit = normalized(row[JournalEntries.debit]),
                credit = normalized(row[JournalEntries.credit])
            )
        }.toSet()
        return actual == expected
    }

    fun isTradeSettled(tradeId: String): Boolean = transaction(db) {
        Settlements.selectAll().where { Settlements.tradeId eq tradeId }.count() > 0
    }

    /**
     * Settles one Binance Spot Testnet execution against a locally held amount.
     * Fill IDs are persisted in Settlements so repeated order polling cannot apply
     * the same exchange execution twice. The external custody and fee accounts
     * keep every asset journal balanced while the user's available/held balances
     * and position reflect the testnet fill.
     */
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
    ): Boolean {
        require(executionId.length in 1..64) { "Execution id must contain 1 to 64 characters" }
        require(quantity > BigDecimal.ZERO && quoteQuantity > BigDecimal.ZERO) { "Fill amounts must be positive" }
        require(commission >= BigDecimal.ZERO) { "Commission cannot be negative" }
        val normalizedSide = side.uppercase()
        require(normalizedSide in setOf("BUY", "SELL")) { "Invalid testnet fill side" }
        val feeAsset = commissionAsset.uppercase()
        val normalizedBase = baseAsset.uppercase()
        val normalizedQuote = quoteAsset.uppercase()
        return transaction(db) {
            if (Settlements.selectAll().where { Settlements.tradeId eq "binance-testnet:$executionId" }.firstOrNull() != null) {
                return@transaction false
            }
            Settlements.insert {
                it[Settlements.tradeId] = "binance-testnet:$executionId"
                it[createdAt] = Instant.now().toEpochMilli()
            }
            val baseFee = if (feeAsset == normalizedBase) commission else BigDecimal.ZERO
            val quoteFee = if (feeAsset == normalizedQuote) commission else BigDecimal.ZERO
            val txId = UUID.randomUUID().toString()
            if (normalizedSide == "BUY") {
                val quoteDebit = quoteQuantity + quoteFee
                val netBase = quantity - baseFee
                require(netBase >= BigDecimal.ZERO) { "Base-asset commission exceeds the filled quantity" }
                consumeHold(holdId, userId, normalizedQuote, quoteDebit)
                if (netBase > BigDecimal.ZERO) creditAvailable(userId, normalizedBase, netBase)
                journal(txId, userId, normalizedQuote, quoteDebit, BigDecimal.ZERO, "Binance Testnet buy quote execution=$executionId")
                journal(txId, "BINANCE_TESTNET_CUSTODY", normalizedQuote, BigDecimal.ZERO, quoteQuantity, "Binance Testnet quote received execution=$executionId")
                if (quoteFee > BigDecimal.ZERO) {
                    journal(txId, "BINANCE_TESTNET_FEES", normalizedQuote, BigDecimal.ZERO, quoteFee, "Binance Testnet quote fee execution=$executionId")
                }
                journal(txId, "BINANCE_TESTNET_CUSTODY", normalizedBase, quantity, BigDecimal.ZERO, "Binance Testnet base delivered execution=$executionId")
                if (netBase > BigDecimal.ZERO) journal(txId, userId, normalizedBase, BigDecimal.ZERO, netBase, "Binance Testnet buy base execution=$executionId")
                if (baseFee > BigDecimal.ZERO) journal(txId, "BINANCE_TESTNET_FEES", normalizedBase, BigDecimal.ZERO, baseFee, "Binance Testnet base fee execution=$executionId")
                val externalFee = commission - baseFee - quoteFee
                if (externalFee > BigDecimal.ZERO) {
                    debitAvailable(userId, feeAsset, externalFee)
                    journal(txId, userId, feeAsset, externalFee, BigDecimal.ZERO, "Binance Testnet fee execution=$executionId")
                    journal(txId, "BINANCE_TESTNET_FEES", feeAsset, BigDecimal.ZERO, externalFee, "Binance Testnet external fee execution=$executionId")
                }
                assertTxBalanced(txId)
                if (netBase > BigDecimal.ZERO) {
                    applyPosition(userId, symbol, netBase, quoteDebit.divide(netBase, 18, java.math.RoundingMode.HALF_UP))
                }
            } else {
                val baseDebit = quantity + baseFee
                val netQuote = quoteQuantity - quoteFee
                require(netQuote >= BigDecimal.ZERO) { "Quote-asset commission exceeds the proceeds" }
                consumeHold(holdId, userId, normalizedBase, baseDebit)
                if (netQuote > BigDecimal.ZERO) creditAvailable(userId, normalizedQuote, netQuote)
                journal(txId, userId, normalizedBase, baseDebit, BigDecimal.ZERO, "Binance Testnet sell base execution=$executionId")
                journal(txId, "BINANCE_TESTNET_CUSTODY", normalizedBase, BigDecimal.ZERO, quantity, "Binance Testnet base received execution=$executionId")
                if (baseFee > BigDecimal.ZERO) journal(txId, "BINANCE_TESTNET_FEES", normalizedBase, BigDecimal.ZERO, baseFee, "Binance Testnet base fee execution=$executionId")
                journal(txId, "BINANCE_TESTNET_CUSTODY", normalizedQuote, quoteQuantity, BigDecimal.ZERO, "Binance Testnet quote paid execution=$executionId")
                if (netQuote > BigDecimal.ZERO) journal(txId, userId, normalizedQuote, BigDecimal.ZERO, netQuote, "Binance Testnet sell quote execution=$executionId")
                if (quoteFee > BigDecimal.ZERO) journal(txId, "BINANCE_TESTNET_FEES", normalizedQuote, BigDecimal.ZERO, quoteFee, "Binance Testnet quote fee execution=$executionId")
                val externalFee = commission - baseFee - quoteFee
                if (externalFee > BigDecimal.ZERO) {
                    debitAvailable(userId, feeAsset, externalFee)
                    journal(txId, userId, feeAsset, externalFee, BigDecimal.ZERO, "Binance Testnet fee execution=$executionId")
                    journal(txId, "BINANCE_TESTNET_FEES", feeAsset, BigDecimal.ZERO, externalFee, "Binance Testnet external fee execution=$executionId")
                }
                assertTxBalanced(txId)
                applyPosition(userId, symbol, baseDebit.negate(), netQuote.divide(baseDebit, 18, java.math.RoundingMode.HALF_UP))
            }
            true
        }
    }

}
