package com.argent.gateway.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class ExternalQuote(
    val symbol: String,
    val price: String,
    val change: String? = null,
    val changePercent: String? = null,
    val previousClose: String? = null,
    val timestamp: Long,
    val source: String = "Finnhub",
    val currency: String = "USD"
)

@Serializable
data class MarketNewsItem(
    val id: Long,
    val headline: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    val relatedSymbols: String = ""
)

@Serializable
data class ExternalMarketStatus(
    val provider: String = "Finnhub",
    val configured: Boolean,
    val planNote: String = "Provider entitlements, API quota and display license apply"
)

@Serializable
data class CreatePriceAlertRequest(val symbol: String, val direction: String, val threshold: String)

@Serializable
data class PriceAlertResponse(
    val id: String, val symbol: String, val direction: String, val threshold: String, val state: String,
    val createdAt: Long, val triggeredAt: Long? = null, val triggeredPrice: String? = null
)

fun PriceAlert.toResponse() = PriceAlertResponse(id, symbol, direction, threshold, state, createdAt, triggeredAt, triggeredPrice)

/** Optional, cache-first connector for user-configured personal-use Nasdaq/news access. */
class ExternalMarketDataService(private val apiKey: String?) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val quoteCache = ConcurrentHashMap<String, Pair<Long, ExternalQuote>>()
    @Volatile private var newsCache: Pair<Long, List<MarketNewsItem>>? = null

    fun status() = ExternalMarketStatus(configured = !apiKey.isNullOrBlank())

    suspend fun quote(rawSymbol: String): ExternalQuote {
        val symbol = normalizeSymbol(rawSymbol)
        require(symbol in NASDAQ_WATCHLIST) { "Nasdaq dış piyasa izleme listesinde desteklenmeyen sembol: $symbol" }
        val now = System.currentTimeMillis()
        quoteCache[symbol]?.takeIf { now - it.first < QUOTE_CACHE_MS }?.let { return it.second }
        val payload = get("/quote?symbol=$symbol").jsonObject
        val price = payload.number("c") ?: throw IllegalStateException("Sağlayıcı $symbol için kullanılabilir fiyat döndürmedi")
        val timestamp = (payload.number("t")?.toLongOrNull() ?: now / 1000) * 1000
        val quote = ExternalQuote(
            symbol = symbol,
            price = price,
            change = payload.number("d"),
            changePercent = payload.number("dp"),
            previousClose = payload.number("pc"),
            timestamp = timestamp
        )
        quoteCache[symbol] = now to quote
        return quote
    }

    suspend fun news(category: String = "general"): List<MarketNewsItem> {
        require(category in NEWS_CATEGORIES) { "Haber kategorisi general, forex, crypto veya merger olmalı" }
        val now = System.currentTimeMillis()
        if (category == "general") newsCache?.takeIf { now - it.first < NEWS_CACHE_MS }?.let { return it.second }
        val items = get("/news?category=$category").jsonArray.mapNotNull { element ->
            val row = element.jsonObject
            val headline = row["headline"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val url = row["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (headline.isBlank() || !url.startsWith("https://")) return@mapNotNull null
            MarketNewsItem(
                id = row["id"]?.jsonPrimitive?.longOrNull ?: url.hashCode().toLong(),
                headline = headline,
                summary = row["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                source = row["source"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                url = url,
                publishedAt = (row["datetime"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000,
                relatedSymbols = row["related"]?.jsonPrimitive?.contentOrNull.orEmpty()
            )
        }.take(40)
        if (category == "general") newsCache = now to items
        return items
    }

    private suspend fun get(path: String): JsonElement {
        if (apiKey.isNullOrBlank()) throw IllegalStateException("Finnhub kaynağı yapılandırılmamış. FINNHUB_API_KEY ekleyin.")
        val request = HttpRequest.newBuilder(URI("https://finnhub.io/api/v1$path&token=${encode(apiKey)}"))
            .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build()
        val response = try { withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) } }
        catch (failure: Exception) { throw IllegalStateException("Finnhub'a ulaşılamadı: ${failure.message}") }
        if (response.statusCode() == 429) throw IllegalStateException("Finnhub istek kotası doldu; sağlayıcının limit yenilemesini bekleyin")
        if (response.statusCode() !in 200..299) throw IllegalStateException("Finnhub isteği başarısız (HTTP ${response.statusCode()})")
        return json.parseToJsonElement(response.body())
    }

    private fun normalizeSymbol(value: String) = value.trim().uppercase().also {
        require(it.matches(Regex("[A-Z.]{1,10}"))) { "Geçersiz sembol" }
    }

    private fun kotlinx.serialization.json.JsonObject.number(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf {
        val value = it.toDoubleOrNull()
        value != null && value.isFinite() && (value > 0 || name in setOf("d", "dp"))
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    companion object {
        val NASDAQ_WATCHLIST = setOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA")
        private val NEWS_CATEGORIES = setOf("general", "forex", "crypto", "merger")
        private const val QUOTE_CACHE_MS = 20_000L
        private const val NEWS_CACHE_MS = 180_000L
    }
}
