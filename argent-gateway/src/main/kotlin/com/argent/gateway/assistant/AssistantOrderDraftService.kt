package com.argent.gateway.assistant

import com.argent.common.domain.Instruments
import com.argent.common.domain.AssetClass
import com.argent.gateway.api.AssistantOrderDraftResponse
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.market.MarketDataService
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Short-lived, user-bound drafts. No language model can submit or mutate one. */
class AssistantOrderDraftService {
    private data class Draft(val userId: String, val response: AssistantOrderDraftResponse, val request: PlaceOrderRequest)
    private val drafts = ConcurrentHashMap<String, Draft>()

    fun create(userId: String, prompt: String, market: MarketDataService): AssistantOrderDraftResponse {
        require(prompt.length in 5..500) { "Emir isteği 5–500 karakter olmalı." }
        val normalized = prompt.uppercase().replace(',', '.')
        val side = when {
            listOf("ALIŞ", "ALIS", "SATIN AL", " BUY", "BUY ").any(normalized::contains) -> "BUY"
            listOf("SATIŞ", "SATIS", "SAT ", " SELL", "SELL ").any(normalized::contains) -> "SELL"
            else -> throw IllegalArgumentException("Alış mı satış mı olduğunu belirtin.")
        }
        val instrument = Instruments.all.firstOrNull { i ->
            Regex("(?<![A-Z0-9])${Regex.escape(i.symbol)}(?![A-Z0-9])").containsMatchIn(normalized) ||
                (i.symbol == "BTCUSDT" && Regex("(?<![A-Z0-9])BTC(?![A-Z0-9])").containsMatchIn(normalized)) ||
                (i.symbol == "ETHUSDT" && Regex("(?<![A-Z0-9])ETH(?![A-Z0-9])").containsMatchIn(normalized)) ||
                (i.symbol == "SOLUSDT" && Regex("(?<![A-Z0-9])SOL(?![A-Z0-9])").containsMatchIn(normalized))
        } ?: throw IllegalArgumentException("Sembolü tanıyamadım. Desteklenenler: ${Instruments.all.joinToString { it.symbol }}")
        val amountMatch = Regex("(?<!\\d)(\\d+(?:\\.\\d+)?)\\s*(USDT|USD|TRY|TL)").find(normalized)
            ?: throw IllegalArgumentException("Tutarı para birimiyle yazın; ör. ‘BTC’den 100 USDT’lik limit alış’.")
        val amount = amountMatch.groupValues[1].toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            ?: throw IllegalArgumentException("Tutar geçersiz.")
        val askedCurrency = amountMatch.groupValues[2].let { if (it == "USD") "USDT" else if (it == "TL") "TRY" else it }
        require(askedCurrency == instrument.quoteAsset) { "${instrument.symbol} için tutar ${instrument.quoteAsset} olmalı." }
        require(listOf("LIMIT", "LİMİT", "LIMITLI", "LİMİTLİ").any(normalized::contains)) { "Şimdilik yalnızca limit emir taslağı hazırlanabiliyor." }
        val quote = market.lastTicker(instrument.symbol) ?: throw IllegalArgumentException("${instrument.symbol} için güncel fiyat bulunamadı; taslak oluşturulmadı.")
        val now = System.currentTimeMillis()
        require(now - quote.timestamp in 0..30_000) { "Piyasa fiyatı güncel değil; taslak oluşturulmadı." }
        if (listOf("SIMULATED", "SANDBOX", "ARGENT-SIM").any { quote.source.contains(it, ignoreCase = true) }) {
            throw IllegalArgumentException("${instrument.symbol} için yalnızca simüle fiyat mevcut; bu fiyatla AI emir taslağı hazırlamıyorum.")
        }
        val explicitPrice = Regex("(?:@|FIYAT(?:I)?\\s*)(\\d+(?:\\.\\d+)?)").find(normalized)?.groupValues?.get(1)?.toBigDecimalOrNull()
        val referencePrice = explicitPrice ?: (if (side == "BUY") quote.ask else quote.bid)?.toBigDecimalOrNull() ?: quote.price.toBigDecimal()
        require(referencePrice.signum() > 0) { "Limit fiyatı geçersiz." }
        val lot = instrument.lotSize.toBigDecimal()
        val rawQty = amount.divide(referencePrice, instrument.qtyScale + 8, RoundingMode.DOWN)
        val qty = rawQty.divide(lot, 0, RoundingMode.DOWN).multiply(lot).stripTrailingZeros()
        require(qty.signum() > 0) { "Tutar, ${instrument.symbol} için minimum lot miktarından düşük." }
        val price = referencePrice.divide(instrument.tickSize.toBigDecimal(), 0, RoundingMode.HALF_UP)
            .multiply(instrument.tickSize.toBigDecimal()).setScale(instrument.priceScale, RoundingMode.UNNECESSARY)
        val id = UUID.randomUUID().toString()
        val response = AssistantOrderDraftResponse(
            draftId = id, symbol = instrument.symbol, side = side, type = "LIMIT",
            quantity = qty.toPlainString(), price = price.toPlainString(),
            quoteNotional = qty.multiply(price).setScale(instrument.priceScale, RoundingMode.HALF_UP).toPlainString(),
            quoteAsset = instrument.quoteAsset, source = quote.source, dataAsOf = quote.timestamp, expiresAt = now + 5 * 60_000,
            note = if (explicitPrice == null) "Limit fiyatı anlık ${if (side == "BUY") "satış" else "alış"} teklifinden alındı; gönderimden önce kontrol edin." else "Limit fiyatı isteğinizden alındı; gönderimden önce kontrol edin."
        )
        drafts[id] = Draft(userId, response, PlaceOrderRequest(instrument.symbol, side, "LIMIT", price.toPlainString(), qty.toPlainString(), id))
        drafts.entries.removeIf { it.value.response.expiresAt < now }
        return response
    }

    fun confirm(userId: String, draftId: String): PlaceOrderRequest {
        val draft = drafts[draftId] ?: throw IllegalArgumentException("Taslak bulunamadı veya süresi doldu; yeniden oluşturun.")
        require(draft.userId == userId) { "Bu taslak bu hesaba ait değil." }
        require(draft.response.expiresAt >= System.currentTimeMillis()) { drafts.remove(draftId); "Taslağın süresi doldu; yeniden oluşturun." }
        // Keep the same draft key until expiry so a network retry reaches TradingService's idempotency check.
        return draft.request
    }
}
