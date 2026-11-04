package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.market.CreatePriceAlertRequest
import com.argent.gateway.market.toResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

internal fun io.ktor.server.routing.Route.registerPriceAlertRoutes(ctx: GatewayRouteContext) {
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/price-alerts") {
                val p = call.requireArgentPrincipal()
                call.respond(priceAlertStore.list(p.userId).map { it.toResponse() })
            }
            post("/api/v1/price-alerts") {
                val p = call.requireArgentPrincipal()
                val request = call.receive<CreatePriceAlertRequest>()
                val symbol = request.symbol.trim().uppercase()
                val direction = request.direction.trim().uppercase()
                require(
                    externalMarketData.status().configured,
                ) { "Gerçek fiyat alarmı için FINNHUB_API_KEY yapılandırılmalı" }
                require(
                    Instruments.all.any {
                        it.symbol == symbol && it.assetClass == AssetClass.US_EQUITY && it.active
                    },
                ) { "Alarm yalnızca desteklenen Nasdaq sembollerinde kullanılabilir" }
                require(direction in setOf("ABOVE", "BELOW")) { "direction ABOVE veya BELOW olmalı" }
                val threshold =
                    request.threshold.trim().replace(',', '.').toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("Eşik pozitif bir sayı olmalı")
                require(threshold.signum() > 0 && threshold.precision() <= 18 && threshold.scale() <= 8) {
                    "Eşik pozitif olmalı ve en fazla 8 ondalık basamak içermeli"
                }
                require(
                    priceAlertStore.list(p.userId).count {
                        it.state == "ACTIVE"
                    } < 20,
                ) { "En fazla 20 etkin fiyat alarmı oluşturabilirsiniz" }
                val currentQuote =
                    runCatching { externalMarketData.quote(symbol) }.getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadGateway,
                            ErrorResponse(it.message ?: "Gerçek fiyat doğrulanamadı; alarm kurulmadı"),
                        )
                    }
                val created =
                    priceAlertStore.create(
                        p.userId,
                        symbol,
                        direction,
                        threshold.stripTrailingZeros().toPlainString(),
                    )
                priceAlertStore.trigger(symbol, currentQuote.price, currentQuote.timestamp)
                call.respond(
                    HttpStatusCode.Created,
                    priceAlertStore.list(p.userId).first { it.id == created.id }.toResponse(),
                )
            }
            delete("/api/v1/price-alerts/{id}") {
                val p = call.requireArgentPrincipal()
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Alarm kimliği gerekli"))
                if (!priceAlertStore.delete(
                        p.userId,
                        id,
                    )
                ) {
                    return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("Alarm bulunamadı"))
                }
                call.respond(mapOf("deleted" to true))
            }
        }
    }
}
