package com.argent.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.OrderBookDto
import com.argent.client.core.TechnicalAnalysisDto
import com.argent.client.core.TradeDto
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey
import java.util.Locale

@Composable
internal fun TechnicalAnalysisCard(analysis: TechnicalAnalysisDto?) {
    val language = LocalUiLanguage.current
    val observedAt =
        analysis?.timestamp?.takeIf { it > 0 }?.let {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: "—"
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                UiStrings.text(UiTextKey.TECHNICAL_VIEW, language),
                color = Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TechnicalValue(
                    UiStrings.text(UiTextKey.TREND, language),
                    when (analysis?.trend) {
                        "UPWARD" -> UiStrings.text(UiTextKey.UPWARD, language)
                        "DOWNWARD" -> UiStrings.text(UiTextKey.DOWNWARD, language)
                        "MIXED" -> UiStrings.text(UiTextKey.MIXED, language)
                        else -> UiStrings.text(UiTextKey.WAITING_FOR_DATA, language)
                    },
                )
                TechnicalValue("RSI 14", analysis?.rsi14 ?: "—")
                TechnicalValue("SMA 20", analysis?.sma20 ?: "—")
                TechnicalValue("SMA 50", analysis?.sma50 ?: "—")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TechnicalValue(
                    UiStrings.text(UiTextKey.TWENTY_SAMPLE_CHANGE, language),
                    analysis?.change20Pct?.let { "$it%" } ?: "—",
                )
                TechnicalValue(
                    UiStrings.text(UiTextKey.LOW_HIGH_30, language),
                    "${analysis?.support30 ?: "—"} / ${analysis?.resistance30 ?: "—"}",
                )
                TechnicalValue(
                    UiStrings.text(UiTextKey.SAMPLE_COUNT, language),
                    analysis?.sampleCount?.toString() ?: "0",
                )
            }
            Text(
                UiStrings.formatted(
                    UiTextKey.RECENT_PRICE_OBSERVATIONS,
                    language,
                    analysis?.source ?: if (language == "en") "source pending" else "kaynak bekleniyor",
                    observedAt,
                ),
                color = Muted,
                fontSize = 9.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
private fun TechnicalValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 9.sp)
        Text(value, color = Color(0xFFE0E4E8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun OrderBookCard(
    book: OrderBookDto,
    readOnly: Boolean = false,
    onPrice: (String) -> Unit,
) {
    val language = LocalUiLanguage.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "${book.symbol} · ${if (language == "en") "ORDER BOOK" else "EMİR DEFTERİ"}",
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            if (readOnly) {
                Text(
                    if (language == "en") {
                        "Public market depth · read only"
                    } else {
                        "Herkese açık piyasa derinliği · salt okunur"
                    },
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (language == "en") "BUY" else "ALIŞ", color = Green)
                Text(if (language == "en") "SELL" else "SATIŞ", color = Red)
            }
            val bids = book.bids.take(5).reversed()
            val asks = book.asks.take(5)
            if (bids.isEmpty() && asks.isEmpty()) {
                Text(
                    if (language == "en") {
                        "Waiting for public order book levels."
                    } else {
                        "Herkese açık emir defteri seviyeleri bekleniyor."
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            (0 until maxOf(bids.size, asks.size)).forEach { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bid = bids.getOrNull(i)
                    val ask = asks.getOrNull(i)
                    Text(
                        bid?.let {
                            "${it.price}  ×  ${it.quantity}"
                        } ?: "",
                        color = Green,
                        modifier =
                            Modifier.weight(
                                1f,
                            ).then(
                                if (readOnly) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        bid?.price?.let(onPrice)
                                    }
                                },
                            ),
                        fontSize = 11.sp,
                    )
                    Text(
                        ask?.let {
                            "${it.price}  ×  ${it.quantity}"
                        } ?: "",
                        color = Red,
                        modifier =
                            Modifier.weight(
                                1f,
                            ).then(
                                if (readOnly) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        ask?.price?.let(onPrice)
                                    }
                                },
                            ),
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PublicMarketTradesCard(
    trades: List<TradeDto>,
    quoteAsset: String,
) {
    val language = LocalUiLanguage.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (language == "en") "RECENT MARKET TRADES" else "SON PİYASA İŞLEMLERİ",
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (language == "en") {
                    "Public executions · not your personal trade history"
                } else {
                    "Herkese açık gerçekleşmeler · kişisel işlem geçmişiniz değildir"
                },
                color = Muted,
                fontSize = 10.sp,
            )
            if (trades.isEmpty()) {
                Text(
                    if (language == "en") {
                        "No recent public executions."
                    } else {
                        "Yakın zamanda herkese açık gerçekleşen işlem yok."
                    },
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            trades.take(8).forEach { trade ->
                val price =
                    trade.price
                        .toBigDecimalOrNull()
                        ?.setScale(6, java.math.RoundingMode.HALF_UP)
                        ?.stripTrailingZeros()
                        ?.toPlainString() ?: trade.price
                val quantity =
                    trade.quantity
                        .toBigDecimalOrNull()
                        ?.setScale(6, java.math.RoundingMode.HALF_UP)
                        ?.stripTrailingZeros()
                        ?.toPlainString() ?: trade.quantity
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trade.symbol, color = Color(0xFFE0E4E8), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text(
                        "$quantity × $price${if (quoteAsset.isBlank()) "" else " $quoteAsset"}",
                        color = Muted,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
