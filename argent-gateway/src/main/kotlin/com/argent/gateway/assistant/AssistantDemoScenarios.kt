package com.argent.gateway.assistant

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

internal fun asksForDemo(question: String) =
    listOf(
        "demo senaryo",
        "demo akış",
        "örnek senaryo",
        "demo göster",
        "demo scenario",
        "demo flow",
        "show demo",
    ).any(question.lowercase()::contains)

internal fun demoScenarios(english: Boolean) =
    buildJsonArray {
        listOf(
            if (english) "Pending order: place a LIMIT order away from market, confirm it appears in Open Orders, then cancel it." else "Bekleyen emir: piyasa fiyatından uzak bir LIMIT emir ver; Açık Emirler'de beklediğini gör, ardından kendin iptal et.",
            if (english) "Full fill: submit a small order that crosses the best opposing quote, then review it under My Trades." else "Tam eşleşme: en iyi karşı teklifi karşılayan küçük miktarlı emir gir; dolumu İşlemlerim'de izle.",
            if (english) "Partial fill: submit an IOC order larger than available liquidity and compare the filled and cancelled quantities." else "Kısmi dolum: karşı likiditeden büyük miktarlı IOC emir gir; dolan miktarla iptal edilen kalanı karşılaştır.",
            if (english) "Cancellation: place a LIMIT order away from market, cancel it under Open Orders, and verify the result in My Trades." else "İptal: piyasa fiyatından uzak LIMIT emir aç; Açık Emirler'den iptal et ve sonucu İşlemlerim'de gör.",
        ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }
