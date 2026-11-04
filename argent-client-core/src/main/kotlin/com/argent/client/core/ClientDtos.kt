package com.argent.client.core

import kotlinx.serialization.Serializable

@Serializable data class ErrorBody(val error: String? = null, val reason: String? = null)

@Serializable data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class GoogleAuthConfigDto(
    val configured: Boolean,
    val webClientId: String? = null,
    val desktopClientId: String? = null,
)

@Serializable data class GoogleNonceDto(val nonce: String)

@Serializable data class GoogleLoginRequestDto(val idToken: String, val nonce: String)

@Serializable
data class InvitationRegistrationRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val token: String,
)

@Serializable data class TenantSwitchRequest(val tenantId: String)

@Serializable data class TenantInviteRequest(val email: String, val role: String)

@Serializable data class TenantInviteAcceptRequest(val token: String)

@Serializable data class TenantMembershipDto(val tenantId: String, val tenantName: String, val role: String)

@Serializable data class TenantRoleUpdateRequest(val role: String)

@Serializable data class TenantDto(val tenantId: String, val tenantName: String, val role: String)

@Serializable
data class TenantMemberDto(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class TenantInvitationDto(
    val tenantId: String,
    val email: String,
    val role: String,
    val token: String,
    val expiresAt: Long,
)

@Serializable data class MarketMakerStateDto(
    val enabled: Boolean,
    val mode: String,
    val quoteNotionalCap: String,
    val inventoryNotionalCap: String,
    val staleQuoteTtlMs: Long,
)

@Serializable data class MarketMakerActionDto(val enabled: Boolean, val cancelledOrders: Int = 0)

@Serializable data class BinanceTestnetTickerPriceDto(val symbol: String, val price: String)

@Serializable
data class BinanceTestnetStatusDto(
    val configured: Boolean,
    val environment: String = "SPOT_TESTNET",
    val usesArgentLedger: Boolean = false,
)

@Serializable data class BinanceTestnetOrderRequestDto(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: String,
    val price: String? = null,
    val timeInForce: String = "GTC",
    val clientOrderId: String? = null,
)

@Serializable data class BinanceTestnetOrderDto(
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
    val fills: List<com.argent.client.core.BinanceTestnetFillDto> = emptyList(),
    val ledgerSynced: Boolean = false,
)

@Serializable data class BinanceTestnetFillDto(
    val price: String,
    val qty: String,
    val commission: String,
    val commissionAsset: String,
    val tradeId: Long? = null,
)

@Serializable data class BistReplayRequestDto(val csv: String, val speed: Int = 1000)

@Serializable data class BistReplayDto(
    val replayId: String? = null,
    val state: String,
    val rows: Int = 0,
    val processed: Int = 0,
    val error: String? = null,
)

@Serializable data class EmptyRequest(val unused: String = "")

@Serializable data class LoginRequest(val email: String, val password: String)

@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable data class LogoutResponse(val revoked: Boolean)

@Serializable data class RegisterResponse(val userId: String, val email: String, val displayName: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)

@Serializable
data class MeResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String,
    val tenantId: String = "",
    val tenantRole: String = "VIEWER",
)

@Serializable
data class InstrumentDto(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val assetClass: String,
    val displayName: String,
    val tickSize: String,
    val lotSize: String,
    val active: Boolean = true,
    val expiryDate: String? = null,
    val referencePrice: String? = null,
)

@Serializable
data class TickerDto(
    val symbol: String,
    val price: String,
    val bid: String? = null,
    val ask: String? = null,
    val volume24h: String? = null,
    val source: String? = null,
    val timestamp: Long = 0,
)

@Serializable data class TechnicalAnalysisDto(
    val symbol: String,
    val currentPrice: String? = null,
    val sma20: String? = null,
    val sma50: String? = null,
    val rsi14: String? = null,
    val change20Pct: String? = null,
    val support30: String? = null,
    val resistance30: String? = null,
    val sampleCount: Int = 0,
    val trend: String = "INSUFFICIENT_DATA",
    val source: String? = null,
    val timestamp: Long = 0,
)

@Serializable
data class ExternalMarketStatusDto(
    val provider: String = "Finnhub",
    val configured: Boolean,
    val planNote: String = "",
)

@Serializable data class ExternalQuoteDto(
    val symbol: String,
    val price: String,
    val change: String? = null,
    val changePercent: String? = null,
    val previousClose: String? = null,
    val timestamp: Long,
    val source: String = "Finnhub",
    val currency: String = "USD",
)

@Serializable data class MarketNewsDto(
    val id: Long,
    val headline: String,
    val summary: String = "",
    val source: String = "",
    val url: String,
    val publishedAt: Long,
    val relatedSymbols: String = "",
    val imageUrl: String? = null,
    val translatedHeadline: String? = null,
)

@Serializable data class CreatePriceAlertDto(val symbol: String, val direction: String, val threshold: String)

@Serializable data class PriceAlertDto(
    val id: String,
    val symbol: String,
    val direction: String,
    val threshold: String,
    val state: String,
    val createdAt: Long,
    val triggeredAt: Long? = null,
    val triggeredPrice: String? = null,
)

@Serializable data class DepthLevelDto(val price: String, val quantity: String, val orderCount: Int)

@Serializable
data class OrderBookDto(
    val symbol: String,
    val bids: List<DepthLevelDto> = emptyList(),
    val asks: List<DepthLevelDto> = emptyList(),
    val bestBid: String? = null,
    val bestAsk: String? = null,
)

@Serializable data class OrderUpdateDto(val event: String, val orderId: String, val symbol: String, val status: String)

@Serializable
data class BalanceDto(
    val userId: String,
    val asset: String,
    val available: String,
    val held: String,
    val version: Long = 0,
)

@Serializable
data class PositionDto(
    val userId: String,
    val symbol: String,
    val quantity: String,
    val avgEntryPrice: String,
    val realizedPnl: String,
)

@Serializable data class PortfolioValuationDto(
    val quoteAsset: String,
    val equity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val grossExposure: String,
    val priceComplete: Boolean,
    val pnlComplete: Boolean,
)

@Serializable data class PortfolioUsdValuationDto(
    val equity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val grossExposure: String,
    val complete: Boolean,
    val rateDate: String,
)

@Serializable data class PortfolioDto(
    val userId: String,
    val balances: List<BalanceDto> = emptyList(),
    val positions: List<PositionDto> = emptyList(),
    val equity: String = "0",
    val realizedPnl: String = "0",
    val unrealizedPnl: String = "0",
    val riskScore: Int = 0,
    val valuations: List<PortfolioValuationDto> = emptyList(),
    val usdValuation: PortfolioUsdValuationDto? = null,
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
    val updatedAt: Long = 0,
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
    val executedAt: Long = 0,
)

@Serializable data class AssistantStatusDto(val enabled: Boolean, val provider: String, val model: String? = null)

@Serializable data class AssistantAlertsDto(
    val alerts: List<String>,
    val generatedAt: Long,
    val dataAsOf: Long,
    val sources: List<String>,
    val marketMode: String,
)

@Serializable data class AssistantQuestionDto(
    val question: String,
    val symbol: String? = null,
    val draftSide: String? = null,
    val draftType: String? = null,
    val draftQuantity: String? = null,
    val draftPrice: String? = null,
    val language: String = "tr",
)

@Serializable data class AssistantAnswerDto(
    val answer: String,
    val generatedAt: Long,
    val dataAsOf: Long,
    val sources: List<String> = emptyList(),
    val marketMode: String,
)

@Serializable data class AssistantOrderDraftRequestDto(val prompt: String)

@Serializable data class ConfirmAssistantOrderDraftRequestDto(val password: String, val acknowledgeSummary: Boolean)

@Serializable data class AssistantOrderDraftDto(
    val draftId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: String,
    val price: String,
    val quoteNotional: String,
    val quoteAsset: String,
    val source: String,
    val dataAsOf: Long,
    val expiresAt: Long,
    val note: String,
)

@Serializable data class PlaceOrderRequest(
    val symbol: String,
    val side: String,
    val type: String,
    val price: String? = null,
    val quantity: String,
    val clientOrderId: String? = null,
)

@Serializable data class OrderResultDto(
    val orderId: String,
    val status: String,
    val filledQuantity: String,
    val remainingQuantity: String,
    val accepted: Boolean,
    val reason: String,
    val trades: List<TradeDto> = emptyList(),
)
