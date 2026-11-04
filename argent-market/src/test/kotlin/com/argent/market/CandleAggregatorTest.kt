package com.argent.market

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CandleAggregatorTest : FunSpec({
    test("aggregates a known tick sequence into OHLCV and emits the closed minute") {
        val emitted = mutableListOf<List<MarketCandle>>()
        val aggregator = CandleAggregator { batch -> emitted.add(batch) }
        val start = 1_800_000_000_000L
        aggregator.accept(TickerUpdate("BTCUSDT", "10", volume24h = "100", source = "test", timestamp = start + 1_000))
        aggregator.accept(TickerUpdate("BTCUSDT", "12", volume24h = "103", source = "test", timestamp = start + 20_000))
        aggregator.accept(TickerUpdate("BTCUSDT", "9", volume24h = "105", source = "test", timestamp = start + 59_000))

        val closed = aggregator.accept(TickerUpdate("BTCUSDT", "11", volume24h = "109", source = "test", timestamp = start + 60_000))
            .single { it.interval == "1m" }
        closed.open shouldBe "10"
        closed.high shouldBe "12"
        closed.low shouldBe "9"
        closed.close shouldBe "9"
        closed.volume shouldBe "5"
        closed.openTime shouldBe start
        emitted.single().contains(closed) shouldBe true
    }

    test("ignores out-of-order ticks and exposes supported intervals") {
        val aggregator = CandleAggregator()
        val start = 1_800_000_000_000L
        aggregator.accept(TickerUpdate("ETHUSDT", "10", source = "test", timestamp = start + 65_000))
        aggregator.accept(TickerUpdate("ETHUSDT", "1", source = "test", timestamp = start + 10_000))
        val closed = aggregator.accept(TickerUpdate("ETHUSDT", "12", source = "test", timestamp = start + 120_000))

        closed.single { it.interval == "1m" }.close shouldBe "10"
        CandleInterval.fromCode("15m") shouldBe CandleInterval.FIFTEEN_MINUTES
        CandleInterval.fromCode("bad") shouldBe null
    }

    test("derives a five minute candle from completed one minute candles") {
        val aggregator = CandleAggregator()
        val start = 1_800_000_000_000L
        for (minute in 0L..5L) {
            aggregator.accept(
                TickerUpdate("BTCUSDT", (100 + minute).toString(), volume24h = (100 + minute).toString(),
                    source = "test", timestamp = start + minute * 60_000 + 1_000)
            )
        }
        val fiveMinute = aggregator.accept(
            TickerUpdate("BTCUSDT", "106", volume24h = "106", source = "test", timestamp = start + 6 * 60_000)
        ).single { it.interval == "5m" }
        fiveMinute.openTime shouldBe start
        fiveMinute.open shouldBe "100"
        fiveMinute.high shouldBe "104"
        fiveMinute.low shouldBe "100"
        fiveMinute.close shouldBe "104"
        fiveMinute.volume shouldBe "4"
    }
})
