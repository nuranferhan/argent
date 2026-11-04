package com.argent.gateway.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {
    @Test
    fun `local development defaults signup off and creates a non-static jwt secret`() {
        val first = AppConfig.load(emptyMap())
        val second = AppConfig.load(emptyMap())

        assertTrue(first.isDev)
        assertFalse(first.publicSignupEnabled)
        assertTrue(first.jwtSecret.length >= 32)
        assertTrue(first.seedDemoFunds)
        assertTrue(first.seedMarketMaker)
        assertTrue(first.jwtSecret != second.jwtSecret)
    }

    @Test
    fun `development password and virtual funding switches cannot be enabled outside dev`() {
        val production = secureProductionEnvironment()
        assertFailsWith<IllegalArgumentException> {
            AppConfig.load(production + ("SEED_DEMO_FUNDS" to "true"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.load(production + ("SEED_MARKET_MAKER" to "true"))
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.load(production + ("JWT_SECRET" to "argent-dev-secret-change-me-in-prod-32chars"))
        }
    }

    @Test
    fun `production requires explicit strong secrets and leaves signup disabled by default`() {
        val config = AppConfig.load(secureProductionEnvironment())

        assertFalse(config.isDev)
        assertFalse(config.publicSignupEnabled)
        assertFalse(config.seedDemoFunds)
        assertFalse(config.seedMarketMaker)
        assertEquals("prod", config.env)
    }

    private fun secureProductionEnvironment() =
        mapOf(
            "ARGENT_ENV" to "prod",
            "JWT_SECRET" to "jwt-secret-for-production-longer-than-32-characters",
            "DB_PASS" to "production-database-password-long-enough",
            "REDIS_URL" to "redis://:production-redis-password-long-enough@localhost:6379",
            "RABBITMQ_URL" to "amqp://argent:production-rabbit-password-long-enough@localhost:5672",
            "GRAFANA_ADMIN_PASSWORD" to "production-grafana-password-long-enough",
            "ARGENT_CORS_HOSTS" to "argent.example.com",
            "OBSERVABILITY_TOKEN" to "production-observability-token-long-enough",
            "SEED_MARKET_MAKER" to "false",
            "SEED_DEMO_FUNDS" to "false",
        )
}
