package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.InstrumentDto
import com.argent.gateway.api.InstrumentUpsertRequest
import com.argent.gateway.auth.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put

/** Admin-managed instrument catalog: new symbols do not require a source edit or redeploy. */
internal fun Route.registerInstrumentAdminRoutes(ctx: GatewayRouteContext) {
    authenticate("auth") {
        put("/api/v1/admin/instruments/{symbol}") {
            val principal = call.requireArgentPrincipal()
            if (principal.role != UserRole.ADMIN.name) {
                return@put call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
            }
            val symbol =
                call.parameters["symbol"]
                    ?.trim()
                    ?.uppercase()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Symbol is required"))
            val request = call.receive<InstrumentUpsertRequest>()
            val assetClass =
                runCatching { AssetClass.valueOf(request.assetClass.trim().uppercase()) }
                    .getOrElse {
                        return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unsupported asset class"))
                    }
            val instrument =
                Instrument(
                    symbol = symbol,
                    baseAsset = request.baseAsset.trim().uppercase(),
                    quoteAsset = request.quoteAsset.trim().uppercase(),
                    assetClass = assetClass,
                    qtyScale = request.qtyScale,
                    priceScale = request.priceScale,
                    tickSize = request.tickSize.trim(),
                    lotSize = request.lotSize.trim(),
                    displayName = request.displayName.trim(),
                    active = request.active,
                    expiryDate = request.expiryDate?.trim()?.takeIf(String::isNotEmpty),
                    referencePrice = request.referencePrice?.trim()?.takeIf(String::isNotEmpty),
                )
            try {
                ctx.instrumentCatalog.upsert(instrument)
            } catch (failure: IllegalArgumentException) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(failure.message ?: "Invalid instrument"),
                )
            }
            call.respond(
                HttpStatusCode.OK,
                InstrumentDto(
                    instrument.symbol,
                    instrument.baseAsset,
                    instrument.quoteAsset,
                    instrument.assetClass.name,
                    instrument.displayName,
                    instrument.tickSize,
                    instrument.lotSize,
                    instrument.active,
                    instrument.expiryDate,
                    instrument.referencePrice,
                ),
            )
        }
    }
}
