package com.argent.gateway

import com.argent.gateway.auth.AuthService
import com.argent.gateway.auth.UserRole
import com.argent.gateway.config.AppConfig
import com.argent.ledger.LedgerService
import com.auth0.jwt.JWT
import io.ktor.server.routing.get
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.MessageDigest

private val log = LoggerFactory.getLogger("ArgentGatewayBootstrap")

internal fun secureTokenMatches(
    expected: String?,
    authorization: String?,
): Boolean {
    if (expected.isNullOrBlank() || authorization.isNullOrBlank()) return false
    val supplied = authorization.removePrefix("Bearer ").trim().toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), supplied)
}

internal fun seedBalanceForPublicTrader(
    ledger: LedgerService,
    accessToken: String,
) {
    val claims = JWT.decode(accessToken)
    if (claims.getClaim("tenantId").asString() == AuthService.PUBLIC_TENANT_ID) {
        seedPublicPaperBalance(
            ledger,
            claims.subject,
        )
    }
}

internal fun seedPublicPaperBalance(
    ledger: LedgerService,
    userId: String,
) {
    ledger.deposit(userId, "USDT", BigDecimal("25000"), "public-signup-v1")
    ledger.deposit(userId, "TRY", BigDecimal("500000"), "public-signup-v1")
}

internal fun bootstrap(
    config: AppConfig,
    auth: AuthService,
    ledger: LedgerService,
) {
    val email = config.bootstrapAdminEmail
    val pass = config.bootstrapAdminPassword
    if (!email.isNullOrBlank() && !pass.isNullOrBlank()) {
        val admin =
            auth.findByEmail(email) ?: auth.register(email, pass, "Admin", UserRole.ADMIN).also {
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
                "ASELS" to "8000",
            ).forEach { (asset, amt) ->
                ledger.deposit(admin.id, asset, BigDecimal(amt), "seed-admin-$asset")
            }
            log.warn("Development demo funds applied to admin account id={}", admin.id)
        }
    }
    if (config.seedMarketMaker) {
        val mm =
            try {
                auth.register("mm@argent.local", config.marketMakerPassword, "Liquidity", UserRole.MARKET_MAKER)
            } catch (_: IllegalArgumentException) {
                auth.findByEmail("mm@argent.local")
            }
        if (mm != null) {
            val assets =
                listOf(
                    "TRY" to "100000000",
                    "USDT" to "5000000",
                    "BTC" to "50",
                    "ETH" to "400",
                    "SOL" to "5000",
                    "THYAO" to "50000",
                    "GARAN" to "50000",
                    "ASELS" to "50000",
                    "EREGL" to "50000",
                    "SISE" to "50000",
                )
            assets.forEach { (asset, amt) ->
                ledger.deposit(mm.id, asset, BigDecimal(amt), "seed-mm-$asset")
            }
            log.warn("Development demo funds applied to market maker id={}", mm.id)
        }
    }
}
