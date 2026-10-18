package com.argent.terminal.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ArgentApi(var baseUrl: String = "http://127.0.0.1:8080") {
    var token: String? = null
    private var refreshToken: String? = null

    fun clearSession() {
        token = null
        refreshToken = null
    }

    suspend fun logout() {
        val currentRefreshToken = refreshToken
        if (currentRefreshToken != null) {
            runCatching {
                client.post("$baseUrl/api/v1/auth/logout") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(currentRefreshToken))
                }
            }
        }
        clearSession()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        expectSuccess = false
    }

    suspend fun register(email: String, password: String, name: String): RegisterResponse =
        post("/api/v1/auth/register", RegisterRequest(email, password, name))

    suspend fun registerFromInvitation(email: String, password: String, name: String, token: String): TokenResponse {
        val tokens: TokenResponse = post("/api/v1/auth/register/invitation", InvitationRegistrationRequest(email, password, name, token))
        saveTokens(tokens)
        return tokens
    }

    suspend fun login(email: String, password: String): TokenResponse {
        val tokens: TokenResponse = post("/api/v1/auth/login", LoginRequest(email, password))
        saveTokens(tokens)
        return tokens
    }

    suspend fun me(): MeResponse = get("/api/v1/me")
    suspend fun tenants(): List<TenantDto> = get("/api/v1/tenants")
    suspend fun tenantMembers(): List<TenantMemberDto> = get("/api/v1/tenants/members")
    suspend fun inviteTenantMember(email: String, role: String): TenantInvitationDto =
        post("/api/v1/tenants/invitations", TenantInviteRequest(email, role))
    suspend fun acceptTenantInvitation(token: String): TenantMembershipDto =
        post("/api/v1/tenants/invitations/accept", TenantInviteAcceptRequest(token))
    suspend fun updateTenantMemberRole(userId: String, role: String) {
        val response = client.patch("$baseUrl/api/v1/tenants/members/$userId/role") {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(TenantRoleUpdateRequest(role))
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
    }
    suspend fun removeTenantMember(userId: String) {
        val response = client.delete("$baseUrl/api/v1/tenants/members/$userId") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
    }
    suspend fun closeWorkspace() {
        val response = client.delete("$baseUrl/api/v1/tenants/current") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        clearSession()
    }
    suspend fun switchTenant(tenantId: String) {
        val tokens: TokenResponse = post("/api/v1/tenants/switch", TenantSwitchRequest(tenantId))
        saveTokens(tokens)
    }
    suspend fun marketMakerState(): MarketMakerStateDto = get("/api/v1/admin/market-maker")
    suspend fun binanceSpotTestnetStatus(): BinanceTestnetStatusDto = get("/api/v1/admin/binance-spot-testnet")
    suspend fun placeBinanceSpotTestnetOrder(request: BinanceTestnetOrderRequestDto): BinanceTestnetOrderDto =
        post("/api/v1/admin/binance-spot-testnet/orders", request)
    suspend fun binanceSpotTestnetOrder(symbol: String, orderId: Long): BinanceTestnetOrderDto =
        get("/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol")
    suspend fun cancelBinanceSpotTestnetOrder(symbol: String, orderId: Long): BinanceTestnetOrderDto {
        var response = client.delete("$baseUrl/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            response = client.delete("$baseUrl/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol") {
                token?.let { header("Authorization", "Bearer $it") }
            }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }
    suspend fun startBistHistoricalReplay(csv: String, speed: Int): BistReplayDto =
        post("/api/v1/admin/market-data/bist/replay", BistReplayRequestDto(csv, speed))
    suspend fun bistHistoricalReplayStatus(): BistReplayDto = get("/api/v1/admin/market-data/bist/replay")
    suspend fun killMarketMaker(): MarketMakerActionDto = post("/api/v1/admin/market-maker/kill-switch", EmptyRequest())
    suspend fun resumeMarketMaker(): MarketMakerActionDto = post("/api/v1/admin/market-maker/resume", EmptyRequest())
    suspend fun instruments(): List<InstrumentDto> = get("/api/v1/instruments")
    suspend fun orderBook(symbol: String): OrderBookDto = get("/api/v1/orderbook/$symbol")
    suspend fun ticker(symbol: String): TickerDto = get("/api/v1/ticker/$symbol")
    suspend fun history(symbol: String): List<TickerDto> = get("/api/v1/ticker/$symbol/history")
    suspend fun balances(): List<BalanceDto> = get("/api/v1/balances")
    suspend fun positions(): List<PositionDto> = get("/api/v1/positions")
    suspend fun portfolio(): PortfolioDto = get("/api/v1/portfolio")
    suspend fun orders(): List<OrderDto> = get("/api/v1/orders")
    suspend fun fills(): List<TradeDto> = get("/api/v1/fills")
    suspend fun assistantStatus(): AssistantStatusDto = get("/api/v1/assistant/status")
    suspend fun askAssistant(question: String, symbol: String? = null): AssistantAnswerDto =
        post("/api/v1/assistant/chat", AssistantQuestionDto(question, symbol))
    suspend fun publicTrades(symbol: String): List<TradeDto> = get("/api/v1/trades?symbol=$symbol")

    suspend fun placeOrder(
        symbol: String,
        side: String,
        type: String,
        quantity: String,
        price: String?,
        clientOrderId: String?
    ): OrderResultDto {
        val response = client.post("$baseUrl/api/v1/orders") {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            clientOrderId?.let { header("Idempotency-Key", it) }
            setBody(PlaceOrderRequest(symbol, side, type, price, quantity, clientOrderId))
        }
        if (!response.status.isSuccess() && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            return placeOrder(symbol, side, type, quantity, price, clientOrderId)
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }

    suspend fun cancel(orderId: String, symbol: String) {
        val response = client.delete("$baseUrl/api/v1/orders/$orderId?symbol=$symbol") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        if (!response.status.isSuccess() && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            return cancel(orderId, symbol)
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
    }

    suspend fun adminDeposit(userId: String, asset: String, amount: String, key: String) {
        post<DepositRequest, BalanceDto>("/api/v1/admin/deposit", DepositRequest(userId, asset, amount, key))
    }

    suspend fun withdraw(asset: String, amount: String, key: String): BalanceDto =
        post("/api/v1/withdraw", WithdrawRequest(asset, amount, key))

    suspend fun health(): HealthDto = get("/health")

    fun tickerFlow(): Flow<TickerDto> = flow {
        val url = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        while (true) {
            try {
                client.webSocket("$url/ws/ticker") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            emit(json.decodeFromString(TickerDto.serializer(), frame.readText()))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            delay(1_000)
        }
    }

    fun orderBookFlow(symbol: String): Flow<OrderBookDto> = flow {
        val url = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        while (true) {
            try {
                client.webSocket("$url/ws/orderbook/$symbol") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            emit(json.decodeFromString(OrderBookDto.serializer(), frame.readText()))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            delay(1_000)
        }
    }

    private suspend inline fun <reified T> get(path: String): T {
        var response = client.get("$baseUrl$path") {
            token?.let { header("Authorization", "Bearer $it") }
        }
        if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            response = client.get("$baseUrl$path") { token?.let { header("Authorization", "Bearer $it") } }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }

    private suspend inline fun <reified B, reified T> post(path: String, body: B): T {
        var response = client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            setBody(body)
        }
        if (path != "/api/v1/auth/refresh" && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
            response = client.post("$baseUrl$path") {
                contentType(ContentType.Application.Json)
                token?.let { header("Authorization", "Bearer $it") }
                setBody(body)
            }
        }
        if (!response.status.isSuccess()) fail(response.bodyAsText())
        return response.body()
    }

    private fun saveTokens(tokens: TokenResponse) {
        token = tokens.accessToken
        refreshToken = tokens.refreshToken
    }

    private suspend fun refreshSession(): Boolean {
        val currentRefreshToken = refreshToken ?: return false
        return runCatching {
            val response = client.post("$baseUrl/api/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(currentRefreshToken))
            }
            if (!response.status.isSuccess()) return false
            saveTokens(response.body())
            true
        }.getOrDefault(false)
    }

    private fun fail(body: String): Nothing {
        val parsed = runCatching { json.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        error(parsed?.error ?: parsed?.reason ?: body.take(280))
    }
}

@Serializable data class ErrorBody(val error: String? = null, val reason: String? = null)

@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String)
@Serializable data class InvitationRegistrationRequest(val email: String, val password: String, val displayName: String, val token: String)
@Serializable data class TenantSwitchRequest(val tenantId: String)
@Serializable data class TenantInviteRequest(val email: String, val role: String)
@Serializable data class TenantInviteAcceptRequest(val token: String)
@Serializable data class TenantMembershipDto(val tenantId: String, val tenantName: String, val role: String)
@Serializable data class TenantRoleUpdateRequest(val role: String)
@Serializable data class TenantDto(val tenantId: String, val tenantName: String, val role: String)
@Serializable data class TenantMemberDto(val userId: String, val email: String, val displayName: String, val role: String)
@Serializable data class TenantInvitationDto(val tenantId: String, val email: String, val role: String, val token: String, val expiresAt: Long)
@Serializable data class MarketMakerStateDto(
    val enabled: Boolean,
    val mode: String,
    val quoteNotionalCap: String,
    val inventoryNotionalCap: String,
    val staleQuoteTtlMs: Long
)
@Serializable data class MarketMakerActionDto(val enabled: Boolean, val cancelledOrders: Int = 0)
@Serializable data class BinanceTestnetStatusDto(val configured: Boolean, val environment: String = "SPOT_TESTNET", val usesArgentLedger: Boolean = false)
@Serializable data class BinanceTestnetOrderRequestDto(
    val symbol: String, val side: String, val type: String, val quantity: String,
    val price: String? = null, val timeInForce: String = "GTC", val clientOrderId: String? = null
)
@Serializable data class BinanceTestnetOrderDto(
    val symbol: String, val orderId: Long, val clientOrderId: String? = null,
    val price: String, val origQty: String, val executedQty: String, val status: String,
    val timeInForce: String? = null, val type: String, val side: String, val transactTime: Long? = null,
    val cummulativeQuoteQty: String? = null, val fills: List<com.argent.terminal.api.BinanceTestnetFillDto> = emptyList(),
    val ledgerSynced: Boolean = false
)
@Serializable data class BinanceTestnetFillDto(
    val price: String, val qty: String, val commission: String, val commissionAsset: String, val tradeId: Long? = null
)
@Serializable data class BistReplayRequestDto(val csv: String, val speed: Int = 1000)
@Serializable data class BistReplayDto(
    val replayId: String? = null,
    val state: String,
    val rows: Int = 0,
    val processed: Int = 0,
    val error: String? = null
)
@Serializable data class EmptyRequest(val unused: String = "")
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutResponse(val revoked: Boolean)
@Serializable data class RegisterResponse(val userId: String, val email: String, val displayName: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val tokenType: String = "Bearer")
@Serializable data class MeResponse(val userId: String, val email: String, val displayName: String, val role: String, val tenantId: String = "", val tenantRole: String = "VIEWER")
@Serializable data class InstrumentDto(val symbol: String, val baseAsset: String, val quoteAsset: String, val assetClass: String, val displayName: String, val tickSize: String, val lotSize: String)
@Serializable data class TickerDto(val symbol: String, val price: String, val bid: String? = null, val ask: String? = null, val volume24h: String? = null, val source: String? = null, val timestamp: Long = 0)
@Serializable data class DepthLevelDto(val price: String, val quantity: String, val orderCount: Int)
@Serializable data class OrderBookDto(val symbol: String, val bids: List<DepthLevelDto> = emptyList(), val asks: List<DepthLevelDto> = emptyList(), val bestBid: String? = null, val bestAsk: String? = null)
@Serializable data class BalanceDto(val userId: String, val asset: String, val available: String, val held: String, val version: Long = 0)
@Serializable data class PositionDto(val userId: String, val symbol: String, val quantity: String, val avgEntryPrice: String, val realizedPnl: String)
@Serializable data class PortfolioValuationDto(
    val quoteAsset: String,
    val equity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val grossExposure: String,
    val marginUsed: String,
    val marginRatio: String,
    val priceComplete: Boolean,
    val pnlComplete: Boolean
)
@Serializable data class PortfolioDto(
    val userId: String,
    val balances: List<BalanceDto> = emptyList(),
    val positions: List<PositionDto> = emptyList(),
    val equity: String = "0",
    val realizedPnl: String = "0",
    val unrealizedPnl: String = "0",
    val marginUsed: String = "0",
    val marginRatio: String = "0",
    val riskScore: Int = 0,
    val marginCall: Boolean = false,
    val valuations: List<PortfolioValuationDto> = emptyList()
)
@Serializable data class OrderDto(
    val id: String,
    val clientOrderId: String? = null,
    val userId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val price: String? = null,
    val quantity: String,
    val filledQuantity: String,
    val remainingQuantity: String,
    val status: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
@Serializable data class TradeDto(
    val id: String,
    val symbol: String,
    val price: String,
    val quantity: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val makerOrderId: String = "",
    val takerOrderId: String = "",
    val executedAt: Long = 0
)
@Serializable data class AssistantStatusDto(val enabled: Boolean, val provider: String, val model: String? = null)
@Serializable data class AssistantQuestionDto(val question: String, val symbol: String? = null)
@Serializable data class AssistantAnswerDto(
    val answer: String,
    val generatedAt: Long,
    val dataAsOf: Long,
    val sources: List<String> = emptyList(),
    val marketMode: String
)
@Serializable data class PlaceOrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val price: String? = null,
    val quantity: String,
    val clientOrderId: String? = null
)
@Serializable data class OrderResultDto(
    val orderId: String,
    val status: String,
    val filledQuantity: String,
    val remainingQuantity: String,
    val accepted: Boolean,
    val reason: String,
    val trades: List<TradeDto> = emptyList()
)
@Serializable data class DepositRequest(val userId: String, val asset: String, val amount: String, val idempotencyKey: String? = null)
@Serializable data class WithdrawRequest(val asset: String, val amount: String, val idempotencyKey: String? = null)
@Serializable data class HealthDto(
    val status: String,
    val invariantOk: Boolean,
    val redis: Boolean,
    val postgres: Boolean,
    val rabbit: Boolean = false,
    val matchingEngine: Boolean = true,
    val executionMode: String = "INTERNAL_MATCHING",
    val cryptoMarketData: String = "CONNECTING",
    val bistMarketData: String = "UNAVAILABLE"
)
