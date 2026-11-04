package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ChartGeometry
import com.argent.client.core.DepthLevelDto
import com.argent.client.core.OrderBookDto
import com.argent.client.core.TechnicalAnalysisDto
import com.argent.client.core.TickerDto
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentGreenDim
import com.argent.terminal.ui.ArgentLine
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentRedDim
import com.argent.terminal.ui.ArgentSilver
import java.math.BigDecimal
import java.util.Locale

@Composable
internal fun ChartPanel(
    symbol: String,
    ticker: TickerDto?,
    points: List<Float>,
    book: OrderBookDto,
    analysis: TechnicalAnalysisDto?,
) {
    val language = LocalUiLanguage.current
    val change = if (points.size >= 2) points.last() - points.first() else 0f
    val changePct = if (points.size >= 2 && points.first() != 0f) change / points.first() * 100f else 0f
    Column(
        Modifier.fillMaxWidth().height(
            360.dp,
        ).padding(8.dp).background(ArgentPanel, RoundedCornerShape(8.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = ArgentSilver, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(ticker?.price ?: "—", color = ArgentGold, fontFamily = FontFamily.Monospace, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                (if (changePct >= 0) "+" else "") + String.format("%.2f%%", changePct),
                color = if (changePct >= 0) ArgentGreen else ArgentRed,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                UiStrings.formatted(
                    UiTextKey.MARKET_BID_ASK,
                    language,
                    book.bestBid ?: ticker?.bid ?: "—",
                    book.bestAsk ?: ticker?.ask ?: "—",
                ),
                color = ArgentMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(8.dp))
        val pointSnapshot = points.toList()
        val safePoints =
            pointSnapshot.ifEmpty {
                listOfNotNull(ticker?.price?.toFloatOrNull()?.takeIf(Float::isFinite))
            }
        val chartScale = remember(safePoints) { ChartGeometry.scale(safePoints) }
        val axisValues = chartScale.axisValues
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(68.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                axisValues.forEach { value ->
                    Text(
                        String.format(Locale.US, "%.2f", value),
                        color = ArgentMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            SharedPricePlot(
                points = safePoints,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                lineColor = ArgentGold,
                gridColor = ArgentLine.copy(alpha = 0.65f),
                fillColor = ArgentGold,
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 68.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            chartScale.sampleLabels.forEach { label ->
                Text(label.toString(), color = ArgentMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TechnicalMetric(
                UiStrings.text(UiTextKey.TREND, language),
                when (analysis?.trend) {
                    "UPWARD" -> UiStrings.text(UiTextKey.UPWARD, language)
                    "DOWNWARD" -> UiStrings.text(UiTextKey.DOWNWARD, language)
                    "MIXED" -> UiStrings.text(UiTextKey.MIXED, language)
                    else -> UiStrings.text(UiTextKey.WAITING_FOR_DATA, language)
                },
            )
            TechnicalMetric("RSI 14", analysis?.rsi14 ?: "—")
            TechnicalMetric("SMA 20", analysis?.sma20 ?: "—")
            TechnicalMetric("SMA 50", analysis?.sma50 ?: "—")
            TechnicalMetric(UiStrings.text(UiTextKey.TWENTY_SAMPLE_CHANGE, language), analysis?.change20Pct ?: "—")
            TechnicalMetric(UiStrings.text(UiTextKey.SAMPLE_COUNT, language), analysis?.sampleCount?.toString() ?: "0")
        }
        val observedAt =
            analysis?.timestamp?.takeIf { it > 0 }?.let {
                java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(it))
            } ?: "—"
        Text(
            UiStrings.formatted(
                UiTextKey.RECENT_PRICE_OBSERVATIONS,
                language,
                analysis?.source ?: ticker?.source ?: if (language == "en") "source pending" else "kaynak bekleniyor",
                observedAt,
            ),
            color = ArgentMuted,
            fontSize = 9.sp,
        )
    }
}

@Composable
internal fun TechnicalMetric(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = ArgentMuted, fontSize = 9.sp)
        Text(value, color = ArgentSilver, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun OrderBookPanel(
    book: OrderBookDto,
    onPrice: (String) -> Unit,
) {
    val english = LocalUiLanguage.current == "en"
    val maxQty = (book.bids + book.asks).maxOfOrNull { it.quantity.toFloatOrNull() ?: 0f } ?: 1f
    Row(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
        BookSide(
            if (english) "BUY" else "ALIŞ",
            book.bids,
            ArgentGreen,
            ArgentGreenDim,
            maxQty,
            Modifier.weight(1f),
            onPrice,
        )
        Column(
            Modifier.width(88.dp).fillMaxHeight().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("SPREAD", color = ArgentMuted, fontSize = 10.sp)
            Text(
                spread(book),
                color = ArgentGold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        BookSide(
            if (english) "SELL" else "SATIŞ",
            book.asks,
            ArgentRed,
            ArgentRedDim,
            maxQty,
            Modifier.weight(1f),
            onPrice,
        )
    }
}

@Composable
internal fun BookSide(
    title: String,
    levels: List<DepthLevelDto>,
    color: Color,
    bar: Color,
    maxQty: Float,
    modifier: Modifier,
    onPrice: (String) -> Unit,
) {
    Column(modifier.background(ArgentPanel, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (levels.isEmpty()) {
            Text(
                if (LocalUiLanguage.current == "en") "No public levels" else "Açık seviye yok",
                color = ArgentMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (LocalUiLanguage.current == "en") "Price" else "Fiyat",
                color = ArgentMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (LocalUiLanguage.current == "en") "Quantity" else "Miktar",
                color = ArgentMuted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f),
            )
            Text("#", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp))
        }
        LazyColumn {
            items(levels) { lvl ->
                val frac = ((lvl.quantity.toFloatOrNull() ?: 0f) / maxQty.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().clickable { onPrice(lvl.price) }.padding(vertical = 1.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(
                            bar,
                            topLeft = Offset(size.width * (1f - frac), 0f),
                            size = Size(size.width * frac, size.height),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 2.dp)) {
                        Text(
                            lvl.price,
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            compact(lvl.quantity),
                            color = ArgentSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            lvl.orderCount.toString(),
                            color = ArgentMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.width(28.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun spread(book: OrderBookDto): String {
    val bid = book.bestBid?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    val ask = book.bestAsk?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    return compact((ask - bid).toPlainString())
}
