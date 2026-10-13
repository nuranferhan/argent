package com.argent.market

import com.argent.common.domain.Instruments
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
})
