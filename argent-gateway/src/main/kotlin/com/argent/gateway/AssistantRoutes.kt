package com.argent.gateway

import com.argent.common.domain.Instruments
import com.argent.gateway.api.AssistantAlertsResponse
import com.argent.gateway.api.AssistantOrderDraftRequest
import com.argent.gateway.api.AssistantQuestionRequest
import com.argent.gateway.api.AssistantStatusResponse
import com.argent.gateway.api.ConfirmAssistantOrderRequest
import com.argent.gateway.api.ErrorResponse
import com.argent.gateway.assistant.AssistantAlertEvaluator
import com.argent.gateway.assistant.AssistantDraftOrder
import com.argent.gateway.auth.TenantRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import java.math.BigDecimal

internal fun io.ktor.server.routing.Route.registerAssistantRoutes(ctx: GatewayRouteContext) {
    val log = LoggerFactory.getLogger("AssistantRoutes")
    with(ctx) {
        authenticate("auth") {
            get("/api/v1/assistant/status") {
                call.respond(
                    AssistantStatusResponse(
                        assistant.enabled,
                        assistant.statusProvider(),
                        if (assistant.enabled) assistant.model else null,
                    ),
                )
            }
            get("/api/v1/assistant/alerts") {
                val p = call.requireArgentPrincipal()
                val requestedSymbol =
                    call.request.queryParameters["symbol"]?.trim()?.uppercase()?.takeIf(
                        String::isNotBlank,
                    )
                if (requestedSymbol != null && Instruments.all.none { it.symbol == requestedSymbol }) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Bilinmeyen sembol: $requestedSymbol"),
                    )
                }
                val now = System.currentTimeMillis()
                val prices = market.allLastPrices()
                val snapshot = portfolioSnapshot(p.accountId, prices)
                val baseline = assistantPortfolioHistory.recordAndGetDailyBaseline(p.accountId, snapshot, now)
                val ticker = requestedSymbol?.let(market::lastTicker)
                val bistMode = market.bistFeedStatus()
                val marketMode = "CRYPTO ${market.cryptoFeedStatus()} · BIST $bistMode"
                val sources =
                    buildList {
                        add("ARGENT portföy ve sanal defter")
                        add(marketMode)
                        ticker?.let { add("${it.source} piyasa verisi: ${it.symbol}") }
                    }
                call.respond(
                    AssistantAlertsResponse(
                        alerts = AssistantAlertEvaluator.currentAlerts(snapshot, ticker, marketMode, now, baseline),
                        generatedAt = now,
                        dataAsOf = ticker?.timestamp ?: now,
                        sources = sources,
                        marketMode = marketMode,
                    ),
                )
            }
            post("/api/v1/assistant/chat") {
                val p = call.requireArgentPrincipal()
                if (!assistant.enabled) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("ARGENT AI yapılandırılmamış; gateway ortamına ARGENT_AI_API_KEY ekleyin."),
                    )
                }
                val request = call.receive<AssistantQuestionRequest>()
                val question = request.question.trim()
                if (question.isBlank() || question.length > 1200) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Soru boş olamaz ve 1200 karakteri aşamaz."),
                    )
                }
                val symbol = request.symbol?.trim()?.uppercase()?.takeIf(String::isNotBlank)
                if (symbol != null && Instruments.all.none { it.symbol == symbol }) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Bilinmeyen sembol: $symbol"))
                }
                val ticker = symbol?.let(market::lastTicker)
                val draftOrder =
                    runCatching {
                        val side = request.draftSide?.trim()?.uppercase() ?: return@runCatching null
                        val type = request.draftType?.trim()?.uppercase() ?: return@runCatching null
                        val quantity = request.draftQuantity?.trim()?.replace(',', '.') ?: return@runCatching null
                        require(side in setOf("BUY", "SELL") && type in setOf("LIMIT", "MARKET"))
                        require(BigDecimal(quantity).signum() > 0)
                        val price = request.draftPrice?.trim()?.replace(',', '.')?.takeIf(String::isNotBlank)
                        if (type == "LIMIT") require(price != null && BigDecimal(price).signum() > 0)
                        AssistantDraftOrder(side, type, quantity, price)
                    }.getOrNull()
                val bistMode = market.bistFeedStatus()
                val marketMode = "CRYPTO ${market.cryptoFeedStatus()} · BIST $bistMode"
                val answer =
                    runCatching {
                        val portfolioSnapshot = portfolioSnapshot(p.accountId, market.allLastPrices())
                        val baseline =
                            assistantPortfolioHistory.recordAndGetDailyBaseline(
                                p.accountId,
                                portfolioSnapshot,
                                System.currentTimeMillis(),
                            )
                        assistant.answer(
                            question = question,
                            symbol = symbol,
                            portfolio = portfolioSnapshot,
                            orders = orders.findByUser(p.accountId, 500),
                            fills = orders.findTrades(userId = p.accountId, limit = 500),
                            ticker = ticker,
                            recentPrices = symbol?.let(market::history).orEmpty(),
                            depth = symbol?.let { engine.depth(it, 5) },
                            marketMode = marketMode,
                            dailyBaseline = baseline,
                            draftOrder = draftOrder,
                            responseLanguage = request.language,
                        )
                    }.getOrElse {
                        log.warn("Read-only ARGENT AI request failed: {}", it.message)
                        return@post call.respond(
                            HttpStatusCode.BadGateway,
                            ErrorResponse(it.message ?: "ARGENT AI yanıt veremedi."),
                        )
                    }
                call.respond(answer)
            }
            post("/api/v1/assistant/order-drafts") {
                val p = call.requireArgentPrincipal()
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Trading permission required"))
                }
                val body = call.receive<AssistantOrderDraftRequest>()
                val draft = assistantOrderDrafts.create(p.accountId, body.prompt, market)
                call.respond(HttpStatusCode.Created, draft)
            }
            post("/api/v1/assistant/order-drafts/{draftId}/confirm") {
                val p = call.requireArgentPrincipal()
                if (!engine.isHealthy()) {
                    return@post call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("Eşleştirme motoru sağlıklı değil; emir gönderilmedi."),
                    )
                }
                if (p.tenantRole !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                    return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Trading permission required"))
                }
                val body = call.receive<ConfirmAssistantOrderRequest>()
                if (!body.acknowledgeSummary) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Emir özeti açıkça onaylanmalı."),
                    )
                }
                if (!auth.verifyPassword(
                        p.userId,
                        body.password,
                    )
                ) {
                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("Parola doğrulanamadı; emir gönderilmedi."),
                    )
                }
                val draftId = call.parameters["draftId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Taslak kimliği gerekli."))
                val result =
                    trading.withAccountLock(p.accountId) {
                        val current = auth.tenantAccess(p.userId, p.tenantId)
                        if (current == null || current.accountId != p.accountId || current.role !in setOf(TenantRole.OWNER, TenantRole.ADMIN, TenantRole.TRADER)) {
                            null
                        } else {
                            trading.placeOrder(p.accountId, assistantOrderDrafts.confirm(p.accountId, draftId))
                        }
                    } ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("Tenant trading permission is no longer active"))
                call.respond(result)
            }
        }
    }
}
