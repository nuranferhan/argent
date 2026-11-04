package com.argent.gateway

import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

internal fun io.ktor.server.routing.Route.registerPortfolioRoutes(ctx: GatewayRouteContext) {
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/balances") {
                val p = call.requireArgentPrincipal()
                call.respond(ledger.listBalances(p.accountId))
            }
            get("/api/v1/positions") {
                val p = call.requireArgentPrincipal()
                call.respond(ledger.listPositions(p.accountId))
            }
            get("/api/v1/portfolio") {
                val p = call.requireArgentPrincipal()
                val snapshot = portfolioSnapshot(p.accountId, market.allLastPrices())
                runCatching {
                    assistantPortfolioHistory.recordAndGetDailyBaseline(
                        p.accountId,
                        snapshot,
                        System.currentTimeMillis(),
                    )
                }
                    .onFailure { log.warn("Could not record assistant portfolio sample", it) }
                call.respond(snapshot)
            }
        }
    }
}

private val log = org.slf4j.LoggerFactory.getLogger("ArgentPortfolioRoutes")
