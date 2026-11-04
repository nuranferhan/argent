package com.argent.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.ChartLimits
import com.argent.client.core.InstrumentDto
import com.argent.client.core.MeResponse
import com.argent.client.core.OrderBookDto
import com.argent.client.core.OrderDto
import com.argent.client.core.PortfolioDto
import com.argent.client.core.TechnicalAnalysisDto
import com.argent.client.core.TickerDto
import com.argent.client.core.TradeDto
import com.argent.client.core.fills
import com.argent.client.core.health
import com.argent.client.core.history
import com.argent.client.core.instruments
import com.argent.client.core.orderBookFlow
import com.argent.client.core.orderUpdatesFlow
import com.argent.client.core.orders
import com.argent.client.core.placeOrder
import com.argent.client.core.portfolio
import com.argent.client.core.publicTrades
import com.argent.client.core.technicalAnalysis
import com.argent.client.core.ticker
import com.argent.client.core.tickerFlow
import com.argent.terminal.ui.ArgentBg
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentRed
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
