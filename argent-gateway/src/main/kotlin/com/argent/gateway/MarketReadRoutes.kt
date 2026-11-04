package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.engine.MatchingEngine
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.api.InstrumentDto
import com.argent.gateway.api.TickerResponse
import com.argent.gateway.api.TradeInfo
import com.argent.gateway.market.CandleStore
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.order.OrderRepository
import com.argent.market.MarketDataService
import com.argent.market.TechnicalAnalysis
import com.argent.market.TickerUpdate
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal fun Route.marketReadRoutes(
    market: MarketDataService,
    engine: MatchingEngine,
    orders: OrderRepository,
    externalMarketData: ExternalMarketDataService,
    candleStore: CandleStore,
    json: Json,
) {
    get("/api/v1/instruments") {
        call.respond(
            Instruments.all.map {
                InstrumentDto(it.symbol, it.baseAsset, it.quoteAsset, it.assetClass.name, it.displayName, it.tickSize, it.lotSize, it.active, it.expiryDate)
            },
        )
    }
    get("/api/v1/external-market/status") { call.respond(externalMarketData.status()) }
    get("/api/v1/ticker/{symbol}") {
        val requested = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol is required")
        val symbol =
            MarketReadValidation.symbol(requested)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Bilinmeyen sembol: $requested"))
        val live = market.lastTicker(symbol)
        val price =
            live?.price ?: market.lastPrice(symbol)?.toPlainString()
                ?: return@get call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("$symbol için gerçek piyasa verisi mevcut değil"))
        call.respond(
            TickerResponse(
                symbol,
                price,
                live?.bid,
                live?.ask,
                live?.volume24h,
                live?.source,
                live?.timestamp ?: 0L,
            ),
        )
    }
    get("/api/v1/ticker/{symbol}/history") {
        val requested = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol is required")
        val symbol =
            MarketReadValidation.symbol(requested)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Bilinmeyen sembol: $requested"))
        val persisted =
            candleStore.history(
                symbol,
                com.argent.market.CandleInterval.ONE_MINUTE.code,
                com.argent.common.domain.MarketHistoryLimits.PRICE_POINTS,
            )
        val chartHistory =
            if (persisted.isNotEmpty()) {
                persisted.map { candle ->
                    TickerUpdate(
                        symbol = candle.symbol,
                        price = candle.close,
                        volume24h = candle.volume,
                        source = "stored-candle:${candle.interval}",
                        timestamp = candle.closeTime,
                    )
                }
            } else {
                market.history(symbol)
            }
        call.respond(chartHistory)
    }
    get("/api/v1/candles/{symbol}/{interval}") {
        val requested = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol is required")
        val symbol =
            MarketReadValidation.symbol(requested)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Bilinmeyen sembol: $requested"))
        val interval = call.parameters["interval"] ?: throw IllegalArgumentException("Interval is required")
        if (com.argent.market.CandleInterval.fromCode(interval) == null) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Desteklenmeyen mum aralığı: $interval"),
            )
        }
        val limit =
            MarketReadValidation.boundedLimit(
                call.request.queryParameters["limit"],
                default = 500,
                maximum = 2_000,
            )
        call.respond(candleStore.history(symbol, interval, limit))
    }
    get("/api/v1/technical-analysis/{symbol}") {
        val requested = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol is required")
        val symbol =
            MarketReadValidation.symbol(requested)
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Bilinmeyen sembol: $requested"))
        val persisted = candleStore.history(symbol, com.argent.market.CandleInterval.ONE_MINUTE.code, 2_000)
        val observations =
            if (persisted.isNotEmpty()) {
                persisted.map { candle ->
                    TickerUpdate(
                        symbol = candle.symbol,
                        price = candle.close,
                        volume24h = candle.volume,
                        source = "stored-candle:${candle.interval}",
                        timestamp = candle.closeTime,
                    )
                }
            } else {
                market.history(symbol)
            }
        call.respond(TechnicalAnalysis.calculate(symbol, observations))
    }
    get("/api/v1/orderbook/{symbol}") {
        val requested = call.parameters["symbol"] ?: throw IllegalArgumentException("Symbol is required")
        val symbol =
            MarketReadValidation.symbol(requested)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Bilinmeyen sembol: $requested"))
        val levels =
            MarketReadValidation.boundedLimit(
                call.request.queryParameters["levels"],
                default = 10,
                maximum = 50,
            )
        call.respond(engine.depth(symbol, levels))
    }
    get("/api/v1/trades") {
        val requested = call.request.queryParameters["symbol"]
        val symbol =
            if (requested == null) {
                null
            } else {
                MarketReadValidation.symbol(requested)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Bilinmeyen sembol: $requested"))
            }
        val limit =
            MarketReadValidation.boundedLimit(
                call.request.queryParameters["limit"],
                default = 100,
                maximum = 500,
            )
        call.respond(
            orders.findTrades(symbol, limit = limit).map {
                TradeInfo(it.id, it.symbol, it.price, it.quantity, it.buyOrderId, it.sellOrderId, it.makerOrderId, it.takerOrderId, it.executedAt)
            },
        )
    }

    get("/api/v1/stream/ticker") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.respondTextWriter(contentType = ContentType.Text.EventStream) {
            val job =
                CoroutineScope(currentCoroutineContext()).launch {
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
