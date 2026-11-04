package com.argent.gateway.market

import com.argent.market.MarketCandle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database

class CandleStoreTest : FunSpec({
    test("closed candles survive store reconstruction and keep stable key") {
        val database =
            Database.connect(
                "jdbc:h2:mem:candles_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        val store = CandleStore(database).also { it.initSchema() }
        val candle = MarketCandle("BTCUSDT", "1m", 60_000, 120_000, "10", "12", "9", "11", "5")

        store.persistClosed(listOf(candle))
        CandleStore(database).history("btcusdt", "1m") shouldBe listOf(candle)

        store.persistClosed(listOf(candle.copy(close = "11.5")))
        CandleStore(database).history("BTCUSDT", "1m") shouldBe listOf(candle.copy(close = "11.5"))
    }

    test("retention removes expired minute and long-interval candles only") {
        val database =
            Database.connect(
                "jdbc:h2:mem:candle_retention_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        val store = CandleStore(database).also { it.initSchema() }
        val now = 1_800_000_000_000L
        val day = 24 * 60 * 60 * 1_000L
        store.persistClosed(
            listOf(
                MarketCandle("BTCUSDT", "1m", now - 31 * day, now - 31 * day + 60_000, "1", "1", "1", "1", "0"),
                MarketCandle("BTCUSDT", "1m", now - 29 * day, now - 29 * day + 60_000, "2", "2", "2", "2", "0"),
                MarketCandle("BTCUSDT", "1d", now - 6 * 365 * day, now - 6 * 365 * day + day, "3", "3", "3", "3", "0"),
                MarketCandle("BTCUSDT", "1d", now - 4 * 365 * day, now - 4 * 365 * day + day, "4", "4", "4", "4", "0"),
            ),
        )

        store.prune(now)

        store.history("BTCUSDT", "1m").map { it.close } shouldBe listOf("2")
        store.history("BTCUSDT", "1d").map { it.close } shouldBe listOf("4")
    }
})
