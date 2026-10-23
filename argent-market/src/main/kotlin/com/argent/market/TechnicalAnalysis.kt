package com.argent.market

import com.argent.common.domain.Instruments
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/** Descriptive indicators calculated from recent ticker observations, not candle data. */
@Serializable
data class TechnicalAnalysisSnapshot(
    val symbol: String,
    val currentPrice: String? = null,
    val sma20: String? = null,
    val sma50: String? = null,
    val rsi14: String? = null,
    val change20Pct: String? = null,
    val support30: String? = null,
    val resistance30: String? = null,
    val sampleCount: Int = 0,
    val trend: String = "INSUFFICIENT_DATA",
    val source: String? = null,
    val timestamp: Long = 0
)

object TechnicalAnalysis {
    fun calculate(symbol: String, observations: List<TickerUpdate>): TechnicalAnalysisSnapshot {
        val instrument = Instruments.get(symbol) ?: return TechnicalAnalysisSnapshot(symbol = symbol)
        val samples = observations.mapNotNull { update ->
            update.price.toBigDecimalOrNull()?.let { update to it }
        }
        val latest = samples.lastOrNull() ?: return TechnicalAnalysisSnapshot(symbol = instrument.symbol)
        val values = samples.map { it.second }
        val current = latest.second
        val sma20 = average(values, 20)
        val sma50 = average(values, 50)
        val rsi = rsi14(values)
        val change = if (values.size >= 21) {
            val base = values[values.lastIndex - 20]
            if (base.signum() == 0) null else current.subtract(base)
                .multiply(HUNDRED)
                .divide(base, 6, RoundingMode.HALF_UP)
        } else null
        val recent = values.takeLast(30)
        val trend = when {
            sma20 == null || sma50 == null -> "INSUFFICIENT_DATA"
            current > sma20 && sma20 > sma50 -> "UPWARD"
            current < sma20 && sma20 < sma50 -> "DOWNWARD"
            else -> "MIXED"
        }
        return TechnicalAnalysisSnapshot(
            symbol = instrument.symbol,
            currentPrice = format(current, instrument.priceScale),
            sma20 = sma20?.let { format(it, instrument.priceScale) },
            sma50 = sma50?.let { format(it, instrument.priceScale) },
            rsi14 = rsi?.setScale(1, RoundingMode.HALF_UP)?.toPlainString(),
            change20Pct = change?.setScale(2, RoundingMode.HALF_UP)?.toPlainString(),
            support30 = recent.minOrNull()?.let { format(it, instrument.priceScale) },
            resistance30 = recent.maxOrNull()?.let { format(it, instrument.priceScale) },
            sampleCount = values.size,
            trend = trend,
            source = latest.first.source,
            timestamp = latest.first.timestamp
        )
    }

    private fun average(values: List<BigDecimal>, period: Int): BigDecimal? {
        if (values.size < period) return null
        val window = values.takeLast(period)
        return window.fold(BigDecimal.ZERO) { sum, value -> sum.add(value) }
            .divide(BigDecimal(period), 12, RoundingMode.HALF_UP)
    }

    private fun rsi14(values: List<BigDecimal>): BigDecimal? {
        if (values.size < 15) return null
        var gains = BigDecimal.ZERO
        var losses = BigDecimal.ZERO
        val window = values.takeLast(15)
        for (i in 1 until window.size) {
            val change = window[i].subtract(window[i - 1])
            if (change.signum() > 0) gains = gains.add(change)
            if (change.signum() < 0) losses = losses.add(change.abs())
        }
        if (losses.signum() == 0) return if (gains.signum() == 0) BigDecimal("50") else HUNDRED
        val relativeStrength = gains.divide(losses, 12, RoundingMode.HALF_UP)
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(relativeStrength), 6, RoundingMode.HALF_UP))
    }

    private fun format(value: BigDecimal, scale: Int): String =
        value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private val HUNDRED = BigDecimal("100")
}
