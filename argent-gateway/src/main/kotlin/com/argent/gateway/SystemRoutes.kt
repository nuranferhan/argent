package com.argent.gateway

import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.HealthResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

private val log = org.slf4j.LoggerFactory.getLogger("ArgentSystemRoutes")

internal fun Route.registerSystemRoutes(
    ctx: GatewayRouteContext,
    metrics: PrometheusMeterRegistry,
) {
    with(ctx) {
        get("/health") {
            if (!config.isDev && !secureTokenMatches(config.observabilityToken, call.request.header(HttpHeaders.Authorization))) {
                return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Observability credentials required"),
                )
            }
            val invariant =
                runCatching { ledger.verifyInvariant() }
                    .onFailure { log.error("Ledger invariant check failed", it) }
                    .getOrDefault(false)
            val postgres =
                runCatching { dataSource.connection.use { it.isValid(2) } }
                    .onFailure { log.warn("PostgreSQL health check failed: {}", it.message) }
                    .getOrDefault(false)
            val redis = limiter.ping()
            val rabbit = broker.isConnected()
            val matchingEngine = engine.isHealthy()
            val healthy = invariant && postgres && redis && rabbit && matchingEngine
            call.respond(
                HealthResponse(
                    status = if (healthy) "UP" else "DEGRADED",
                    invariantOk = invariant,
                    redis = redis,
                    postgres = postgres,
                    rabbit = rabbit,
                    matchingEngine = matchingEngine,
                    executionMode = "INTERNAL_MATCHING",
                    cryptoMarketData = market.cryptoFeedStatus(),
                    bistMarketData = market.bistFeedStatus(),
                ),
            )
        }
        get("/metrics") {
            if (!config.isDev && !secureTokenMatches(config.observabilityToken, call.request.header(HttpHeaders.Authorization))) {
                return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Observability credentials required"),
                )
            }
            call.respondText(metrics.scrape())
        }
    }
}

internal fun Route.registerPortfolioSocket(ctx: GatewayRouteContext) {
    with(ctx) {
        webSocket("/ws/portfolio") {
            val token =
                call.request.header(HttpHeaders.Authorization)
                    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.substringAfter(' ')
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Bearer token required"))
            val decoded =
                runCatching { JWT.require(Algorithm.HMAC256(config.jwtSecret)).build().verify(token) }.getOrNull()
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
            val userId = decoded.getClaim("userId").asString() ?: decoded.subject
            if (auth.getUser(userId)?.active != true) {
                return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "account unavailable"))
            }
            val tenantId = decoded.getClaim("tenantId").asString()
            val access = tenantId?.let { auth.tenantAccess(userId, it) }
            val accountId = decoded.getClaim("accountId").asString()
            if (access == null || accountId != access.accountId || accountId.isNullOrBlank()) {
                return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "account unavailable"))
            }
            while (true) {
                val snapshot = portfolioSnapshot(accountId, market.allLastPrices())
                send(Frame.Text(json.encodeToString(snapshot)))
                delay(1_000)
            }
        }
    }
}
