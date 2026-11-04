package com.argent.client.core

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun ArgentApi.tickerFlow(): Flow<TickerDto> =
    flow {
        val url = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        while (true) {
            try {
                client.webSocket("$url/ws/ticker") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            emit(json.decodeFromString(TickerDto.serializer(), frame.readText()))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            delay(1_000)
        }
    }

fun ArgentApi.orderBookFlow(symbol: String): Flow<OrderBookDto> =
    flow {
        val url = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        while (true) {
            try {
                client.webSocket("$url/ws/orderbook/$symbol", request = {
                    token?.let { header("Authorization", "Bearer $it") }
                }) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            emit(json.decodeFromString(OrderBookDto.serializer(), frame.readText()))
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
            delay(1_000)
        }
    }

fun ArgentApi.orderUpdatesFlow(): Flow<OrderUpdateDto> =
    flow {
        val url = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
        while (true) {
            try {
                client.webSocket("$url/ws/orders", request = {
                    token?.let { header("Authorization", "Bearer $it") }
                }) {
                    for (frame in incoming) if (frame is Frame.Text) {
                        emit(json.decodeFromString(OrderUpdateDto.serializer(), frame.readText()))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Reconnect after transient gateway/network failures.
            }
            delay(1_000)
        }
    }
