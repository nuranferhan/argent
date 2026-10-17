package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.engine.MatchingEngine
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
import com.argent.gateway.api.LedgerReconciliationResponse
import com.argent.gateway.api.LedgerReconciliationMismatchDto
import com.argent.gateway.api.MeResponse
import com.argent.common.events.TradeExecuted
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
import com.argent.gateway.api.WithdrawRequest
import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.UserRole
import com.argent.gateway.auth.TenantRole
import com.argent.gateway.config.AppConfig
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.mm.LiquidityProvider
import com.argent.gateway.mm.MarketMakerControlStore
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.trading.TradingService
import com.argent.gateway.trading.BinanceTestnetExecutionService
import com.argent.ledger.LedgerService
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.market.MarketFeedMode
import com.argent.market.TickerUpdate
import com.argent.market.BinanceSpotTestnet
import com.argent.market.BinanceTestnetException
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
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("ArgentGateway")
private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

data class ArgentPrincipal(val userId: String, val role: String, val tenantId: String, val tenantRole: TenantRole, val accountId: String) : Principal

fun main() {
    val config = AppConfig.load()
    val ds = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.dbUser
        password = config.dbPass
        maximumPoolSize = 16
        isAutoCommit = false
    })
    val database = Database.connect(ds)
    val broker = EventBroker(config.rabbitUrl).also { it.start() }
    val eventStore = PostgresEventStore(database).also { it.initSchema() }
    val eventBus: (com.argent.common.events.DomainEvent) -> Unit = { e ->
        eventStore.append(e)
        broker.publish(e)
    }

    val ledger = LedgerService(ds, eventBus).also { it.initSchema() }
    val auth = AuthService(database, config.jwtSecret).also { it.initSchema() }
    val orders = OrderRepository(database).also { it.initSchema() }
    val engine = MatchingEngine(eventBus, keepRecordedEvents = false)
    val market = MarketDataService(feedMode = config.marketFeedMode).also { it.start() }
    val binanceTestnet = BinanceSpotTestnet(config.binanceSpotTestnetApiKey, config.binanceSpotTestnetSecret)
    val risk = RiskService(ledger)
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val trading = TradingService(engine, ledger, risk, orders, market, prometheus)
    val limiter = RedisRateLimiter(config.redisUrl)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val binanceTestnetExecution = BinanceTestnetExecutionService(database, ledger, binanceTestnet).also { it.initSchema() }
    if (binanceTestnet.configured) {
        appScope.launch {
            while (true) {
                delay(5.seconds)
                runCatching { binanceTestnetExecution.syncActive() }
                    .onFailure { log.warn("Binance Testnet background sync will retry", it) }
            }
        }
    }

    bootstrap(config, auth, ledger)
    runBlocking {
        val history = eventStore.loadAll()
        val replayableOrderIds = history.filterIsInstance<OrderSubmitted>().map { it.order.id }.toSet()
        val replayedEvents = mutableListOf<com.argent.common.events.DomainEvent>()
        // Replay is read-only with respect to the durable event store. Capture its
        // regenerated fills for settlement recovery without retaining a second
        // unbounded event history in the engine.
        val recoveryEngine = MatchingEngine(
            eventBus = replayedEvents::add,
            keepRecordedEvents = false,
            rollbackOnMutationFailure = false
        )
        val recoveredOrders = recoveryEngine.replay(history)
        recoveredOrders.associateBy { it.id }.values.forEach(orders::save)
        trading.recoverUnsettled(replayedEvents.filterIsInstance<TradeExecuted>())
        recoveryEngine.shutdown()

        orders.findPending().forEach { pending ->
            if (pending.id in replayableOrderIds) {
                error("Order ${pending.id} remains pending after event replay; refusing to start with unresolved funds")
            }
            log.error("Rejecting pending order without a durable submission event id={}", pending.id)
            orders.save(pending.copy(status = com.argent.common.domain.OrderStatus.REJECTED))
            ledger.releaseRemainingHold(pending.id)
        }

        ledger.activeHoldOrderIds().forEach { orderId ->
            val order = orders.findById(orderId)
            if (order == null || order.status !in setOf(
                    com.argent.common.domain.OrderStatus.PENDING,
                    com.argent.common.domain.OrderStatus.ACCEPTED,
                    com.argent.common.domain.OrderStatus.PARTIALLY_FILLED
                )
            ) {
                log.error("Releasing orphaned funds hold orderId={}", orderId)
                ledger.releaseRemainingHold(orderId)
            }
        }

        val active = orders.findActive()
        log.info("Recovering {} active orders", active.size)
        active.forEach { engine.restore(it) }
    }

    var liquidityProvider: LiquidityProvider? = null
    if (config.seedMarketMaker) {
        val mm = auth.findByEmail("mm@argent.local")
        if (mm != null) {
            val controlStore = MarketMakerControlStore(database).also { it.initSchema() }
            liquidityProvider = LiquidityProvider(mm.id, trading, engine, market, orders, ledger, appScope, controlStore).also { it.start() }
            log.info("Liquidity provider started id={}", mm.id)
        }
    }

    val server = embeddedServer(Netty, port = config.port, host = config.bindHost) {
        install(ContentNegotiation) { json(json) }
        install(CORS) {
            config.corsHosts.forEach { allowHost(it, schemes = listOf("http", "https")) }
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader("Idempotency-Key")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Patch)
            allowMethod(HttpMethod.Delete)
        }
        install(CallLogging)
        install(StatusPages) {
            exception<IllegalArgumentException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
            }
            exception<Throwable> { call, cause ->
                log.error("Unhandled", cause)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal error"))
            }
        }
        install(WebSockets) {
            pingPeriodMillis = 15_000
            timeoutMillis = 30_000
        }
        install(MicrometerMetrics) { registry = prometheus }
        install(Authentication) {
            jwt("auth") {
                verifier(JWT.require(Algorithm.HMAC256(config.jwtSecret)).build())
                validate { cred ->
                    val userId = cred.payload.getClaim("userId").asString() ?: cred.payload.subject
                    val user = userId?.let(auth::getUser)
                    val tenantId = cred.payload.getClaim("tenantId").asString() ?: user?.tenantId
                    val access = if (user?.active == true && tenantId != null) auth.tenantAccess(user.id, tenantId) else null
                    val accountId = cred.payload.getClaim("accountId").asString()
                    val tenantRole = cred.payload.getClaim("tenantRole").asString()
                    if (access != null && accountId == access.accountId && tenantRole == access.role.name) {
                        ArgentPrincipal(user!!.id, user.role.name, tenantId!!, access.role, access.accountId)
                    } else null
                }
            }
        }

        intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            val skip = path == "/" || path == "/health" || path == "/metrics"
            if (skip) return@intercept
            val remoteHost = call.request.local.remoteHost
            val (bucket, requestLimit) = when (path) {
                "/api/v1/auth/login" -> "auth:login" to 10
                "/api/v1/auth/register" -> "auth:register" to 5
                "/api/v1/auth/register/invitation" -> "auth:invitation" to 10
                "/api/v1/auth/refresh" -> "auth:refresh" to 20
                else -> if (call.request.httpMethod == HttpMethod.Get) {
                    // The desktop terminal polls several read endpoints every second. A single
                    // IP-wide 120/minute bucket throttles normal UI use (and users behind NAT).
                    // Keep reads bounded per route, while preserving a shared write limit below.
                    "api:read:$path" to 300
                } else {
                    "api:write" to 120
                }
            }
            if (!limiter.allow("$bucket:$remoteHost", requestLimit)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("Rate limit exceeded"))
                finish()
            }
        }

        routing {
            get("/") {
                call.respondText("ARGENT Multi-Asset Trading & Settlement Platform", ContentType.Text.Plain)
            }
            get("/health") {
                val invariant = try { ledger.verifyInvariant() } catch (_: Exception) { false }
                val postgres = runCatching { ds.connection.use { it.isValid(2) } }.getOrDefault(false)
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
                        bistMarketData = when (config.marketFeedMode) {
                            MarketFeedMode.SIMULATED -> "SIMULATED"
                            MarketFeedMode.BINANCE_PUBLIC -> "SIMULATED"
                        }
                    )
                )
            }
            get("/metrics") { call.respondText(prometheus.scrape()) }

            get("/api/v1/instruments") {
                call.respond(
                    Instruments.all.map {
                        InstrumentDto(it.symbol, it.baseAsset, it.quoteAsset, it.assetClass.name, it.displayName, it.tickSize, it.lotSize)
                    }
                )
            }
            get("/api/v1/ticker/{symbol}") {
                val symbol = call.parameters["symbol"]!!.uppercase()
                val live = market.lastTicker(symbol)
                val price = live?.price ?: market.lastPrice(symbol)?.toPlainString()
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Unknown symbol"))
                call.respond(TickerResponse(symbol, price, live?.bid, live?.ask))
            }
            get("/api/v1/ticker/{symbol}/history") {
                call.respond(market.history(call.parameters["symbol"]!!.uppercase()))
            }
            get("/api/v1/orderbook/{symbol}") {
                call.respond(engine.depth(call.parameters["symbol"]!!.uppercase()))
            }
            get("/api/v1/trades") {
                val symbol = call.request.queryParameters["symbol"]?.uppercase()
                call.respond(
                    orders.findTrades(symbol).map {
                        TradeInfo(it.id, it.symbol, it.price, it.quantity, it.buyOrderId, it.sellOrderId, it.makerOrderId, it.takerOrderId, it.executedAt)
                    }
                )
            }

            get("/api/v1/stream/ticker") {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    val job = launch {
                        market.tickers.collect { t ->
                            write("data: ${json.encodeToString(TickerUpdate.serializer(), t)}\n\n")
                            flush()
                        }
                    }
                    try {
                        while (true) delay(60_000)
                    } finally {
                        job.cancel()
                    }
                }
            }

            post("/api/v1/auth/register") {
                if (!config.isDev) {
                    return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Public registration is disabled until email verification and abuse controls are configured"))
                }
                val body = call.receive<RegisterRequest>()
                val user = auth.register(body.email, body.password, body.displayName)
                call.respond(HttpStatusCode.Created, RegisterResponse(user.id, user.email, user.displayName))
            }
            post("/api/v1/auth/register/invitation") {
                val body = call.receive<InvitationRegistrationRequest>()
                val tokens = auth.registerFromInvitation(body.email, body.password, body.displayName, body.token)
                call.respond(HttpStatusCode.Created, TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
            }
            post("/api/v1/auth/login") {
                val body = call.receive<LoginRequest>()
                val tokens = auth.login(body.email, body.password)
                call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
            }
            post("/api/v1/auth/refresh") {
                val body = call.receive<RefreshRequest>()
                val tokens = auth.refresh(body.refreshToken)
                call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
            }
            post("/api/v1/auth/logout") {
                val body = call.receive<RefreshRequest>()
                call.respond(mapOf("revoked" to auth.revokeRefreshToken(body.refreshToken)))
            }

            authenticate("auth") {
                get("/api/v1/me") {
                    val p = call.principal<ArgentPrincipal>()!!
                    val user = auth.getUser(p.userId) ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                    call.respond(MeResponse(user.id, user.email, user.displayName, user.role.name, p.tenantId, p.tenantRole.name))
                }
                get("/api/v1/tenants") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(auth.memberships(p.userId).map { TenantMembershipDto(it.tenantId, it.tenantName, it.role.name) })
                }
                get("/api/v1/tenants/members") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                        return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant administrator role required"))
                    }
                    call.respond(auth.tenantMembers(p.tenantId, p.userId).map {
                        TenantMemberDto(it.userId, it.email, it.displayName, it.role.name)
                    })
                }
                patch("/api/v1/tenants/members/{userId}/role") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                        return@patch call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant administrator role required"))
                    }
                    val targetUserId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                    val body = call.receive<TenantRoleUpdateRequest>()
                    val newRole = runCatching { TenantRole.valueOf(body.role.trim().uppercase()) }.getOrElse {
                        throw IllegalArgumentException("Role must be ADMIN, TRADER, or VIEWER")
                    }
                    val targetAccess = auth.tenantAccess(targetUserId, p.tenantId)
                        ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Tenant member not found"))
                    trading.withAccountLock(targetAccess.accountId) {
                        auth.updateTenantRole(p.tenantId, p.userId, targetUserId, newRole)
                    }
                    call.respond(TenantRoleUpdateResponse(targetUserId, newRole.name, true))
                }
                delete("/api/v1/tenants/members/{userId}") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN)) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant administrator role required"))
                    }
                    val targetUserId = call.parameters["userId"] ?: throw IllegalArgumentException("userId is required")
                    val target = auth.tenantAccess(targetUserId, p.tenantId)
                        ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Tenant member not found"))
                    val revoked = trading.withAccountLock(target.accountId) {
                        orders.findActiveByUser(target.accountId).forEach { order ->
                            trading.cancel(target.accountId, order.id, order.symbol)
                        }
                        if (orders.findActiveByUser(target.accountId).isNotEmpty()) false
                        else {
                            auth.deactivateMember(p.tenantId, p.userId, targetUserId)
                            true
                        }
                    }
                    if (!revoked) return@delete call.respond(HttpStatusCode.Conflict, ErrorResponse("Active orders could not all be cancelled; membership remains active"))
                    call.respond(TenantMemberRevokedResponse(targetUserId, true, true))
                }
                delete("/api/v1/tenants/current") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole != TenantRole.OWNER) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Workspace owner role required"))
                    }
                    val closed = trading.withAccountLock(p.accountId) {
                        orders.findActiveByUser(p.accountId).forEach { order ->
                            trading.cancel(p.accountId, order.id, order.symbol)
                        }
                        if (orders.findActiveByUser(p.accountId).isNotEmpty()) false
                        else {
                            auth.deactivateWorkspace(p.tenantId, p.userId)
                            true
                        }
                    }
                    if (!closed) return@delete call.respond(HttpStatusCode.Conflict, ErrorResponse("Active orders could not all be cancelled; workspace remains open"))
                    call.respond(TenantWorkspaceClosedResponse(p.tenantId, true))
                }
                post("/api/v1/tenants/switch") {
                    val p = call.principal<ArgentPrincipal>()!!
                    val body = call.receive<TenantSwitchRequest>()
                    val tokens = auth.switchTenant(p.userId, body.tenantId)
                    call.respond(TokenResponse(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds))
                }
                post("/api/v1/tenants/invitations") {
                    val p = call.principal<ArgentPrincipal>()!!
                    val body = call.receive<TenantInviteRequest>()
                    val role = runCatching { TenantRole.valueOf(body.role.trim().uppercase()) }.getOrElse {
                        throw IllegalArgumentException("Tenant role must be TRADER or VIEWER")
                    }
                    val invitation = auth.createInvitation(p.tenantId, p.userId, body.email, role)
                    call.respond(
                        HttpStatusCode.Created,
                        TenantInvitationDto(invitation.tenantId, invitation.email, invitation.role.name, invitation.token, invitation.expiresAt)
                    )
                }
                post("/api/v1/tenants/invitations/accept") {
                    val p = call.principal<ArgentPrincipal>()!!
                    val body = call.receive<TenantInviteAcceptRequest>()
                    val member = auth.acceptInvitation(p.userId, body.token)
                    call.respond(TenantMembershipDto(member.tenantId, member.tenantName, member.role.name))
                }
                get("/api/v1/balances") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(ledger.listBalances(p.accountId))
                }
                get("/api/v1/positions") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(ledger.listPositions(p.accountId))
                }
                get("/api/v1/portfolio") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(risk.portfolio(p.accountId, market.allLastPrices()))
                }
                get("/api/v1/orders") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(orders.findByUser(p.accountId))
                }
                get("/api/v1/fills") {
                    val p = call.principal<ArgentPrincipal>()!!
                    call.respond(
                        orders.findTrades(userId = p.accountId).map {
                            TradeInfo(it.id, it.symbol, it.price, it.quantity, it.buyOrderId, it.sellOrderId, it.makerOrderId, it.takerOrderId, it.executedAt)
                        }
                    )
                }
                post("/api/v1/orders") {
                    if (!engine.isHealthy()) {
                        return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Matching is paused after an internal failure; restart the gateway to replay durable events"))
                    }
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                        return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Trading permission required"))
                    }
                    val req = call.receive<PlaceOrderRequest>()
                    val headerKey = call.request.header("Idempotency-Key")
                    require(headerKey.isNullOrBlank() || req.clientOrderId.isNullOrBlank() || headerKey == req.clientOrderId) {
                        "Idempotency-Key must match clientOrderId when both are provided"
                    }
                    val effective = if (req.clientOrderId.isNullOrBlank() && !headerKey.isNullOrBlank()) {
                        req.copy(clientOrderId = headerKey)
                    } else req
                    require(!effective.clientOrderId.isNullOrBlank() && effective.clientOrderId.length <= 128) {
                        "Provide an Idempotency-Key or clientOrderId between 1 and 128 characters"
                    }
                    val result = trading.withAccountLock(p.accountId) {
                        val current = auth.tenantAccess(p.userId, p.tenantId)
                        if (current == null || current.accountId != p.accountId || current.role !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) null
                        else trading.placeOrder(p.accountId, effective)
                    } ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant trading permission is no longer active"))
                    call.respond(result)
                }
                delete("/api/v1/orders/{orderId}") {
                    if (!engine.isHealthy()) {
                        return@delete call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Matching is paused after an internal failure; restart the gateway to replay durable events"))
                    }
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                        return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Trading permission required"))
                    }
                    val orderId = call.parameters["orderId"]!!
                    val symbol = call.request.queryParameters["symbol"]?.uppercase()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query param required"))
                    val cancelled = trading.withAccountLock(p.accountId) {
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
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("External withdrawal settlement is not configured; no withdrawal was processed"))
                }
                post("/api/v1/admin/deposit") {
                    call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("External deposit verification is not configured; no funds were credited"))
                }
                get("/api/v1/admin/ledger/reconciliation") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    val report = ledger.reconciliationReport()
                    call.respond(
                        LedgerReconciliationResponse(
                            ok = report.ok,
                            checkedAccountAssets = report.checkedAccountAssets,
                            mismatches = report.mismatches.map {
                                LedgerReconciliationMismatchDto(it.account, it.asset, it.journalNet, it.balanceTotal)
                            }
                        )
                    )
                }
                get("/api/v1/admin/binance-spot-testnet") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    call.respond(BinanceTestnetStatusResponse(configured = binanceTestnet.configured, usesArgentLedger = binanceTestnet.configured))
                }
                post("/api/v1/admin/binance-spot-testnet/orders") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    if (!binanceTestnet.configured) return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Configure Binance Spot Testnet credentials locally first"))
                    val body = call.receive<BinanceTestnetOrderRequest>()
                    try {
                        val result = binanceTestnetExecution.place(p.accountId, TestnetOrderRequest(
                            symbol = body.symbol,
                            side = body.side,
                            type = body.type,
                            quantity = body.quantity,
                            price = body.price,
                            timeInForce = body.timeInForce,
                            clientOrderId = body.clientOrderId
                        ))
                        call.respond(result)
                    } catch (failure: BinanceTestnetException) {
                        val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                        call.respond(status, ErrorResponse(failure.message ?: "Binance Spot Testnet request failed"))
                    }
                }
                get("/api/v1/admin/binance-spot-testnet/orders/{orderId}") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    if (!binanceTestnet.configured) return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Configure Binance Spot Testnet credentials locally first"))
                    val symbol = call.request.queryParameters["symbol"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query parameter is required"))
                    val orderId = call.parameters["orderId"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("orderId must be numeric"))
                    try { call.respond(binanceTestnetExecution.get(p.accountId, symbol, orderId)) }
                    catch (failure: BinanceTestnetException) {
                        val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                        call.respond(status, ErrorResponse(failure.message ?: "Binance Spot Testnet request failed"))
                    }
                }
                delete("/api/v1/admin/binance-spot-testnet/orders/{orderId}") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    if (!binanceTestnet.configured) return@delete call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Configure Binance Spot Testnet credentials locally first"))
                    val symbol = call.request.queryParameters["symbol"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("symbol query parameter is required"))
                    val orderId = call.parameters["orderId"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("orderId must be numeric"))
                    try { call.respond(binanceTestnetExecution.cancel(p.accountId, symbol, orderId)) }
                    catch (failure: BinanceTestnetException) {
                        val status = if (failure.upstreamStatus in 400..499) HttpStatusCode.BadRequest else HttpStatusCode.BadGateway
                        call.respond(status, ErrorResponse(failure.message ?: "Binance Spot Testnet request failed"))
                    }
                }
                post("/api/v1/admin/market-data/bist/replay") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    val request = call.receive<BistHistoricalReplayRequest>()
                    try {
                        call.respond(HttpStatusCode.Accepted, market.startBistHistoricalReplay(request.csv, request.speed))
                    } catch (failure: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(failure.message ?: "Invalid BIST CSV"))
                    } catch (failure: IllegalStateException) {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse(failure.message ?: "BIST replay unavailable"))
                    }
                }
                get("/api/v1/admin/market-data/bist/replay") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    call.respond(market.bistHistoricalReplayStatus() ?: mapOf("state" to "IDLE"))
                }
                get("/api/v1/admin/market-maker") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    val mm = liquidityProvider
                        ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                    call.respond(MarketMakerStatusResponse(
                        enabled = mm.isEnabled,
                        mode = "INTERNAL",
                        quoteNotionalCap = mm.quoteNotionalLimit,
                        inventoryNotionalCap = mm.inventoryNotionalLimit,
                        staleQuoteTtlMs = mm.quoteAgeLimitMs
                    ))
                }
                post("/api/v1/admin/market-maker/kill-switch") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    val mm = liquidityProvider
                        ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                    val cancelled = mm.killSwitch()
                    call.respond(MarketMakerActionResponse(false, cancelled))
                }
                post("/api/v1/admin/market-maker/resume") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                    val mm = liquidityProvider
                        ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Market maker is not configured"))
                    mm.resume()
                    call.respond(MarketMakerActionResponse(true))
                }
                post("/api/v1/admin/replay/{symbol}") {
                    val p = call.principal<ArgentPrincipal>()!!
                    if (p.role != UserRole.ADMIN.name) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
                        return@post
                    }
                    val symbol = call.parameters["symbol"]!!.uppercase()
                    val events = eventStore.load(symbol)
                    val scratch = MatchingEngine(
                        keepRecordedEvents = false,
                        rollbackOnMutationFailure = false
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
                                bestAsk = depth.bestAsk
                            )
                        )
                    } finally {
                        scratch.shutdown()
                    }
                }
            }

            webSocket("/ws/ticker") {
                val job = launch {
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
                val symbol = call.parameters["symbol"]!!.uppercase()
                while (true) {
                    val depth = engine.depth(symbol)
                    send(Frame.Text(json.encodeToString(OrderBookDepth.serializer(), depth)))
                    delay(400)
                }
            }
            webSocket("/ws/portfolio") {
                val authHeader = call.request.header(HttpHeaders.Authorization)
                val token = authHeader
                    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.substringAfter(' ')
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Bearer token required"))
                val decoded = runCatching { JWT.require(Algorithm.HMAC256(config.jwtSecret)).build().verify(token) }.getOrNull()
                    ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                val userId = decoded.getClaim("userId").asString() ?: decoded.subject
                if (auth.getUser(userId)?.active != true) {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "account unavailable"))
                }
                val tenantId = decoded.getClaim("tenantId").asString()
                val access = tenantId?.let { auth.tenantAccess(userId, it) }
                val accountId = decoded.getClaim("accountId").asString()
                if (access == null || accountId != access.accountId) {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "tenant unavailable"))
                }
                while (true) {
                    val snap = risk.portfolio(accountId!!, market.allLastPrices())
                    send(Frame.Text(json.encodeToString(com.argent.common.domain.PortfolioSnapshot.serializer(), snap)))
                    delay(1_000)
                }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down")
        engine.shutdown()
        market.stop()
        broker.close()
        limiter.close()
        ds.close()
    })
    server.start(wait = true)
}

private fun bootstrap(config: AppConfig, auth: AuthService, ledger: LedgerService) {
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
