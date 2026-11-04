package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.fills
import com.argent.client.core.health
import com.argent.client.core.history
import com.argent.client.core.instruments
import com.argent.client.core.orderBookFlow
import com.argent.client.core.orders
import com.argent.client.core.orderUpdatesFlow
import com.argent.client.core.placeOrder
import com.argent.client.core.portfolio
import com.argent.client.core.publicTrades
import com.argent.client.core.technicalAnalysis
import com.argent.client.core.tickerFlow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentBg
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentGreenDim
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentRedDim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun TradingDesk(
    api: ArgentApi,
    me: MeResponse,
    onLogout: () -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    darkTheme: Boolean,
    fontScale: Float,
    refreshSeconds: Int,
    marketRefreshSeconds: Int,
    guestMode: Boolean = false,
    onSettingsPreview: (Boolean, Float) -> Unit,
    onLanguagePreview: (String) -> Unit,
    onSettingsCancel: () -> Unit,
    onSettingsSave: (Boolean, Float, Int, Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var instruments by remember { mutableStateOf(listOf<InstrumentDto>()) }
    var symbol by remember { mutableStateOf("THYAO") }
    var book by remember { mutableStateOf(OrderBookDto(symbol)) }
    var technicalAnalysis by remember { mutableStateOf<TechnicalAnalysisDto?>(null) }
    val tickers = remember { mutableStateMapOf<String, TickerDto>() }
    val chart = remember { mutableStateListOf<Float>() }
    var portfolio by remember { mutableStateOf<PortfolioDto?>(null) }
    var orders by remember { mutableStateOf(listOf<OrderDto>()) }
    var fills by remember { mutableStateOf(listOf<TradeDto>()) }
    var blotter by remember { mutableStateOf(listOf<TradeDto>()) }
    var tab by remember { mutableStateOf(0) }
    var bottomDockHeight by remember { mutableStateOf(420.dp) }
    var settingsOpen by remember { mutableStateOf(false) }
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("10") }
    var flash by remember { mutableStateOf<String?>(null) }
    var health by remember { mutableStateOf("…") }
    var orderSubmitting by remember { mutableStateOf(false) }
    var orderRequestFingerprint by remember { mutableStateOf<String?>(null) }
    var orderRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    val loginRequiredMessage = guestLoginMessage()

    LaunchedEffect(Unit) {
        runCatching { instruments = api.instruments() }
    }
    LaunchedEffect(symbol) {
        chart.clear()
        launch {
            runCatching {
                api.history(symbol).forEach { it.price.toFloatOrNull()?.takeIf(Float::isFinite)?.let(chart::add) }
            }
        }
        launch {
            api.tickerFlow().collect { t ->
                tickers[t.symbol] = t
                if (t.symbol == symbol) {
                    chart.add(t.price.toFloatOrNull()?.takeIf(Float::isFinite) ?: return@collect)
                    while (chart.size > ChartLimits.HISTORY_POINTS) chart.removeAt(0)
                }
            }
        }
    }
    LaunchedEffect(symbol, refreshSeconds) {
        technicalAnalysis = null
        launch { api.orderBookFlow(symbol).collect { book = it } }
        launch {
            while (true) {
                runCatching { api.ticker(symbol) }.onSuccess { ticker ->
                    tickers[symbol] = ticker
                    ticker.price.toFloatOrNull()?.takeIf(Float::isFinite)?.let { point ->
                        chart.add(point)
                        while (chart.size > ChartLimits.HISTORY_POINTS) chart.removeAt(0)
                    }
                }
                runCatching { api.technicalAnalysis(symbol) }.onSuccess { technicalAnalysis = it }
                delay(refreshSeconds * 1_000L)
            }
        }
    }
    LaunchedEffect(book.bestBid, book.bestAsk, side, type) {
        if (type == "MARKET") return@LaunchedEffect
        if (price.isNotBlank()) return@LaunchedEffect
        price = if (side == "BUY") book.bestAsk ?: book.bestBid ?: price else book.bestBid ?: book.bestAsk ?: price
    }
    LaunchedEffect(me, refreshSeconds, guestMode) {
        launch {
            if (!guestMode) {
                api.orderUpdatesFlow().collect { update ->
                    flash =
                        listOf(
                            "Emir güncellemesi",
                            update.orderId.take(8),
                            update.status,
                            update.symbol,
                        ).joinToString(" · ")
                }
            }
        }
        while (true) {
            if (!guestMode) {
                runCatching { portfolio = api.portfolio() }
                runCatching { orders = api.orders() }
                runCatching { fills = api.fills() }
            }
            runCatching { blotter = api.publicTrades(symbol) }
            runCatching {
                val h = api.health()
                health =
                    if (h.status == "UP") {
                        buildString {
                            append("INFRA OK · ")
                            append(if (h.matchingEngine) "ENGINE OK" else "ENGINE PAUSED")
                            append(" · ")
                            append(h.executionMode.replace('_', ' '))
                            append(" · CRYPTO ")
                            append(h.cryptoMarketData)
                            append(" · BIST ")
                            append(h.bistMarketData)
                        }
                    } else {
                        buildString {
                            append("INFRA DEGRADED")
                            if (!h.postgres) append(" · DB")
                            if (!h.redis) append(" · REDIS")
                            if (!h.rabbit) append(" · MQ")
                            if (!h.invariantOk) append(" · LEDGER")
                            if (!h.matchingEngine) append(" · ENGINE PAUSED")
                        }
                    }
            }.onFailure { health = "GATEWAY'E BAĞLANILAMIYOR · YENİDEN DENENİYOR" }
            delay(refreshSeconds * 1_000L)
        }
    }

    Column(Modifier.fillMaxSize().background(ArgentBg)) {
        TopBar(
            me, tickers, health, onLogout, api, symbol, scope, guestMode,
            onFlash = { flash = it },
            onMeChanged = onMeChanged,
            onWorkspaceClosed = onLogout,
            onSettings = { settingsOpen = true },
        )
        flash?.let {
            Text(
                it,
                color = if (it.contains("REJECTED", true) || it.contains("hata", true)) ArgentRed else ArgentGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Watchlist(instruments, tickers, symbol) {
                symbol = it
                price = ""
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ChartPanel(symbol, tickers[symbol], chart, book, technicalAnalysis)
                OrderBookPanel(book) { clicked -> price = clicked }
            }
            if (!guestMode) {
                Ticket(
                    symbol = symbol,
                    instrument = instruments.firstOrNull { it.symbol == symbol },
                    ticker = tickers[symbol],
                    side = side,
                    type = type,
                    price = price,
                    qty = qty,
                    submitting = orderSubmitting,
                    onSide = {
                        side = it
                        price = ""
                    },
                    onType = {
                        type = it
                        if (it == "MARKET") price = ""
                    },
                    onPrice = { price = it },
                    onQty = { qty = it },
                    onSubmit = {
                        val fingerprint = listOf(symbol, side, type, price, qty).joinToString("|")
                        if (fingerprint != orderRequestFingerprint) {
                            orderRequestFingerprint = fingerprint
                            orderRequestId = UUID.randomUUID().toString()
                        }
                        val idempotencyKey = orderRequestId
                        orderSubmitting = true
                        scope.launch {
                            try {
                                val result =
                                    api.placeOrder(
                                        symbol,
                                        side,
                                        type,
                                        qty,
                                        price.ifBlank { null },
                                        idempotencyKey,
                                    )
                                flash = "${result.status}  filled=${result.filledQuantity}  ${result.reason}".trim()
                                portfolio = api.portfolio()
                                orders = api.orders()
                                orderRequestFingerprint = null
                                orderRequestId = UUID.randomUUID().toString()
                            } catch (error: Exception) {
                                flash =
                                    error.message
                                        ?: "Emir gönderilemedi; aynı emir anahtarıyla yeniden deneyebilirsin."
                            } finally {
                                orderSubmitting = false
                            }
                        }
                    },
                )
            }
        }
        if (settingsOpen) {
            SettingsDialog(me, darkTheme, fontScale, refreshSeconds, marketRefreshSeconds, LocalUiLanguage.current, {
                settingsOpen = false
            }, onSettingsPreview, onLanguagePreview, onSettingsCancel, onSettingsSave)
        }
        BottomDock(
            tab = tab,
            onTab = { tab = it },
            height = bottomDockHeight,
            onHeightChange = { bottomDockHeight = it },
            portfolio = portfolio,
            orders = orders,
            fills = fills,
            blotter = blotter,
            tickers = tickers,
            api = api,
            me = me,
            selectedSymbol = symbol,
            draftSide = side,
            draftType = type,
            draftQuantity = qty,
            draftPrice = price,
            scope = scope,
            marketRefreshSeconds = marketRefreshSeconds,
            guestMode = guestMode,
            onRequireLogin = { flash = loginRequiredMessage },
            onFlash = { flash = it },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TopBar(
    me: MeResponse,
    tickers: Map<String, TickerDto>,
    health: String,
    onLogout: () -> Unit,
    api: ArgentApi,
    selectedSymbol: String,
    scope: kotlinx.coroutines.CoroutineScope,
    guestMode: Boolean,
    onFlash: (String) -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    onWorkspaceClosed: () -> Unit,
    onSettings: () -> Unit,
) {
    var workspaceOpen by remember { mutableStateOf(false) }
    var lastTestnetOrder by remember { mutableStateOf<BinanceTestnetOrderDto?>(null) }
    val loginRequiredMessage = guestLoginMessage()
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = 56.dp,
        ).background(ArgentPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource("argent-icon.png"),
            contentDescription = "ARGENT",
            modifier = Modifier.size(30.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Spacer(Modifier.width(20.dp))
        val marketTickerText =
            tickers.values.sortedBy { it.symbol }
                .joinToString("     •     ") { "${it.symbol} ${compact(it.price)}" }
        if (marketTickerText.isNotBlank()) {
            TickerMarquee(
                text = "$marketTickerText     •     ",
                modifier = Modifier.weight(1f).height(28.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        val ok = health.startsWith("INFRA OK")
        Box(
            Modifier.widthIn(min = 180.dp, max = 260.dp)
                .background(if (ok) ArgentGreenDim else ArgentRedDim, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "$health     •     $health",
                color = if (ok) ArgentGreen else ArgentRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (guestMode) guestLabel() else "${me.displayName} · ${me.role}",
            color = ArgentMuted,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onSettings) { Text("Ayarlar", color = ArgentGold) }
        TextButton(onClick = {
            if (guestMode) onFlash(loginRequiredMessage) else workspaceOpen = true
        }) { Text(if (LocalUiLanguage.current == "en") "Workspace" else "Çalışma alanı", color = ArgentGold) }
        TextButton(onClick = {
            if (guestMode) {
                onLogout()
                return@TextButton
            }
            scope.launch {
                api.logout()
                onLogout()
            }
        }) {
            Text(
                if (guestMode) {
                    if (LocalUiLanguage.current == "en") "Sign in" else "Giriş yap"
                } else if (LocalUiLanguage.current == "en") {
                    "Sign out"
                } else {
                    "Çıkış"
                },
                color = ArgentRed,
            )
        }
    }
    if (workspaceOpen) {
        WorkspaceDialog(
            api = api,
            me = me,
            selectedSymbol = selectedSymbol,
            scope = scope,
            onClose = { workspaceOpen = false },
            onFlash = onFlash,
            onMeChanged = onMeChanged,
            onWorkspaceClosed = onWorkspaceClosed,
            testnetOrder = lastTestnetOrder,
            onTestnetOrderChanged = { lastTestnetOrder = it },
        )
    }
}
