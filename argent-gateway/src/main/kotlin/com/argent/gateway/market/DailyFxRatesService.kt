package com.argent.gateway.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory

/** Daily reference conversion for display/accounting; this is not an execution price source. */
class DailyFxRatesService(
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
    private val endpoint: String = "https://api.frankfurter.dev/v2/rates?base=USD&quotes=TRY"
) {
    data class Snapshot(val usdPerCurrency: Map<String, BigDecimal>, val date: String, val available: Boolean)

    @Volatile private var cached: Pair<Long, Snapshot>? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(DailyFxRatesService::class.java)

    suspend fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - it.first < CACHE_MS }?.let { return it.second }
        return try {
            val request = HttpRequest.newBuilder(URI(endpoint)).timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json").GET().build()
            val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
            require(response.statusCode() in 200..299) { "FX provider returned HTTP ${response.statusCode()}" }
            val row = json.parseToJsonElement(response.body()).jsonArray.first().jsonObject
            val rate = row.getValue("rate").jsonPrimitive.content.toBigDecimal()
            require(rate.signum() > 0)
            val rates = mutableMapOf(
                "USD" to BigDecimal.ONE,
                "TRY" to BigDecimal.ONE.divide(rate, 12, java.math.RoundingMode.HALF_UP)
            )
            runCatching {
                val spotRequest = HttpRequest.newBuilder(URI("https://api.binance.com/api/v3/ticker/price?symbol=USDTTRY"))
                    .timeout(Duration.ofSeconds(4)).header("Accept", "application/json").GET().build()
                val spotResponse = withContext(Dispatchers.IO) { http.send(spotRequest, HttpResponse.BodyHandlers.ofString()) }
                if (spotResponse.statusCode() in 200..299) {
                    val usdtTry = json.parseToJsonElement(spotResponse.body()).jsonObject.getValue("price").jsonPrimitive.content.toBigDecimal()
                    if (usdtTry.signum() > 0) rates["USDT"] = usdtTry.divide(rate, 12, java.math.RoundingMode.HALF_UP)
                }
            }
            Snapshot(
                usdPerCurrency = rates,
                date = row.getValue("date").jsonPrimitive.content + if ("USDT" in rates) " + live USDT/TRY" else "",
                available = true
            ).also { cached = now to it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            log.warn("Daily FX refresh failed; using cached rates when available: {}", failure.message)
            cached?.second ?: Snapshot(mapOf("USD" to BigDecimal.ONE), "unavailable", false)
        }
    }

    private companion object { const val CACHE_MS = 6 * 60 * 60 * 1000L }
}
