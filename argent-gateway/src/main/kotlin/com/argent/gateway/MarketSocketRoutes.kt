package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderAccepted
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderRejected
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import com.argent.engine.MatchingEngine
import com.argent.engine.OrderBookDepth
import com.argent.gateway.auth.AuthService
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.security.ClientAddressResolver
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.market.TickerUpdate
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

internal fun Route.registerMarketSockets(
    market: MarketDataService,
    engine: MatchingEngine,
    broker: EventBroker,
    jwtSecret: String,
    auth: AuthService,
    clientAddressResolver: ClientAddressResolver,
    limiter: RedisRateLimiter,
    orders: OrderRepository,
    json: Json,
) {
    webSocket("/ws/ticker") {
        val job =
            launch {
                market.tickers.collect { t ->
                    if (market.lastTicker(t.symbol)?.timestamp == t.timestamp) {
                        send(Frame.Text(json.encodeToString(TickerUpdate.serializer(), t)))
                    }
                }
            }
        try {
            for (frame in incoming) { /* keepalive */ }
        } finally {
            job.cancel()
        }
    }
    webSocket("/ws/orderbook/{symbol}") {
        val symbol = call.parameters["symbol"]?.uppercase()
        if (symbol == null || !symbol.matches(Regex("[A-Z0-9._-]{1,32}")) || Instruments.get(symbol) == null) {
            return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid instrument"))
        }
        val token =
            call.request.header(HttpHeaders.Authorization)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substringAfter(' ')?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Bearer token required"))
        if (resolveWebSocketPrincipal(token, jwtSecret, auth) == null) {
            return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid session"))
        }
        val clientAddress =
            clientAddressResolver.resolve(
                call.request.local.remoteHost,
                call.request.header("X-Forwarded-For"),
            )
        if (!limiter.allow("ws:orderbook:$clientAddress", 20, 60)) {
            return@webSocket close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "connection rate limit"))
        }
        val updates = Channel<Unit>(Channel.CONFLATED)
        val subscription = broker.subscribe { event -> if (event.aggregateId == symbol) updates.trySend(Unit) }
        try {
            send(Frame.Text(json.encodeToString(OrderBookDepth.serializer(), engine.depth(symbol))))
            while (true) {
                val changed = withTimeoutOrNull(30.seconds) { updates.receive() }
                if (resolveWebSocketPrincipal(token, jwtSecret, auth) == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session expired or revoked"))
                    break
                }
                if (changed != null) {
                    send(
                        Frame.Text(json.encodeToString(OrderBookDepth.serializer(), engine.depth(symbol))),
                    )
                }
            }
        } finally {
            subscription.close()
            updates.close()
        }
    }
    webSocket("/ws/orders") {
        val token =
            call.request.header(HttpHeaders.Authorization)
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substringAfter(' ')?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Bearer token required"))
        var session =
            resolveWebSocketPrincipal(token, jwtSecret, auth)
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid session"))
        val clientAddress =
            clientAddressResolver.resolve(
                call.request.local.remoteHost,
                call.request.header("X-Forwarded-For"),
            )
        if (!limiter.allow("ws:orders:${session.userId}:$clientAddress", 10, 60)) {
            return@webSocket close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "connection rate limit"))
        }
        val updates = Channel<WsOrderUpdate>(capacity = 64)
        val subscription =
            broker.subscribe { event ->
                orderUpdateForUser(event, session.userId, orders)?.let { updates.trySend(it) }
            }
        var lastAuthorizationCheck = System.currentTimeMillis()
        try {
            while (true) {
                val update = withTimeoutOrNull(30.seconds) { updates.receive() }
                if (System.currentTimeMillis() - lastAuthorizationCheck >= 30_000L) {
                    session = resolveWebSocketPrincipal(token, jwtSecret, auth)
                        ?: run {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session expired or revoked"))
                            break
                        }
                    lastAuthorizationCheck = System.currentTimeMillis()
                }
                if (update != null) send(Frame.Text(json.encodeToString(WsOrderUpdate.serializer(), update)))
            }
        } finally {
            subscription.close()
            updates.close()
        }
    }
}

internal fun resolveWebSocketPrincipal(
    token: String,
    secret: String,
    auth: AuthService,
): ArgentPrincipal? =
    runCatching {
        val decoded = JWT.require(Algorithm.HMAC256(secret)).build().verify(token)
        val userId = decoded.getClaim("userId").asString() ?: decoded.subject
        val user = auth.getUser(userId)?.takeIf { it.active } ?: return null
        val tenantId = decoded.getClaim("tenantId").asString() ?: user.tenantId
        val access = auth.tenantAccess(user.id, tenantId) ?: return null
        val accountId = decoded.getClaim("accountId").asString()
        val tenantRole = decoded.getClaim("tenantRole").asString()
        if (accountId != access.accountId || tenantRole != access.role.name) return null
        ArgentPrincipal(user.id, user.role.name, tenantId, access.role, access.accountId)
    }.getOrNull()

internal fun orderUpdateForUser(
    event: DomainEvent,
    userId: String,
    orders: OrderRepository,
): WsOrderUpdate? =
    when (event) {
        is OrderSubmitted ->
            event.order.takeIf {
                it.userId == userId
            }?.let { WsOrderUpdate("order", it.id, it.symbol, it.status.name) }
        is OrderAccepted ->
            event.order.takeIf {
                it.userId == userId
            }?.let { WsOrderUpdate("order", it.id, it.symbol, it.status.name) }
        is OrderRejected ->
            orders.findById(event.orderId)?.takeIf { it.userId == userId }
                ?.let { WsOrderUpdate("order", it.id, it.symbol, "REJECTED") }
        is OrderCancelled ->
            orders.findById(event.orderId)?.takeIf { it.userId == userId }
                ?.let { WsOrderUpdate("order", it.id, it.symbol, "CANCELLED") }
        is TradeExecuted ->
            event.trade.let { trade ->
                when (userId) {
                    trade.buyUserId -> WsOrderUpdate("fill", trade.buyOrderId, trade.symbol, "FILL_EXECUTED")
                    trade.sellUserId -> WsOrderUpdate("fill", trade.sellOrderId, trade.symbol, "FILL_EXECUTED")
                    else -> null
                }
            }
        else -> null
    }
