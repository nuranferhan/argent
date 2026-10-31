package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.engine.MatchingEngine
import com.argent.core.ArgentMatchingCore
import com.argent.engine.OrderBookDepth
import com.argent.gateway.api.CancelResponse
import com.argent.gateway.api.BinanceTestnetOrderRequest
import com.argent.gateway.api.BinanceTestnetStatusResponse
import com.argent.gateway.api.BistHistoricalReplayRequest
import com.argent.gateway.api.DepositRequest
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.HealthResponse
import com.argent.gateway.api.InstrumentDto
import com.argent.gateway.api.LoginRequest
import com.argent.gateway.api.GoogleLoginRequest
import com.argent.gateway.api.GoogleAuthConfigResponse
import com.argent.gateway.api.GoogleNonceResponse
import com.argent.gateway.api.LedgerReconciliationResponse
import com.argent.gateway.api.LedgerReconciliationMismatchDto
import com.argent.gateway.api.MeResponse
import com.argent.common.events.TradeExecuted
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderAccepted
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderRejected
import com.argent.common.events.OrderSubmitted
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.api.ReplayResponse
import com.argent.gateway.api.RefreshRequest
import com.argent.gateway.api.TenantSwitchRequest
import com.argent.gateway.api.TenantInviteRequest
import com.argent.gateway.api.TenantInviteAcceptRequest
import com.argent.gateway.api.TenantMembershipDto
import com.argent.gateway.api.TenantInvitationDto
import com.argent.gateway.api.TenantMemberDto
import com.argent.gateway.api.TenantRoleUpdateRequest
import com.argent.gateway.api.TenantRoleUpdateResponse
import com.argent.gateway.api.TenantMemberRevokedResponse
import com.argent.gateway.api.TenantWorkspaceClosedResponse
import com.argent.gateway.api.MarketMakerStatusResponse
import com.argent.gateway.api.MarketMakerActionResponse
import com.argent.gateway.api.RegisterRequest
import com.argent.gateway.api.InvitationRegistrationRequest
import com.argent.gateway.api.RegisterResponse
import com.argent.gateway.api.TickerResponse
import com.argent.gateway.api.TokenResponse
import com.argent.gateway.api.TradeInfo
import com.argent.gateway.api.AssistantQuestionRequest
import com.argent.gateway.api.AssistantOrderDraftRequest
import com.argent.gateway.api.AssistantOrderDraftResponse
import com.argent.gateway.api.ConfirmAssistantOrderRequest
import com.argent.gateway.api.AssistantStatusResponse
import com.argent.gateway.api.AssistantAlertsResponse
import com.argent.gateway.api.WithdrawRequest
import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.GoogleIdentityService
import com.argent.gateway.assistant.ReadOnlyAssistantService
import com.argent.gateway.assistant.AssistantPortfolioHistoryStore
import com.argent.gateway.assistant.AssistantDraftOrder
import com.argent.gateway.assistant.AssistantOrderDraftService
import com.argent.gateway.auth.UserRole
import com.argent.gateway.auth.TenantRole
import com.argent.gateway.config.AppConfig
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.mm.LiquidityProvider
import com.argent.gateway.mm.MarketMakerControlStore
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.security.ClientAddressResolver
import com.argent.gateway.trading.TradingService
import com.argent.gateway.trading.BinanceTestnetExecutionService
import com.argent.ledger.LedgerService
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.market.MarketFeedMode
import com.argent.market.TickerUpdate
import com.argent.market.TechnicalAnalysis
import com.argent.market.BinanceSpotTestnet
import com.argent.market.BinanceTestnetException
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.market.DailyFxRatesService
import com.argent.gateway.market.PriceAlertStore
import com.argent.gateway.market.CreatePriceAlertRequest
import com.argent.gateway.market.toResponse
import com.argent.market.TestnetOrderRequest
import com.argent.risk.RiskService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("ArgentGatewayBootstrap")

internal fun secureTokenMatches(expected: String?, authorization: String?): Boolean {
    if (expected.isNullOrBlank() || authorization.isNullOrBlank()) return false
    val supplied = authorization.removePrefix("Bearer ").trim().toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), supplied)
}

internal fun seedBalanceForPublicTrader(ledger: LedgerService, accessToken: String) {
    val claims = JWT.decode(accessToken)
    if (claims.getClaim("tenantId").asString() == AuthService.PUBLIC_TENANT_ID) seedPublicPaperBalance(ledger, claims.subject)
}

internal fun seedPublicPaperBalance(ledger: LedgerService, userId: String) {
    ledger.deposit(userId, "USDT", BigDecimal("25000"), "public-signup-v1")
    ledger.deposit(userId, "TRY", BigDecimal("500000"), "public-signup-v1")
}

internal fun bootstrap(config: AppConfig, auth: AuthService, ledger: LedgerService) {
    val email = config.bootstrapAdminEmail
    val pass = config.bootstrapAdminPassword
    if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
        val admin = auth.findByEmail(email) ?: auth.register(email, pass, "Admin", UserRole.ADMIN).also {
            log.info("Bootstrap ADMIN id={} email={}", it.id, it.email)
        }
        check(admin.role == UserRole.ADMIN) {
            "BOOTSTRAP_ADMIN_EMAIL belongs to a non-admin account; refusing automatic privilege escalation"
        }
        if (config.seedDemoFunds) {
            listOf(
                "TRY" to "5000000",
                "USDT" to "250000",
                "BNB" to "100",
                "THYAO" to "8000",
                "GARAN" to "8000",
                "ASELS" to "8000"
            ).forEach { (asset, amt) ->
                ledger.deposit(admin.id, asset, BigDecimal(amt), "seed-admin-$asset")
            }
            log.warn("Development demo funds applied to admin account id={}", admin.id)
        }
    }
    if (config.seedMarketMaker) {
        val mm = try {
            auth.register("mm@argent.local", config.marketMakerPassword, "Liquidity", UserRole.MARKET_MAKER)
        } catch (_: IllegalArgumentException) {
            auth.findByEmail("mm@argent.local")
        }
        if (mm != null) {
            val assets = listOf(
                "TRY" to "100000000",
                "USDT" to "5000000",
                "BTC" to "50",
                "ETH" to "400",
                "SOL" to "5000",
                "THYAO" to "50000",
                "GARAN" to "50000",
                "ASELS" to "50000",
                "EREGL" to "50000",
                "SISE" to "50000"
            )
            assets.forEach { (asset, amt) ->
                ledger.deposit(mm.id, asset, BigDecimal(amt), "seed-mm-$asset")
            }
            log.warn("Development demo funds applied to market maker id={}", mm.id)
        }
    }
}
