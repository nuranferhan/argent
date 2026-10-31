package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.engine.MatchingEngine
import com.argent.engine.OrderBookDepth
import com.argent.gateway.api.InstrumentDto
import com.argent.gateway.api.TickerResponse
import com.argent.gateway.api.TradeInfo
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.order.OrderRepository
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.market.TechnicalAnalysis
import com.argent.market.TickerUpdate
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal fun Route.marketReadRoutes(
    market: MarketDataService,
    engine: MatchingEngine,
    orders: OrderRepository,
    externalMarketData: ExternalMarketDataService,
    json: Json
) {
            get("/api/v1/instruments") {
                call.respond(
                    Instruments.all.map {
                        InstrumentDto(it.symbol, it.baseAsset, it.quoteAsset, it.assetClass.name, it.displayName, it.tickSize, it.lotSize)
                    }
                )
            }
            get("/api/v1/external-market/status") { call.respond(externalMarketData.status()) }
            get("/api/v1/ticker/{symbol}") {
                val symbol = call.parameters["symbol"]?.uppercase() ?: throw IllegalArgumentException("Symbol is required")
                if (Instruments.get(symbol) == null) return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Bilinmeyen sembol: $symbol"))
                val live = market.lastTicker(symbol)
                val price = live?.price ?: market.lastPrice(symbol)?.toPlainString()
                    ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("$symbol için gerçek piyasa verisi mevcut değil"))
                call.respond(TickerResponse(symbol, price, live?.bid, live?.ask, live?.volume24h, live?.source, live?.timestamp ?: 0L))
            }
            get("/api/v1/ticker/{symbol}/history") {
                call.respond(market.history(call.parameters["symbol"]?.uppercase() ?: throw IllegalArgumentException("Symbol is required")))
            }
            get("/api/v1/technical-analysis/{symbol}") {
                val symbol = call.parameters["symbol"]?.uppercase() ?: throw IllegalArgumentException("Symbol is required")
                if (Instruments.get(symbol) == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Bilinmeyen sembol: $symbol"))
                }
                call.respond(TechnicalAnalysis.calculate(symbol, market.history(symbol)))
            }
            get("/api/v1/orderbook/{symbol}") {
                call.respond(engine.depth(call.parameters["symbol"]?.uppercase() ?: throw IllegalArgumentException("Symbol is required")))
            }
            get("/api/v1/trades") {
                val symbol = call.request.queryParameters["symbol"]?.uppercase()
                call.respond(
                    orders.findTrades(symbol).map {
                        TradeInfo(it.id, it.symbol, it.price, it.quantity, it.buyOrderId, it.sellOrderId, it.makerOrderId, it.takerOrderId, it.executedAt)
                    }
                )
            }

            get("/api/v1/stream/ticker") {
                call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    val job = CoroutineScope(currentCoroutineContext()).launch {
                        market.tickers.collect { t ->
                            write("data: ${json.encodeToString(TickerUpdate.serializer(), t)}\n\n")
                            flush()
                        }
                    }
                    try {
                        while (true) delay(60_000)
                    } finally {
                        job.cancel()
                    }
                }
            }

}
