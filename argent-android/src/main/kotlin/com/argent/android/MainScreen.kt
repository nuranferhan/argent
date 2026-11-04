package com.argent.android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.ChartLimits
import com.argent.client.core.HealthDto
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
import com.argent.client.core.orderBook
import com.argent.client.core.orderBookFlow
import com.argent.client.core.orderUpdatesFlow
import com.argent.client.core.orders
import com.argent.client.core.portfolio
import com.argent.client.core.publicTrades
import com.argent.client.core.technicalAnalysis
import com.argent.client.core.ticker
import com.argent.client.core.tickerFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MainScreen(
    api: ArgentApi,
    initialMe: MeResponse,
    isGuest: Boolean = false,
    onMeChanged: (MeResponse) -> Unit,
    onLogout: () -> Unit,
    dark: Boolean,
    fontScale: Float,
    refreshSeconds: Int,
    marketRefreshSeconds: Int,
    onSettingsPreview: (Boolean, Float) -> Unit,
    onLanguagePreview: (String) -> Unit,
    onSettingsCancel: () -> Unit,
    onSettingsSave: (Boolean, Float, Int, Int, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
    var me by remember { mutableStateOf(initialMe) }
    var page by remember { mutableStateOf(Page.TRADE) }
    var previousPage by remember { mutableStateOf(Page.TRADE) }
    LaunchedEffect(page) {
        if (previousPage == Page.SETTINGS && page != Page.SETTINGS) onSettingsCancel()
        previousPage = page
    }
    var instruments by remember { mutableStateOf(emptyList<InstrumentDto>()) }
    var symbol by remember { mutableStateOf("THYAO") }
    var ticker by remember { mutableStateOf<TickerDto?>(null) }
    var marketTickers by remember { mutableStateOf(emptyMap<String, TickerDto>()) }
    var technicalAnalysis by remember { mutableStateOf<TechnicalAnalysisDto?>(null) }
    var book by remember { mutableStateOf(OrderBookDto(symbol)) }
    var marketTrades by remember { mutableStateOf(emptyList<TradeDto>()) }
    var portfolio by remember { mutableStateOf<PortfolioDto?>(null) }
    var orders by remember { mutableStateOf(emptyList<OrderDto>()) }
    var fills by remember { mutableStateOf(emptyList<TradeDto>()) }
    var health by remember { mutableStateOf<HealthDto?>(null) }
    var gatewayReachable by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var draftSide by remember { mutableStateOf("BUY") }
    var draftType by remember { mutableStateOf("LIMIT") }
    var draftQuantity by remember { mutableStateOf("1") }
    var draftPrice by remember { mutableStateOf("") }
    val prices = remember { mutableStateListOf<Float>() }
    LaunchedEffect(me.userId, isGuest) {
        if (!isGuest) {
            api.orderUpdatesFlow().collect { update ->
                message = "Emir güncellemesi · ${'$'}{update.symbol} · ${'$'}{update.status}"
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { instruments = api.instruments() }
    }
    LaunchedEffect(symbol) {
        prices.clear()
        launch {
            runCatching {
                api.history(symbol).forEach { it.price.toFloatOrNull()?.takeIf(Float::isFinite)?.let(prices::add) }
            }
        }
        launch {
            api.tickerFlow().collect { update ->
                marketTickers = marketTickers + (update.symbol to update)
                if (update.symbol == symbol) {
                    ticker = update
                    update.price.toFloatOrNull()?.takeIf(Float::isFinite)?.let { point ->
                        prices.add(point)
                        while (prices.size > ChartLimits.HISTORY_POINTS) prices.removeAt(0)
                    }
                }
            }
        }
    }
    LaunchedEffect(symbol, refreshSeconds) {
        technicalAnalysis = null
        launch { api.orderBookFlow(symbol).collect { book = it } }
        while (true) {
            runCatching {
                ticker = api.ticker(symbol)
                ticker?.price?.toFloatOrNull()?.takeIf(Float::isFinite)?.let { point ->
                    prices.add(point)
                    while (prices.size > ChartLimits.HISTORY_POINTS) prices.removeAt(0)
                }
                book = api.orderBook(symbol)
                technicalAnalysis = api.technicalAnalysis(symbol)
            }
            delay(refreshSeconds * 1_000L)
        }
    }
    LaunchedEffect(symbol, marketRefreshSeconds) {
        while (true) {
            runCatching { api.publicTrades(symbol) }.onSuccess { marketTrades = it }
            delay(marketRefreshSeconds * 1_000L)
        }
    }
    LaunchedEffect(refreshSeconds, isGuest) {
        while (true) {
            if (!isGuest) {
                runCatching { portfolio = api.portfolio() }
                runCatching { orders = api.orders() }
                runCatching { fills = api.fills() }
            }
            runCatching {
                health = api.health()
                gatewayReachable = true
            }
                .onFailure { gatewayReachable = false }
            delay(refreshSeconds * 1_000L)
        }
    }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Page.entries.filter { it != Page.WORKSPACE }.forEach { item ->
                NavigationBarItem(
                    selected = page == item || (page == Page.WORKSPACE && item == Page.SETTINGS),
                    onClick = { page = item },
                    icon = {
                        Text(
                            when (item) {
                                Page.TRADE -> "↗"
                                Page.PORTFOLIO -> "◫"
                                Page.ORDERS -> "≡"
                                Page.ASSISTANT -> "✦"
                                Page.WORKSPACE -> "⚙"
                                Page.SETTINGS -> "☷"
                            },
                            fontSize = 20.sp,
                        )
                    },
                    label = { Text(item.localizedTitle(LocalUiLanguage.current), fontSize = 10.sp) },
                )
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Image(
                    painterResource(com.argent.android.R.drawable.ic_launcher_foreground),
                    contentDescription = "ARGENT",
                    modifier = Modifier.size(30.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                Text(
                    if (isGuest) {
                        if (LocalUiLanguage.current == "en") "Guest · Sign in" else "Misafir · Giriş yap"
                    } else {
                        "${me.displayName} · ${me.tenantRole}"
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { if (isGuest) onLogout() },
                )
            }
            val marketTickerText =
                marketTickers.values.sortedBy { it.symbol }
                    .joinToString("     •     ") { "${it.symbol} ${it.price}" }
            if (marketTickerText.isNotBlank()) {
                Text(
                    "$marketTickerText     •     $marketTickerText",
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
                    color = Color(0xFFE0E4E8),
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            val statusLine =
                when {
                    !gatewayReachable -> "GATEWAY'E BAĞLANILAMIYOR · YENİDEN DENENİYOR"
                    health == null -> "GATEWAY DURUMU KONTROL EDİLİYOR"
                    else ->
                        health?.let {
                            "${it.status} · ${it.executionMode} · CRYPTO ${it.cryptoMarketData} · " +
                                "BIST ${it.bistMarketData}"
                        }.orEmpty()
                }
            Text(
                "$statusLine     •     $statusLine",
                color = if (gatewayReachable && health?.status == "UP") Green else Red,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier.fillMaxWidth().padding(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
            )
            message?.let {
                Text(
                    it,
                    color = if (it.contains("hata", true) || it.contains("REJECTED", true)) Red else Gold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            when (page) {
                Page.TRADE ->
                    TradePage(
                        api, instruments, symbol, {
                            symbol = it
                        }, ticker, book, marketTrades, technicalAnalysis, prices, busy,
                        isGuest = isGuest,
                        onSignIn = onLogout,
                        marketRefreshSeconds = marketRefreshSeconds,
                        onMessage = { message = it }, onBusy = { busy = it }, onRefresh = {
                            portfolio = api.portfolio()
                            orders = api.orders()
                        },
                        onDraftChange = {
                                side,
                                type,
                                quantity,
                                price,
                            ->
                            draftSide = side
                            draftType = type
                            draftQuantity = quantity
                            draftPrice = price
                        },
                    )
                Page.PORTFOLIO, Page.ORDERS, Page.ASSISTANT, Page.WORKSPACE ->
                    if (isGuest) {
                        GuestAccessPage(
                            section =
                                when (page) {
                                    Page.PORTFOLIO -> if (uiLanguage == "en") "your portfolio" else "Portföy"
                                    Page.ORDERS ->
                                        if (uiLanguage == "en") "your orders and trades" else "Emirler ve işlemler"
                                    Page.ASSISTANT -> "ARGENT AI"
                                    else -> if (uiLanguage == "en") "workspace settings" else "Çalışma alanı"
                                },
                            onLogin = onLogout,
                        )
                    } else {
                        when (page) {
                            Page.PORTFOLIO -> PortfolioPage(portfolio)
                            Page.ORDERS -> OrdersPage(orders, fills, api, onMessage = { message = it })
                            Page.ASSISTANT ->
                                AssistantScreen(
                                    api,
                                    symbol,
                                    draftSide,
                                    draftType,
                                    draftQuantity,
                                    draftPrice,
                                )
                            else ->
                                WorkspacePage(api, me, {
                                    me = it
                                    onMeChanged(it)
                                }, onMessage = { message = it }, onLogout = onLogout, onBack = { page = Page.SETTINGS })
                        }
                    }
                Page.SETTINGS ->
                    SettingsPage(
                        me,
                        dark,
                        fontScale,
                        refreshSeconds,
                        marketRefreshSeconds,
                        onSettingsPreview,
                        onLanguagePreview,
                        onSettingsSave,
                        onOpenWorkspace = { page = Page.WORKSPACE },
                        onSecureLogout = {
                            if (isGuest) {
                                onLogout()
                            } else {
                                scope.launch {
                                    runCatching {
                                        api.logout()
                                        onLogout()
                                    }
                                        .onFailure {
                                            message =
                                                if (uiLanguage == "en") {
                                                    "Sign out failed: ${it.message}"
                                                } else {
                                                    "Güvenli çıkış başarısız: ${it.message}"
                                                }
                                        }
                                }
                            }
                        },
                    )
            }
        }
    }
}
