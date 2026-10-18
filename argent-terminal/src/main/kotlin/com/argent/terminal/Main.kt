package com.argent.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.painterResource
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
import com.argent.terminal.api.TradeDto
import com.argent.terminal.api.TenantDto
import com.argent.terminal.api.TenantMemberDto
import com.argent.terminal.api.MarketMakerStateDto
import com.argent.terminal.api.BinanceTestnetStatusDto
import com.argent.terminal.api.BinanceTestnetOrderDto
import com.argent.terminal.api.BinanceTestnetOrderRequestDto
import com.argent.terminal.api.BistReplayDto
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale
import java.math.RoundingMode
import java.util.UUID

fun main() = application {
    Window(
        icon = painterResource("argent-icon.png"),
        onCloseRequest = ::exitApplication,
        title = "ARGENT — Multi-Asset Trading Terminal",
        state = rememberWindowState(width = 1560.dp, height = 940.dp)
    ) {
        MaterialTheme(colorScheme = ArgentColors) {
            Surface(modifier = Modifier.fillMaxSize(), color = ArgentBg) {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val api = remember { ArgentApi() }
    var me by remember { mutableStateOf<MeResponse?>(null) }
    if (me == null) {
        LoginPane(api) { me = it }
    } else {
        TradingDesk(api, me!!, onLogout = { me = null }, onMeChanged = { me = it })
    }
}

@Composable
private fun LoginPane(api: ArgentApi, onAuthed: (MeResponse) -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("http://127.0.0.1:8080") }
    var email by remember { mutableStateOf(System.getenv("BOOTSTRAP_ADMIN_EMAIL").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Admin") }
    var invitationToken by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("login") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp)
                .background(ArgentPanel, RoundedCornerShape(14.dp))
                .border(1.dp, ArgentLine, RoundedCornerShape(14.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ARGENT", color = ArgentGold, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("Multi-Asset Trading & Settlement Platform", color = ArgentMuted, fontSize = 13.sp)
            Text("ARGENT gateway hesabınla oturum aç veya çalışma alanı davetini kabul et.", color = ArgentMuted, fontSize = 12.sp)
            field("Gateway", host) { host = it }
            field("E-posta", email) { email = it }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            if (mode != "login") {
                Text("Yeni hesap şifresi en az 12 karakter olmalıdır.", color = ArgentMuted, fontSize = 11.sp)
            }
            if (mode != "login") field("Görünen ad", name) { name = it }
            if (mode == "invitation") field("Davet kodu", invitationToken) { invitationToken = it }
            error?.let { Text(it, color = ArgentRed, fontSize = 12.sp) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    api.baseUrl = host.trimEnd('/')
                    scope.launch {
                        try {
                            when (mode) {
                                "register" -> {
                                    api.register(email, password, name.ifBlank { email.substringBefore("@") })
                                    api.login(email, password)
                                }
                                "invitation" -> api.registerFromInvitation(
                                    email, password, name.ifBlank { email.substringBefore("@") }, invitationToken.trim()
                                )
                                else -> api.login(email, password)
                            }
                            onAuthed(api.me())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message
                        } finally {
                            password = ""
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = Color(0xFF111111))
            ) { Text(when (mode) { "login" -> "Giriş"; "invitation" -> "Daveti kabul et"; else -> "Kayıt ol ve giriş" }, fontWeight = FontWeight.Bold) }
            if (mode != "invitation") TextButton(onClick = { mode = if (mode == "login") "register" else "login" }) {
                Text(if (mode == "login") "Geliştirme kaydı" else "Giriş ekranına dön", color = ArgentBlue)
            }
            TextButton(onClick = { mode = if (mode == "invitation") "login" else "invitation" }) {
                Text(if (mode == "invitation") "Giriş ekranına dön" else "Davet koduyla katıl", color = ArgentBlue)
            }
        }
    }
}

@Composable
private fun TradingDesk(api: ArgentApi, me: MeResponse, onLogout: () -> Unit, onMeChanged: (MeResponse) -> Unit) {
    val scope = rememberCoroutineScope()
    var instruments by remember { mutableStateOf(listOf<InstrumentDto>()) }
    var symbol by remember { mutableStateOf("THYAO") }
    var book by remember { mutableStateOf(OrderBookDto(symbol)) }
    val tickers = remember { mutableStateMapOf<String, TickerDto>() }
    val chart = remember { mutableStateListOf<Float>() }
    var portfolio by remember { mutableStateOf<PortfolioDto?>(null) }
    var orders by remember { mutableStateOf(listOf<OrderDto>()) }
    var fills by remember { mutableStateOf(listOf<TradeDto>()) }
    var blotter by remember { mutableStateOf(listOf<TradeDto>()) }
    var tab by remember { mutableStateOf(0) }
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("10") }
    var flash by remember { mutableStateOf<String?>(null) }
    var health by remember { mutableStateOf("…") }
    var orderSubmitting by remember { mutableStateOf(false) }
    var orderRequestFingerprint by remember { mutableStateOf<String?>(null) }
    var orderRequestId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(Unit) {
        runCatching { instruments = api.instruments() }
        launch {
            api.tickerFlow().collect { t ->
                tickers[t.symbol] = t
                if (t.symbol == symbol) {
                    chart.add(t.price.toFloatOrNull() ?: return@collect)
                    if (chart.size > 220) chart.removeAt(0)
                }
            }
        }
    }
    LaunchedEffect(symbol) {
        chart.clear()
        runCatching { api.history(symbol).forEach { it.price.toFloatOrNull()?.let(chart::add) } }
        launch { api.orderBookFlow(symbol).collect { book = it } }
    }
    LaunchedEffect(book.bestBid, book.bestAsk, side, type) {
        if (type == "MARKET") return@LaunchedEffect
        if (price.isNotBlank()) return@LaunchedEffect
        price = if (side == "BUY") book.bestAsk ?: book.bestBid ?: price else book.bestBid ?: book.bestAsk ?: price
    }
    LaunchedEffect(me) {
        while (true) {
            runCatching { portfolio = api.portfolio() }
            runCatching { orders = api.orders() }
            runCatching { fills = api.fills() }
            runCatching { blotter = api.publicTrades(symbol) }
            runCatching {
                val h = api.health()
                health = if (h.status == "UP") buildString {
                    append("INFRA OK · ")
                    append(if (h.matchingEngine) "ENGINE OK" else "ENGINE PAUSED")
                    append(" · ")
                    append(h.executionMode.replace('_', ' '))
                    append(" · CRYPTO ")
                    append(h.cryptoMarketData)
                    append(" · BIST ")
                    append(h.bistMarketData)
                } else buildString {
                    append("INFRA DEGRADED")
                    if (!h.postgres) append(" · DB")
                    if (!h.redis) append(" · REDIS")
                    if (!h.rabbit) append(" · MQ")
                    if (!h.invariantOk) append(" · LEDGER")
                    if (!h.matchingEngine) append(" · ENGINE PAUSED")
                }
            }.onFailure { health = "GATEWAY'E BAĞLANILAMIYOR · YENİDEN DENENİYOR" }
            delay(1_000)
        }
    }

    Column(Modifier.fillMaxSize().background(ArgentBg)) {
        TopBar(
            me, tickers, health, onLogout, api, symbol, scope,
            onFlash = { flash = it },
            onMeChanged = onMeChanged,
            onWorkspaceClosed = onLogout
        )
        flash?.let {
            Text(
                it,
                color = if (it.contains("REJECTED", true) || it.contains("hata", true)) ArgentRed else ArgentGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Watchlist(instruments, tickers, symbol) { symbol = it; price = "" }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ChartPanel(symbol, tickers[symbol], chart, book)
                OrderBookPanel(book) { clicked -> price = clicked }
            }
            Ticket(
                symbol = symbol,
                instrument = instruments.firstOrNull { it.symbol == symbol },
                ticker = tickers[symbol],
                side = side,
                type = type,
                price = price,
                qty = qty,
                submitting = orderSubmitting,
                onSide = { side = it; price = "" },
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
                            val result = api.placeOrder(symbol, side, type, qty, price.ifBlank { null }, idempotencyKey)
                            flash = "${result.status}  filled=${result.filledQuantity}  ${result.reason}".trim()
                            portfolio = api.portfolio()
                            orders = api.orders()
                            orderRequestFingerprint = null
                            orderRequestId = UUID.randomUUID().toString()
                        } catch (error: Exception) {
                            flash = error.message ?: "Emir gönderilemedi; aynı emir anahtarıyla yeniden deneyebilirsin."
                        } finally {
                            orderSubmitting = false
                        }
                    }
                }
            )
        }
        BottomDock(tab, { tab = it }, portfolio, orders, fills, blotter, tickers, api, me, symbol, scope) { flash = it }
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
    onFlash: (String) -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    onWorkspaceClosed: () -> Unit
) {
    var workspaceOpen by remember { mutableStateOf(false) }
    var lastTestnetOrder by remember { mutableStateOf<BinanceTestnetOrderDto?>(null) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(ArgentPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ARGENT", color = ArgentGold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 18.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(20.dp))
        LazyRow(
            Modifier.weight(1f).heightIn(min = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tickers.values.sortedBy { it.symbol }.take(8), key = { it.symbol }) { t ->
                Text(
                    "${t.symbol} ${compact(t.price)}",
                    color = ArgentSilver,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        val ok = health.startsWith("INFRA OK")
        Box(
            Modifier.widthIn(min = 180.dp, max = 260.dp)
                .background(if (ok) ArgentGreenDim else ArgentRedDim, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "$health     •     $health",
                color = if (ok) ArgentGreen else ArgentRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee()
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${me.displayName} · ${me.role}",
            color = ArgentMuted,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = { workspaceOpen = true }) { Text("Çalışma alanı", color = ArgentGold) }
        TextButton(onClick = {
            scope.launch {
                api.logout()
                onLogout()
            }
        }) { Text("Çıkış", color = ArgentRed) }
    }
    if (workspaceOpen) WorkspaceDialog(
        api = api,
        me = me,
        selectedSymbol = selectedSymbol,
        scope = scope,
        onClose = { workspaceOpen = false },
        onFlash = onFlash,
        onMeChanged = onMeChanged,
        onWorkspaceClosed = onWorkspaceClosed,
        testnetOrder = lastTestnetOrder,
        onTestnetOrderChanged = { lastTestnetOrder = it }
    )
}

@Composable
private fun WorkspaceDialog(
    api: ArgentApi,
    me: MeResponse,
    selectedSymbol: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onClose: () -> Unit,
    onFlash: (String) -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    onWorkspaceClosed: () -> Unit,
    testnetOrder: BinanceTestnetOrderDto?,
    onTestnetOrderChanged: (BinanceTestnetOrderDto?) -> Unit
) {
    var tenants by remember { mutableStateOf(emptyList<TenantDto>()) }
    var members by remember { mutableStateOf(emptyList<TenantMemberDto>()) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("TRADER") }
    var inviteToken by remember { mutableStateOf("") }
    var acceptToken by remember { mutableStateOf("") }
    var acceptingInvitation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var marketMaker by remember { mutableStateOf<MarketMakerStateDto?>(null) }
    var testnet by remember { mutableStateOf<BinanceTestnetStatusDto?>(null) }
    var testnetSubmitting by remember { mutableStateOf(false) }
    var testnetSymbol by remember { mutableStateOf("BTCUSDT") }
    var testnetSide by remember { mutableStateOf("BUY") }
    var testnetType by remember { mutableStateOf("LIMIT") }
    var testnetTif by remember { mutableStateOf("GTC") }
    var testnetQuantity by remember { mutableStateOf("0.001") }
    var testnetPrice by remember { mutableStateOf("30000") }
    var bistCsv by remember { mutableStateOf("") }
    var bistReplaySpeed by remember { mutableStateOf("1000") }
    var bistReplay by remember { mutableStateOf<BistReplayDto?>(null) }
    val canManage = me.tenantRole == "OWNER" || me.tenantRole == "ADMIN"

    LaunchedEffect(me.tenantId) {
        tenants = runCatching { api.tenants() }.getOrDefault(emptyList())
        if (canManage) members = runCatching { api.tenantMembers() }.getOrDefault(emptyList())
        if (me.role == "ADMIN") marketMaker = runCatching { api.marketMakerState() }.getOrNull()
        if (me.role == "ADMIN") testnet = runCatching { api.binanceSpotTestnetStatus() }.getOrNull()
        if (me.role == "ADMIN") bistReplay = runCatching { api.bistHistoricalReplayStatus() }.getOrNull()
    }
    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            try { action() } catch (failure: Exception) {
                error = failure.message ?: "İşlem başarısız"
                onFlash(error!!)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Çalışma alanı", color = ArgentGold) },
        text = {
            Column(
                Modifier.width(600.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Aktif rol: ${me.tenantRole}", color = ArgentMuted, fontSize = 12.sp)
                if (me.role == "ADMIN") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            marketMaker?.let { "Market maker ${if (it.enabled) "AÇIK" else "KAPALI"} · emir üst sınırı ${it.quoteNotionalCap} · stok sınırı ${it.inventoryNotionalCap}" }
                                ?: "Market maker durumu kullanılamıyor",
                            Modifier.weight(1f), color = ArgentMuted, fontSize = 11.sp
                        )
                        if (marketMaker != null) TextButton(onClick = {
                            runAction {
                                if (marketMaker!!.enabled) api.killMarketMaker() else api.resumeMarketMaker()
                                marketMaker = api.marketMakerState()
                            }
                        }) { Text(if (marketMaker!!.enabled) "KILL SWITCH" else "Yeniden başlat", color = if (marketMaker!!.enabled) ArgentRed else ArgentGreen) }
                    }
                    Text("Binance Spot Testnet · gerçek para yok · gerçekleşen emirler ARGENT sanal bakiyesine yansır", color = ArgentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Emir gönderilince ARGENT varlığı geçici rezerve edilir. Testnet gerçekleşmeleri ve komisyonları bakiyeye yazılır; iptal veya tamamlanmada kullanılmayan rezerv serbest kalır.", color = ArgentMuted, fontSize = 11.sp)
                    Text(
                        when (testnet?.configured) {
                            true -> "Testnet anahtarları yerel gateway ortamında yapılandırılmış. Emirler yalnızca Binance test ağına gönderilir."
                            false -> "Testnet kapalı. Yerel .env dosyasına yalnızca Binance Spot Testnet anahtarlarını ekleyip gateway'i yeniden başlatın."
                            null -> "Testnet ayarı okunamadı."
                        }, color = ArgentMuted, fontSize = 11.sp
                    )
                    if (testnet?.configured == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(testnetSymbol, { testnetSymbol = it.uppercase() }, label = { Text("Sembol") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            TextButton(onClick = { testnetSide = if (testnetSide == "BUY") "SELL" else "BUY" }) { Text(testnetSide, color = if (testnetSide == "BUY") ArgentGreen else ArgentRed) }
                            TextButton(onClick = { testnetType = if (testnetType == "LIMIT") "MARKET" else "LIMIT" }) { Text(testnetType, color = ArgentGold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(testnetQuantity, { testnetQuantity = it }, label = { Text("Miktar") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            if (testnetType == "LIMIT") OutlinedTextField(testnetPrice, { testnetPrice = it }, label = { Text("Fiyat") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            if (testnetType == "LIMIT") TextButton(onClick = { testnetTif = when (testnetTif) { "GTC" -> "IOC"; "IOC" -> "FOK"; else -> "GTC" } }) { Text(testnetTif, color = ArgentGold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(enabled = !testnetSubmitting, onClick = {
                                testnetSubmitting = true
                                runAction {
                                    try {
                                        val result = api.placeBinanceSpotTestnetOrder(BinanceTestnetOrderRequestDto(
                                            symbol = testnetSymbol.trim(), side = testnetSide, type = testnetType,
                                            quantity = testnetQuantity.trim(), price = testnetPrice.takeIf { testnetType == "LIMIT" }?.trim(),
                                            timeInForce = if (testnetType == "LIMIT") testnetTif else "GTC",
                                            clientOrderId = "ag-${java.util.UUID.randomUUID().toString().replace("-", "")}"
                                        ))
                                        onTestnetOrderChanged(result)
                                        onFlash("Testnet ${result.status} · ${if (result.ledgerSynced) "fill ARGENT defterine işlendi" else "ARGENT defterinde inceleme gerekiyor"} · #${result.orderId}")
                                    } finally {
                                        testnetSubmitting = false
                                    }
                                }
                            }) { Text(if (testnetSubmitting) "GÖNDERİLİYOR…" else "TESTNET EMRİ GÖNDER") }
                            testnetOrder?.let { order ->
                                TextButton(onClick = { runAction { onTestnetOrderChanged(api.binanceSpotTestnetOrder(order.symbol, order.orderId)) } }) { Text("Durumu sorgula", color = ArgentBlue) }
                                if (order.status in setOf("NEW", "PARTIALLY_FILLED")) TextButton(onClick = {
                                    runAction { onTestnetOrderChanged(api.cancelBinanceSpotTestnetOrder(order.symbol, order.orderId)) }
                                }) { Text("Test emrini iptal et", color = ArgentRed) }
                            }
                        }
                        testnetOrder?.let { order ->
                            Text(
                                "${order.symbol} ${order.side} · ${order.status} · ${order.executedQty}/${order.origQty} · ${if (order.ledgerSynced) "fill ARGENT defterine işlendi" else "ARGENT defterinde inceleme gerekiyor"} · testnet #${order.orderId}",
                                color = if (order.ledgerSynced) ArgentSilver else ArgentRed,
                                fontSize = 11.sp
                            )
                        }
                        error?.let { Text("Testnet sonucu: $it", color = ArgentRed, fontSize = 11.sp) }
                    }
                    Text("BIST: fiyat akışı simüledir. Yalnızca kullanım hakkınız olan CSV verisini yönetici replay API'siyle içe aktarın; gerçek zamanlı BIST verisi veya BIST emri değildir.", color = ArgentMuted, fontSize = 10.sp)
                    OutlinedTextField(
                        value = bistCsv,
                        onValueChange = { if (it.length <= 2 * 1024 * 1024) bistCsv = it },
                        label = { Text("BIST tarihsel CSV (timestamp,symbol,price[,volume24h])") },
                        placeholder = { Text("2026-01-02T10:00:00Z,THYAO,300.25,1000") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, colors = fieldColors()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bistReplaySpeed,
                            onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) bistReplaySpeed = it },
                            label = { Text("Hız çarpanı") }, modifier = Modifier.width(150.dp), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors()
                        )
                        Button(enabled = bistCsv.isNotBlank(), onClick = {
                            runAction {
                                bistReplay = api.startBistHistoricalReplay(bistCsv, bistReplaySpeed.toIntOrNull() ?: 1000)
                                bistCsv = ""
                            }
                        }) { Text("BIST CSV replay başlat") }
                        TextButton(onClick = { runAction { bistReplay = api.bistHistoricalReplayStatus() } }) { Text("Durumu yenile", color = ArgentBlue) }
                    }
                    bistReplay?.let { replay ->
                        Text("Replay ${replay.state} · ${replay.processed}/${replay.rows} satır${replay.error?.let { " · $it" } ?: ""}", color = if (replay.state == "FAILED") ArgentRed else ArgentMuted, fontSize = 11.sp)
                    }
                }
                tenants.forEach { tenant ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tenant.tenantName} · ${tenant.role}", Modifier.weight(1f), color = ArgentSilver)
                        if (tenant.tenantId != me.tenantId) TextButton(onClick = {
                            runAction { api.switchTenant(tenant.tenantId); onMeChanged(api.me()) }
                        }) { Text("Geçiş yap", color = ArgentBlue) }
                    }
                }
                if (canManage) {
                    Text("Üyeler", color = ArgentGold, fontWeight = FontWeight.Bold)
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${member.displayName} · ${member.email} · ${member.role}", Modifier.weight(1f), color = ArgentSilver, fontSize = 11.sp)
                            val mayManageMember = member.role != "OWNER" && member.userId != me.userId &&
                                (me.tenantRole == "OWNER" || member.role != "ADMIN")
                            if (mayManageMember) {
                                TextButton(onClick = {
                                    val nextRole = when (member.role) {
                                        "VIEWER" -> "TRADER"
                                        "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"
                                        else -> "VIEWER"
                                    }
                                    runAction {
                                        api.updateTenantMemberRole(member.userId, nextRole)
                                        members = api.tenantMembers()
                                    }
                                }) { Text("Rolü değiştir", color = ArgentBlue) }
                                TextButton(onClick = {
                                    runAction {
                                        api.removeTenantMember(member.userId)
                                        members = api.tenantMembers()
                                    }
                                }) { Text("Çıkar", color = ArgentRed) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(inviteEmail, { inviteEmail = it }, label = { Text("Davet e-postası") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                        TextButton(onClick = {
                            inviteRole = when (inviteRole) {
                                "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"
                                "VIEWER" -> "TRADER"
                                else -> "VIEWER"
                            }
                        }) { Text(inviteRole, color = ArgentGold) }
                        Button(onClick = {
                            runAction {
                                inviteToken = api.inviteTenantMember(inviteEmail, inviteRole).token
                                inviteEmail = ""
                            }
                        }) { Text("Davet oluştur") }
                    }
                    if (inviteToken.isNotBlank()) Text("Davet kodu (tek sefer gösterilir): $inviteToken", color = ArgentGold, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(acceptToken, { acceptToken = it }, label = { Text("Mevcut kullanıcı için davet kodu") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                    Button(enabled = !acceptingInvitation, onClick = {
                        acceptingInvitation = true
                        runAction {
                            try {
                                val membership = api.acceptTenantInvitation(acceptToken.trim())
                                acceptToken = ""
                                tenants = api.tenants()
                                onFlash("Çalışma alanına katıldın · ${membership.role}")
                            } finally {
                                acceptingInvitation = false
                            }
                        }
                    }) { Text(if (acceptingInvitation) "Kabul ediliyor…" else "Kabul et") }
                }
                error?.let { Text(it, color = ArgentRed, fontSize = 12.sp) }
                if (me.tenantRole == "OWNER") TextButton(onClick = { closing = true }) {
                    Text("Bu çalışma alanını kapat", color = ArgentRed)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } }
    )
    if (closing) AlertDialog(
        onDismissRequest = { closing = false },
        title = { Text("Çalışma alanı kapatılsın mı?") },
        text = { Text("Bu işlem oturumları sonlandırır. Diğer üyeler önce çıkarılmalı; açık emirler kapatma sırasında iptal edilir.") },
        confirmButton = {
            TextButton(onClick = {
                closing = false
                runAction { api.closeWorkspace(); onWorkspaceClosed() }
            }) { Text("Kapat", color = ArgentRed) }
        },
        dismissButton = { TextButton(onClick = { closing = false }) { Text("Vazgeç") } }
    )
}

@Composable
private fun Watchlist(
    instruments: List<InstrumentDto>,
    tickers: Map<String, TickerDto>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (instruments.isEmpty()) {
        Column(Modifier.fillMaxSize().background(ArgentPanel).padding(10.dp)) {
            Text("PİYASALAR", color = ArgentMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Enstrüman verisi bekleniyor veya gateway'e ulaşılamıyor.", color = ArgentMuted, fontSize = 11.sp)
        }
        return
    }
    Column(Modifier.width(228.dp).fillMaxHeight().background(ArgentPanel).padding(10.dp)) {
        Text("PİYASALAR", color = ArgentMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(instruments) { ins ->
                val px = tickers[ins.symbol]?.price ?: "—"
                val bg = if (ins.symbol == selected) ArgentPanelAlt else Color.Transparent
                Row(
                    Modifier.fillMaxWidth().background(bg, RoundedCornerShape(6.dp)).clickable { onSelect(ins.symbol) }.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(ins.symbol, color = ArgentSilver, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(ins.assetClass, color = ArgentMuted, fontSize = 10.sp)
                    }
                    Text(compact(px), color = ArgentGold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ChartPanel(symbol: String, ticker: TickerDto?, points: List<Float>, book: OrderBookDto) {
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
    }
}

@Composable
private fun OrderBookPanel(book: OrderBookDto, onPrice: (String) -> Unit) {
    val maxQty = (book.bids + book.asks).maxOfOrNull { it.quantity.toFloatOrNull() ?: 0f } ?: 1f
    Row(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
        BookSide("ALIŞ", book.bids, ArgentGreen, ArgentGreenDim, maxQty, Modifier.weight(1f), onPrice)
        Column(
            Modifier.width(88.dp).fillMaxHeight().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SPREAD", color = ArgentMuted, fontSize = 10.sp)
            Text(spread(book), color = ArgentGold, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        BookSide("SATIŞ", book.asks, ArgentRed, ArgentRedDim, maxQty, Modifier.weight(1f), onPrice)
    }
}

@Composable
private fun BookSide(
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
        Row(Modifier.fillMaxWidth()) {
            Text("Fiyat", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text("Miktar", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
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
private fun Ticket(
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
        type != "MARKET" && tickSize != null && priceValue!!.remainder(tickSize).signum() != 0 -> "Fiyat tick adımına uygun değil (${instrument.tickSize})."
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
private fun BottomDock(
    tab: Int,
    onTab: (Int) -> Unit,
    portfolio: PortfolioDto?,
    orders: List<OrderDto>,
    fills: List<TradeDto>,
    blotter: List<TradeDto>,
    tickers: Map<String, TickerDto>,
    api: ArgentApi,
    me: MeResponse,
    selectedSymbol: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onFlash: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().height(if (tab == 4) 420.dp else 268.dp).background(ArgentPanelAlt)) {
        TabRow(selectedTabIndex = tab, containerColor = ArgentPanel, contentColor = ArgentGold) {
            listOf("Portföy", "Açık emirler", "İşlemlerim", "Piyasa işleri", "ARGENT AI").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(t, fontSize = 12.sp) })
            }
        }
        when (tab) {
            0 -> PortfolioTab(portfolio, tickers)
            1 -> OrdersTab(orders, api, scope, onFlash)
            2 -> TradesTab(fills, orders)
            3 -> TradesTab(blotter)
            else -> AssistantTab(api, selectedSymbol, scope)
        }
    }
}

private data class AssistantUiMessage(val fromUser: Boolean, val text: String)

private sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock
    data class Paragraph(val text: String) : AssistantBlock
    data class Bullet(val text: String) : AssistantBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
private fun AssistantTab(api: ArgentApi, symbol: String, scope: kotlinx.coroutines.CoroutineScope) {
    val messages = remember { androidx.compose.runtime.mutableStateListOf<AssistantUiMessage>() }
    val listState = rememberLazyListState()
    var prompt by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { enabled = api.assistantStatus().enabled } }
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ARGENT AI", color = ArgentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Portföy · emirler · $symbol", color = ArgentMuted, fontSize = 10.sp)
            }
            Surface(color = ArgentGreenDim, shape = RoundedCornerShape(50)) {
                Text("SALT OKUNUR", color = ArgentGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        Text(
            when (enabled) {
                true -> "ARGENT verileriyle yanıtlar · Emir veya bakiye değişikliği yapamaz"
                false -> "Asistan kapalı · Gateway AI anahtarını kontrol et"
                null -> "Asistan bağlantısı kontrol ediliyor…"
            }, color = ArgentMuted, fontSize = 10.sp
        )
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Nasıl yardımcı olabilirim?", color = ArgentSilver, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Hesap verilerin veya seçili piyasa hakkında sor.", color = ArgentMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
                listOf("Bugün portföyüm nasıl?", "Bekleyen emirlerimi açıkla", "$symbol piyasa özeti").forEach { suggestion ->
                    Surface(color = ArgentPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { prompt = suggestion }) {
                        Text(suggestion, color = ArgentSilver, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 3.dp)
            ) {
                if (busy) item { Text("ARGENT AI yanıt hazırlıyor…", color = ArgentMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp, top = 3.dp)) }
                items(messages.size) { index -> AssistantMessageBubble(messages[messages.lastIndex - index]) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, enabled = !busy,
                placeholder = { Text("ARGENT'e sor…") }, minLines = 1, maxLines = 3,
                modifier = Modifier.weight(1f), colors = fieldColors()
            )
            Button(enabled = !busy && prompt.isNotBlank() && enabled != false, onClick = {
                val question = prompt.trim(); prompt = ""; messages.add(AssistantUiMessage(true, question)); busy = true
                scope.launch {
                    try {
                        runCatching { api.askAssistant(question, symbol) }
                            .onSuccess { result -> messages.add(AssistantUiMessage(false, result.answer)) }
                            .onFailure { messages.add(AssistantUiMessage(false, it.message ?: "Asistan yanıt veremedi.")) }
                    } finally { busy = false }
                }
            }, modifier = Modifier.height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = ArgentBg)) {
                Text(if (busy) "…" else "Gönder")
            }
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: AssistantUiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 1050.dp).fillMaxWidth(if (message.fromUser) 0.72f else 0.96f),
            color = if (message.fromUser) ArgentGoldDim else ArgentPanel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (message.fromUser) "SEN" else "ARGENT AI", color = if (message.fromUser) ArgentGold else ArgentGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                AssistantFormattedAnswer(message.text)
            }
        }
    }
}

@Composable
private fun AssistantFormattedAnswer(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        assistantBlocks(markdown).forEach { block ->
            when (block) {
                is AssistantBlock.Heading -> Text(block.text, color = ArgentGold, fontSize = if (block.level <= 2) 12.sp else 11.sp, fontWeight = FontWeight.Bold)
                is AssistantBlock.Paragraph -> Text(block.text, color = ArgentSilver, fontSize = 11.sp, lineHeight = 16.sp)
                is AssistantBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = ArgentGold, fontWeight = FontWeight.Bold)
                    Text(block.text, color = ArgentSilver, fontSize = 11.sp, lineHeight = 16.sp)
                }
                is AssistantBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.rows.forEach { row ->
                        Surface(color = ArgentPanelAlt, shape = RoundedCornerShape(7.dp)) {
                            Column(Modifier.fillMaxWidth().padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                block.headers.forEachIndexed { index, heading ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        Text(heading, color = ArgentMuted, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.weight(1.15f))
                                        Text(row.getOrElse(index) { "—" }, color = ArgentSilver, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun assistantBlocks(markdown: String): List<AssistantBlock> {
    val lines = markdown.lines().map { it.trim().replace(Regex("^(>\\s*)+"), "").trim() }
    val result = mutableListOf<AssistantBlock>()
    var index = 0
    fun clean(value: String) = value.replace("**", "").replace("__", "").replace("`", "").trim()
    fun isTableRow(value: String) = value.trim().startsWith("|") && value.trim().endsWith("|")
    fun cells(value: String) = value.trim().trim('|').split('|').map(::clean)
    while (index < lines.size) {
        val line = lines[index].trim()
        if (line.isBlank()) { index++; continue }
        if (isTableRow(line)) {
            val tableLines = mutableListOf<String>()
            while (index < lines.size && isTableRow(lines[index])) tableLines += lines[index++]
            val rows = tableLines.map(::cells).filterNot { row -> row.all { it.matches(Regex(":?-{2,}:?")) } }
            if (rows.size > 1) result += AssistantBlock.Table(rows.first(), rows.drop(1))
            else rows.firstOrNull()?.forEach { result += AssistantBlock.Paragraph(it) }
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
        if (heading != null) { result += AssistantBlock.Heading(clean(heading.groupValues[2]), heading.groupValues[1].length); index++; continue }
        val bullet = Regex("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$").matchEntire(line)
        if (bullet != null) { result += AssistantBlock.Bullet(clean(bullet.groupValues[1])); index++; continue }
        val paragraph = mutableListOf(clean(line))
        index++
        while (index < lines.size && lines[index].isNotBlank() && !isTableRow(lines[index].trim()) &&
            !Regex("^(?:#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+)").containsMatchIn(lines[index].trim())) {
            paragraph += clean(lines[index]); index++
        }
        result += AssistantBlock.Paragraph(paragraph.joinToString(" "))
    }
    return result
}

@Composable
private fun PortfolioTab(
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
private fun OrdersTab(
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
                            "${o.symbol} ${o.side} ${o.type} ${compact(o.quantity)}@${o.price ?: "MKT"}  ${orderStatusLabel(o.status)}  filled ${compact(o.filledQuantity)}",
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
private fun TradesTab(trades: List<TradeDto>, orders: List<OrderDto> = emptyList()) {
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
                "${o.symbol} ${o.side} ${o.type} · ${orderStatusLabel(o.status)} · dolan ${compact(o.filledQuantity)}/${compact(o.quantity)} · kalan ${compact(o.remainingQuantity)} · ${o.price ?: "MKT"}",
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

private fun orderStatusLabel(status: String): String = when (status.uppercase()) {
    "ACCEPTED", "NEW", "PENDING" -> "AÇIK"
    "PARTIALLY_FILLED" -> "KISMEN DOLDU"
    "FILLED" -> "DOLDU"
    "CANCELLED", "CANCELED" -> "İPTAL"
    "REJECTED" -> "REDDEDİLDİ"
    "EXPIRED" -> "SÜRESİ DOLDU"
    else -> status
}

@Composable
private fun Metric(label: String, value: String, negative: Boolean = false) {
    Column {
        Text(label, color = ArgentMuted, fontSize = 10.sp)
        Text(compact(value), color = if (negative) ArgentRed else ArgentGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) color.copy(alpha = 0.25f) else ArgentPanelAlt, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) color else ArgentLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(text, color = if (selected) color else ArgentMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ArgentSilver,
    unfocusedTextColor = ArgentSilver,
    focusedBorderColor = ArgentGold,
    unfocusedBorderColor = ArgentLine,
    focusedLabelColor = ArgentGold,
    unfocusedLabelColor = ArgentMuted
)

private fun compact(raw: String): String = runCatching {
    BigDecimal(raw).stripTrailingZeros().toPlainString()
}.getOrElse { raw }

private fun spread(book: OrderBookDto): String {
    val bid = book.bestBid?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    val ask = book.bestAsk?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: return "—"
    return (ask - bid).stripTrailingZeros().toPlainString()
}
