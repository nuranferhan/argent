package com.argent.gateway.assistant

import com.argent.common.domain.Instruments
import com.argent.common.domain.Order
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.Trade
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object AssistantHistorySearch {
    fun formatTimestamp(value: Long): String {
        if (value <= 0L) return "unknown"
        return runCatching {
            Instant.ofEpochMilli(
                value,
            ).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        }.getOrDefault("unknown")
    }

    fun buildHistorySearch(
        question: String,
        orders: List<Order>,
        fills: List<Trade>,
        now: Long,
        english: Boolean,
    ): JsonObject? {
        val q = question.lowercase()
        val historyIntent =
            listOf("emir geçmiş", "işlem geçmiş", "son ", "iptal edilen", "reddedilen", "dolan emir", "emirlerimi", "order history", "trade history", "last ", "cancelled", "canceled", "rejected", "my orders").any(
                q::contains,
            )
        if (!historyIntent) return null
        val days =
            (Regex("son\\s+(\\d{1,3})\\s+gün(?:\\p{L}*)?(?:\\s+içinde\\p{L}*)?").find(q)?.groupValues?.get(1) ?: Regex("(?:last|past)\\s+(\\d{1,3})\\s+days?").find(q)?.groupValues?.get(1))?.toLongOrNull()?.coerceIn(
                1,
                365,
            )
        val status =
            when {
                "iptal" in q -> setOf(OrderStatus.CANCELLED)
                "cancelled" in q || "canceled" in q -> setOf(OrderStatus.CANCELLED)
                "redded" in q -> setOf(OrderStatus.REJECTED)
                "rejected" in q -> setOf(OrderStatus.REJECTED)
                "bekleyen" in q || "açık emir" in q || "pending" in q || "open order" in q ->
                    setOf(
                        OrderStatus.ACCEPTED,
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.PENDING,
                    )
                "kısmen" in q || "kısmi" in q || "partially filled" in q || "partial fill" in q ->
                    setOf(
                        OrderStatus.PARTIALLY_FILLED,
                    )
                "dolmuş" in q || "dolan" in q || "tamamlanan" in q || "gerçekleşen" in q || "filled" in q || "completed" in q ->
                    setOf(
                        OrderStatus.FILLED,
                    )
                else -> null
            }
        val symbol = Instruments.all.firstOrNull { it.symbol.lowercase() in q }?.symbol
        val cutoff = days?.let { now - it * 24 * 60 * 60 * 1000L }
        val matches =
            orders.filter { order ->
                (cutoff == null || order.createdAt >= cutoff) &&
                    (status == null || order.status in status) &&
                    (symbol == null || order.symbol == symbol)
            }.sortedByDescending { it.createdAt }
        return buildJsonObject {
            put("requestedDays", days ?: -1)
            put("statusFilter", status?.joinToString(",") { it.name } ?: "ALL")
            put("symbolFilter", symbol ?: "ALL")
            put("scannedOrderCount", orders.size)
            put(
                "coverage",
                if (english) "At most the latest 500 stored orders were searched; this may not represent complete archival history." else "En fazla son 500 kayıtlı emir tarandı; arşiv geçmişinin tamamı olmayabilir.",
            )
            putJsonArray("matches") {
                matches.take(20).forEach { order ->
                    add(
                        buildJsonObject {
                            put("symbol", order.symbol)
                            put("side", order.side.name)
                            put("type", order.type.name)
                            put("status", order.status.name)
                            put("price", order.price ?: "MARKET")
                            put("quantity", order.quantity)
                            put("filled", order.filledQuantity)
                            put("remaining", order.remainingQuantity)
                            put("createdAt", formatTimestamp(order.createdAt))
                        },
                    )
                }
            }
            if ("işlem" in q || "trade" in q || "gerçekleş" in q || "fill" in q) {
                putJsonArray("tradeMatches") {
                    fills.filter { (cutoff == null || it.executedAt >= cutoff) && (symbol == null || it.symbol == symbol) }
                        .sortedByDescending { it.executedAt }
                        .take(20)
                        .forEach { trade ->
                            add(
                                buildJsonObject {
                                    put("symbol", trade.symbol)
                                    put("price", trade.price)
                                    put("quantity", trade.quantity)
                                    put("executedAt", formatTimestamp(trade.executedAt))
                                },
                            )
                        }
                }
            }
        }
    }
}
