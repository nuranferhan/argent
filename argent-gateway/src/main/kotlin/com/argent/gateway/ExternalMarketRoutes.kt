package com.argent.gateway

import com.argent.gateway.api.ErrorResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

internal fun io.ktor.server.routing.Route.registerExternalMarketRoutes(ctx: GatewayRouteContext) {
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/external-market/nasdaq/{symbol}") {
                val symbol = call.parameters["symbol"] ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Sembol gerekli"))
                val quote =
                    runCatching { externalMarketData.quote(symbol) }.getOrElse {
                        val status = if (it.message?.contains("yapılandırılmamış") == true) HttpStatusCode.ServiceUnavailable else HttpStatusCode.BadGateway
                        return@get call.respond(status, ErrorResponse(it.message ?: "Nasdaq verisi alınamadı"))
                    }
                call.respond(quote)
            }
            get("/api/v1/external-market/news") {
                val category = call.request.queryParameters["category"] ?: "general"
                val news =
                    runCatching { externalMarketData.news(category) }.getOrElse {
                        val status = if (it.message?.contains("yapılandırılmamış") == true) HttpStatusCode.ServiceUnavailable else HttpStatusCode.BadGateway
                        return@get call.respond(status, ErrorResponse(it.message ?: "Haber akışı alınamadı"))
                    }
                call.respond(news)
            }
            get("/api/v1/external-market/news-image") {
                val imageUrl =
                    call.request.queryParameters["url"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Haber görseli gerekli"))
                val image =
                    runCatching { externalMarketData.image(imageUrl) }.getOrNull()
                        ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Haber görseli alınamadı"))
                call.respondBytes(image.bytes, io.ktor.http.ContentType.parse(image.contentType))
            }
        }
    }
}
