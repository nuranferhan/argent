package com.argent.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.argent.terminal.api.ArgentApi
import com.argent.terminal.api.DepthLevelDto
import com.argent.terminal.api.InstrumentDto
import com.argent.terminal.api.MeResponse
import com.argent.terminal.api.OrderBookDto
import com.argent.terminal.api.OrderDto
import com.argent.terminal.api.PortfolioDto
import com.argent.terminal.api.TickerDto
import com.argent.terminal.api.TechnicalAnalysisDto
import com.argent.terminal.api.TradeDto
import com.argent.terminal.api.TenantDto
import com.argent.terminal.api.TenantMemberDto
import com.argent.terminal.api.MarketMakerStateDto
import com.argent.terminal.api.BinanceTestnetStatusDto
import com.argent.terminal.api.BinanceTestnetOrderDto
import com.argent.terminal.api.BinanceTestnetOrderRequestDto
import com.argent.terminal.api.BistReplayDto
import com.argent.terminal.api.ExternalQuoteDto
import com.argent.terminal.api.MarketNewsDto
import com.argent.terminal.api.PriceAlertDto
import com.argent.terminal.api.ExternalMarketStatusDto
import com.argent.terminal.api.AssistantOrderDraftDto
import com.argent.terminal.ui.ArgentTheme
import com.argent.terminal.ui.ArgentBg
import com.argent.terminal.ui.ArgentBlue
import com.argent.terminal.ui.ArgentColors
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGoldDim
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentGreenDim
import com.argent.terminal.ui.ArgentLine
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentRedDim
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Locale
import java.math.RoundingMode
import java.util.UUID
import java.util.prefs.Preferences



@Composable
internal fun ChartPanel(symbol: String, ticker: TickerDto?, points: List<Float>, book: OrderBookDto, analysis: TechnicalAnalysisDto?) {
    val change = if (points.size >= 2) points.last() - points.first() else 0f
    val changePct = if (points.size >= 2 && points.first() != 0f) change / points.first() * 100f else 0f
    Column(
        Modifier.fillMaxWidth().height(360.dp).padding(8.dp).background(ArgentPanel, RoundedCornerShape(8.dp)).padding(12.dp)
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
                fontSize = 13.sp
            )
            Spacer(Modifier.width(16.dp))
            Text("bid ${book.bestBid ?: ticker?.bid ?: "—"}   ask ${book.bestAsk ?: ticker?.ask ?: "—"}", color = ArgentMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(8.dp))
        val safePoints = points.ifEmpty { listOf(ticker?.price?.toFloatOrNull() ?: 0f) }
        val rawMin = safePoints.minOrNull() ?: 0f
        val rawMax = safePoints.maxOrNull() ?: rawMin
        val padding = ((rawMax - rawMin) * 0.08f).coerceAtLeast(kotlin.math.abs(rawMax) * 0.0005f).coerceAtLeast(0.01f)
        val min = rawMin - padding
        val max = rawMax + padding
        val axisValues = (0..4).map { i -> max - (max - min) * i / 4f }
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(68.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                axisValues.forEach { value ->
                    Text(String.format(Locale.US, "%.2f", value), color = ArgentMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                repeat(5) { i ->
                    val y = size.height * i / 4f
                    drawLine(ArgentLine.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                if (points.size >= 2) {
                    val path = Path()
                    val fill = Path()
                    points.forEachIndexed { i, v ->
                        val x = size.width * i / (points.size - 1).coerceAtLeast(1)
                        val y = size.height - ((v - min) / (max - min) * size.height)
                        if (i == 0) { path.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) }
                        else { path.lineTo(x, y); fill.lineTo(x, y) }
                    }
                    fill.lineTo(size.width, size.height)
                    fill.close()
                    drawPath(fill, Brush.verticalGradient(listOf(ArgentGold.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(path, ArgentGold, style = Stroke(width = 2.4f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 68.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", color = ArgentMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(((points.size + 1) / 2).coerceAtLeast(1).toString(), color = ArgentMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(points.size.coerceAtLeast(1).toString(), color = ArgentMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TechnicalMetric("TREND", when (analysis?.trend) { "UPWARD" -> "Yukarı"; "DOWNWARD" -> "Aşağı"; "MIXED" -> "Karışık"; else -> "Veri bekleniyor" })
            TechnicalMetric("RSI 14", analysis?.rsi14 ?: "—")
            TechnicalMetric("SMA 20", analysis?.sma20 ?: "—")
            TechnicalMetric("SMA 50", analysis?.sma50 ?: "—")
            TechnicalMetric("20 örnek %", analysis?.change20Pct ?: "—")
            TechnicalMetric("Örnek", analysis?.sampleCount?.toString() ?: "0")
        }
        val observedAt = analysis?.timestamp?.takeIf { it > 0 }?.let {
            java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(it))
        } ?: "—"
        Text("Son fiyat gözlemleri · ${analysis?.source ?: ticker?.source ?: "kaynak bekleniyor"} · $observedAt · Yatırım tavsiyesi değildir", color = ArgentMuted, fontSize = 9.sp)
    }
}

@Composable
internal fun TechnicalMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = ArgentMuted, fontSize = 9.sp)
        Text(value, color = ArgentSilver, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun OrderBookPanel(book: OrderBookDto, onPrice: (String) -> Unit) {
    val english = LocalUiLanguage.current == "en"
    val maxQty = (book.bids + book.asks).maxOfOrNull { it.quantity.toFloatOrNull() ?: 0f } ?: 1f
    Row(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
        BookSide(if (english) "BUY" else "ALIŞ", book.bids, ArgentGreen, ArgentGreenDim, maxQty, Modifier.weight(1f), onPrice)
        Column(
            Modifier.width(88.dp).fillMaxHeight().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SPREAD", color = ArgentMuted, fontSize = 10.sp)
            Text(spread(book), color = ArgentGold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        BookSide(if (english) "SELL" else "SATIŞ", book.asks, ArgentRed, ArgentRedDim, maxQty, Modifier.weight(1f), onPrice)
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
    onPrice: (String) -> Unit
) {
    Column(modifier.background(ArgentPanel, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text(title, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (levels.isEmpty()) Text(if (LocalUiLanguage.current == "en") "No public levels" else "Açık seviye yok", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(if (LocalUiLanguage.current == "en") "Price" else "Fiyat", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(if (LocalUiLanguage.current == "en") "Quantity" else "Miktar", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("#", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.width(28.dp))
        }
        LazyColumn {
            items(levels) { lvl ->
                val frac = ((lvl.quantity.toFloatOrNull() ?: 0f) / maxQty.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().clickable { onPrice(lvl.price) }.padding(vertical = 1.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(bar, topLeft = Offset(size.width * (1f - frac), 0f), size = Size(size.width * frac, size.height))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 2.dp)) {
                        Text(lvl.price, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(compact(lvl.quantity), color = ArgentSilver, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(lvl.orderCount.toString(), color = ArgentMuted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Ticket(
    symbol: String,
    instrument: InstrumentDto?,
    ticker: TickerDto?,
    side: String,
    type: String,
    price: String,
    qty: String,
    submitting: Boolean,
    onSide: (String) -> Unit,
    onType: (String) -> Unit,
    onPrice: (String) -> Unit,
    onQty: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val quantityValue = qty.toBigDecimalOrNull()
    val priceValue = price.toBigDecimalOrNull()
    val lotSize = instrument?.lotSize?.toBigDecimalOrNull()
    val tickSize = instrument?.tickSize?.toBigDecimalOrNull()
    val validationMessage = when {
        instrument == null -> "Enstrüman bilgisi yükleniyor."
        quantityValue == null || quantityValue.signum() <= 0 -> "Miktar pozitif bir sayı olmalı."
        lotSize != null && quantityValue.remainder(lotSize).signum() != 0 -> "Miktar lot adımına uygun değil (${instrument.lotSize})."
        type == "MARKET" && ticker?.price == null -> "Piyasa fiyatı henüz alınmadı."
        type != "MARKET" && (priceValue == null || priceValue.signum() <= 0) -> "Fiyat pozitif bir sayı olmalı."
        type != "MARKET" && tickSize != null && priceValue != null && priceValue.remainder(tickSize).signum() != 0 -> "Fiyat tick adımına uygun değil (${instrument.tickSize})."
        else -> null
    }
    val notional = runCatching {
        val px = if (type == "MARKET") ticker?.price ?: "0" else price.ifBlank { "0" }
        BigDecimal(px).multiply(BigDecimal(qty.ifBlank { "0" })).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }.getOrElse { "—" }
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(ArgentPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("EMİR  $symbol", color = ArgentSilver, fontWeight = FontWeight.Bold)
        instrument?.let { Text("${it.displayName}  tick ${it.tickSize}  lot ${it.lotSize}", color = ArgentMuted, fontSize = 11.sp) }
        Row {
            Chip("AL", side == "BUY", ArgentGreen) { onSide("BUY") }
            Spacer(Modifier.width(8.dp))
            Chip("SAT", side == "SELL", ArgentRed) { onSide("SELL") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { t ->
                Chip(t, type == t, ArgentGold) { onType(t) }
            }
        }
        if (type != "MARKET") field("Fiyat", price, KeyboardType.Decimal, onPrice)
        field("Miktar", qty, KeyboardType.Decimal, onQty)
        Text("Tahmini notional  $notional", color = ArgentMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        validationMessage?.let { Text(it, color = ArgentRed, fontSize = 11.sp) }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onSubmit,
            enabled = !submitting && validationMessage == null,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (side == "BUY") ArgentGreen else ArgentRed,
                contentColor = Color.White
            )
        ) { Text(if (submitting) "GÖNDERİLİYOR…" else if (side == "BUY") "ALIŞ EMRİ" else "SATIŞ EMRİ", fontWeight = FontWeight.Bold) }
        Text(
            "Limit deftere yazılır. Market/IOC kalanı iptal eder. FOK tamamen dolmazsa işlem açılmaz. Fiyata tıklayınca bilet dolar.",
            color = ArgentMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
internal fun BottomDock(
    tab: Int,
    onTab: (Int) -> Unit,
    height: androidx.compose.ui.unit.Dp,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    portfolio: PortfolioDto?,
    orders: List<OrderDto>,
    fills: List<TradeDto>,
    blotter: List<TradeDto>,
    tickers: Map<String, TickerDto>,
    api: ArgentApi,
    me: MeResponse,
    selectedSymbol: String,
    draftSide: String,
    draftType: String,
    draftQuantity: String,
    draftPrice: String,
    scope: kotlinx.coroutines.CoroutineScope,
    marketRefreshSeconds: Int,
    guestMode: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onFlash: (String) -> Unit
) {
    val currentHeight = rememberUpdatedState(height)
    val updateHeight = rememberUpdatedState(onHeightChange)
    Column(Modifier.fillMaxWidth().height(height).background(ArgentPanelAlt)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(12.dp)
                .pointerInput(Unit) {
                    var dragHeight = currentHeight.value
                    detectDragGestures(
                        onDragStart = { dragHeight = currentHeight.value },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragHeight = (dragHeight - dragAmount.y.toDp()).coerceIn(180.dp, 700.dp)
                            updateHeight.value(dragHeight)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(42.dp).height(3.dp).background(ArgentMuted, RoundedCornerShape(50)))
        }
        TabRow(selectedTabIndex = tab, containerColor = ArgentPanel, contentColor = ArgentGold) {
            listOf("Portföy", "Açık emirler", "İşlemlerim", "Piyasa işleri", "ARGENT AI").forEachIndexed { i, t ->
                val title = when (i) {
                    0 -> if (LocalUiLanguage.current == "en") "Portfolio" else "Portföy"
                    1 -> if (LocalUiLanguage.current == "en") "Open orders" else "Açık emirler"
                    2 -> if (LocalUiLanguage.current == "en") "My trades" else "İşlemlerim"
                    3 -> if (LocalUiLanguage.current == "en") "Market activity" else "Piyasa işleri"
                    else -> "ARGENT AI"
                }
                Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(title, fontSize = 12.sp) })
            }
        }
        when (tab) {
            0 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else PortfolioTab(portfolio, tickers)
            1 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else OrdersTab(orders, api, scope, onFlash)
            2 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else TradesTab(fills, orders)
            3 -> ExternalMarketsTab(api, scope, onFlash, blotter, marketRefreshSeconds, guestMode, onRequireLogin)
            else -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else AssistantTab(api, selectedSymbol, draftSide, draftType, draftQuantity, draftPrice, scope)
        }
    }
}

@Composable
internal fun GuestAccessCard(onLogin: () -> Unit) {
    val english = LocalUiLanguage.current == "en"
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (english) "Sign in required" else "Giriş yapmanız gerekiyor", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (english) "Sign in to access your portfolio, orders, trades, AI assistant and workspace." else "Portföyünüze, emirlerinize, işlemlerinize, ARGENT AI'a ve çalışma alanına erişmek için lütfen giriş yapın.", color = ArgentMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onLogin) { Text(if (english) "Go to sign in" else "Giriş ekranına dön") }
    }
}

@Composable
internal fun PortfolioTab(
    p: PortfolioDto?,
    tickers: Map<String, TickerDto>
) {
    if (p == null) {
        Text("Yükleniyor…", color = ArgentMuted, modifier = Modifier.padding(12.dp))
        return
    }
    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Metric("Pozisyon yoğunlaşması", "${p.riskScore}%")
            Text("En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. ARGENT göstergesidir; genel risk notu değildir. Spot işlem · kaldıraç/marjin kapalı.", color = ArgentMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
        p.usdValuation?.let { value ->
            Spacer(Modifier.height(8.dp))
            Text(if (LocalUiLanguage.current == "en") "USD valuation${if (value.complete) "" else " · partial"}" else "USD değerlemesi${if (value.complete) "" else " · kısmi"}", color = ArgentGold, fontWeight = FontWeight.SemiBold)
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
                Metric("Gerçekleşen PnL", "${compact(value.realizedPnl)} ${value.quoteAsset}", value.realizedPnl.startsWith("-"))
                Metric("Açık PnL", "${compact(value.unrealizedPnl)} ${value.quoteAsset}", value.unrealizedPnl.startsWith("-"))
                Metric("Brüt maruziyet", "${compact(value.grossExposure)} ${value.quoteAsset}")
            }
            if (!value.priceComplete) {
                Text("Güncel fiyatı olmayan varlıklar maliyet bazlı değerlenir; özkaynak kısmi tahmindir.", color = ArgentRed, fontSize = 11.sp)
            }
            if (!value.pnlComplete) {
                Text("Başlangıç bakiyelerinin maliyet temeli bilinmediği için PnL eksik olabilir.", color = ArgentRed, fontSize = 11.sp)
            }
        }
        if (p.valuations.isEmpty()) Text("Değerleme için bakiye veya pozisyon yok.", color = ArgentMuted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Bakiyeler", color = ArgentMuted, fontSize = 11.sp)
                p.balances.forEach { b ->
                    Text("${b.asset}  serbest ${compact(b.available)}  bloke ${compact(b.held)}", color = ArgentSilver, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Pozisyonlar", color = ArgentMuted, fontSize = 11.sp)
                p.positions.forEach { pos ->
                    val mark = tickers[pos.symbol]?.price ?: pos.avgEntryPrice
                    Text(
                        "${pos.symbol}  qty ${compact(pos.quantity)}  avg ${compact(pos.avgEntryPrice)}  mark ${compact(mark)}  rPnL ${compact(pos.realizedPnl)}",
                        color = ArgentSilver,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Para yatırma ve çekme, doğrulanmış ödeme/saklama sağlayıcısı bağlandığında etkinleştirilir.", color = ArgentMuted, fontSize = 11.sp)
    }
}

@Composable
internal fun OrdersTab(
    orders: List<OrderDto>,
    api: ArgentApi,
    scope: kotlinx.coroutines.CoroutineScope,
    onFlash: (String) -> Unit
) {
    val openOrders = orders.filter { it.status in setOf("PENDING", "ACCEPTED", "PARTIALLY_FILLED") && (it.remainingQuantity.toBigDecimalOrNull()?.signum() ?: 0) > 0 }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text("AÇIK EMİRLER · ${openOrders.size} bekleyen", color = ArgentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
        if (openOrders.isEmpty()) {
            Text("Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemlerim geçmişindedir.", color = ArgentMuted, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                items(openOrders, key = { it.id }) { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${o.symbol} ${o.side} ${o.type} ${compact(o.quantity)}@${o.price ?: "MKT"}  ${orderStatusLabel(o.status, LocalUiLanguage.current)}  filled ${compact(o.filledQuantity)}",
                            color = ArgentSilver,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        if (o.status == "ACCEPTED" || o.status == "PARTIALLY_FILLED") {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { api.cancel(o.id, o.symbol); onFlash("İptal ${o.id.take(8)}") }
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
internal fun TradesTab(trades: List<TradeDto>, orders: List<OrderDto> = emptyList()) {
    val orderHistory = orders.sortedByDescending { it.updatedAt.takeIf { updated -> updated > 0 } ?: it.createdAt }
    if (trades.isEmpty() && orderHistory.isEmpty()) {
        Text("Emir veya gerçekleşen işlem geçmişi yok.", color = ArgentMuted, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(top = 8.dp)) {
        item { Text("EMİR GEÇMİŞİ · ${orderHistory.size} son emir", color = ArgentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
        if (orderHistory.isEmpty()) item { Text("Emir geçmişi boş.", color = ArgentMuted, fontSize = 12.sp) }
        items(orderHistory, key = { "order-${it.id}" }) { o ->
            Text(
                if (LocalUiLanguage.current == "en") "${o.symbol} ${o.side} ${o.type} · ${orderStatusLabel(o.status, LocalUiLanguage.current)} · filled ${compact(o.filledQuantity)}/${compact(o.quantity)} · remaining ${compact(o.remainingQuantity)} · ${o.price ?: "MKT"}" else "${o.symbol} ${o.side} ${o.type} · ${orderStatusLabel(o.status, LocalUiLanguage.current)} · dolan ${compact(o.filledQuantity)}/${compact(o.quantity)} · kalan ${compact(o.remainingQuantity)} · ${o.price ?: "MKT"}",
                color = ArgentSilver, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        if (trades.isNotEmpty()) item { Text("GERÇEKLEŞEN İŞLEMLER", color = ArgentGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)) }
        items(trades) { t ->
            Text(
                "${t.symbol}  ${compact(t.quantity)} @ ${compact(t.price)}  ${t.id.take(8)}",
                color = ArgentSilver,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

internal fun orderStatusLabel(status: String, language: String = "tr"): String = when (status.uppercase()) {
    "ACCEPTED", "NEW", "PENDING" -> if (language == "en") "OPEN" else "AÇIK"
    "PARTIALLY_FILLED" -> if (language == "en") "PARTIALLY FILLED" else "KISMEN DOLDU"
    "FILLED" -> if (language == "en") "FILLED" else "DOLDU"
    "CANCELLED", "CANCELED" -> if (language == "en") "CANCELLED" else "İPTAL"
    "REJECTED" -> if (language == "en") "REJECTED" else "REDDEDİLDİ"
    "EXPIRED" -> if (language == "en") "EXPIRED" else "SÜRESİ DOLDU"
    else -> status
}

@Composable
internal fun Metric(label: String, value: String, negative: Boolean = false) {
    Column {
        Text(label, color = ArgentMuted, fontSize = 10.sp)
        Text(compact(value), color = if (negative) ArgentRed else ArgentGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) color.copy(alpha = 0.25f) else ArgentPanelAlt, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) color else ArgentLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(text, color = if (selected) color else ArgentMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = fieldColors(),
        singleLine = true
    )
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ArgentSilver,
    unfocusedTextColor = ArgentSilver,
    focusedBorderColor = ArgentGold,
    unfocusedBorderColor = ArgentLine,
    focusedLabelColor = ArgentGold,
    unfocusedLabelColor = ArgentMuted
)

internal fun compact(raw: String): String = runCatching {
    val value = BigDecimal(raw)
    (if (value.scale() > 6) value.setScale(6, RoundingMode.HALF_UP) else value).stripTrailingZeros().toPlainString()
}.getOrElse { raw }

internal fun spread(book: OrderBookDto): String {
    val bid = book.bestBid?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    val ask = book.bestAsk?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    return compact((ask - bid).toPlainString())
}
