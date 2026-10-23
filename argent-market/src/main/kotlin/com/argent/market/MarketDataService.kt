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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime
import java.time.Duration

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

data class BistHistoricalRow(val timestamp: Long, val symbol: String, val price: String, val volume24h: String? = null)
@Serializable
data class BistReplayStatus(val replayId: String, val state: String, val rows: Int, val processed: Int, val error: String? = null)

enum class MarketFeedMode { SIMULATED, BINANCE_PUBLIC, ISTFIN_BIST }

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
    private val feedMode: MarketFeedMode = MarketFeedMode.BINANCE_PUBLIC,
    private val istfinApiKey: String? = null
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
    private val bistReplayActive = AtomicBoolean(false)
    @Volatile private var bistReplayStatus: BistReplayStatus? = null

    fun start() {
        when (feedMode) {
            MarketFeedMode.SIMULATED -> scope.launch { simulateAllInstruments() }
            MarketFeedMode.BINANCE_PUBLIC, MarketFeedMode.ISTFIN_BIST -> {
                Instruments.all.filter { it.assetClass == AssetClass.CRYPTO }.let { crypto ->
                    if (crypto.isNotEmpty()) {
                        scope.launch { connectBinance(crypto.map { it.symbol.lowercase() }) }
                    }
                }
                if (feedMode == MarketFeedMode.ISTFIN_BIST) {
                    if (istfinApiKey.isNullOrBlank()) log.warn("ISTFIN_BIST selected but ISTFIN_API_KEY is missing; BIST prices stay unavailable")
                    else scope.launch { connectIstfinBist() }
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
                val request = HttpRequest.newBuilder(URI("https://api.istfin.com/v1/quotes?symbols=$symbols"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer ${istfinApiKey}")
                    .header("Accept", "application/json")
                    .GET().build()
                val response = withContext(Dispatchers.IO) { bistHttp.send(request, HttpResponse.BodyHandlers.ofString()) }
                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    log.error("istfin rejected BIST market-data credentials/entitlement (HTTP {})", response.statusCode())
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
                val rows = json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonArray
                    ?: error("istfin returned an invalid quotes payload")
                var published = 0
                rows.forEach { rowElement ->
                    val row = rowElement.jsonObject
                    val symbol = row["symbol"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: return@forEach
                    if (Instruments.get(symbol)?.assetClass != AssetClass.EQUITY) return@forEach
                    val price = row["price"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                        ?.takeIf { it.signum() > 0 } ?: return@forEach
                    val asOf = row["asOf"]?.jsonPrimitive?.contentOrNull?.let {
                        runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                    } ?: System.currentTimeMillis()
                    publish(TickerUpdate(
                        symbol = symbol,
                        price = price.stripTrailingZeros().toPlainString(),
                        volume24h = row["volume"]?.jsonPrimitive?.contentOrNull,
                        source = "istfin-bist",
                        timestamp = asOf
                    ))
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
                if (instrument.assetClass == AssetClass.EQUITY && bistReplayActive.get()) continue
                val symbol = instrument.symbol
                // Resume the simulator from the most recent replayed/imported equity quote.
                val last = lastPrices[symbol] ?: prices.getValue(symbol)
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

    /** Imports only caller-supplied local CSV rows; no BIST data source or redistribution is implied. */
    fun startBistHistoricalReplay(csv: String, speed: Int): BistReplayStatus {
        require(csv.toByteArray(Charsets.UTF_8).size <= MAX_BIST_CSV_BYTES) { "CSV exceeds the 2 MiB limit" }
        require(speed in 1..100_000) { "speed must be between 1 and 100000" }
        val rows = parseBistCsv(csv)
        check(bistReplayActive.compareAndSet(false, true)) { "A BIST historical replay is already running" }
        val id = UUID.randomUUID().toString()
        bistReplayStatus = BistReplayStatus(id, "RUNNING", rows.size, 0)
        scope.launch {
            try {
                var previousTimestamp: Long? = null
                rows.forEachIndexed { index, row ->
                    previousTimestamp?.let { previous ->
                        val scaledDelay = ((row.timestamp - previous).coerceAtLeast(0) / speed).coerceAtMost(5_000)
                        if (scaledDelay > 0) delay(scaledDelay)
                    }
                    previousTimestamp = row.timestamp
                    publish(TickerUpdate(row.symbol, row.price, volume24h = row.volume24h, source = "bist-csv-replay", timestamp = row.timestamp))
                    bistReplayStatus = BistReplayStatus(id, "RUNNING", rows.size, index + 1)
                }
                bistReplayStatus = BistReplayStatus(id, "COMPLETED", rows.size, rows.size)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                bistReplayStatus = BistReplayStatus(id, "CANCELLED", rows.size, bistReplayStatus?.processed ?: 0)
                throw cancelled
            } catch (failure: Exception) {
                bistReplayStatus = BistReplayStatus(id, "FAILED", rows.size, bistReplayStatus?.processed ?: 0, failure.message ?: "Replay failed")
            } finally {
                bistReplayActive.set(false)
            }
        }
        return bistReplayStatus!!
    }

    fun bistHistoricalReplayStatus(): BistReplayStatus? = bistReplayStatus

    private fun parseBistCsv(csv: String): List<BistHistoricalRow> {
        val lines = csv.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.size in 2..(MAX_BIST_ROWS + 1)) { "CSV must contain a header and 1-${MAX_BIST_ROWS} data rows" }
        val header = lines.first().removePrefix("\uFEFF").split(',').map { it.trim().lowercase() }
        require(header == listOf("timestamp", "symbol", "price") || header == listOf("timestamp", "symbol", "price", "volume24h")) {
            "CSV header must be timestamp,symbol,price[,volume24h]"
        }
        val rows = lines.drop(1).mapIndexed { index, line ->
            val columns = line.split(',').map(String::trim)
            require(columns.size == header.size) { "Invalid CSV column count at data row ${index + 1}" }
            val timestamp = columns[0].toLongOrNull() ?: runCatching { Instant.parse(columns[0]).toEpochMilli() }
                .getOrElse { throw IllegalArgumentException("Invalid timestamp at data row ${index + 1}") }
            require(timestamp > 0) { "Timestamp must be Unix milliseconds or ISO-8601" }
            val symbol = columns[1].uppercase()
            val instrument = Instruments.get(symbol)
            require(instrument?.assetClass == AssetClass.EQUITY) { "Only configured BIST equity symbols are accepted: $symbol" }
            val price = columns[2].toBigDecimalOrNull()
            require(price != null && price.signum() > 0 && price.scale() <= instrument!!.priceScale &&
                price.remainder(BigDecimal(instrument.tickSize)).signum() == 0) {
                "Invalid price precision or tick size at data row ${index + 1}"
            }
            val volume = columns.getOrNull(3)?.takeIf(String::isNotBlank)?.also {
                val parsed = it.toBigDecimalOrNull()
                require(parsed != null && parsed.signum() >= 0 && parsed.scale() <= 12) { "Invalid volume at data row ${index + 1}" }
            }
            BistHistoricalRow(timestamp, symbol, price!!.stripTrailingZeros().toPlainString(), volume)
        }
        require(rows.zipWithNext().all { (a, b) -> a.timestamp <= b.timestamp }) { "CSV rows must be in ascending timestamp order" }
        return rows
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

    fun bistFeedStatus(): String {
        val equitySymbols = Instruments.all.filter { it.assetClass == AssetClass.EQUITY }.map { it.symbol }
        if (equitySymbols.isEmpty()) return "UNAVAILABLE"
        if (equitySymbols.all { isFresh(it) && lastTickers[it]?.source == "argent-sim" }) return "SIMULATED"
        if (equitySymbols.all { isFresh(it) && lastTickers[it]?.source == "istfin-bist" }) return "DELAYED"
        if (equitySymbols.any { isFresh(it) && lastTickers[it]?.source == "istfin-bist" }) return "PARTIAL"
        if (equitySymbols.any { lastTickers[it]?.source == "istfin-bist" }) return "STALE"
        return if (feedMode == MarketFeedMode.ISTFIN_BIST && istfinApiKey.isNullOrBlank()) "NOT_CONFIGURED" else "UNAVAILABLE"
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
        private const val MAX_PRICE_AGE_MS = 15_000L
        // Provider quotes are refreshed in a 30-second batch and may be delayed by the provider.
        private const val MAX_BIST_QUOTE_AGE_MS = 5 * 60_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val MAX_BIST_CSV_BYTES = 2 * 1024 * 1024
        private const val MAX_BIST_ROWS = 50_000
    }
}
