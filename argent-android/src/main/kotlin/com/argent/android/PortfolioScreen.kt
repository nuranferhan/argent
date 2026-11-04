package com.argent.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.argent.client.core.PortfolioDto
import java.util.Locale

@Composable
internal fun PortfolioPage(p: PortfolioDto?) {
    val english = LocalUiLanguage.current == "en"
    val localValuations = p?.valuations.orEmpty()
    val currencies =
        buildList {
            localValuations.mapTo(this) { it.quoteAsset.uppercase(Locale.ROOT) }
            if (p?.usdValuation != null) add("USD")
        }.distinct().sortedBy { currency ->
            when (currency) {
                "TRY" -> 0
                "USD" -> 1
                "USDT" -> 2
                else -> 3
            }
        }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            MetricCard(
                "Pozisyon yoğunlaşması",
                "${p?.riskScore ?: "—"}%",
                "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. Tek varlıkta %100; ARGENT göstergesidir, genel risk notu değildir.",
            )
        }
        currencies.forEach { currency ->
            val local = localValuations.firstOrNull { it.quoteAsset.equals(currency, ignoreCase = true) }
            val usd = if (currency == "USD") p?.usdValuation else null
            item {
                Text(
                    if (english) "$currency VALUATION" else "$currency DEĞERLEMESİ",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (usd != null) {
                item {
                    MetricCard(
                        if (english) "Equity · USD" else "Özkaynak · USD",
                        "${usd.equity} USD",
                        "${if (english) "Gross exposure" else "Brüt maruziyet"} ${usd.grossExposure} USD",
                    )
                }
                item {
                    MetricCard(
                        if (english) "Realized PnL · USD" else "Gerçekleşen PnL · USD",
                        "${usd.realizedPnl} USD",
                        "${if (english) "FX reference date" else "Kur referans tarihi"}: ${usd.rateDate}",
                    )
                }
                item {
                    MetricCard(
                        if (english) "Unrealized PnL · USD" else "Açık PnL · USD",
                        "${usd.unrealizedPnl} USD",
                        if (usd.complete) {
                            if (english) "Valuation is complete" else "Değerleme tam"
                        } else {
                            if (english) "FX conversion is partial" else "Kur dönüşümü kısmi"
                        },
                    )
                }
            } else if (local != null) {
                item {
                    MetricCard(
                        if (english) "Equity · $currency" else "Özkaynak · $currency",
                        "${local.equity} $currency",
                        "${if (english) "Gross exposure" else "Brüt maruziyet"} ${local.grossExposure} $currency",
                    )
                }
                item {
                    MetricCard(
                        if (english) "Realized PnL · $currency" else "Gerçekleşen PnL · $currency",
                        "${local.realizedPnl} $currency",
                        if (english) "Realized result" else "Gerçekleşen sonuç",
                    )
                }
                item {
                    MetricCard(
                        if (english) "Unrealized PnL · $currency" else "Açık PnL · $currency",
                        "${local.unrealizedPnl} $currency",
                        if (local.priceComplete) {
                            if (english) "Calculated with current prices" else "Güncel fiyatlarla hesaplandı"
                        } else {
                            if (english) "Some current prices are unavailable" else "Bazı varlıklarda güncel fiyat yok; tahmin kısmi"
                        },
                    )
                }
            }
        }
        if (currencies.isEmpty()) {
            item {
                MetricCard(
                    "Özkaynak",
                    p?.equity ?: "—",
                    "Gerçekleşen PnL ${p?.realizedPnl ?: "—"} · Açık PnL ${p?.unrealizedPnl ?: "—"}",
                )
            }
        }
        item { Text("BAKİYELER", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.balances.orEmpty()) { b -> DataRow(b.asset, "${b.available} kullanılabilir · ${b.held} blokeli") }
        item { Text("POZİSYONLAR", color = Gold, fontWeight = FontWeight.Bold) }
        items(
            p?.positions.orEmpty(),
        ) { pos -> DataRow(pos.symbol, "${pos.quantity} · ort. ${pos.avgEntryPrice} · PnL ${pos.realizedPnl}") }
    }
}
