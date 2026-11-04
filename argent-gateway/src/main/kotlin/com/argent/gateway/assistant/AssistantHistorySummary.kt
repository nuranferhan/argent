package com.argent.gateway.assistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal object AssistantHistorySummary {
    fun summarize(
        search: JsonObject,
        english: Boolean,
    ): String {
        val days = search["requestedDays"]?.toString()?.toIntOrNull()?.takeIf { it > 0 }
        val status = search["statusFilter"]?.toString()?.trim('"')?.takeIf { it != "ALL" }
        val symbol = search["symbolFilter"]?.toString()?.trim('"')?.takeIf { it != "ALL" }
        val matches = search["matches"] as? JsonArray ?: JsonArray(emptyList())
        val period =
            when {
                days != null && english -> "in the last $days days"
                days != null -> "son $days gün içinde"
                english -> "in the available order history"
                else -> "mevcut emir geçmişinde"
            }
        val statusText =
            when (status) {
                "CANCELLED" -> if (english) "cancelled" else "iptal edilen"
                "REJECTED" -> if (english) "rejected" else "reddedilen"
                "FILLED" -> if (english) "filled" else "tamamlanan"
                "PARTIALLY_FILLED" -> if (english) "partially filled" else "kısmen gerçekleşen"
                "ACCEPTED,PARTIALLY_FILLED,PENDING" -> if (english) "open" else "açık"
                else -> if (english) "matching" else "eşleşen"
            }
        val symbolText = symbol?.let { " $it" }.orEmpty()
        if (matches.isEmpty()) {
            return if (english) "There are no$symbolText $statusText orders $period." else "$period$symbolText için $statusText emir bulunmuyor."
        }
        val items =
            matches.take(5).mapNotNull { item ->
                val row = item as? JsonObject ?: return@mapNotNull null

                fun field(name: String) = row[name]?.toString()?.trim('"').orEmpty()
                val side =
                    field("side").let {
                        if (english) {
                            it
                        } else if (it == "BUY") {
                            "alış"
                        } else {
                            "satış"
                        }
                    }
                val price = field("price").takeUnless { it == "MARKET" }?.let { " @ $it" }.orEmpty()
                "${field("symbol")} $side ${field("type")} ${field("quantity")}$price (${field("createdAt")})"
            }
        val intro = if (english) "I found ${matches.size}$symbolText $statusText order${if (matches.size == 1) "" else "s"} $period:" else "$period$symbolText için ${matches.size} $statusText emir buldum:"
        val suffix =
            if (matches.size > items.size) {
                if (english) {
                    "; showing the 5 most recent."
                } else {
                    "; en yeni 5 kayıt gösteriliyor."
                }
            } else {
                ""
            }
        return "$intro ${items.joinToString("; ")}$suffix"
    }
}
