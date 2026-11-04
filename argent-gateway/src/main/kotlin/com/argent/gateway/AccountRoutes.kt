package com.argent.gateway

import io.ktor.server.routing.Route

internal fun Route.registerAccountRoutes(ctx: GatewayRouteContext) {
    registerExternalMarketRoutes(ctx)
    registerPriceAlertRoutes(ctx)
    registerTenantRoutes(ctx)
    registerPortfolioRoutes(ctx)
    registerAssistantRoutes(ctx)
}
