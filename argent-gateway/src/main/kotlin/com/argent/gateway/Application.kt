package com.argent.gateway

import com.argent.common.events.DomainEvent
import com.argent.core.ArgentMatchingCore
import com.argent.engine.MatchingEngine
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.assistant.AssistantOrderDraftService
import com.argent.gateway.assistant.AssistantPortfolioHistoryStore
import com.argent.gateway.assistant.ReadOnlyAssistantService
import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.GoogleIdentityService
import com.argent.gateway.auth.TenantRole
import com.argent.gateway.config.AppConfig
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.market.CandleStore
import com.argent.gateway.market.DailyFxRatesService
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.market.PriceAlertStore
import com.argent.gateway.mm.LiquidityProvider
import com.argent.gateway.mm.MarketMakerControlStore
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.security.ClientAddressResolver
import com.argent.gateway.trading.BinanceTestnetExecutionService
import com.argent.gateway.trading.TradingService
import com.argent.market.BinanceSpotTestnet
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
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
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.close
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("ArgentGateway")
private val json =
    Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private class MissingAuthenticatedPrincipal : RuntimeException()

internal fun ApplicationCall.requireArgentPrincipal(): ArgentPrincipal =
    principal<ArgentPrincipal>() ?: throw MissingAuthenticatedPrincipal()

@Serializable
internal data class WsOrderUpdate(val event: String, val orderId: String, val symbol: String, val status: String)

data class ArgentPrincipal(val userId: String, val role: String, val tenantId: String, val tenantRole: TenantRole, val accountId: String) : Principal

fun main() {
    val config = AppConfig.load()
    val ds =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                username = config.dbUser
                password = config.dbPass
                maximumPoolSize = 16
                isAutoCommit = false
            },
        )
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load()
        .migrate()
    val instrumentCatalog = InstrumentCatalogStore(ds).also { it.loadIntoCache() }
    val database = Database.connect(ds)
    val broker = EventBroker(config.rabbitUrl).also { it.start() }
    val eventStore = PostgresEventStore(database).also { it.initSchema() }
    val eventBus: (com.argent.common.events.DomainEvent) -> Unit = { e ->
        eventStore.append(e)
        broker.publish(e)
    }

    val core = ArgentMatchingCore(ds, eventBus)
    val ledger = core.ledger
    val auth = AuthService(database, config.jwtSecret).also { it.initSchema() }
    val googleIdentity = GoogleIdentityService(config.googleOAuthClientIds)
    val orders = OrderRepository(database).also { it.initSchema() }
    val engine = core.matchingEngine
    // The production candle table is provisioned by Flyway (V2__market_candles.sql).
    // Keep initSchema available for isolated H2 tests, but do not mutate production schema at startup.
    val candleStore = CandleStore(database).also { it.prune() }
    val market =
        MarketDataService(
            feedMode = config.marketFeedMode,
            istfinApiKey = config.istfinApiKey,
            candleSink = candleStore::persistClosed,
        ).also { it.start() }
    val binanceTestnet = BinanceSpotTestnet(config.binanceSpotTestnetApiKey, config.binanceSpotTestnetSecret)
    val risk = RiskService(ledger)
    val dailyFxRates = DailyFxRatesService()

    suspend fun portfolioSnapshot(
        accountId: String,
        prices: Map<String, java.math.BigDecimal>,
    ) = dailyFxRates.snapshot().let { fx -> risk.portfolio(accountId, prices, fx.usdPerCurrency, fx.date) }
    val assistant = ReadOnlyAssistantService.fromEnvironment()
    val assistantOrderDrafts = AssistantOrderDraftService()
    val externalMarketData = ExternalMarketDataService(System.getenv("FINNHUB_API_KEY")?.takeIf(String::isNotBlank))
    val priceAlertStore = PriceAlertStore(database).also { it.initSchema() }
    val assistantPortfolioHistory = AssistantPortfolioHistoryStore(database).also { it.initSchema() }
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val trading = TradingService(engine, ledger, risk, orders, market, prometheus)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    startInstrumentMaintenance(appScope, instrumentCatalog, trading, ledger)
    val limiter = RedisRateLimiter(config.redisUrl)
    val clientAddressResolver = ClientAddressResolver(config.trustedProxyCidrs)
    startCandleRetention(appScope, candleStore)
    if (externalMarketData.status().configured) {
        appScope.launch {
            while (true) {
                priceAlertStore.activeSymbols().forEach { symbol ->
                    runCatching {
                        val quote = externalMarketData.quote(symbol)
                        priceAlertStore.trigger(symbol, quote.price, quote.timestamp)
                    }.onFailure { log.debug("Price alert polling for {} deferred: {}", symbol, it.message) }
                }
                delay(60_000)
            }
        }
    }
    val binanceTestnetExecution =
        BinanceTestnetExecutionService(
            database,
            ledger,
            binanceTestnet,
        ).also { it.initSchema() }
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
    recoverGatewayState(core, eventStore, orders, ledger, trading, engine)

    var liquidityProvider: LiquidityProvider? = null
    if (config.seedMarketMaker) {
        val mm = auth.findByEmail("mm@argent.local")
        if (mm != null) {
            val controlStore = MarketMakerControlStore(database).also { it.initSchema() }
            liquidityProvider =
                LiquidityProvider(mm.id, trading, engine, market, orders, ledger, appScope, controlStore).also {
                    it.start()
                }
            log.info("Liquidity provider started id={}", mm.id)
        }
    }

    val routeContext =
        GatewayRouteContext(
            config = config,
            dataSource = ds,
            instrumentCatalog = instrumentCatalog,
            broker = broker,
            limiter = limiter,
            auth = auth,
            externalMarketData = externalMarketData,
            priceAlertStore = priceAlertStore,
            trading = trading,
            portfolioSnapshot = { accountId, prices -> portfolioSnapshot(accountId, prices) },
            market = market,
            assistant = assistant,
            assistantPortfolioHistory = assistantPortfolioHistory,
            assistantOrderDrafts = assistantOrderDrafts,
            orders = orders,
            eventStore = eventStore,
            core = core,
            engine = engine,
            ledger = ledger,
            risk = risk,
            binanceTestnet = binanceTestnet,
            binanceTestnetExecution = binanceTestnetExecution,
            liquidityProvider = liquidityProvider,
            json = json,
        )

    val server =
        embeddedServer(Netty, port = config.port, host = config.bindHost) {
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
                exception<MissingAuthenticatedPrincipal> { call, _ ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
                }
                exception<MatchingEngine.EngineOverloadedException> { call, _ ->
                    call.response.headers.append(HttpHeaders.RetryAfter, "1")
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Matching engine is busy; retry shortly"),
                    )
                }
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
                        val user = userId?.let(auth::getUser)?.takeIf { it.active }
                        val tenantId = cred.payload.getClaim("tenantId").asString() ?: user?.tenantId
                        val access =
                            if (user != null && tenantId != null) {
                                auth.tenantAccess(
                                    user.id,
                                    tenantId,
                                )
                            } else {
                                null
                            }
                        val accountId = cred.payload.getClaim("accountId").asString()
                        val tenantRole = cred.payload.getClaim("tenantRole").asString()
                        if (user != null && access != null && tenantId != null && accountId == access.accountId && tenantRole == access.role.name) {
                            ArgentPrincipal(user.id, user.role.name, tenantId, access.role, access.accountId)
                        } else {
                            null
                        }
                    }
                }
            }

            intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                val skip = path == "/" || path == "/health" || path == "/metrics"
                if (skip) return@intercept
                val remoteHost =
                    clientAddressResolver.resolve(
                        call.request.local.remoteHost,
                        call.request.header("X-Forwarded-For"),
                    )
                val (bucket, requestLimit) =
                    when (path) {
                        "/api/v1/auth/login" -> "auth:login" to 10
                        "/api/v1/auth/register" -> "auth:register" to 5
                        "/api/v1/auth/google/nonce" -> "auth:google-nonce" to 10
                        "/api/v1/auth/google" -> "auth:google" to 10
                        "/api/v1/auth/register/invitation" -> "auth:invitation" to 10
                        "/api/v1/auth/refresh" -> "auth:refresh" to 20
                        "/api/v1/assistant/chat" -> "api:assistant" to 20
                        else ->
                            if (call.request.httpMethod == HttpMethod.Get) {
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
                registerSystemRoutes(routeContext, prometheus)

                marketReadRoutes(market, engine, orders, externalMarketData, candleStore, json)
                publicAuthRoutes(
                    config = config,
                    auth = auth,
                    googleIdentity = googleIdentity,
                    seedPublicPaperBalance = { userId -> seedPublicPaperBalance(ledger, userId) },
                    seedBalanceForTrader = { accessToken -> seedBalanceForPublicTrader(ledger, accessToken) },
                )

                registerAccountRoutes(routeContext)
                registerInstrumentAdminRoutes(routeContext)
                registerTradingAdminRoutes(routeContext)

                registerMarketSockets(market, engine, broker, config.jwtSecret, auth, clientAddressResolver, limiter, orders, json)
                registerPortfolioSocket(routeContext)
            }
        }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            log.info("Shutting down")
            engine.shutdown()
            market.stop()
            broker.close()
            limiter.close()
            ds.close()
        },
    )
    server.start(wait = true)
}
