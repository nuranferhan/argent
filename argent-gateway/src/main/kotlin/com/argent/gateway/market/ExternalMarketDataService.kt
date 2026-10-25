package com.argent.gateway.market

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val relatedSymbols: String = "",
    val imageUrl: String? = null,
    val translatedHeadline: String? = null
)

data class ExternalMarketImage(val bytes: ByteArray, val contentType: String)

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
class ExternalMarketDataService(
    private val apiKey: String?,
    private val aiApiKey: String? = System.getenv("ARGENT_AI_API_KEY")?.takeIf(String::isNotBlank),
    private val aiBaseUrl: String = System.getenv("ARGENT_AI_BASE_URL")?.trimEnd('/') ?: "https://api.groq.com/openai/v1",
    private val aiModel: String = System.getenv("ARGENT_AI_MODEL") ?: "openai/gpt-oss-20b"
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NEVER).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val quoteCache = ConcurrentHashMap<String, Pair<Long, ExternalQuote>>()
    @Volatile private var newsCache: Pair<Long, List<MarketNewsItem>>? = null
    private val knownImageUrls = ConcurrentHashMap.newKeySet<String>()
    private val imageCache = ConcurrentHashMap<String, Pair<Long, ExternalMarketImage>>()
    private val headlineTranslationCache = ConcurrentHashMap<String, Pair<Long, String>>()
    private val translationBudgetLock = Any()
    @Volatile private var translationBudgetDay = LocalDate.now(ZoneOffset.UTC)
    @Volatile private var translationBudgetChars = 0

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
                relatedSymbols = row["related"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                imageUrl = row["image"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("https://") && it.length <= 2048 }
            )
        }.take(40)
        knownImageUrls.clear()
        knownImageUrls.addAll(items.mapNotNull { it.imageUrl })
        val translated = translateHeadlines(items)
        if (category == "general") newsCache = now to translated
        return translated
    }

    suspend fun image(rawUrl: String): ExternalMarketImage? {
        require(rawUrl.length in 1..2048 && rawUrl in knownImageUrls) { "Haber görseli bu akışa ait değil" }
        imageCache[rawUrl]?.takeIf { System.currentTimeMillis() - it.first < IMAGE_CACHE_MS }?.let { return it.second }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6)).header("Accept", "image/*").GET().build()
        val response = try { withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofByteArray()) } }
        catch (_: Exception) { return null }
        if (response.statusCode() !in 200..299) return null
        val mime = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
        if (mime !in ALLOWED_IMAGE_TYPES) return null
        val bytes = response.body().takeIf { it.size in 1..MAX_IMAGE_BYTES } ?: return null
        return ExternalMarketImage(bytes, mime).also { imageCache[rawUrl] = System.currentTimeMillis() to it }
    }

    private suspend fun translateHeadlines(items: List<MarketNewsItem>): List<MarketNewsItem> {
        val english = items.filter { looksEnglish(it.headline) }.take(8)
        if (english.isEmpty()) return items
        val translations = mutableMapOf<Long, String>()
        if (!aiApiKey.isNullOrBlank()) {
            runCatching {
                val messages = kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("role", kotlinx.serialization.json.JsonPrimitive("system"))
                        put("content", kotlinx.serialization.json.JsonPrimitive("Translate each financial news headline into natural Turkish. Keep company names, tickers and numbers unchanged. Return only a JSON array of strings in the same order."))
                    })
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("role", kotlinx.serialization.json.JsonPrimitive("user"))
                        put("content", kotlinx.serialization.json.JsonPrimitive(Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), english.map { it.headline })))
                    })
                }
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("model", kotlinx.serialization.json.JsonPrimitive(aiModel))
                    put("temperature", kotlinx.serialization.json.JsonPrimitive(0))
                    put("max_tokens", kotlinx.serialization.json.JsonPrimitive((english.size * 48).coerceAtMost(1200)))
                    put("messages", messages)
                }
                val request = HttpRequest.newBuilder(URI(aiBaseUrl.trimEnd('/') + "/chat/completions"))
                    .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer $aiApiKey")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload))).build()
                val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
                if (response.statusCode() in 200..299) {
                    val content = Json.parseToJsonElement(response.body()).jsonObject["choices"]?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val clean = content.substringAfter("[", "").let { "[" + it.substringBeforeLast("]", "") + "]" }
                    val result = Json.parseToJsonElement(clean).jsonArray.map { it.jsonPrimitive.content.trim() }
                    if (result.size == english.size) english.forEachIndexed { index, item ->
                        result[index].takeIf { it.isNotBlank() && it != item.headline }?.let { translations[item.id] = it.take(300) }
                    }
                }
            }
        }
        val missing = english.filter { translations[it.id].isNullOrBlank() }
        val fallback = coroutineScope {
            missing.map { item -> async { item.id to translateWithMyMemory(item.headline) } }
                .awaitAll().mapNotNull { (id, title) -> title?.let { id to it } }.toMap()
        }
        translations.putAll(fallback)
        return items.map { item -> item.copy(translatedHeadline = translations[item.id]?.takeIf { it.isNotBlank() && it != item.headline }) }
    }

    private suspend fun translateWithMyMemory(headline: String): String? {
        val now = System.currentTimeMillis()
        headlineTranslationCache[headline]?.let { (savedAt, value) ->
            val ttl = if (value.isBlank()) 15 * 60_000L else 7 * 24 * 60 * 60_000L
            if (now - savedAt < ttl) return value.takeIf(String::isNotBlank)
        }
        val chars = headline.toByteArray(Charsets.UTF_8).size
        val allowed = synchronized(translationBudgetLock) {
            val today = LocalDate.now(ZoneOffset.UTC)
            if (today != translationBudgetDay) { translationBudgetDay = today; translationBudgetChars = 0 }
            if (chars > 500 || translationBudgetChars + chars > 4_500) false
            else { translationBudgetChars += chars; true }
        }
        if (!allowed) return null
        val request = HttpRequest.newBuilder(URI("https://api.mymemory.translated.net/get?q=${encode(headline)}&langpair=${encode("en|tr")}&mt=1"))
            .timeout(Duration.ofSeconds(6)).header("Accept", "application/json").GET().build()
        val translated = runCatching {
            val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            if (response.statusCode() !in 200..299) return@runCatching null
            Json.parseToJsonElement(response.body()).jsonObject["responseData"]?.jsonObject?.get("translatedText")
                ?.jsonPrimitive?.contentOrNull?.replace("&amp;", "&")?.replace("&quot;", "\"")
                ?.replace("&#39;", "'")?.replace("&lt;", "<")?.replace("&gt;", ">")?.trim()
                ?.takeIf { it.isNotBlank() && it != headline }
        }.getOrNull()
        headlineTranslationCache[headline] = now to (translated ?: "")
        return translated
    }
    private fun looksEnglish(text: String): Boolean {
        if (text.any { it in "çğıöşüÇĞİÖŞÜ" }) return false
        val words = Regex("[A-Za-z]{3,}").findAll(text).map { it.value.lowercase() }.toList()
        if (words.size < 4) return false
        val englishSignals = setOf("the", "and", "for", "with", "from", "after", "before", "shares", "stock", "market", "company", "reports", "announces", "plans", "quarter", "investors", "earnings", "revenue", "profit", "loss", "deal", "cuts", "raises", "how", "its", "their", "says", "amid", "over", "wall", "street", "funding", "fears", "adjusting", "portfolio", "retail", "more", "than", "years", "ago", "former", "hedge", "manager")
        return words.count { it in englishSignals } >= 1 || words.size >= 7
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
        private const val IMAGE_CACHE_MS = 900_000L
        private const val MAX_IMAGE_BYTES = 2_000_000
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
