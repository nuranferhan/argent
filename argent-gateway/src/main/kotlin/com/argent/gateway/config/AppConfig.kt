package com.argent.gateway.config

import com.argent.market.MarketFeedMode

data class AppConfig(
    val port: Int,
    val bindHost: String,
    val corsHosts: List<String>,
    val env: String,
    val jdbcUrl: String,
    val dbUser: String,
    val dbPass: String,
    val jwtSecret: String,
    val redisUrl: String,
    val rabbitUrl: String?,
    val bootstrapAdminEmail: String?,
    val bootstrapAdminPassword: String?,
    val seedMarketMaker: Boolean,
    val seedDemoFunds: Boolean,
    val marketFeedMode: MarketFeedMode,
    val istfinApiKey: String?,
    val marketMakerPassword: String,
    val binanceSpotTestnetApiKey: String?,
    val binanceSpotTestnetSecret: String?
) {
    val isDev: Boolean get() = env == "dev"

    companion object {
        fun load(): AppConfig {
            val env = (System.getenv("ARGENT_ENV") ?: "dev").trim().lowercase()
            require(env in setOf("dev", "test", "staging", "prod", "production")) {
                "ARGENT_ENV must be one of: dev, test, staging, prod, production"
            }
            val jwt = System.getenv("JWT_SECRET")
                ?: if (env == "dev") "argent-dev-secret-change-me-in-prod-32chars"
                else error("JWT_SECRET environment variable is required (min 32 chars)")
            require(jwt.length >= 32 && jwt.none(Char::isWhitespace)) { "JWT_SECRET must be at least 32 non-whitespace characters" }
            val dbPass = System.getenv("DB_PASS") ?: "argent"
            val redisUrl = System.getenv("REDIS_URL") ?: "redis://127.0.0.1:6379"
            val rabbitUrl = System.getenv("RABBITMQ_URL")
            val configuredCorsHosts = System.getenv("ARGENT_CORS_HOSTS")?.takeIf(String::isNotBlank)
            val corsSource = configuredCorsHosts
                ?: if (env == "dev") "localhost:3000,127.0.0.1:3000,localhost:5173,127.0.0.1:5173" else ""
            val corsHosts = corsSource
                .split(',').map(String::trim).filter(String::isNotEmpty)
            val bootstrapEmail = System.getenv("BOOTSTRAP_ADMIN_EMAIL")?.takeIf(String::isNotBlank)
            val bootstrapPassword = System.getenv("BOOTSTRAP_ADMIN_PASSWORD")?.takeIf(String::isNotBlank)
            if (env != "dev") {
                require(jwt != "argent-dev-secret-change-me-in-prod-32chars") { "Production JWT_SECRET must not use the development value" }
                require(dbPass.length >= 24 && dbPass != "argent") { "Production DB_PASS must be a unique secret of at least 24 characters" }
                require(urlPassword(redisUrl).length >= 24 && urlPassword(redisUrl) != "argent") {
                    "Production REDIS_URL must include a unique authentication password of at least 24 characters"
                }
                require(urlPassword(rabbitUrl).length >= 24 && urlPassword(rabbitUrl) != "argent") {
                    "Production RABBITMQ_URL must include a unique authentication password of at least 24 characters"
                }
                val grafanaPassword = System.getenv("GRAFANA_ADMIN_PASSWORD").orEmpty()
                require(grafanaPassword.length >= 24 && grafanaPassword != "argent") {
                    "Production GRAFANA_ADMIN_PASSWORD must be a unique secret of at least 24 characters"
                }
                require(configuredCorsHosts != null && corsHosts.isNotEmpty()) { "Production ARGENT_CORS_HOSTS must be set explicitly" }
                require((bootstrapEmail == null) == (bootstrapPassword == null)) {
                    "Set both BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD, or neither"
                }
                require(bootstrapPassword == null || bootstrapPassword.length >= 12) {
                    "Production bootstrap administrator password must be at least 12 characters"
                }
            }
            val simulationDefaults = env == "dev"
            val seedMarketMaker = (System.getenv("SEED_MARKET_MAKER") ?: simulationDefaults.toString()).toBooleanStrictOrNull()
                ?: error("SEED_MARKET_MAKER must be true or false")
            val seedDemoFunds = (System.getenv("SEED_DEMO_FUNDS") ?: simulationDefaults.toString()).toBooleanStrictOrNull()
                ?: error("SEED_DEMO_FUNDS must be true or false")
            val marketFeedMode = runCatching {
                MarketFeedMode.valueOf((System.getenv("MARKET_DATA_MODE") ?: "BINANCE_PUBLIC").trim().uppercase())
            }.getOrElse {
                error("MARKET_DATA_MODE must be SIMULATED, BINANCE_PUBLIC, or ISTFIN_BIST")
            }
            val istfinApiKey = System.getenv("ISTFIN_API_KEY")?.takeIf(String::isNotBlank)
            require(env == "dev" || marketFeedMode != MarketFeedMode.ISTFIN_BIST || istfinApiKey != null) {
                "ISTFIN_API_KEY is required when MARKET_DATA_MODE=ISTFIN_BIST"
            }
            val binanceTestnetKey = System.getenv("BINANCE_SPOT_TESTNET_API_KEY")?.takeIf(String::isNotBlank)
            val binanceTestnetSecret = System.getenv("BINANCE_SPOT_TESTNET_SECRET")?.takeIf(String::isNotBlank)
            require((binanceTestnetKey == null) == (binanceTestnetSecret == null)) {
                "Set both BINANCE_SPOT_TESTNET_API_KEY and BINANCE_SPOT_TESTNET_SECRET, or neither"
            }
            require(env == "dev" || binanceTestnetKey == null) {
                "Binance Spot Testnet credentials are allowed only in local development"
            }
            require(env == "dev" || (!seedMarketMaker && !seedDemoFunds)) {
                "Market maker seeding and demo funds are allowed only when ARGENT_ENV=dev"
            }
            require(!seedMarketMaker || seedDemoFunds) {
                "SEED_MARKET_MAKER requires SEED_DEMO_FUNDS=true in development"
            }
            return AppConfig(
                port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
                bindHost = System.getenv("ARGENT_BIND_HOST") ?: "127.0.0.1",
                corsHosts = corsHosts,
                env = env,
                jdbcUrl = System.getenv("JDBC_URL") ?: "jdbc:postgresql://localhost:5432/argent",
                dbUser = System.getenv("DB_USER") ?: "argent",
                dbPass = dbPass,
                jwtSecret = jwt,
                redisUrl = redisUrl,
                rabbitUrl = rabbitUrl,
                bootstrapAdminEmail = bootstrapEmail,
                bootstrapAdminPassword = bootstrapPassword,
                seedMarketMaker = seedMarketMaker,
                seedDemoFunds = seedDemoFunds,
                marketFeedMode = marketFeedMode,
                istfinApiKey = istfinApiKey,
                marketMakerPassword = System.getenv("MARKET_MAKER_PASSWORD")
                    ?.takeIf(String::isNotBlank)
                    ?: java.util.UUID.randomUUID().toString() + "aA1!",
                binanceSpotTestnetApiKey = binanceTestnetKey,
                binanceSpotTestnetSecret = binanceTestnetSecret
            )
        }

        private fun urlPassword(url: String?): String = runCatching {
            java.net.URI(url).userInfo?.substringAfter(':', "").orEmpty()
        }.getOrDefault("")
    }
}
