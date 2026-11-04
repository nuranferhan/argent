package com.argent.client.core

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

suspend fun ArgentApi.instruments(): List<InstrumentDto> = get("/api/v1/instruments")

suspend fun ArgentApi.orderBook(symbol: String): OrderBookDto = get("/api/v1/orderbook/$symbol")

suspend fun ArgentApi.ticker(symbol: String): TickerDto = get("/api/v1/ticker/$symbol")

suspend fun ArgentApi.history(symbol: String): List<TickerDto> = get("/api/v1/ticker/$symbol/history")

suspend fun ArgentApi.technicalAnalysis(symbol: String): TechnicalAnalysisDto =
    get(
        "/api/v1/technical-analysis/$symbol",
    )

suspend fun ArgentApi.externalMarketStatus(): ExternalMarketStatusDto = get("/api/v1/external-market/status")

suspend fun ArgentApi.nasdaqQuote(symbol: String): ExternalQuoteDto = get("/api/v1/external-market/nasdaq/$symbol")

suspend fun ArgentApi.marketNews(): List<MarketNewsDto> = get("/api/v1/external-market/news?category=general")

suspend fun ArgentApi.marketNewsImage(url: String): ByteArray? {
    if (!url.startsWith("https://") || url.length > 2048) return null
    synchronized(newsImageCache) { newsImageCache[url]?.let { return it } }
    return runCatching {
        val encoded = java.net.URLEncoder.encode(url, Charsets.UTF_8)
        val response =
            client.get("$baseUrl/api/v1/external-market/news-image?url=$encoded") {
                token?.let { header("Authorization", "Bearer $it") }
            }
        if (!response.status.isSuccess()) return@runCatching null
        val bytes: ByteArray = response.body()
        bytes.takeIf { it.size in 1..2_000_000 }?.also {
                image ->
            synchronized(newsImageCache) { newsImageCache[url] = image }
        }
    }.getOrNull()
}

suspend fun ArgentApi.priceAlerts(): List<PriceAlertDto> = get("/api/v1/price-alerts")

suspend fun ArgentApi.createPriceAlert(
    symbol: String,
    direction: String,
    threshold: String,
): PriceAlertDto = post("/api/v1/price-alerts", CreatePriceAlertDto(symbol, direction, threshold))

suspend fun ArgentApi.deletePriceAlert(id: String) {
    var response =
        client.delete("$baseUrl/api/v1/price-alerts/$id") {
            token?.let { header("Authorization", "Bearer $it") }
        }
    if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
        response =
            client.delete("$baseUrl/api/v1/price-alerts/$id") {
                token?.let { header("Authorization", "Bearer $it") }
            }
    }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
}
