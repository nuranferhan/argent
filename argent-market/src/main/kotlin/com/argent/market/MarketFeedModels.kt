package com.argent.market

import com.argent.common.domain.AssetClass
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class TickerUpdate(
    val symbol: String,
    val price: String,
    val bid: String? = null,
    val ask: String? = null,
    val volume24h: String? = null,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class BistReplayStatus(
    val replayId: String,
    val state: String,
    val rows: Int,
    val processed: Int,
    val error: String? = null,
)

enum class MarketFeedMode {
    SIMULATED,
    BINANCE_PUBLIC,
    ISTFIN_BIST,
}

internal data class BistHistoricalRow(
    val timestamp: Long,
    val symbol: String,
    val price: String,
    val volume24h: String? = null,
)

/** Deterministic, instrument-aware sandbox price process; never a source of executable market prices. */
internal class StochasticPriceGenerator(
    private val instrument: com.argent.common.domain.Instrument,
    seed: Long,
) {
    private val priceRandom = java.util.Random(seed)
    private val volumeRandom = java.util.Random(seed xor 0x51A7L)
    private val volatility = if (instrument.assetClass == AssetClass.CRYPTO) 0.0012 else 0.00035

    fun nextPrice(previous: BigDecimal): BigDecimal {
        val logReturn = priceRandom.nextGaussian() * volatility
        return previous.multiply(BigDecimal.valueOf(kotlin.math.exp(logReturn)))
            .max(BigDecimal(instrument.tickSize))
            .setScale(instrument.priceScale, RoundingMode.HALF_UP)
    }

    fun nextVolume(): String =
        BigDecimal.valueOf(volumeRandom.nextDouble(100.0, 100_000.0))
            .setScale(instrument.qtyScale.coerceAtMost(6), RoundingMode.HALF_UP).toPlainString()
}

@Serializable
internal data class BinanceCombined(
    val stream: String? = null,
    val data: BinanceTicker? = null,
)

@Serializable
internal data class BinanceTicker(
    val s: String? = null,
    val c: String? = null,
    val b: String? = null,
    val a: String? = null,
    val v: String? = null,
)
