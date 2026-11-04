package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.cancel


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.OrderDto
import com.argent.client.core.PortfolioDto
import com.argent.client.core.TickerDto
import com.argent.client.core.TradeDto
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.launch

@Composable
internal fun PortfolioTab(
    p: PortfolioDto?,
    tickers: Map<String, TickerDto>,
) {
    if (p == null) {
        Text("Yükleniyor…", color = ArgentMuted, modifier = Modifier.padding(12.dp))
        return
    }
    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Metric("Pozisyon yoğunlaşması", "${p.riskScore}%")
            Text(
                "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. ARGENT göstergesidir; genel risk notu değildir. Spot işlem · kaldıraç/marjin kapalı.",
                color = ArgentMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
        }
        p.usdValuation?.let { value ->
            Spacer(Modifier.height(8.dp))
            Text(
                if (LocalUiLanguage.current == "en") "USD valuation${if (value.complete) "" else " · partial"}" else "USD değerlemesi${if (value.complete) "" else " · kısmi"}",
                color = ArgentGold,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Metric("Özkaynak", "${compact(value.equity)} USD")
                Metric("Gerçekleşen PnL", "${compact(value.realizedPnl)} USD", value.realizedPnl.startsWith("-"))
                Metric("Açık PnL", "${compact(value.unrealizedPnl)} USD", value.unrealizedPnl.startsWith("-"))
                Metric("Brüt maruziyet", "${compact(value.grossExposure)} USD")
            }
        }
        p.valuations.forEach { value ->
            Spacer(Modifier.height(8.dp))
            Text("${value.quoteAsset} değerlemesi", color = ArgentGold, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Metric("Özkaynak", "${compact(value.equity)} ${value.quoteAsset}")
                Metric(
                    "Gerçekleşen PnL",
                    "${compact(value.realizedPnl)} ${value.quoteAsset}",
                    value.realizedPnl.startsWith("-"),
                )
                Metric(
                    "Açık PnL",
                    "${compact(value.unrealizedPnl)} ${value.quoteAsset}",
                    value.unrealizedPnl.startsWith("-"),
                )
                Metric("Brüt maruziyet", "${compact(value.grossExposure)} ${value.quoteAsset}")
            }
            if (!value.priceComplete) {
                Text(
                    "Güncel fiyatı olmayan varlıklar maliyet bazlı değerlenir; özkaynak kısmi tahmindir.",
                    color = ArgentRed,
                    fontSize = 11.sp,
                )
            }
            if (!value.pnlComplete) {
                Text(
                    "Başlangıç bakiyelerinin maliyet temeli bilinmediği için PnL eksik olabilir.",
                    color = ArgentRed,
                    fontSize = 11.sp,
                )
            }
        }
        if (p.valuations.isEmpty()) {
            Text(
                "Değerleme için bakiye veya pozisyon yok.",
                color = ArgentMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Bakiyeler", color = ArgentMuted, fontSize = 11.sp)
                p.balances.forEach { b ->
                    Text(
                        "${b.asset}  serbest ${compact(b.available)}  bloke ${compact(b.held)}",
                        color = ArgentSilver,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Pozisyonlar", color = ArgentMuted, fontSize = 11.sp)
                p.positions.forEach { pos ->
                    val mark = tickers[pos.symbol]?.price ?: pos.avgEntryPrice
                    Text(
                        "${pos.symbol}  qty ${compact(
                            pos.quantity,
                        )}  avg ${compact(pos.avgEntryPrice)}  mark ${compact(mark)}  rPnL ${compact(pos.realizedPnl)}",
                        color = ArgentSilver,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Para yatırma ve çekme, doğrulanmış ödeme/saklama sağlayıcısı bağlandığında etkinleştirilir.",
            color = ArgentMuted,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun OrdersTab(
    orders: List<OrderDto>,
    api: ArgentApi,
    scope: kotlinx.coroutines.CoroutineScope,
    onFlash: (String) -> Unit,
) {
    val openOrders =
        orders.filter {
            it.status in setOf("PENDING", "ACCEPTED", "PARTIALLY_FILLED") && (it.remainingQuantity.toBigDecimalOrNull()?.signum() ?: 0) > 0
        }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            "AÇIK EMİRLER · ${openOrders.size} bekleyen",
            color = ArgentGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
        if (openOrders.isEmpty()) {
            Text(
                "Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemlerim geçmişindedir.",
                color = ArgentMuted,
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                items(openOrders, key = { it.id }) { o ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${o.symbol} ${o.side} ${o.type} ${compact(
                                o.quantity,
                            )}@${o.price ?: "MKT"}  ${orderStatusLabel(
                                o.status,
                                LocalUiLanguage.current,
                            )}  filled ${compact(o.filledQuantity)}",
                            color = ArgentSilver,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        if (o.status == "ACCEPTED" || o.status == "PARTIALLY_FILLED") {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        api.cancel(o.id, o.symbol)
                                        onFlash("İptal ${o.id.take(8)}")
                                    }
                                        .onFailure { onFlash(it.message ?: "iptal hatası") }
                                }
                            }) { Text("İptal", color = ArgentRed, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TradesTab(
    trades: List<TradeDto>,
    orders: List<OrderDto> = emptyList(),
) {
    val orderHistory = orders.sortedByDescending { it.updatedAt.takeIf { updated -> updated > 0 } ?: it.createdAt }
    if (trades.isEmpty() && orderHistory.isEmpty()) {
        Text(
            "Emir veya gerçekleşen işlem geçmişi yok.",
            color = ArgentMuted,
            modifier = Modifier.padding(12.dp),
            fontSize = 12.sp,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 8.dp)) {
        item {
            Text(
                "EMİR GEÇMİŞİ · ${orderHistory.size} son emir",
                color = ArgentGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (orderHistory.isEmpty()) item { Text("Emir geçmişi boş.", color = ArgentMuted, fontSize = 12.sp) }
        items(orderHistory, key = { "order-${it.id}" }) { o ->
            Text(
                if (LocalUiLanguage.current == "en") {
                    "${o.symbol} ${o.side} ${o.type} · ${orderStatusLabel(
                        o.status,
                        LocalUiLanguage.current,
                    )} · filled ${compact(
                        o.filledQuantity,
                    )}/${compact(
                        o.quantity,
                    )} · remaining ${compact(
                        o.remainingQuantity,
                    )} · ${o.price ?: "MKT"}"
                } else {
                    "${o.symbol} ${o.side} ${o.type} · ${orderStatusLabel(
                        o.status,
                        LocalUiLanguage.current,
                    )} · dolan ${compact(
                        o.filledQuantity,
                    )}/${compact(o.quantity)} · kalan ${compact(o.remainingQuantity)} · ${o.price ?: "MKT"}"
                },
                color = ArgentSilver,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (trades.isNotEmpty()) {
            item {
                Text(
                    "GERÇEKLEŞEN İŞLEMLER",
                    color = ArgentGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 5.dp),
                )
            }
        }
        items(trades) { t ->
            Text(
                "${t.symbol}  ${compact(t.quantity)} @ ${compact(t.price)}  ${t.id.take(8)}",
                color = ArgentSilver,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
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
