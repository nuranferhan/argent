package com.argent.market

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

enum class CandleInterval(val code: String, val millis: Long) {
    ONE_MINUTE("1m", 60_000),
    FIVE_MINUTES("5m", 5 * 60_000),
    FIFTEEN_MINUTES("15m", 15 * 60_000),
    ONE_HOUR("60m", 60 * 60_000),
    ONE_DAY("1d", Duration.ofDays(1).toMillis()),
    ;

    companion object {
        fun fromCode(code: String): CandleInterval? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class MarketCandle(
    val symbol: String,
    val interval: String,
    val openTime: Long,
    val closeTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
)

/** Builds 1m candles from ticks, then derives larger intervals from closed 1m candles. */
class CandleAggregator(private val onClosed: (List<MarketCandle>) -> Unit = {}) {
    private data class Key(val symbol: String, val interval: CandleInterval)

    private data class Building(
        val openTime: Long,
        val open: BigDecimal,
        var high: BigDecimal,
        var low: BigDecimal,
        var close: BigDecimal,
        var volume: BigDecimal,
    ) {
        fun add(
            priceOpen: BigDecimal,
            priceHigh: BigDecimal,
            priceLow: BigDecimal,
            priceClose: BigDecimal,
            addedVolume: BigDecimal,
        ) {
            high = high.max(priceHigh)
            low = low.min(priceLow)
            close = priceClose
            volume += addedVolume
        }

        fun candle(
            symbol: String,
            interval: CandleInterval,
        ) = MarketCandle(
            symbol = symbol,
            interval = interval.code,
            openTime = openTime,
            closeTime = openTime + interval.millis,
            open = open.toPlainString(),
            high = high.toPlainString(),
            low = low.toPlainString(),
            close = close.toPlainString(),
            volume = volume.toPlainString(),
        )
    }

    private val building = ConcurrentHashMap<Key, Building>()
    private val previousCumulativeVolume = ConcurrentHashMap<String, BigDecimal>()

    @Synchronized
    fun accept(tick: TickerUpdate): List<MarketCandle> {
        val price = tick.price.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return emptyList()
        val symbol = tick.symbol.uppercase()
        val minute = CandleInterval.ONE_MINUTE
        val key = Key(symbol, minute)
        val volumeDelta = volumeDelta(symbol, tick.volume24h?.toBigDecimalOrNull())
        val minuteStart = tick.timestamp - tick.timestamp % minute.millis
        val current = building[key]
        val closed = mutableListOf<MarketCandle>()
        when {
            current == null -> building[key] = Building(minuteStart, price, price, price, price, volumeDelta)
            minuteStart < current.openTime -> return emptyList()
            minuteStart == current.openTime -> current.add(price, price, price, price, volumeDelta)
            else -> {
                val completedMinute = current.candle(symbol, minute)
                closed += completedMinute
                building[key] = Building(minuteStart, price, price, price, price, volumeDelta)
                closed += deriveFrom(completedMinute)
            }
        }
        if (closed.isNotEmpty()) onClosed(closed)
        return closed
    }

    private fun deriveFrom(minute: MarketCandle): List<MarketCandle> {
        val result = mutableListOf<MarketCandle>()
        val open = minute.open.toBigDecimal()
        val high = minute.high.toBigDecimal()
        val low = minute.low.toBigDecimal()
        val close = minute.close.toBigDecimal()
        val volume = minute.volume.toBigDecimal()
        for (interval in CandleInterval.entries.drop(1)) {
            val key = Key(minute.symbol, interval)
            val start = minute.openTime - minute.openTime % interval.millis
            val current = building[key]
            when {
                current == null -> building[key] = Building(start, open, high, low, close, volume)
                start < current.openTime -> Unit
                start == current.openTime -> current.add(open, high, low, close, volume)
                else -> {
                    result += current.candle(minute.symbol, interval)
                    building[key] = Building(start, open, high, low, close, volume)
                }
            }
        }
        return result
    }

    private fun volumeDelta(
        symbol: String,
        cumulative: BigDecimal?,
    ): BigDecimal {
        if (cumulative == null || cumulative.signum() < 0) return BigDecimal.ZERO
        val previous = previousCumulativeVolume.put(symbol, cumulative)
        return when {
            previous == null -> BigDecimal.ZERO
            cumulative < previous -> cumulative
            else -> cumulative - previous
        }
    }
}
