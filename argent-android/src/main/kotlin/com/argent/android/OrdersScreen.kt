package com.argent.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.OrderDto
import com.argent.client.core.TradeDto
import com.argent.client.core.cancel
import kotlinx.coroutines.launch

@Composable
internal fun OrdersPage(
    orders: List<OrderDto>,
    fills: List<TradeDto>,
    api: ArgentApi,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val openOrders =
        orders.filter {
            it.status in setOf("PENDING", "ACCEPTED", "PARTIALLY_FILLED") &&
                (it.remainingQuantity.toBigDecimalOrNull()?.signum() ?: 0) > 0
        }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("AÇIK EMİRLER", tab == 0) { tab = 0 }
            Choice("İŞLEMLER", tab == 1) { tab = 1 }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                item { Text("AÇIK EMİRLER · ${openOrders.size} bekleyen", color = Gold, fontWeight = FontWeight.Bold) }
                if (openOrders.isEmpty()) {
                    item {
                        Text(
                            "Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemler geçmişindedir.",
                            color = Muted,
                        )
                    }
                }
                items(openOrders, key = { it.id }) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${order.symbol} ${order.side} ${order.type}", fontWeight = FontWeight.Bold)
                                Text(
                                    if (LocalUiLanguage.current == "en") {
                                        "${orderStatusLabel(
                                            order.status,
                                            LocalUiLanguage.current,
                                        )} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "market"}"
                                    } else {
                                        "${orderStatusLabel(
                                            order.status,
                                            LocalUiLanguage.current,
                                        )} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "piyasa"}"
                                    },
                                    color = Muted,
                                    fontSize = 11.sp,
                                )
                            }
                            if (order.status in setOf("ACCEPTED", "PARTIALLY_FILLED")) {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            api.cancel(order.id, order.symbol)
                                            onMessage("${order.symbol} emri iptal edildi")
                                        }.onFailure { onMessage("İptal hatası: ${it.message}") }
                                    }
                                }) { Text("İPTAL", color = Red) }
                            }
                        }
                    }
                }
            }
            if (tab == 1) {
                val sortedOrders =
                    orders.sortedByDescending {
                        it.updatedAt.takeIf {
                                updated ->
                            updated > 0
                        } ?: it.createdAt
                    }
                item { Text("EMİR GEÇMİŞİ", color = Gold, fontWeight = FontWeight.Bold) }
                if (sortedOrders.isEmpty()) item { Text("Emir geçmişi boş.", color = Muted) }
                items(sortedOrders, key = { it.id }) { order ->
                    DataRow(
                        "${order.symbol} ${order.side} ${order.type} · ${orderStatusLabel(
                            order.status,
                            LocalUiLanguage.current,
                        )}",
                        if (LocalUiLanguage.current == "en") {
                            "Filled ${order.filledQuantity}/${order.quantity} · " +
                                "remaining ${order.remainingQuantity} · " +
                                (order.price ?: "market")
                        } else {
                            "Dolan ${order.filledQuantity}/${order.quantity} · " +
                                "kalan ${order.remainingQuantity} · " +
                                (order.price ?: "piyasa")
                        },
                    )
                }
                item { Text("GERÇEKLEŞEN İŞLEMLER", color = Gold, fontWeight = FontWeight.Bold) }
                if (fills.isEmpty()) item { Text("İşlem geçmişi boş.", color = Muted) }
                items(fills) { trade -> DataRow(trade.symbol, "${trade.quantity} @ ${trade.price}") }
            }
        }
    }
}

internal fun orderStatusLabel(
    status: String,
    language: String = "tr",
): String =
    when (status.uppercase()) {
        "ACCEPTED", "NEW", "PENDING" -> if (language == "en") "OPEN" else "AÇIK"
        "PARTIALLY_FILLED" -> if (language == "en") "PARTIALLY FILLED" else "KISMEN DOLDU"
        "FILLED" -> if (language == "en") "FILLED" else "DOLDU"
        "CANCELLED", "CANCELED" -> if (language == "en") "CANCELLED" else "İPTAL"
        "REJECTED" -> if (language == "en") "REJECTED" else "REDDEDİLDİ"
        "EXPIRED" -> if (language == "en") "EXPIRED" else "SÜRESİ DOLDU"
        else -> status
    }
