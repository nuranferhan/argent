package com.argent.gateway.trading

import com.argent.engine.MatchingEngine
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.order.OrderRepository
import com.argent.ledger.LedgerService
import com.argent.market.MarketDataService
import com.argent.risk.RiskService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database
import java.math.BigDecimal

class TradingServiceTest : FunSpec({

    fun harness(): Triple<TradingService, LedgerService, MatchingEngine> {
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:trade_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                maximumPoolSize = 8
                isAutoCommit = false
            }
        )
        val db = Database.connect(ds)
        val ledger = LedgerService(ds).also { it.initSchema() }
        val orders = OrderRepository(db).also { it.initSchema() }
        val engine = MatchingEngine()
        val market = MarketDataService().also { it.seedLastPrice("THYAO", BigDecimal("310")) }
        val risk = RiskService(ledger)
        return Triple(TradingService(engine, ledger, risk, orders, market), ledger, engine)
    }

    test("end to end match settles both books and keeps ledger invariant") {
        val (trading, ledger, engine) = harness()
        ledger.deposit("buyer", "TRY", BigDecimal("100000"), "db")
        ledger.deposit("seller", "THYAO", BigDecimal("100"), "ds")
        val sell = trading.placeOrder(
            "seller",
            PlaceOrderRequest("THYAO", "SELL", "LIMIT", "310", "10", "s1")
        )
        sell.accepted shouldBe true
        val buy = trading.placeOrder(
            "buyer",
            PlaceOrderRequest("THYAO", "BUY", "LIMIT", "310", "10", "b1")
        )
        buy.accepted shouldBe true
        buy.status shouldBe "FILLED"
        buy.trades.size shouldBe 1
        ledger.getBalance("buyer", "THYAO").availableBd() shouldBe BigDecimal("10")
        ledger.getBalance("seller", "TRY").availableBd() shouldBe BigDecimal("3100")
        ledger.verifyInvariant() shouldBe true
        engine.shutdown()
    }

    test("idempotency key returns the same order") {
        val (trading, ledger, engine) = harness()
        ledger.deposit("u", "TRY", BigDecimal("100000"), "d")
        val a = trading.placeOrder("u", PlaceOrderRequest("THYAO", "BUY", "LIMIT", "300", "1", "same-key"))
        val b = trading.placeOrder("u", PlaceOrderRequest("THYAO", "BUY", "LIMIT", "300", "1", "same-key"))
        a.orderId shouldBe b.orderId
        b.reason shouldBe "idempotent replay"
        engine.shutdown()
    }

    test("insufficient funds is rejected before the book") {
        val (trading, _, engine) = harness()
        val result = trading.placeOrder(
            "empty",
            PlaceOrderRequest("THYAO", "BUY", "LIMIT", "310", "1", "x")
        )
        result.accepted shouldBe false
        result.status shouldBe "REJECTED"
        engine.shutdown()
    }
})
