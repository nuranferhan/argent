package com.argent.market

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Binance Spot Testnet only. The host is deliberately fixed; no production URL is configurable. */
class BinanceSpotTestnet(
    private val apiKey: String?,
    private val secret: String?,
    private val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 15_000
        }
    }
) : AutoCloseable {
    private val json = Json { ignoreUnknownKeys = true }
    private val timeSyncMutex = Mutex()
    @Volatile private var serverTimeOffsetMillis: Long? = null
    @Volatile private var serverTimeSyncedAtNanos: Long = 0
    val configured: Boolean get() = !apiKey.isNullOrBlank() && !secret.isNullOrBlank()

    suspend fun tickerPrice(rawSymbol: String): BinanceTestnetTickerPrice {
        val symbol = safeSymbol(rawSymbol)
        val response = try { http.get("$BASE_URL/api/v3/ticker/price?symbol=$symbol") }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { throw BinanceTestnetException(502, "Could not load the Binance Spot Testnet reference price") }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            val message = runCatching { Json.parseToJsonElement(body).jsonObject["msg"]?.jsonPrimitive?.content }.getOrNull()
            throw BinanceTestnetException(response.status.value, message ?: "Could not load the Binance Spot Testnet reference price")
        }
        return runCatching { json.decodeFromString<BinanceTestnetTickerPrice>(body) }
            .getOrElse { throw BinanceTestnetException(502, "Invalid price response from Binance Spot Testnet") }
    }

    suspend fun placeOrder(request: TestnetOrderRequest): TestnetOrder {
        requireConfigured()
        val symbol = request.symbol.uppercase()
        require(symbol.matches(Regex("[A-Z0-9]{5,20}"))) { "Invalid Binance symbol" }
        val side = request.side.uppercase()
        require(side in setOf("BUY", "SELL")) { "side must be BUY or SELL" }
        val type = request.type.uppercase()
        require(type in setOf("LIMIT", "MARKET")) { "type must be LIMIT or MARKET" }
        val quantity = positiveDecimal(request.quantity, "quantity")
        val params = linkedMapOf(
            "symbol" to symbol,
            "side" to side,
            "type" to type,
            "quantity" to quantity,
            "newOrderRespType" to "FULL"
        )
        if (type == "LIMIT") {
            val price = positiveDecimal(request.price ?: error("price is required for LIMIT"), "price")
            val timeInForce = request.timeInForce.uppercase()
            require(timeInForce in setOf("GTC", "IOC", "FOK")) { "timeInForce must be GTC, IOC, or FOK" }
            params["timeInForce"] = timeInForce
            params["price"] = price
        } else {
            require(request.price.isNullOrBlank()) { "price is not accepted for MARKET" }
            require(request.timeInForce == "GTC") { "timeInForce is only supported for LIMIT" }
        }
        request.clientOrderId?.let {
            require(it.length <= 36 && it.matches(Regex("[A-Za-z0-9._:-]+"))) { "clientOrderId must be 1-36 safe characters" }
            params["newClientOrderId"] = it
        }
        return signed("POST", "/api/v3/order", params)
    }

    suspend fun getOrder(symbol: String, orderId: Long): TestnetOrder {
        requireConfigured()
        require(orderId > 0) { "orderId must be positive" }
        return signed("GET", "/api/v3/order", linkedMapOf("symbol" to safeSymbol(symbol), "orderId" to orderId.toString()))
    }

    suspend fun getOrderByClientOrderId(symbol: String, clientOrderId: String): TestnetOrder {
        requireConfigured()
        require(clientOrderId.length in 1..36 && clientOrderId.matches(Regex("[A-Za-z0-9._:-]+"))) {
            "clientOrderId must be 1-36 safe characters"
        }
        return signed("GET", "/api/v3/order", linkedMapOf("symbol" to safeSymbol(symbol), "origClientOrderId" to clientOrderId))
    }

    suspend fun trades(symbol: String, orderId: Long): List<BinanceTestnetTrade> {
        requireConfigured()
        require(orderId > 0) { "orderId must be positive" }
        return signed("GET", "/api/v3/myTrades", linkedMapOf("symbol" to safeSymbol(symbol), "orderId" to orderId.toString()))
    }

    suspend fun cancelOrder(symbol: String, orderId: Long): TestnetOrder {
        requireConfigured()
        require(orderId > 0) { "orderId must be positive" }
        return signed("DELETE", "/api/v3/order", linkedMapOf("symbol" to safeSymbol(symbol), "orderId" to orderId.toString()))
    }

    private suspend inline fun <reified T> signed(method: String, path: String, params: LinkedHashMap<String, String>): T {
        params["recvWindow"] = "5000"
        params["timestamp"] = testnetTimestamp().toString()
        val query = params.entries.joinToString("&") { (key, value) -> "$key=$value" }
        val signature = hmacSha256(secret!!, query)
        val url = "$BASE_URL$path?$query&signature=$signature"
        val response = try {
            when (method) {
                "POST" -> http.post(url) { header("X-MBX-APIKEY", apiKey!!) }
                "DELETE" -> http.delete(url) { header("X-MBX-APIKEY", apiKey!!) }
                else -> http.get(url) { header("X-MBX-APIKEY", apiKey!!) }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not leak signed request URLs in application logs or error responses.
            throw BinanceTestnetException(502, "Binance Spot Testnet is unavailable")
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val message = runCatching { Json.parseToJsonElement(body).jsonObject["msg"]?.jsonPrimitive?.content }.getOrNull()
            throw BinanceTestnetException(response.status.value, message ?: "Binance Spot Testnet request failed")
        }
        return runCatching { json.decodeFromString<T>(body) }
            .getOrElse { throw BinanceTestnetException(502, "Invalid response from Binance Spot Testnet") }
    }

    private suspend fun testnetTimestamp(): Long {
        val nowNanos = System.nanoTime()
        val currentOffset = serverTimeOffsetMillis
        if (currentOffset != null && nowNanos - serverTimeSyncedAtNanos < TIME_OFFSET_TTL_NANOS) {
            return System.currentTimeMillis() + currentOffset
        }
        return timeSyncMutex.withLock {
            val checkedAt = System.nanoTime()
            val offset = serverTimeOffsetMillis
            if (offset != null && checkedAt - serverTimeSyncedAtNanos < TIME_OFFSET_TTL_NANOS) {
                return@withLock System.currentTimeMillis() + offset
            }
            val startedAt = System.currentTimeMillis()
            val response = try {
                http.get("$BASE_URL/api/v3/time")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw BinanceTestnetException(502, "Could not synchronize with Binance Spot Testnet time")
            }
            if (response.status != HttpStatusCode.OK) {
                throw BinanceTestnetException(502, "Could not synchronize with Binance Spot Testnet time")
            }
            val serverTime = runCatching {
                json.decodeFromString<BinanceServerTime>(response.bodyAsText()).serverTime
            }.getOrElse {
                throw BinanceTestnetException(502, "Invalid time response from Binance Spot Testnet")
            }
            val receivedAt = System.currentTimeMillis()
            val midpoint = startedAt + (receivedAt - startedAt) / 2
            serverTimeOffsetMillis = serverTime - midpoint
            serverTimeSyncedAtNanos = System.nanoTime()
            receivedAt + serverTimeOffsetMillis!!
        }
    }

    private fun requireConfigured() {
        check(configured) { "Binance Spot Testnet credentials are not configured" }
    }

    private fun safeSymbol(symbol: String): String = symbol.uppercase().also {
        require(it.matches(Regex("[A-Z0-9]{5,20}"))) { "Invalid Binance symbol" }
    }

    override fun close() = http.close()

    companion object {
        private const val BASE_URL = "https://testnet.binance.vision"
        private const val TIME_OFFSET_TTL_NANOS = 30_000_000_000L

        private fun positiveDecimal(value: String, name: String): String {
            val parsed = value.toBigDecimalOrNull() ?: throw IllegalArgumentException("$name must be a decimal number")
            require(parsed > BigDecimal.ZERO && parsed.scale() <= 16) { "$name must be positive with at most 16 decimal places" }
            return parsed.stripTrailingZeros().toPlainString()
        }

        private fun hmacSha256(secret: String, value: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
data class BinanceTestnetTickerPrice(val symbol: String, val price: String)

@Serializable
private data class BinanceServerTime(val serverTime: Long)

data class TestnetOrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: String,
    val price: String? = null,
    val timeInForce: String = "GTC",
    val clientOrderId: String? = null
)

@Serializable
data class TestnetOrder(
    val symbol: String,
    val orderId: Long,
    val clientOrderId: String? = null,
    val price: String,
    val origQty: String,
    val executedQty: String,
    val status: String,
    val timeInForce: String? = null,
    val type: String,
    val side: String,
    val transactTime: Long? = null,
    val cummulativeQuoteQty: String? = null,
    val fills: List<BinanceTestnetFill> = emptyList(),
    val ledgerSynced: Boolean = false
)

@Serializable
data class BinanceTestnetFill(
    val price: String,
    val qty: String,
    val commission: String,
    val commissionAsset: String,
    val tradeId: Long? = null
)

@Serializable
data class BinanceTestnetTrade(
    val id: Long,
    val orderId: Long,
    val price: String,
    val qty: String,
    val quoteQty: String,
    val commission: String,
    val commissionAsset: String,
    val isBuyer: Boolean,
    val time: Long
)

class BinanceTestnetException(val upstreamStatus: Int, message: String) : RuntimeException(message)
