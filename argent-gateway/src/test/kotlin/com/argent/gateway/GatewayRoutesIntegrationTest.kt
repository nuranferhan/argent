package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.common.domain.Instruments
import com.argent.core.ArgentMatchingCore
import com.argent.gateway.assistant.AssistantOrderDraftService
import com.argent.gateway.assistant.AssistantPortfolioHistoryStore
import com.argent.gateway.assistant.ReadOnlyAssistantService
import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.GoogleIdentityService
import com.argent.gateway.config.AppConfig
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.market.CandleStore
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.market.PriceAlertStore
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.trading.BinanceTestnetExecutionService
import com.argent.gateway.trading.TradingService
import com.argent.market.BinanceSpotTestnet
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.market.MarketFeedMode
import com.argent.risk.RiskService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.Database
import java.math.BigDecimal
import java.util.UUID

class GatewayRoutesIntegrationTest : FunSpec({
    test("login, place and cancel order, then read balances and portfolio over HTTP") {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        Instruments.installCatalog(
            listOf(Instrument("THYAO", "THYAO", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Turkish Airlines")),
        )
        val secret = "integration-test-secret-$suffix-0123456789abcdef"
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:gateway_$suffix;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                    maximumPoolSize = 4
                    isAutoCommit = false
                },
            )
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """CREATE TABLE instrument_catalog (
                        symbol VARCHAR(64) PRIMARY KEY, base_asset VARCHAR(64) NOT NULL,
                        quote_asset VARCHAR(16) NOT NULL, asset_class VARCHAR(24) NOT NULL,
                        qty_scale INTEGER NOT NULL, price_scale INTEGER NOT NULL,
                        tick_size VARCHAR(64) NOT NULL, lot_size VARCHAR(64) NOT NULL,
                        display_name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL,
                        expiry_date DATE NULL, reference_price VARCHAR(64) NULL
                    )""",
                )
                statement.execute(
                    """INSERT INTO instrument_catalog
                        (symbol,base_asset,quote_asset,asset_class,qty_scale,price_scale,tick_size,lot_size,display_name,active,expiry_date,reference_price)
                        VALUES ('THYAO','THYAO','TRY','EQUITY',0,2,'0.01','1','Turkish Airlines',TRUE,NULL,'310')""",
                )
            }
            connection.commit()
        }
        val instrumentCatalog = InstrumentCatalogStore(dataSource).also { it.loadIntoCache() }
        val database = Database.connect(dataSource)
        val core = ArgentMatchingCore(dataSource)
        val ledger = core.ledger
        val auth = AuthService(database, secret).also { it.initSchema() }
        val registration = auth.registerPublic("route-$suffix@example.test", "Test-password-123!", "Route Tester")
        ledger.deposit(registration.user.id, "TRY", BigDecimal("10000"), "integration-test-funding")
        val orders = OrderRepository(database).also { it.initSchema() }
        val market =
            MarketDataService(feedMode = MarketFeedMode.SIMULATED).also {
                it.seedLastPrice("THYAO", BigDecimal("310"))
            }
        val engine = core.matchingEngine
        val risk = RiskService(ledger)
        val trading = TradingService(engine, ledger, risk, orders, market, SimpleMeterRegistry())
        val config =
            AppConfig(
                port = 0,
                bindHost = "127.0.0.1",
                corsHosts = emptyList(),
                env = "test",
                jdbcUrl = "jdbc:h2:mem:gateway_$suffix;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                dbUser = "sa",
                dbPass = "",
                jwtSecret = secret,
                redisUrl = "redis://127.0.0.1:1",
                rabbitUrl = null,
                bootstrapAdminEmail = null,
                bootstrapAdminPassword = null,
                seedMarketMaker = false,
                seedDemoFunds = false,
                marketFeedMode = MarketFeedMode.SIMULATED,
                istfinApiKey = null,
                marketMakerPassword = "unused-test-password",
                binanceSpotTestnetApiKey = null,
                binanceSpotTestnetSecret = null,
                publicSignupEnabled = false,
            )
        val broker = EventBroker(null)
        val limiter = RedisRateLimiter(config.redisUrl)
        val externalMarketData = ExternalMarketDataService(null)
        val priceAlerts = PriceAlertStore(database).also { it.initSchema() }
        val portfolioHistory = AssistantPortfolioHistoryStore(database).also { it.initSchema() }
        val eventStore = PostgresEventStore(database).also { it.initSchema() }
        val candleStore = CandleStore(database).also { it.initSchema() }
        val binance = BinanceSpotTestnet(null, null)
        val binanceExecution = BinanceTestnetExecutionService(database, ledger, binance).also { it.initSchema() }
        val context =
            GatewayRouteContext(
                config = config,
                dataSource = dataSource,
                instrumentCatalog = instrumentCatalog,
                broker = broker,
                limiter = limiter,
                auth = auth,
                externalMarketData = externalMarketData,
                priceAlertStore = priceAlerts,
                trading = trading,
                portfolioSnapshot = { accountId, prices -> risk.portfolio(accountId, prices) },
                market = market,
                assistant = ReadOnlyAssistantService(null, "https://unused.invalid", "unused"),
                assistantPortfolioHistory = portfolioHistory,
                assistantOrderDrafts = AssistantOrderDraftService(),
                orders = orders,
                eventStore = eventStore,
                core = core,
                engine = engine,
                ledger = ledger,
                risk = risk,
                binanceTestnet = binance,
                binanceTestnetExecution = binanceExecution,
                liquidityProvider = null,
                json = Json { ignoreUnknownKeys = true },
            )

        try {
            testApplication {
                application {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                    install(StatusPages) {
                        exception<IllegalArgumentException> { call, failure ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (failure.message ?: "Bad request")),
                            )
                        }
                    }
                    install(Authentication) {
                        jwt("auth") {
                            verifier(
                                com.auth0.jwt.JWT.require(com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)).build(),
                            )
                            validate { credential ->
                                val userId =
                                    credential.payload.getClaim("userId").asString() ?: credential.payload.subject
                                val user = userId?.let(auth::getUser)?.takeIf { it.active }
                                val tenantId = credential.payload.getClaim("tenantId").asString() ?: user?.tenantId
                                val access =
                                    if (user != null && tenantId != null) {
                                        auth.tenantAccess(
                                            user.id,
                                            tenantId,
                                        )
                                    } else {
                                        null
                                    }
                                val accountId = credential.payload.getClaim("accountId").asString()
                                val tenantRole = credential.payload.getClaim("tenantRole").asString()
                                if (
                                    user != null &&
                                    access != null &&
                                    accountId == access.accountId &&
                                    tenantRole == access.role.name
                                ) {
                                    ArgentPrincipal(user.id, user.role.name, tenantId!!, access.role, access.accountId)
                                } else {
                                    null
                                }
                            }
                        }
                    }
                    routing {
                        publicAuthRoutes(
                            config,
                            auth,
                            GoogleIdentityService(emptySet()),
                            seedPublicPaperBalance = {},
                            seedBalanceForTrader = {},
                        )
                        registerAccountRoutes(context)
                        registerTradingAdminRoutes(context)
                    }
                }
                val publicRegistration =
                    client.post("/api/v1/auth/register") {
                        headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
                        setBody(
                            """
                            {"email":"closed-$suffix@example.test","password":"Test-password-123!","displayName":"Closed Signup"}
                            """.trimIndent(),
                        )
                    }
                publicRegistration.status shouldBe HttpStatusCode.Forbidden

                val login =
                    client.post("/api/v1/auth/login") {
                        headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
                        setBody("""{"email":"route-$suffix@example.test","password":"Test-password-123!"}""")
                    }
                login.status shouldBe HttpStatusCode.OK
                val accessToken =
                    Json.parseToJsonElement(
                        login.bodyAsText(),
                    ).jsonObject["accessToken"]!!.jsonPrimitive.content
                val unauthenticatedBalances = client.get("/api/v1/balances")
                unauthenticatedBalances.status shouldBe HttpStatusCode.Unauthorized

                val placed =
                    client.post("/api/v1/orders") {
                        headers[HttpHeaders.Authorization] = "Bearer $accessToken"
                        headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
                        setBody(
                            """
                            {"symbol":"THYAO","side":"BUY","type":"LIMIT","price":"300","quantity":"2","clientOrderId":"integration-$suffix"}
                            """.trimIndent(),
                        )
                    }
                placed.status shouldBe HttpStatusCode.OK
                val order = Json.parseToJsonElement(placed.bodyAsText()).jsonObject
                val orderId = order["orderId"]!!.jsonPrimitive.content

                val balance =
                    client.get(
                        "/api/v1/balances",
                    ) { headers[HttpHeaders.Authorization] = "Bearer $accessToken" }
                balance.status shouldBe HttpStatusCode.OK
                balance.bodyAsText().contains("\"asset\":\"TRY\"") shouldBe true

                val portfolio =
                    client.get(
                        "/api/v1/portfolio",
                    ) { headers[HttpHeaders.Authorization] = "Bearer $accessToken" }
                portfolio.status shouldBe HttpStatusCode.OK
                portfolio.bodyAsText().contains(registration.user.id) shouldBe true

                val cancelled =
                    client.delete("/api/v1/orders/$orderId?symbol=THYAO") {
                        headers[HttpHeaders.Authorization] = "Bearer $accessToken"
                    }
                cancelled.status shouldBe HttpStatusCode.OK

                val openOrders =
                    client.get(
                        "/api/v1/orders",
                    ) { headers[HttpHeaders.Authorization] = "Bearer $accessToken" }
                openOrders.status shouldBe HttpStatusCode.OK
                openOrders.bodyAsText().contains(orderId) shouldBe true
            }
        } finally {
            engine.shutdown()
            market.stop()
            limiter.close()
            binance.close()
            dataSource.close()
        }
    }
})
