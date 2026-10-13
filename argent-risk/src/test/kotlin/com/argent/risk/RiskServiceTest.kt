package com.argent.risk

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import com.argent.ledger.LedgerService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class RiskServiceTest : FunSpec({

    fun risk(): Pair<RiskService, LedgerService> {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:risk_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        val ledger = LedgerService(ds).also { it.initSchema() }
        return RiskService(ledger) to ledger
    }

    test("buy is rejected without cash") {
        val (risk, _) = risk()
        val result = risk.checkOrder(
            Order(userId = "u", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "100", quantity = "10"),
            BigDecimal("100")
        )
        result.allowed shouldBe false
    }

    test("odd lot is rejected") {
        val (risk, ledger) = risk()
        ledger.deposit("u", "TRY", BigDecimal("10000"), "d")
        val result = risk.checkOrder(
            Order(userId = "u", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "100", quantity = "1.5"),
            BigDecimal("100")
        )
        result.allowed shouldBe false
    }

    test("buy is allowed when cash covers notional") {
        val (risk, ledger) = risk()
        ledger.deposit("u", "TRY", BigDecimal("10000"), "d")
        val result = risk.checkOrder(
            Order(userId = "u", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "100", quantity = "10"),
            BigDecimal("100")
        )
        result.allowed shouldBe true
        result.requiredAsset shouldBe "TRY"
        result.requiredAmount shouldBe BigDecimal("1000")
    }

    test("market buy pads notional") {
        val (risk, ledger) = risk()
        ledger.deposit("u", "USDT", BigDecimal("1000"), "d")
        val result = risk.checkOrder(
            Order(userId = "u", symbol = "BTCUSDT", side = OrderSide.BUY, type = OrderType.MARKET, quantity = "0.01"),
            BigDecimal("65000")
        )
        result.allowed shouldBe true
        result.requiredAmount shouldBe BigDecimal("65000").multiply(BigDecimal("0.01")).multiply(BigDecimal("1.05"))
    }

    test("concentration risk score is 100 for a single name") {
        val (risk, ledger) = risk()
        ledger.deposit("u", "TRY", BigDecimal("100000"), "d")
        ledger.deposit("c", "THYAO", BigDecimal("10"), "s")
        ledger.hold("u", "TRY", BigDecimal("3100"), "b")
        ledger.hold("c", "THYAO", BigDecimal("10"), "s1")
        ledger.settleTrade("t", "THYAO", "u", "c", "THYAO", "TRY", BigDecimal("10"), BigDecimal("310"), "b", "s1")
        val snap = risk.portfolio("u", mapOf("THYAO" to BigDecimal("310")))
        snap.riskScore shouldBe 100
        snap.marginCall shouldBe false
    }
})
