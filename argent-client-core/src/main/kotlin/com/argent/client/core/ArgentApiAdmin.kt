package com.argent.client.core

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

suspend fun ArgentApi.marketMakerState(): MarketMakerStateDto = get("/api/v1/admin/market-maker")

suspend fun ArgentApi.binanceSpotTestnetStatus(): BinanceTestnetStatusDto = get("/api/v1/admin/binance-spot-testnet")

suspend fun ArgentApi.binanceSpotTestnetPrice(symbol: String): BinanceTestnetTickerPriceDto =
    get("/api/v1/admin/binance-spot-testnet/price?symbol=$symbol")

suspend fun ArgentApi.placeBinanceSpotTestnetOrder(request: BinanceTestnetOrderRequestDto): BinanceTestnetOrderDto =
    post("/api/v1/admin/binance-spot-testnet/orders", request)

suspend fun ArgentApi.binanceSpotTestnetOrder(
    symbol: String,
    orderId: Long,
): BinanceTestnetOrderDto = get("/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol")

suspend fun ArgentApi.cancelBinanceSpotTestnetOrder(
    symbol: String,
    orderId: Long,
): BinanceTestnetOrderDto {
    var response =
        client.delete("$baseUrl/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol") {
            token?.let { header("Authorization", "Bearer $it") }
        }
    if (response.status == HttpStatusCode.Unauthorized && refreshSession()) {
        response =
            client.delete("$baseUrl/api/v1/admin/binance-spot-testnet/orders/$orderId?symbol=$symbol") {
                token?.let { header("Authorization", "Bearer $it") }
            }
    }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
    return response.body()
}

suspend fun ArgentApi.startBistHistoricalReplay(
    csv: String,
    speed: Int,
): BistReplayDto = post("/api/v1/admin/market-data/bist/replay", BistReplayRequestDto(csv, speed))

suspend fun ArgentApi.bistHistoricalReplayStatus(): BistReplayDto = get("/api/v1/admin/market-data/bist/replay")

suspend fun ArgentApi.killMarketMaker(): MarketMakerActionDto =
    post(
        "/api/v1/admin/market-maker/kill-switch",
        EmptyRequest(),
    )

suspend fun ArgentApi.resumeMarketMaker(): MarketMakerActionDto =
    post(
        "/api/v1/admin/market-maker/resume",
        EmptyRequest(),
    )
