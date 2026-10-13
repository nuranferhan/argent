package com.argent.ledger

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal

class LedgerInvariantTest : FunSpec({

    fun ledger(): LedgerService {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:argent_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        return LedgerService(ds).also { it.initSchema() }
    }

    test("deposit is double-entry balanced") {
        val l = ledger()
        l.deposit("u1", "TRY", BigDecimal("1000"), "dep-1")
        l.verifyInvariant() shouldBe true
        l.getBalance("u1", "TRY").available shouldBe "1000"
    }

    test("idempotent deposit does not double credit") {
        val l = ledger()
        l.deposit("u1", "TRY", BigDecimal("100"), "same")
        l.deposit("u1", "TRY", BigDecimal("100"), "same")
        l.getBalance("u1", "TRY").availableBd() shouldBe BigDecimal("100")
        l.verifyInvariant() shouldBe true
    }

    test("idempotency keys are scoped to the account") {
        val l = ledger()
        l.deposit("u1", "TRY", BigDecimal("100"), "same")
        l.deposit("u2", "TRY", BigDecimal("250"), "same")
        l.getBalance("u1", "TRY").availableBd() shouldBe BigDecimal("100")
        l.getBalance("u2", "TRY").availableBd() shouldBe BigDecimal("250")
        l.verifyInvariant() shouldBe true
    }

    test("hold then release restores available") {
        val l = ledger()
        l.deposit("u1", "TRY", BigDecimal("500"), "d")
        l.hold("u1", "TRY", BigDecimal("200"), "ord-1") shouldBe true
        l.getBalance("u1", "TRY").availableBd() shouldBe BigDecimal("300")
        l.getBalance("u1", "TRY").heldBd() shouldBe BigDecimal("200")
        l.releaseRemainingHold("ord-1")
        l.getBalance("u1", "TRY").availableBd() shouldBe BigDecimal("500")
        l.getBalance("u1", "TRY").heldBd() shouldBe BigDecimal.ZERO
        l.verifyInvariant() shouldBe true
    }

    test("trade settlement keeps invariant and updates positions") {
        val l = ledger()
        l.deposit("buyer", "TRY", BigDecimal("10000"), "db")
        l.deposit("seller", "THYAO", BigDecimal("100"), "ds")
        l.hold("buyer", "TRY", BigDecimal("3100"), "buy") shouldBe true
        l.hold("seller", "THYAO", BigDecimal("10"), "sell") shouldBe true
        l.settleTrade(
            tradeId = "t1",
            symbol = "THYAO",
            buyUserId = "buyer",
            sellUserId = "seller",
            baseAsset = "THYAO",
            quoteAsset = "TRY",
            quantity = BigDecimal("10"),
            price = BigDecimal("310"),
            buyOrderId = "buy",
            sellOrderId = "sell"
        )
        l.verifyInvariant() shouldBe true
        l.getBalance("buyer", "THYAO").availableBd() shouldBe BigDecimal("10")
        l.getBalance("seller", "TRY").availableBd() shouldBe BigDecimal("3100")
        l.getPosition("buyer", "THYAO").quantityBd() shouldBe BigDecimal("10")
        l.getPosition("seller", "THYAO").quantityBd() shouldBe BigDecimal("-10")
    }

    test("settlement is idempotent on trade id") {
        val l = ledger()
        l.deposit("buyer", "TRY", BigDecimal("20000"), "db2")
        l.deposit("seller", "THYAO", BigDecimal("20"), "ds2")
        l.hold("buyer", "TRY", BigDecimal("6200"), "buy2") shouldBe true
        l.hold("seller", "THYAO", BigDecimal("20"), "sell2") shouldBe true
        repeat(2) {
            l.settleTrade(
                tradeId = "t-idem",
                symbol = "THYAO",
                buyUserId = "buyer",
                sellUserId = "seller",
                baseAsset = "THYAO",
                quoteAsset = "TRY",
                quantity = BigDecimal("10"),
                price = BigDecimal("310"),
                buyOrderId = "buy2",
                sellOrderId = "sell2"
            )
        }
        l.getBalance("buyer", "THYAO").availableBd() shouldBe BigDecimal("10")
        l.verifyInvariant() shouldBe true
    }

    test("withdraw rejects when funds are held") {
        val l = ledger()
        l.deposit("u1", "USDT", BigDecimal("50"), "d")
        l.hold("u1", "USDT", BigDecimal("40"), "o")
        runCatching { l.withdraw("u1", "USDT", BigDecimal("20")) }.isFailure shouldBe true
        l.withdraw("u1", "USDT", BigDecimal("10")).availableBd() shouldBe BigDecimal.ZERO
        l.verifyInvariant() shouldBe true
    }

    test("event delivery failure after committed settlement is safe to replay") {
        var failOnce = true
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:failure_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        val l = LedgerService(ds) { event ->
            if (event is com.argent.common.events.SettlementCompleted && failOnce) {
                failOnce = false
                error("injected event-broker outage after commit")
            }
        }.also { it.initSchema() }
        l.deposit("buyer", "TRY", BigDecimal("5000"), "seed-buyer")
        l.deposit("seller", "THYAO", BigDecimal("10"), "seed-seller")
        l.hold("buyer", "TRY", BigDecimal("2000"), "buy-order") shouldBe true
        l.hold("seller", "THYAO", BigDecimal("2"), "sell-order") shouldBe true

        runCatching {
            l.settleTrade("trade-failover", "THYAO", "buyer", "seller", "THYAO", "TRY", BigDecimal("2"), BigDecimal("1000"), "buy-order", "sell-order")
        }.isFailure shouldBe true
        l.verifyInvariant() shouldBe true
        l.settleTrade("trade-failover", "THYAO", "buyer", "seller", "THYAO", "TRY", BigDecimal("2"), BigDecimal("1000"), "buy-order", "sell-order")
        l.getBalance("buyer", "THYAO").availableBd() shouldBe BigDecimal("2")
        l.getBalance("seller", "TRY").availableBd() shouldBe BigDecimal("2000")
        l.verifyInvariant() shouldBe true
        ds.close()
    }

    test("concurrent holds cannot reserve more than the funded balance") {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:concurrent_hold_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 16
            isAutoCommit = false
        })
        try {
            val l = LedgerService(ds).also { it.initSchema() }
            l.deposit("u-concurrent", "TRY", BigDecimal("1000"), "concurrent-seed")

            val results = runBlocking {
                coroutineScope {
                    (0 until 40).map { index ->
                        async(Dispatchers.IO) {
                            l.hold("u-concurrent", "TRY", BigDecimal("25"), "concurrent-order-$index")
                        }
                    }.awaitAll()
                }
            }

            results.count { it } shouldBe 40
            val balance = l.getBalance("u-concurrent", "TRY")
            balance.availableBd() shouldBe BigDecimal.ZERO
            balance.heldBd() shouldBe BigDecimal("1000")
            l.verifyInvariant() shouldBe true
        } finally {
            ds.close()
        }
    }

    test("account balances reconcile with journal net after settlement and detect drift") {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:reconciliation_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        try {
            val l = LedgerService(ds).also { it.initSchema() }
            l.deposit("recon-buyer", "TRY", BigDecimal("5000"), "recon-buyer-seed")
            l.deposit("recon-seller", "THYAO", BigDecimal("10"), "recon-seller-seed")
            l.hold("recon-buyer", "TRY", BigDecimal("2000"), "recon-buy-order") shouldBe true
            l.hold("recon-seller", "THYAO", BigDecimal("2"), "recon-sell-order") shouldBe true
            l.settleTrade(
                "recon-trade", "THYAO", "recon-buyer", "recon-seller", "THYAO", "TRY",
                BigDecimal("2"), BigDecimal("1000"), "recon-buy-order", "recon-sell-order"
            )

            l.verifyInvariant() shouldBe true
            l.verifyReconciliation() shouldBe true
            val balancedReport = l.reconciliationReport()
            balancedReport.ok shouldBe true
            balancedReport.checkedAccountAssets shouldBe 4
            balancedReport.mismatches shouldBe emptyList()

            ds.connection.use { connection ->
                connection.prepareStatement(
                    "UPDATE balances SET available = available + 1 WHERE user_id = ? AND asset = ?"
                ).use { statement ->
                    statement.setString(1, "recon-buyer")
                    statement.setString(2, "TRY")
                    statement.executeUpdate() shouldBe 1
                }
                connection.commit()
            }
            val driftReport = l.reconciliationReport()
            driftReport.ok shouldBe false
            driftReport.mismatches.map { it.account to it.asset } shouldBe listOf("recon-buyer" to "TRY")
            l.verifyReconciliation() shouldBe false
            l.verifyInvariant() shouldBe true
        } finally {
            ds.close()
        }
    }
})
