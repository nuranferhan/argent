package com.argent.market

import com.argent.common.domain.Instruments
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import io.kotest.assertions.throwables.shouldThrow
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class MarketDataServiceTest : FunSpec({
    test("keyless simulated feed publishes every supported instrument") {
        runTest {
            val service = MarketDataService(scope = backgroundScope, feedMode = MarketFeedMode.SIMULATED)
            service.start()
            runCurrent()

            Instruments.all.forEach { instrument ->
                val ticker = service.lastTicker(instrument.symbol)
                ticker?.source shouldBe "argent-sim"
                ticker?.symbol shouldBe instrument.symbol
            }
            service.cryptoFeedStatus() shouldBe "SIMULATED"
            service.stop()
        }
    }

    test("stochastic price path is reproducible and respects instrument tick precision") {
        val instrument = Instruments.require("BTCUSDT")
        val left = StochasticPriceGenerator(instrument, 42L)
        val right = StochasticPriceGenerator(instrument, 42L)
        var leftPrice = BigDecimal("65000")
        var rightPrice = BigDecimal("65000")
        repeat(1_000) {
            leftPrice = left.nextPrice(leftPrice)
            rightPrice = right.nextPrice(rightPrice)
            leftPrice shouldBe rightPrice
            leftPrice.scale() shouldBe instrument.priceScale
            (leftPrice >= BigDecimal(instrument.tickSize)) shouldBe true
        }
    }

    test("caller supplied BIST CSV replays in order and simulator stays separate") {
        runTest {
            val service = MarketDataService(scope = this)
            val csv = """timestamp,symbol,price,volume24h
                |2026-01-02T10:00:00Z,THYAO,300.25,1000
                |2026-01-02T10:01:00Z,THYAO,300.50,1250
            """.trimMargin()

            service.startBistHistoricalReplay(csv, speed = 100_000).state shouldBe "RUNNING"
            advanceUntilIdle()

            service.bistHistoricalReplayStatus()?.state shouldBe "COMPLETED"
            service.bistHistoricalReplayStatus()?.processed shouldBe 2
            service.history("THYAO").map { it.price } shouldBe listOf("300.25", "300.5")
            service.history("THYAO").all { it.source == "bist-csv-replay" } shouldBe true
        }
    }

    test("BIST replay rejects unsupported symbols and malformed CSV") {
        val service = MarketDataService()
        shouldThrow<IllegalArgumentException> {
            service.startBistHistoricalReplay("timestamp,symbol,price\n1,BTCUSDT,10", speed = 1000)
        }
        shouldThrow<IllegalArgumentException> {
            service.startBistHistoricalReplay("timestamp,symbol,price\nnot-a-time,THYAO,10", speed = 1000)
        }
        service.stop()
    }

    test("Spot Testnet adapter fails closed without both credentials") {
        val adapter = BinanceSpotTestnet(apiKey = null, secret = null)
        shouldThrow<IllegalStateException> {
            kotlinx.coroutines.test.runTest {
                adapter.placeOrder(TestnetOrderRequest("BTCUSDT", "BUY", "LIMIT", "0.001", "30000"))
            }
        }
        adapter.close()
    }
})
