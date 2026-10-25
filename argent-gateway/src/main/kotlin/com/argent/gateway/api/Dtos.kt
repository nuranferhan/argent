package com.argent.gateway.api

import com.argent.common.domain.Balance
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class InvitationRegistrationRequest(val email: String, val password: String, val displayName: String, val token: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GoogleLoginRequest(val idToken: String, val nonce: String)

@Serializable
data class GoogleAuthConfigResponse(val configured: Boolean, val webClientId: String? = null, val desktopClientId: String? = null)

@Serializable
data class GoogleNonceResponse(val nonce: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TenantSwitchRequest(val tenantId: String)

@Serializable
data class TenantInviteRequest(val email: String, val role: String = "TRADER")

@Serializable
data class TenantInviteAcceptRequest(val token: String)

@Serializable
data class TenantRoleUpdateRequest(val role: String)

@Serializable
data class TenantRoleUpdateResponse(val userId: String, val role: String, val reauthenticationRequired: Boolean)

@Serializable
data class TenantMembershipDto(val tenantId: String, val tenantName: String, val role: String)

@Serializable
data class TenantMemberDto(val userId: String, val email: String, val displayName: String, val role: String)

@Serializable
data class TenantMemberRevokedResponse(val userId: String, val revoked: Boolean, val openOrdersCancelled: Boolean)

@Serializable
data class TenantWorkspaceClosedResponse(val tenantId: String, val closed: Boolean)

@Serializable
data class MarketMakerStatusResponse(
    val enabled: Boolean,
    val mode: String,
    val quoteNotionalCap: String,
    val inventoryNotionalCap: String,
    val staleQuoteTtlMs: Long
)

@Serializable
data class MarketMakerActionResponse(val enabled: Boolean, val cancelledOrders: Int = 0)

@Serializable
data class TenantInvitationDto(
    val tenantId: String,
    val email: String,
    val role: String,
    val token: String,
    val expiresAt: Long
)

@Serializable
data class PlaceOrderRequest(
    val symbol: String,
    val side: String,
    val type: String = "LIMIT",
    val price: String? = null,
    val quantity: String,
    val clientOrderId: String? = null
)

@Serializable
data class DepositRequest(
    val userId: String,
    val asset: String,
    val amount: String,
    val idempotencyKey: String? = null
)

@Serializable
data class WithdrawRequest(
    val asset: String,
    val amount: String,
    val idempotencyKey: String? = null
)

@Serializable
data class HealthResponse(
    val status: String,
    val invariantOk: Boolean,
    val redis: Boolean,
    val postgres: Boolean,
    val rabbit: Boolean,
    val matchingEngine: Boolean = true,
    val executionMode: String = "INTERNAL_MATCHING",
    val cryptoMarketData: String = "CONNECTING",
    val bistMarketData: String = "UNAVAILABLE"
)

@Serializable
data class LedgerReconciliationResponse(
    val ok: Boolean,
    val checkedAccountAssets: Int,
    val mismatches: List<LedgerReconciliationMismatchDto>
)

@Serializable
data class LedgerReconciliationMismatchDto(
    val account: String,
    val asset: String,
    val journalNet: String,
    val balanceTotal: String
)

@Serializable data class BinanceTestnetOrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: String,
    val price: String? = null,
    val timeInForce: String = "GTC",
    val clientOrderId: String? = null
)
@Serializable data class BinanceTestnetStatusResponse(val configured: Boolean, val environment: String = "SPOT_TESTNET", val usesArgentLedger: Boolean = true)
@Serializable data class BistHistoricalReplayRequest(val csv: String, val speed: Int = 1000)

@Serializable
data class TickerResponse(
    val symbol: String,
    val price: String,
    val bid: String? = null,
    val ask: String? = null,
    val volume24h: String? = null,
    val source: String? = null,
    val timestamp: Long = 0
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class RegisterResponse(val userId: String, val email: String, val displayName: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

@Serializable
data class MeResponse(val userId: String, val email: String, val displayName: String, val role: String, val tenantId: String, val tenantRole: String)

@Serializable
data class CancelResponse(val status: String, val orderId: String)

@Serializable
data class TradeInfo(
    val id: String,
    val symbol: String,
    val price: String,
    val quantity: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val makerOrderId: String,
    val takerOrderId: String,
    val executedAt: Long
)

@Serializable
data class AssistantQuestionRequest(
    val question: String,
    val symbol: String? = null,
    val draftSide: String? = null,
    val draftType: String? = null,
    val draftQuantity: String? = null,
    val draftPrice: String? = null,
    val language: String = "tr"
)

@Serializable data class AssistantOrderDraftRequest(val prompt: String)
@Serializable data class ConfirmAssistantOrderRequest(val password: String, val acknowledgeSummary: Boolean)
@Serializable data class AssistantOrderDraftResponse(
    val draftId: String, val symbol: String, val side: String, val type: String,
    val quantity: String, val price: String, val quoteNotional: String,
    val quoteAsset: String, val source: String, val dataAsOf: Long, val expiresAt: Long,
    val note: String = "Bu yalnızca taslaktır; gönderim için ayrıca onay gerekir."
)

@Serializable
data class AssistantStatusResponse(val enabled: Boolean, val provider: String, val model: String? = null)

@Serializable
data class AssistantAnswerResponse(
    val answer: String,
    val generatedAt: Long,
    val dataAsOf: Long,
    val sources: List<String>,
    val marketMode: String
)

@Serializable
data class AssistantAlertsResponse(
    val alerts: List<String>,
    val generatedAt: Long,
    val dataAsOf: Long,
    val sources: List<String>,
    val marketMode: String
)

@Serializable
data class OrderResultResponse(
    val orderId: String,
    val status: String,
    val filledQuantity: String,
    val remainingQuantity: String,
    val accepted: Boolean,
    val reason: String,
    val trades: List<TradeInfo>
)

@Serializable
data class ReplayResponse(
    val symbol: String,
    val events: Int,
    val restingOrders: Int,
    val bestBid: String? = null,
    val bestAsk: String? = null
)

@Serializable
data class InstrumentDto(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val assetClass: String,
    val displayName: String,
    val tickSize: String,
    val lotSize: String
)
