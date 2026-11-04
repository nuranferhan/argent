package com.argent.gateway

import com.argent.gateway.api.BinanceTestnetOrderRequest
import com.argent.gateway.api.BinanceTestnetStatusResponse
import com.argent.gateway.api.BistHistoricalReplayRequest
import com.argent.gateway.api.CancelResponse
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.LedgerReconciliationMismatchDto
import com.argent.gateway.api.LedgerReconciliationResponse
import com.argent.gateway.api.MarketMakerActionResponse
import com.argent.gateway.api.MarketMakerStatusResponse
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.api.ReplayResponse
import com.argent.gateway.api.TradeInfo
import com.argent.gateway.auth.TenantRole
import com.argent.gateway.auth.UserRole
import com.argent.market.BinanceTestnetException
import com.argent.market.TestnetOrderRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

internal fun io.ktor.server.routing.Route.registerTradingAdminRoutes(ctx: GatewayRouteContext) {
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/orders") {
                val p = call.requireArgentPrincipal()
                call.respond(orders.findByUser(p.accountId))
            }
            get("/api/v1/fills") {
                val p = call.requireArgentPrincipal()
                call.respond(
                    orders.findTrades(userId = p.accountId).map {
                        TradeInfo(it.id, it.symbol, it.price, it.quantity, it.buyOrderId, it.sellOrderId, it.makerOrderId, it.takerOrderId, it.executedAt)
                    },
                )
            }
            post("/api/v1/orders") {
                if (!engine.isHealthy()) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(
                            "Matching is paused after an internal failure; restart the gateway to replay durable events",
                        ),
                    )
                }
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Trading permission required"))
                }
                val req = call.receive<PlaceOrderRequest>()
                val headerKey = call.request.header("Idempotency-Key")
                require(
                    headerKey.isNullOrBlank() || req.clientOrderId.isNullOrBlank() || headerKey == req.clientOrderId,
                ) {
                    "Idempotency-Key must match clientOrderId when both are provided"
                }
                val effective =
                    if (req.clientOrderId.isNullOrBlank() && !headerKey.isNullOrBlank()) {
                        req.copy(clientOrderId = headerKey)
                    } else {
                        req
                    }
                require(!effective.clientOrderId.isNullOrBlank() && effective.clientOrderId.length <= 128) {
                    "Provide an Idempotency-Key or clientOrderId between 1 and 128 characters"
                }
                val result =
                    trading.withAccountLock(p.accountId) {
                        val current = auth.tenantAccess(p.userId, p.tenantId)
                        if (current == null || current.accountId != p.accountId || current.role !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                            null
                        } else {
                            trading.placeOrder(p.accountId, effective)
                        }
                    } ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant trading permission is no longer active"))
                call.respond(result)
            }
            delete("/api/v1/orders/{orderId}") {
                if (!engine.isHealthy()) {
                    return@delete call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse(
                            "Matching is paused after an internal failure; restart the gateway to replay durable events",
                        ),
                    )
                }
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Trading permission required"),
                    )
                }
                val orderId = call.parameters["orderId"] ?: throw IllegalArgumentException("Order id is required")
                val symbol =
                    call.request.queryParameters["symbol"]?.uppercase()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query param required"))
                val cancelled =
                    trading.withAccountLock(p.accountId) {
                        val current = auth.tenantAccess(p.userId, p.tenantId)
                        current != null && current.accountId == p.accountId && current.role in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER) &&
                            trading.cancel(p.accountId, orderId, symbol)
                    }
                if (cancelled) {
                    call.respond(CancelResponse("cancelled", orderId))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Order not found or not in book"))
                }
            }
            post("/api/v1/withdraw") {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("External withdrawal settlement is not configured; no withdrawal was processed"),
                )
            }
            post("/api/v1/admin/deposit") {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    ErrorResponse("External deposit verification is not configured; no funds were credited"),
                )
            }
            get("/api/v1/admin/ledger/reconciliation") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val report = ledger.reconciliationReport()
                call.respond(
                    LedgerReconciliationResponse(
                        ok = report.ok,
                        checkedAccountAssets = report.checkedAccountAssets,
                        mismatches =
                            report.mismatches.map {
                                LedgerReconciliationMismatchDto(it.account, it.asset, it.journalNet, it.balanceTotal)
                            },
                    ),
                )
            }
            get("/api/v1/admin/binance-spot-testnet") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                call.respond(
                    BinanceTestnetStatusResponse(
                        configured = binanceTestnet.configured,
                        usesArgentLedger = binanceTestnet.configured,
                    ),
                )
            }
            get("/api/v1/admin/binance-spot-testnet/price") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val symbol = call.request.queryParameters["symbol"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query parameter is required"))
                try {
                    call.respond(binanceTestnet.tickerPrice(symbol))
                } catch (failure: BinanceTestnetException) {
                    val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                    call.respond(status, ErrorResponse(failure.message ?: "Could not load Testnet price"))
                }
            }
            post("/api/v1/admin/binance-spot-testnet/orders") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                if (!binanceTestnet.configured) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Configure Binance Spot Testnet credentials locally first"),
                    )
                }
                val body = call.receive<BinanceTestnetOrderRequest>()
                try {
                    val result =
                        binanceTestnetExecution.place(
                            p.accountId,
                            TestnetOrderRequest(
                                symbol = body.symbol,
                                side = body.side,
                                type = body.type,
                                quantity = body.quantity,
                                price = body.price,
                                timeInForce = body.timeInForce,
                                clientOrderId = body.clientOrderId,
                            ),
                        )
                    call.respond(result)
                } catch (failure: BinanceTestnetException) {
                    val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                    val message = failure.message.orEmpty()
                    val userMessage =
                        if (message.contains("PERCENT_PRICE_BY_SIDE", ignoreCase = true)) {
                            "Fiyat Binance Spot Testnet'in izin verdiği aralığın dışında; güncel Testnet fiyatını yenileyip tekrar deneyin. (PERCENT_PRICE_BY_SIDE)"
                        } else {
                            message.ifBlank { "Binance Spot Testnet request failed" }
                        }
                    call.respond(status, ErrorResponse(userMessage))
                }
            }
            get("/api/v1/admin/binance-spot-testnet/orders/{orderId}") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                if (!binanceTestnet.configured) {
                    return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Configure Binance Spot Testnet credentials locally first"),
                    )
                }
                val symbol = call.request.queryParameters["symbol"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query parameter is required"))
                val orderId = call.parameters["orderId"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("orderId must be numeric"))
                try {
                    call.respond(binanceTestnetExecution.get(p.accountId, symbol, orderId))
                } catch (
                    failure: BinanceTestnetException,
                ) {
                    val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                    call.respond(status, ErrorResponse(failure.message ?: "Binance Spot Testnet request failed"))
                }
            }
            delete("/api/v1/admin/binance-spot-testnet/orders/{orderId}") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@delete call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                if (!binanceTestnet.configured) {
                    return@delete call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Configure Binance Spot Testnet credentials locally first"),
                    )
                }
                val symbol = call.request.queryParameters["symbol"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query parameter is required"))
                val orderId = call.parameters["orderId"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("orderId must be numeric"))
                try {
                    call.respond(binanceTestnetExecution.cancel(p.accountId, symbol, orderId))
                } catch (
                    failure: BinanceTestnetException,
                ) {
                    val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                    call.respond(status, ErrorResponse(failure.message ?: "Binance Spot Testnet request failed"))
                }
            }
            post("/api/v1/admin/market-data/bist/replay") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val request = call.receive<BistHistoricalReplayRequest>()
                try {
                    call.respond(
                        HttpStatusCode.Accepted,
                        market.startBistHistoricalReplay(request.csv, request.speed),
                    )
                } catch (failure: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(failure.message ?: "Invalid BIST CSV"))
                } catch (failure: IllegalStateException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(failure.message ?: "BIST replay unavailable"),
                    )
                }
            }
            get("/api/v1/admin/market-data/bist/replay") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                call.respond(market.bistHistoricalReplayStatus() ?: mapOf("state" to "IDLE"))
            }
            get("/api/v1/admin/market-maker") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@get call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val mm =
                    liquidityProvider
                        ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                call.respond(
                    MarketMakerStatusResponse(
                        enabled = mm.isEnabled,
                        mode = "INTERNAL",
                        quoteNotionalCap = mm.quoteNotionalLimit,
                        inventoryNotionalCap = mm.inventoryNotionalLimit,
                        staleQuoteTtlMs = mm.quoteAgeLimitMs,
                    ),
                )
            }
            post("/api/v1/admin/market-maker/kill-switch") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val mm =
                    liquidityProvider
                        ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                val cancelled = mm.killSwitch()
                call.respond(MarketMakerActionResponse(false, cancelled))
            }
            post("/api/v1/admin/market-maker/resume") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("Admin only"),
                    )
                }
                val mm =
                    liquidityProvider
                        ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                mm.resume()
                call.respond(MarketMakerActionResponse(true))
            }
            post("/api/v1/admin/replay/{symbol}") {
                val p = call.requireArgentPrincipal()
                if (p.role != UserRole.ADMIN.name) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    return@post
                }
                val symbol = call.parameters["symbol"]?.uppercase() ?: throw IllegalArgumentException("Symbol is required")
                val events = eventStore.load(symbol)
                val scratch =
                    core.newMatchingEngine(
                        eventSink = {},
                        keepRecordedEvents = false,
                        rollbackOnMutationFailure = false,
                    )
                try {
                    scratch.replay(events)
                    val depth = scratch.depth(symbol)
                    val resting = scratch.snapshot(symbol)
                    call.respond(
                        ReplayResponse(
                            symbol = symbol,
                            events = events.size,
                            restingOrders = resting.size,
                            bestBid = depth.bestBid,
                            bestAsk = depth.bestAsk,
                        ),
                    )
                } finally {
                    scratch.shutdown()
                }
            }
        }
    }
}

private val log = org.slf4j.LoggerFactory.getLogger("ArgentTradingRoutes")
