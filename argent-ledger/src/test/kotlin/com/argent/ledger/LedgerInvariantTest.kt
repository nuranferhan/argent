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

    test("simulated derivative expiry closes positions once and balances the writeoff") {
        val l = ledger()
        l.deposit("buyer", "USD", BigDecimal("1000"), "expiry-buyer-cash")
        l.deposit("seller", "OPTION-X", BigDecimal("2"), "expiry-seller-contract")
        l.hold("buyer", "USD", BigDecimal("200"), "expiry-buy") shouldBe true
        l.hold("seller", "OPTION-X", BigDecimal("2"), "expiry-sell") shouldBe true
        l.settleTrade("expiry-trade", "OPTION-X", "buyer", "seller", "OPTION-X", "USD", BigDecimal("2"), BigDecimal("100"), "expiry-buy", "expiry-sell")

        l.expireWorthlessDerivative("OPTION-X") shouldBe 2
        l.expireWorthlessDerivative("OPTION-X") shouldBe 0
        l.getPosition("buyer", "OPTION-X").quantityBd() shouldBe BigDecimal.ZERO
        l.getPosition("buyer", "OPTION-X").realizedPnlBd() shouldBe BigDecimal("-200")
        l.getPosition("seller", "OPTION-X").quantityBd() shouldBe BigDecimal.ZERO
        l.getPosition("seller", "OPTION-X").realizedPnlBd() shouldBe BigDecimal("200")
        l.getBalance("buyer", "OPTION-X").availableBd() shouldBe BigDecimal.ZERO
        l.verifyInvariant() shouldBe true
        l.verifyReconciliation() shouldBe true
    }

    test("recovery repairs a missing settlement marker from committed legacy postings") {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:legacy_settlement_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        val l = LedgerService(ds).also { it.initSchema() }
        l.deposit("legacy-buyer", "TRY", BigDecimal("5000"), "legacy-buyer-seed")
        l.deposit("legacy-seller", "THYAO", BigDecimal("10"), "legacy-seller-seed")
        l.hold("legacy-buyer", "TRY", BigDecimal("1550"), "legacy-buy") shouldBe true
        l.hold("legacy-seller", "THYAO", BigDecimal("5"), "legacy-sell") shouldBe true
        l.settleTrade(
            "legacy-trade", "THYAO", "legacy-buyer", "legacy-seller", "THYAO", "TRY",
            BigDecimal("5"), BigDecimal("310"), "legacy-buy", "legacy-sell"
        )
        val buyerTryBeforeReplay = l.getBalance("legacy-buyer", "TRY").availableBd()
        val buyerSharesBeforeReplay = l.getBalance("legacy-buyer", "THYAO").availableBd()
        ds.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM settlements WHERE trade_id = 'legacy-trade'")
                statement.executeUpdate("DELETE FROM order_holds WHERE order_id IN ('legacy-buy', 'legacy-sell')")
            }
        }

        l.settleTrade(
            "legacy-trade", "THYAO", "legacy-buyer", "legacy-seller", "THYAO", "TRY",
            BigDecimal("5"), BigDecimal("310"), "legacy-buy", "legacy-sell"
        )

        l.isTradeSettled("legacy-trade") shouldBe true
        l.getBalance("legacy-buyer", "TRY").availableBd() shouldBe buyerTryBeforeReplay
        l.getBalance("legacy-buyer", "THYAO").availableBd() shouldBe buyerSharesBeforeReplay
        l.verifyInvariant() shouldBe true
        ds.close()
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

    test("withdraw is idempotent and rejects invalid amounts or changed replay payload") {
        val l = ledger()
        l.deposit("withdraw-user", "USD", BigDecimal("40"), "seed")
        l.withdraw("withdraw-user", "USD", BigDecimal("10"), "withdraw-1").availableBd() shouldBe BigDecimal("30")
        l.withdraw("withdraw-user", "USD", BigDecimal("10"), "withdraw-1").availableBd() shouldBe BigDecimal("30")
        runCatching { l.withdraw("withdraw-user", "USD", BigDecimal("11"), "withdraw-1") }.isFailure shouldBe true
        runCatching { l.deposit("withdraw-user", "USD", BigDecimal.ZERO) }.isFailure shouldBe true
        runCatching { l.withdraw("withdraw-user", "USD", BigDecimal.ZERO) }.isFailure shouldBe true
        runCatching { l.withdraw("withdraw-user", "USD", BigDecimal("31")) }.isFailure shouldBe true
        l.listBalances("withdraw-user").single().availableBd() shouldBe BigDecimal("30")
        l.verifyInvariant() shouldBe true
    }

    test("hold is idempotent, insufficient funds do not reserve, and release is safe") {
        val l = ledger()
        l.deposit("hold-user", "TRY", BigDecimal("25"), "seed")
        l.hold("hold-user", "TRY", BigDecimal("20"), "order-1") shouldBe true
        l.hold("hold-user", "TRY", BigDecimal("20"), "order-1") shouldBe true
        l.hold("hold-user", "TRY", BigDecimal("10"), "order-2") shouldBe false
        l.activeHoldOrderIds() shouldBe listOf("order-1")
        l.releaseRemainingHold("missing-order")
        l.releaseRemainingHold("order-1")
        l.releaseRemainingHold("order-1")
        l.getBalance("hold-user", "TRY").availableBd() shouldBe BigDecimal("25")
        l.getBalance("hold-user", "TRY").heldBd() shouldBe BigDecimal.ZERO
        l.activeHoldOrderIds() shouldBe emptyList()
        l.verifyInvariant() shouldBe true
    }

    test("settlement releases unused holds and tracks closed and reversed positions") {
        val l = ledger()
        l.deposit("position-buyer", "TRY", BigDecimal("5000"), "cash")
        l.deposit("position-seller", "XYZ", BigDecimal("10"), "shares")
        l.hold("position-buyer", "TRY", BigDecimal("3000"), "buy-order") shouldBe true
        l.hold("position-seller", "XYZ", BigDecimal("5"), "sell-order") shouldBe true
        l.settleTrade(
            "position-open", "XYZ", "position-buyer", "position-seller", "XYZ", "TRY",
            BigDecimal("2"), BigDecimal("100"), "buy-order", "sell-order"
        )
        l.releaseRemainingHold("buy-order")
        l.releaseRemainingHold("sell-order")
        l.getPosition("position-buyer", "XYZ").quantityBd() shouldBe BigDecimal("2")
        l.listPositions("position-buyer").single().symbol shouldBe "XYZ"
        l.journalForTx("missing-tx") shouldBe emptyList()
        l.verifyInvariant() shouldBe true
        l.verifyReconciliation() shouldBe true
    }

    test("Binance testnet buy and sell settlements apply quote base and external fees") {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:testnet_fills_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        try {
            val l = LedgerService(ds).also { it.initSchema() }
            l.deposit("testnet-user", "USDT", BigDecimal("500"), "cash")
            l.deposit("testnet-user", "BTC", BigDecimal("2"), "coin")
            l.deposit("testnet-user", "BNB", BigDecimal("1"), "fee-token")

            l.hold("testnet-user", "USDT", BigDecimal("250"), "buy-quote") shouldBe true
            l.settleBinanceTestnetFill("buy-quote-fee", "buy-quote", "testnet-user", "BTCUSDT", "BUY", "BTC", "USDT", BigDecimal("1"), BigDecimal("100"), BigDecimal("0.5"), "USDT") shouldBe true
            l.hold("testnet-user", "USDT", BigDecimal("100"), "buy-base") shouldBe true
            l.settleBinanceTestnetFill("buy-base-fee", "buy-base", "testnet-user", "BTCUSDT", "BUY", "BTC", "USDT", BigDecimal("1"), BigDecimal("100"), BigDecimal("0.01"), "BTC") shouldBe true

            l.hold("testnet-user", "BTC", BigDecimal("1.1"), "sell-quote") shouldBe true
            l.settleBinanceTestnetFill("sell-quote-fee", "sell-quote", "testnet-user", "BTCUSDT", "SELL", "BTC", "USDT", BigDecimal("1"), BigDecimal("100"), BigDecimal("1"), "USDT") shouldBe true
            l.hold("testnet-user", "BTC", BigDecimal("1"), "sell-external") shouldBe true
            l.settleBinanceTestnetFill("sell-external-fee", "sell-external", "testnet-user", "BTCUSDT", "SELL", "BTC", "USDT", BigDecimal("1"), BigDecimal("100"), BigDecimal("0.01"), "BNB") shouldBe true
            listOf("buy-quote", "buy-base", "sell-quote", "sell-external").forEach(l::releaseRemainingHold)

            l.getBalance("testnet-user", "BTC").availableBd() shouldBe BigDecimal("1.99")
            l.getBalance("testnet-user", "USDT").availableBd() shouldBe BigDecimal("498.5")
            l.getBalance("testnet-user", "BNB").availableBd() shouldBe BigDecimal("0.99")
            l.getPosition("testnet-user", "BTCUSDT").quantityBd() shouldBe BigDecimal("-0.01")
            l.settleBinanceTestnetFill("buy-quote-fee", "buy-quote", "testnet-user", "BTCUSDT", "BUY", "BTC", "USDT", BigDecimal("1"), BigDecimal("100"), BigDecimal("0.5"), "USDT") shouldBe false
            l.verifyInvariant() shouldBe true
            l.verifyReconciliation() shouldBe true
        } finally {
            ds.close()
        }
    }

    test("Binance testnet settlement rejects invalid fee, side and unaffordable fills atomically") {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:testnet_invalid_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        try {
            val l = LedgerService(ds).also { it.initSchema() }
            l.deposit("testnet-invalid", "USDT", BigDecimal("100"), "cash")
            l.hold("testnet-invalid", "USDT", BigDecimal("50"), "buy") shouldBe true
            runCatching {
                l.settleBinanceTestnetFill("bad-side", "buy", "testnet-invalid", "BTCUSDT", "HOLD", "BTC", "USDT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, "USDT")
            }.isFailure shouldBe true
            runCatching {
                l.settleBinanceTestnetFill("bad-fee", "buy", "testnet-invalid", "BTCUSDT", "BUY", "BTC", "USDT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal("-1"), "USDT")
            }.isFailure shouldBe true
            runCatching {
                l.settleBinanceTestnetFill("too-large", "buy", "testnet-invalid", "BTCUSDT", "BUY", "BTC", "USDT", BigDecimal.ONE, BigDecimal("60"), BigDecimal.ZERO, "USDT")
            }.isFailure shouldBe true
            l.getBalance("testnet-invalid", "USDT").availableBd() shouldBe BigDecimal("50")
            l.getBalance("testnet-invalid", "USDT").heldBd() shouldBe BigDecimal("50")
            l.isTradeSettled("binance-testnet:too-large") shouldBe false
            l.verifyInvariant() shouldBe true
        } finally {
            ds.close()
        }
    }
})
