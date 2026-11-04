package com.argent.market

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import com.argent.common.domain.MarketHistoryLimits
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

class MarketDataService(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val feedMode: MarketFeedMode = MarketFeedMode.BINANCE_PUBLIC,
    private val istfinApiKey: String? = null,
    private val candleSink: ((List<MarketCandle>) -> Unit)? = null,
    private val simulationSeed: Long =
        System.getenv("MARKET_SIMULATION_SEED")?.toLongOrNull() ?: DEFAULT_SIMULATION_SEED,
) {
    private val log = LoggerFactory.getLogger(MarketDataService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val bistHttp = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val _tickers = MutableSharedFlow<TickerUpdate>(replay = 32, extraBufferCapacity = 256)
    val tickers: SharedFlow<TickerUpdate> = _tickers
    private val lastPrices = ConcurrentHashMap<String, BigDecimal>()
    private val lastTickers = ConcurrentHashMap<String, TickerUpdate>()
    private val lastUpdatedAt = ConcurrentHashMap<String, Long>()
    private val history = ConcurrentHashMap<String, ArrayDeque<TickerUpdate>>()
    private val bistReplay = BistHistoricalReplayService(scope, ::publish)
    private val closedCandles = Channel<List<MarketCandle>>(capacity = 1_024)
    private val candleAggregator =
        CandleAggregator { candles ->
            if (candleSink != null && closedCandles.trySend(candles).isFailure) {
                log.error("Closed-candle queue is full; persisted history may have a gap")
            }
        }

    fun start() {
        if (candleSink != null) {
            scope.launch {
                for (batch in closedCandles) {
                    var retryMs = 1_000L
                    while (true) {
                        try {
                            candleSink.invoke(batch)
                            break
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            log.error(
                                "Could not persist {} closed market candles; retry in {}ms",
                                batch.size,
                                retryMs,
                                failure,
                            )
                            delay(retryMs)
                            retryMs = (retryMs * 2).coerceAtMost(60_000L)
                        }
                    }
                }
            }
        }
        when (feedMode) {
            MarketFeedMode.SIMULATED -> scope.launch { simulateCatalogInstruments { true } }
            MarketFeedMode.BINANCE_PUBLIC, MarketFeedMode.ISTFIN_BIST -> {
                Instruments.all.filter { it.assetClass == AssetClass.CRYPTO }.let { crypto ->
                    if (crypto.isNotEmpty()) {
                        scope.launch { connectBinance(crypto.map { it.symbol.lowercase() }) }
                    }
                }
                // Newly added crypto symbols are not part of the stream URL created at startup.
                // Give any symbol without a fresh Binance quote a labelled paper-price fallback.
                scope.launch {
                    simulateCatalogInstruments {
                        it.assetClass != AssetClass.CRYPTO || lastTickers[it.symbol]?.let { ticker ->
                            ticker.source != "binance" || !isFresh(it.symbol)
                        } != false
                    }
                }
                if (feedMode == MarketFeedMode.ISTFIN_BIST) {
                    if (istfinApiKey.isNullOrBlank()) {
                        log.warn(
                            "ISTFIN_BIST selected without an API key; " +
                                "BIST instruments use the labelled paper-market simulator",
                        )
                    } else {
                        scope.launch { connectIstfinBist() }
                    }
                }
            }
        }
    }

    /** BIST quotes from the configured provider account; requests are batched to conserve quota. */
    private suspend fun connectIstfinBist() {
        val symbols = Instruments.all.filter { it.assetClass == AssetClass.EQUITY }.joinToString(",") { it.symbol }
        var retryMs = 15_000L
        while (true) {
            try {
                val request =
                    HttpRequest.newBuilder(URI("https://api.istfin.com/v1/quotes?symbols=$symbols"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bearer $istfinApiKey")
                        .header("Accept", "application/json")
                        .GET().build()
                val response =
                    withContext(Dispatchers.IO) { bistHttp.send(request, HttpResponse.BodyHandlers.ofString()) }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    log.error(
                        "istfin rejected BIST market-data credentials/entitlement (HTTP {})",
                        response.statusCode(),
                    )
                    delay(5 * 60_000L)
                    continue
                }
                if (response.statusCode() == 429) {
                    val retryAfter = response.headers().firstValue("Retry-After").orElse("60").toLongOrNull() ?: 60L
                    log.warn("istfin quota/rate limit reached; retrying after {} seconds", retryAfter)
                    delay((retryAfter * 1000).coerceIn(5_000L, 15 * 60_000L))
                    continue
                }
                check(response.statusCode() in 200..299) { "istfin HTTP ${response.statusCode()}" }
                val rows =
                    json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonArray
                        ?: error("istfin returned an invalid quotes payload")
                var published = 0
                rows.forEach { rowElement ->
                    val row = rowElement.jsonObject
                    val symbol = row["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return@forEach
                    if (Instruments.get(symbol)?.assetClass != AssetClass.EQUITY) return@forEach
                    val price =
                        row["price"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                            ?.takeIf { it.signum() > 0 } ?: return@forEach
                    val asOf =
                        row["asOf"]?.jsonPrimitive?.contentOrNull?.let {
                            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                        } ?: System.currentTimeMillis()
                    publish(
                        TickerUpdate(
                            symbol = symbol,
                            price = price.stripTrailingZeros().toPlainString(),
                            volume24h = row["volume"]?.jsonPrimitive?.contentOrNull,
                            source = "istfin-bist",
                            timestamp = asOf,
                        ),
                    )
                    published++
                }
                if (published == 0) log.warn("istfin returned no usable quotes for configured BIST symbols")
                retryMs = 15_000L
                delay(30_000L)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn("istfin BIST quote request failed: {}", failure.message)
                delay(retryMs)
                retryMs = (retryMs * 2).coerceAtMost(5 * 60_000L)
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
                        val parsed =
                            runCatching {
                                json.decodeFromString(
                                    BinanceCombined.serializer(),
                                    frame.readText(),
                                )
                            }.getOrNull()
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
                                source = "binance",
                            ),
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

    /** New active DB instruments become paper-priced without restarting the gateway. */
    private suspend fun simulateCatalogInstruments(include: (com.argent.common.domain.Instrument) -> Boolean) {
        val prices = mutableMapOf<String, BigDecimal>()
        val generators = mutableMapOf<String, StochasticPriceGenerator>()
        while (true) {
            val instruments = Instruments.all.filter { it.active && include(it) }
            val activeSymbols = instruments.mapTo(mutableSetOf()) { it.symbol }
            prices.keys.retainAll(activeSymbols)
            generators.keys.retainAll(activeSymbols)
            for (instrument in instruments) {
                val symbol = instrument.symbol
                if (symbol !in prices) {
                    val initial =
                        instrument.referencePrice?.toBigDecimalOrNull()
                            ?: defaultReferencePrice(instrument.assetClass)
                    prices[symbol] = initial
                    generators[symbol] = StochasticPriceGenerator(instrument, simulationSeed + symbol.hashCode())
                    publishQuote(instrument, initial, source = "argent-sim")
                    continue
                }
                val hasLiveBistQuote =
                    lastTickers[instrument.symbol]?.source == "istfin-bist" && isFresh(instrument.symbol)
                if (instrument.assetClass == AssetClass.EQUITY && (bistReplay.isActive() || hasLiveBistQuote)) {
                    continue
                }
                // Resume the simulator from the most recent replayed/imported equity quote.
                val last = lastPrices[symbol] ?: prices.getValue(symbol)
                val generator = generators.getValue(symbol)
                val next = generator.nextPrice(last)
                prices[symbol] = next
                publishQuote(instrument, next, generator.nextVolume(), "argent-sim")
            }
            delay(500)
        }
    }

    private suspend fun publishQuote(
        instrument: com.argent.common.domain.Instrument,
        price: BigDecimal,
        volume: String? = null,
        source: String,
    ) {
        val halfSpread = BigDecimal(instrument.tickSize).multiply(BigDecimal("1.5"))
        val bid =
            price.subtract(halfSpread).max(BigDecimal(instrument.tickSize))
                .setScale(instrument.priceScale, RoundingMode.HALF_UP)
        val ask = price.add(halfSpread).setScale(instrument.priceScale, RoundingMode.HALF_UP)
        publish(
            TickerUpdate(
                symbol = instrument.symbol,
                price = price.setScale(instrument.priceScale, RoundingMode.HALF_UP).toPlainString(),
                bid = bid.toPlainString(),
                ask = ask.toPlainString(),
                volume24h = volume,
                source = source,
            ),
        )
    }

    private fun defaultReferencePrice(assetClass: AssetClass): BigDecimal =
        when (assetClass) {
            AssetClass.CRYPTO -> BigDecimal("100")
            AssetClass.EQUITY, AssetClass.US_EQUITY -> BigDecimal("100")
            AssetClass.FUND -> BigDecimal("10")
            AssetClass.OPTION -> BigDecimal("1")
            AssetClass.VIOP -> BigDecimal("1000")
        }

    /** Imports only caller-supplied local CSV rows; no BIST data source or redistribution is implied. */
    fun startBistHistoricalReplay(
        csv: String,
        speed: Int,
    ): BistReplayStatus = bistReplay.start(csv, speed)

    fun bistHistoricalReplayStatus(): BistReplayStatus? = bistReplay.status()

    private suspend fun publish(update: TickerUpdate) {
        lastPrices[update.symbol] = BigDecimal(update.price)
        lastTickers[update.symbol] = update
        lastUpdatedAt[update.symbol] = System.currentTimeMillis()
        val buf = history.computeIfAbsent(update.symbol) { ArrayDeque() }
        synchronized(buf) {
            buf.addLast(update)
            while (buf.size > MarketHistoryLimits.PRICE_POINTS) buf.removeFirst()
        }
        candleAggregator.accept(update)
        _tickers.emit(update)
    }

    fun lastPrice(symbol: String): BigDecimal? = lastPrices[symbol].takeIf { isFresh(symbol) }

    fun lastTicker(symbol: String): TickerUpdate? = lastTickers[symbol].takeIf { isFresh(symbol) }

    fun seedLastPrice(
        symbol: String,
        price: BigDecimal,
    ) {
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

    fun bistFeedStatus(): String {
        val equitySymbols = Instruments.all.filter { it.assetClass == AssetClass.EQUITY }.map { it.symbol }
        if (equitySymbols.isEmpty()) return "UNAVAILABLE"
        if (equitySymbols.all { isFresh(it) && lastTickers[it]?.source == "argent-sim" }) return "SIMULATED"
        if (equitySymbols.all { isFresh(it) && lastTickers[it]?.source == "istfin-bist" }) return "DELAYED"
        if (equitySymbols.any { isFresh(it) && lastTickers[it]?.source == "istfin-bist" }) return "PARTIAL"
        if (equitySymbols.any { lastTickers[it]?.source == "istfin-bist" }) return "STALE"
        return if (feedMode == MarketFeedMode.ISTFIN_BIST && istfinApiKey.isNullOrBlank()) {
            "NOT_CONFIGURED"
        } else {
            "UNAVAILABLE"
        }
    }

    private fun isFresh(symbol: String): Boolean {
        val quote = lastTickers[symbol]
        val maxAge = if (quote?.source == "istfin-bist") MAX_BIST_QUOTE_AGE_MS else MAX_PRICE_AGE_MS
        return (System.currentTimeMillis() - (lastUpdatedAt[symbol] ?: 0L)) <= maxAge
    }

    fun history(symbol: String): List<TickerUpdate> {
        val buf = history[symbol] ?: return emptyList()
        synchronized(buf) { return buf.toList() }
    }

    fun stop() {
        scope.cancel()
        client.close()
    }

    companion object {
        private const val DEFAULT_SIMULATION_SEED = 0xA26E17L
        private const val MAX_PRICE_AGE_MS = 15_000L

        // Provider quotes are refreshed in a 30-second batch and may be delayed by the provider.
        private const val MAX_BIST_QUOTE_AGE_MS = 5 * 60_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
    }
}
