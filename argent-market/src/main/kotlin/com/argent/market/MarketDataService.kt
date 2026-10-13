package com.argent.market

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class TickerUpdate(
    val symbol: String,
    val price: String,
    val bid: String? = null,
    val ask: String? = null,
    val volume24h: String? = null,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MarketFeedMode { SIMULATED, BINANCE_PUBLIC }

/** Deterministic, instrument-aware sandbox price process; never a source of executable market prices. */
internal class StochasticPriceGenerator(
    private val instrument: com.argent.common.domain.Instrument,
    seed: Long
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

    fun nextVolume(): String = BigDecimal.valueOf(volumeRandom.nextDouble(100.0, 100_000.0))
        .setScale(instrument.qtyScale.coerceAtMost(6), RoundingMode.HALF_UP).toPlainString()
}

@Serializable
private data class BinanceCombined(
    val stream: String? = null,
    val data: BinanceTicker? = null
)

@Serializable
private data class BinanceTicker(
    val s: String? = null,
    val c: String? = null,
    val b: String? = null,
    val a: String? = null,
    val v: String? = null
)

class MarketDataService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val feedMode: MarketFeedMode = MarketFeedMode.SIMULATED
) {
    private val log = LoggerFactory.getLogger(MarketDataService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val _tickers = MutableSharedFlow<TickerUpdate>(replay = 32, extraBufferCapacity = 256)
    val tickers: SharedFlow<TickerUpdate> = _tickers
    private val lastPrices = ConcurrentHashMap<String, BigDecimal>()
    private val lastTickers = ConcurrentHashMap<String, TickerUpdate>()
    private val lastUpdatedAt = ConcurrentHashMap<String, Long>()
    private val history = ConcurrentHashMap<String, ArrayDeque<TickerUpdate>>()

    fun start() {
        when (feedMode) {
            MarketFeedMode.SIMULATED -> scope.launch { simulateAllInstruments() }
            MarketFeedMode.BINANCE_PUBLIC -> {
                Instruments.all.filter { it.assetClass == AssetClass.CRYPTO }.let { crypto ->
                    if (crypto.isNotEmpty()) {
                        scope.launch { connectBinance(crypto.map { it.symbol.lowercase() }) }
                    }
                }
                scope.launch {
                    simulateInstruments(Instruments.all.filter { it.assetClass == AssetClass.EQUITY })
                }
            }
        }
    }

    private suspend fun connectBinance(symbols: List<String>) {
        val streams = symbols.joinToString("/") { "$it@ticker" }
        val url = "wss://stream.binance.com:9443/stream?streams=$streams"
        var retryDelayMs = 1_000L
        while (true) {
            try {
                log.info("Connecting Binance public stream")
                client.webSocket(url) {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val parsed = runCatching { json.decodeFromString(BinanceCombined.serializer(), frame.readText()) }.getOrNull()
                        val data = parsed?.data ?: continue
                        val symbol = data.s ?: continue
                        val price = data.c ?: continue
                        publish(
                            TickerUpdate(
                                symbol = symbol,
                                price = price,
                                bid = data.b,
                                ask = data.a,
                                volume24h = data.v,
                                source = "binance"
                            )
                        )
                    }
                }
                log.warn("Binance stream closed; reconnecting in {}ms", retryDelayMs)
                retryDelayMs = 1_000L
            } catch (e: Exception) {
                log.warn("Binance disconnected: {} — retry in {}ms", e.message, retryDelayMs)
            }
            delay(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }

    private suspend fun simulateAllInstruments() = simulateInstruments(Instruments.all)

    /** Deterministic-seed geometric random walk for a reproducible, keyless paper market. */
    private suspend fun simulateInstruments(instruments: List<com.argent.common.domain.Instrument>) {
        val seeds = mapOf(
            "THYAO" to BigDecimal("312.50"),
            "GARAN" to BigDecimal("128.40"),
            "ASELS" to BigDecimal("98.70"),
            "EREGL" to BigDecimal("54.20"),
            "SISE" to BigDecimal("48.15"),
            "BTCUSDT" to BigDecimal("65000"),
            "ETHUSDT" to BigDecimal("3200"),
            "SOLUSDT" to BigDecimal("150")
        )
        val prices = instruments.associate { instrument ->
            instrument.symbol to (seeds[instrument.symbol] ?: BigDecimal("100"))
        }.toMutableMap()
        val generators = instruments.associate {
            it.symbol to StochasticPriceGenerator(it, 0xA26E17L + it.symbol.hashCode())
        }
        instruments.forEach { instrument ->
            val initial = prices.getValue(instrument.symbol)
            lastPrices[instrument.symbol] = initial
            lastUpdatedAt[instrument.symbol] = System.currentTimeMillis()
        }
        while (true) {
            for (instrument in instruments) {
                val symbol = instrument.symbol
                val last = prices.getValue(symbol)
                val generator = generators.getValue(symbol)
                val next = generator.nextPrice(last)
                prices[symbol] = next
                val halfSpread = BigDecimal(instrument.tickSize).multiply(BigDecimal("1.5"))
                val bid = next.subtract(halfSpread).max(BigDecimal(instrument.tickSize))
                    .setScale(instrument.priceScale, RoundingMode.HALF_UP)
                val ask = next.add(halfSpread).setScale(instrument.priceScale, RoundingMode.HALF_UP)
                publish(
                    TickerUpdate(
                        symbol = symbol,
                        price = next.toPlainString(),
                        bid = bid.toPlainString(),
                        ask = ask.toPlainString(),
                        volume24h = generator.nextVolume(),
                        source = "argent-sim"
                    )
                )
            }
            delay(500)
        }
    }

    private suspend fun publish(update: TickerUpdate) {
        lastPrices[update.symbol] = BigDecimal(update.price)
        lastTickers[update.symbol] = update
        lastUpdatedAt[update.symbol] = System.currentTimeMillis()
        val buf = history.computeIfAbsent(update.symbol) { ArrayDeque() }
        synchronized(buf) {
            buf.addLast(update)
            while (buf.size > 300) buf.removeFirst()
        }
        _tickers.emit(update)
    }

    fun lastPrice(symbol: String): BigDecimal? = lastPrices[symbol].takeIf { isFresh(symbol) }

    fun lastTicker(symbol: String): TickerUpdate? = lastTickers[symbol].takeIf { isFresh(symbol) }

    fun seedLastPrice(symbol: String, price: BigDecimal) {
        lastPrices[symbol] = price
        lastUpdatedAt[symbol] = System.currentTimeMillis()
    }

    fun allLastPrices(): Map<String, BigDecimal> = lastPrices.filterKeys(::isFresh)

    fun cryptoFeedStatus(): String {
        val cryptoSymbols = Instruments.all.filter { it.assetClass == AssetClass.CRYPTO }.map { it.symbol }
        if (cryptoSymbols.isEmpty()) return "UNAVAILABLE"
        if (cryptoSymbols.all { isFresh(it) && lastTickers[it]?.source == "binance" }) return "LIVE"
        if (cryptoSymbols.all { isFresh(it) && lastTickers[it]?.source == "argent-sim" }) return "SIMULATED"
        if (cryptoSymbols.any { lastTickers[it]?.source == "binance" }) return "STALE"
        return "CONNECTING"
    }

    private fun isFresh(symbol: String): Boolean =
        (System.currentTimeMillis() - (lastUpdatedAt[symbol] ?: 0L)) <= MAX_PRICE_AGE_MS

    fun history(symbol: String): List<TickerUpdate> {
        val buf = history[symbol] ?: return emptyList()
        synchronized(buf) { return buf.toList() }
    }

    fun stop() {
        scope.cancel()
        client.close()
    }

    companion object {
        private const val MAX_PRICE_AGE_MS = 15_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
