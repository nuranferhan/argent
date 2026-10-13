package com.argent.gateway.mm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.argent.engine.MatchingEngine
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.trading.TradingService
import com.argent.ledger.LedgerService
import com.argent.market.MarketDataService
import com.argent.risk.RiskService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.math.BigDecimal

class MarketMakerControlStoreTest : FunSpec({
    test("kill switch state survives service reconstruction") {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:mm_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 2
        })
        try {
            val database = Database.connect(dataSource)
            val firstInstance = MarketMakerControlStore(database).also { it.initSchema() }
            firstInstance.isEnabled() shouldBe true
            firstInstance.setEnabled(false)

            MarketMakerControlStore(database).isEnabled() shouldBe false
            firstInstance.setEnabled(true)
            MarketMakerControlStore(database).isEnabled() shouldBe true
        } finally {
            dataSource.close()
        }
    }

    test("concurrent kill and resume leaves market maker stopped with its orders cancelled") {
        runBlocking {
            val dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:mm_race_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                maximumPoolSize = 8
                isAutoCommit = false
            })
            val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val engine = MatchingEngine()
            val market = MarketDataService()
            try {
                val database = Database.connect(dataSource)
                val ledger = LedgerService(dataSource).also { it.initSchema() }
                val orders = OrderRepository(database).also { it.initSchema() }
                val control = MarketMakerControlStore(database).also { it.initSchema() }
                ledger.deposit("mm-user", "TRY", BigDecimal("1000"), "mm-funding")
                val trading = TradingService(engine, ledger, RiskService(ledger), orders, market)
                val makerOrder = trading.placeOrder(
                    "mm-user",
                    PlaceOrderRequest("THYAO", "BUY", "LIMIT", "100", "1", "mm-open-order")
                )
                makerOrder.accepted shouldBe true
                val provider = LiquidityProvider(
                    userId = "mm-user",
                    trading = trading,
                    engine = engine,
                    market = market,
                    orders = orders,
                    ledger = ledger,
                    scope = scope,
                    controlStore = control
                )

                coroutineScope {
                    (0 until 24).map { index ->
                        async(Dispatchers.Default) {
                            if (index % 2 == 0) provider.resume() else provider.killSwitch()
                        }
                    }.forEach { it.await() }
                }
                provider.killSwitch()

                control.isEnabled() shouldBe false
                orders.findActiveByUser("mm-user") shouldBe emptyList()
                ledger.getBalance("mm-user", "TRY").availableBd() shouldBe BigDecimal("1000")
            } finally {
                scope.cancel()
                engine.shutdown()
                market.stop()
                dataSource.close()
            }
        }
    }
})
