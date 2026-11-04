package com.argent.client.core

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

suspend fun ArgentApi.balances(): List<BalanceDto> = get("/api/v1/balances")

suspend fun ArgentApi.positions(): List<PositionDto> = get("/api/v1/positions")

suspend fun ArgentApi.portfolio(): PortfolioDto = get("/api/v1/portfolio")

suspend fun ArgentApi.orders(): List<OrderDto> = get("/api/v1/orders")

suspend fun ArgentApi.fills(): List<TradeDto> = get("/api/v1/fills")

suspend fun ArgentApi.assistantStatus(): AssistantStatusDto = get("/api/v1/assistant/status")

suspend fun ArgentApi.assistantAlerts(symbol: String): AssistantAlertsDto =
    get(
        "/api/v1/assistant/alerts?symbol=$symbol",
    )

suspend fun ArgentApi.askAssistant(
    question: String,
    symbol: String? = null,
    draftSide: String? = null,
    draftType: String? = null,
    draftQuantity: String? = null,
    draftPrice: String? = null,
    language: String = "tr",
): AssistantAnswerDto =
    post(
        "/api/v1/assistant/chat",
        AssistantQuestionDto(question, symbol, draftSide, draftType, draftQuantity, draftPrice, language),
    )

suspend fun ArgentApi.createAssistantOrderDraft(prompt: String): AssistantOrderDraftDto =
    post("/api/v1/assistant/order-drafts", AssistantOrderDraftRequestDto(prompt))

suspend fun ArgentApi.confirmAssistantOrderDraft(
    draftId: String,
    password: String,
): OrderResultDto =
    post("/api/v1/assistant/order-drafts/$draftId/confirm", ConfirmAssistantOrderDraftRequestDto(password, true))

suspend fun ArgentApi.publicTrades(symbol: String): List<TradeDto> = get("/api/v1/trades?symbol=$symbol")

suspend fun ArgentApi.placeOrder(
    symbol: String,
    side: String,
    type: String,
    quantity: String,
    price: String?,
    clientOrderId: String?,
): OrderResultDto {
    val response =
        client.post("$baseUrl/api/v1/orders") {
            contentType(ContentType.Application.Json)
            token?.let { header("Authorization", "Bearer $it") }
            clientOrderId?.let { header("Idempotency-Key", it) }
            setBody(PlaceOrderRequest(symbol, side, type, price, quantity, clientOrderId))
        }
    if (!response.status.isSuccess() && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
        return placeOrder(symbol, side, type, quantity, price, clientOrderId)
    }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
    return response.body()
}

suspend fun ArgentApi.cancel(
    orderId: String,
    symbol: String,
) {
    val response =
        client.delete("$baseUrl/api/v1/orders/$orderId?symbol=$symbol") {
            token?.let { header("Authorization", "Bearer $it") }
        }
    if (!response.status.isSuccess() && response.status == HttpStatusCode.Unauthorized && refreshSession()) {
        return cancel(orderId, symbol)
    }
    if (!response.status.isSuccess()) fail(response.bodyAsText())
}

suspend fun ArgentApi.adminDeposit(
    userId: String,
    asset: String,
    amount: String,
    key: String,
) {
    post<DepositRequest, BalanceDto>("/api/v1/admin/deposit", DepositRequest(userId, asset, amount, key))
}

suspend fun ArgentApi.withdraw(
    asset: String,
    amount: String,
    key: String,
): BalanceDto = post("/api/v1/withdraw", WithdrawRequest(asset, amount, key))

suspend fun ArgentApi.health(): HealthDto = get("/health")
