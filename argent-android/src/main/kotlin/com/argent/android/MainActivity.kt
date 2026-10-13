package com.argent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.api.ArgentApi
import com.argent.terminal.api.HealthDto
import com.argent.terminal.api.InstrumentDto
import com.argent.terminal.api.MeResponse
import com.argent.terminal.api.OrderBookDto
import com.argent.terminal.api.OrderDto
import com.argent.terminal.api.PortfolioDto
import com.argent.terminal.api.TenantDto
import com.argent.terminal.api.TenantMemberDto
import com.argent.terminal.api.TickerDto
import com.argent.terminal.api.TradeDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private val Ink = Color(0xFF0B0E12)
private val Panel = Color(0xFF151A21)
private val Line = Color(0xFF29313C)
private val Gold = Color(0xFFE0B82F)
private val Green = Color(0xFF00C98A)
private val Red = Color(0xFFFF5065)
private val Muted = Color(0xFF98A3B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, background = Ink, surface = Panel, onBackground = Color(0xFFE5E8ED),
                onSurface = Color(0xFFE5E8ED), error = Red
            )) { ArgentMobile() }
        }
    }
}

@Composable
private fun ArgentMobile() {
    val api = remember { ArgentApi() }
    var me by remember { mutableStateOf<MeResponse?>(null) }
    if (me == null) LoginScreen(api) { me = it }
    else MainScreen(api, me!!, onMeChanged = { me = it }, onLogout = { me = null })
}

@Composable
private fun LoginScreen(api: ArgentApi, onLogin: (MeResponse) -> Unit) {
    val scope = rememberCoroutineScope()
    var gateway by remember { mutableStateOf("http://10.0.2.2:8080") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("login") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Ink).padding(20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ARGENT", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Multi-Asset Trading Terminal · Android", color = Muted)
            Text("Emirler dahili sandbox eşleştirmesinde işlenir.", color = Muted, fontSize = 12.sp)
            Field("Gateway URL", gateway, { gateway = it }, KeyboardType.Uri)
            Text("Emülatör için 10.0.2.2; fiziksel telefon için bilgisayarın yerel ağ IP adresini kullan.", color = Muted, fontSize = 11.sp)
            Field("E-posta", email, { email = it }, KeyboardType.Email)
            Field("Şifre", password, { password = it }, secret = true)
            if (mode != "login") Text("Yeni hesap şifresi en az 12 karakter olmalıdır.", color = Muted, fontSize = 11.sp)
            if (mode != "login") Field("Görünen ad", name, { name = it })
            if (mode == "invite") Field("Davet kodu", invite, { invite = it })
            error?.let { Text(it, color = Red, fontSize = 13.sp) }
            Button(enabled = !busy, onClick = {
                busy = true; error = null; api.baseUrl = gateway.trimEnd('/')
                scope.launch {
                    try {
                        when (mode) {
                            "register" -> { api.register(email, password, name.ifBlank { email.substringBefore('@') }); api.login(email, password) }
                            "invite" -> api.registerFromInvitation(email, password, name.ifBlank { email.substringBefore('@') }, invite.trim())
                            else -> api.login(email, password)
                        }
                        onLogin(api.me())
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Bağlantı kurulamadı." }
                    finally { busy = false; password = "" }
                }
            }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Ink) else Text(when (mode) { "invite" -> "Daveti kabul et"; "register" -> "Kayıt ol"; else -> "Giriş" }, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { mode = if (mode == "register") "login" else "register" }) { Text(if (mode == "register") "Giriş" else "Kayıt ol") }
                TextButton(onClick = { mode = if (mode == "invite") "login" else "invite" }) { Text(if (mode == "invite") "Giriş" else "Davet kodu") }
            }
        }
    }
}

private enum class Page(val title: String) { TRADE("Piyasa"), PORTFOLIO("Portföy"), ORDERS("Emirler"), WORKSPACE("Çalışma alanı") }

@Composable
private fun MainScreen(api: ArgentApi, initialMe: MeResponse, onMeChanged: (MeResponse) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf(initialMe) }
    var page by remember { mutableStateOf(Page.TRADE) }
    var instruments by remember { mutableStateOf(emptyList<InstrumentDto>()) }
    var symbol by remember { mutableStateOf("THYAO") }
    var ticker by remember { mutableStateOf<TickerDto?>(null) }
    var book by remember { mutableStateOf(OrderBookDto(symbol)) }
    var portfolio by remember { mutableStateOf<PortfolioDto?>(null) }
    var orders by remember { mutableStateOf(emptyList<OrderDto>()) }
    var fills by remember { mutableStateOf(emptyList<TradeDto>()) }
    var trades by remember { mutableStateOf(emptyList<TradeDto>()) }
    var health by remember { mutableStateOf<HealthDto?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val prices = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        runCatching { instruments = api.instruments() }
        launch { api.tickerFlow().collect { t -> if (t.symbol == symbol) { ticker = t; t.price.toFloatOrNull()?.let { prices.add(it); if (prices.size > 120) prices.removeAt(0) } } } }
    }
    LaunchedEffect(symbol) {
        prices.clear()
        runCatching { api.history(symbol).forEach { it.price.toFloatOrNull()?.let(prices::add) } }
        launch { api.orderBookFlow(symbol).collect { book = it } }
        while (true) {
            runCatching { ticker = api.ticker(symbol); book = api.orderBook(symbol); trades = api.publicTrades(symbol) }
            delay(3_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { portfolio = api.portfolio(); orders = api.orders(); fills = api.fills(); health = api.health() }
            delay(4_000)
        }
    }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Page.entries.forEach { item ->
                NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Text(when (item) { Page.TRADE -> "↗"; Page.PORTFOLIO -> "◫"; Page.ORDERS -> "≡"; Page.WORKSPACE -> "⚙" }, fontSize = 20.sp) }, label = { Text(item.title, fontSize = 10.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ARGENT", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("${me.displayName} · ${me.tenantRole}", color = Muted, fontSize = 11.sp)
            }
            health?.let { Text("${it.status} · ${it.executionMode} · CRYPTO ${it.cryptoMarketData} · BIST ${it.bistMarketData}", color = if (it.status == "UP") Green else Red, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
            message?.let { Text(it, color = if (it.contains("hata", true) || it.contains("REJECTED", true)) Red else Gold, modifier = Modifier.padding(horizontal = 12.dp)) }
            when (page) {
                Page.TRADE -> TradePage(api, instruments, symbol, { symbol = it }, ticker, book, prices, trades, busy,
                    onMessage = { message = it }, onBusy = { busy = it }, onRefresh = { portfolio = api.portfolio(); orders = api.orders() })
                Page.PORTFOLIO -> PortfolioPage(portfolio)
                Page.ORDERS -> OrdersPage(orders, fills, api, onMessage = { message = it })
                Page.WORKSPACE -> WorkspacePage(api, me, { me = it; onMeChanged(it) }, onMessage = { message = it }, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun TradePage(api: ArgentApi, instruments: List<InstrumentDto>, symbol: String, onSymbol: (String) -> Unit,
                      ticker: TickerDto?, book: OrderBookDto, prices: List<Float>, trades: List<TradeDto>, busy: Boolean,
                      onMessage: (String) -> Unit, onBusy: (Boolean) -> Unit, onRefresh: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                instruments.forEach { instrument -> Choice(instrument.symbol, symbol == instrument.symbol) { onSymbol(instrument.symbol); price = "" } }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp)); Text(ticker?.price ?: "—", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f)); Text(ticker?.source ?: "SANDBOX", color = Muted, fontSize = 9.sp)
                    }
                    Text("Bid ${ticker?.bid ?: book.bestBid ?: "—"}   Ask ${ticker?.ask ?: book.bestAsk ?: "—"}", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    PriceChart(prices, Modifier.fillMaxWidth().height(150.dp))
                }
            }
        }
        item { OrderBookCard(book) { price = it } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SON PİYASA İŞLEMLERİ", color = Gold, fontWeight = FontWeight.Bold)
                    if (trades.isEmpty()) Text("Bu sembolde henüz işlem yok", color = Muted, fontSize = 11.sp)
                    trades.take(8).forEach { trade -> Text("${trade.price}  ·  ${trade.quantity}", color = Muted, fontSize = 11.sp) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("EMİR", fontWeight = FontWeight.Bold, color = Gold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("AL", side == "BUY") { side = "BUY" }; Choice("SAT", side == "SELL") { side = "SELL" } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { Choice(it, type == it) { type = it } } }
                    if (type != "MARKET") Field("Fiyat", price, { price = it }, KeyboardType.Decimal)
                    Field("Miktar", quantity, { quantity = it }, KeyboardType.Decimal)
                    Text("Tahmini nominal: ${runCatching { (price.ifBlank { ticker?.price ?: "0" }.replace(',', '.')).toBigDecimal().multiply(quantity.replace(',', '.').toBigDecimal()) }.getOrNull() ?: "—"}", color = Muted, fontSize = 11.sp)
                    Button(enabled = !busy, onClick = {
                        onBusy(true)
                        scope.launch {
                            try {
                                val normalizedQuantity = quantity.trim().replace(',', '.')
                                val normalizedPrice = price.trim().replace(',', '.').ifBlank { null }
                                require(normalizedQuantity.toBigDecimal() > java.math.BigDecimal.ZERO) { "Miktar sıfırdan büyük olmalı." }
                                if (type != "MARKET") require(!normalizedPrice.isNullOrBlank() && normalizedPrice.toBigDecimal() > java.math.BigDecimal.ZERO) { "Fiyat sıfırdan büyük olmalı." }
                                val result = api.placeOrder(symbol, side, type, normalizedQuantity, normalizedPrice, UUID.randomUUID().toString())
                                onMessage("${result.status} · dolan ${result.filledQuantity} · ${result.reason}")
                                onRefresh()
                            } catch (e: Exception) { onMessage("Emir hatası: ${e.message}") }
                            finally { onBusy(false) }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (side == "BUY") Green else Red, contentColor = Ink)) {
                        Text(if (busy) "İŞLENİYOR…" else if (side == "BUY") "ALIŞ EMRİ GÖNDER" else "SATIŞ EMRİ GÖNDER", fontWeight = FontWeight.Bold)
                    }
                    Text("Gerçek borsaya iletilmez; dahili eşleştirme sandbox'ında çalışır.", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun OrderBookCard(book: OrderBookDto, onPrice: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${book.symbol} · EMİR DEFTERİ", color = Gold, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("ALIŞ", color = Green); Text("SATIŞ", color = Red) }
            val bids = book.bids.take(5).reversed()
            val asks = book.asks.take(5)
            (0 until maxOf(bids.size, asks.size)).forEach { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bid = bids.getOrNull(i); val ask = asks.getOrNull(i)
                    Text(bid?.let { "${it.price}  ×  ${it.quantity}" } ?: "", color = Green, modifier = Modifier.weight(1f).clickable { bid?.price?.let(onPrice) }, fontSize = 11.sp)
                    Text(ask?.let { "${it.price}  ×  ${it.quantity}" } ?: "", color = Red, modifier = Modifier.weight(1f).clickable { ask?.price?.let(onPrice) }, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PortfolioPage(p: PortfolioDto?) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Risk skoru", p?.riskScore?.toString() ?: "—", "Spot · kaldıraç/marjin kapalı") }
        item { MetricCard("Özkaynak", p?.equity ?: "—", "Gerçekleşen PnL ${p?.realizedPnl ?: "—"} · Açık PnL ${p?.unrealizedPnl ?: "—"}") }
        item { Text("BAKİYELER", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.balances.orEmpty()) { b -> DataRow(b.asset, "${b.available} kullanılabilir · ${b.held} blokeli") }
        item { Text("POZİSYONLAR", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.positions.orEmpty()) { pos -> DataRow(pos.symbol, "${pos.quantity} · ort. ${pos.avgEntryPrice} · PnL ${pos.realizedPnl}") }
        p?.valuations.orEmpty().forEach { v -> item { DataRow("${v.quoteAsset} değerlemesi", "Özkaynak ${v.equity} · maruziyet ${v.grossExposure} · PnL ${if (v.pnlComplete) "tam" else "kısmi"}") } }
    }
}

@Composable
private fun OrdersPage(orders: List<OrderDto>, fills: List<TradeDto>, api: ArgentApi, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("AÇIK EMİRLER", tab == 0) { tab = 0 }; Choice("İŞLEMLER", tab == 1) { tab = 1 } }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) items(orders) { order ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${order.symbol} ${order.side} ${order.type}", fontWeight = FontWeight.Bold); Text("${order.status} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "piyasa"}", color = Muted, fontSize = 11.sp) }
                        if (order.status in setOf("NEW", "PARTIALLY_FILLED", "OPEN")) TextButton(onClick = { scope.launch { runCatching { api.cancel(order.id, order.symbol); onMessage("${order.symbol} emri iptal edildi") }.onFailure { onMessage("İptal hatası: ${it.message}") } } }) { Text("İPTAL", color = Red) }
                    }
                }
            }
            else items(fills) { trade -> DataRow(trade.symbol, "${trade.quantity} @ ${trade.price}") }
            if (tab == 0 && orders.isEmpty()) item { Text("Açık emir yok", color = Muted) }
            if (tab == 1 && fills.isEmpty()) item { Text("İşlem yok", color = Muted) }
        }
    }
}

@Composable
private fun WorkspacePage(api: ArgentApi, me: MeResponse, onMeChanged: (MeResponse) -> Unit, onMessage: (String) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf(emptyList<TenantMemberDto>()) }
    var tenants by remember { mutableStateOf(emptyList<TenantDto>()) }
    var email by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("TRADER") }
    var inviteCode by remember { mutableStateOf("") }
    var makerEnabled by remember { mutableStateOf<Boolean?>(null) }
    var inviteResult by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { members = api.tenantMembers(); tenants = api.tenants() }
        runCatching { makerEnabled = api.marketMakerState().enabled }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Aktif rol", "${me.tenantRole} · ${me.email}", tenants.firstOrNull()?.tenantName ?: "Çalışma alanı") }
        item { Text("ÇALIŞMA ALANLARI", color = Gold, fontWeight = FontWeight.Bold) }
        items(tenants) { tenant ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(tenant.tenantName, fontWeight = FontWeight.Bold); Text(tenant.role, color = Muted, fontSize = 11.sp) }
                    TextButton(onClick = { scope.launch { runCatching { api.switchTenant(tenant.tenantId); onMeChanged(api.me()) }.onFailure { onMessage("Geçiş başarısız: ${it.message}") } } }) { Text("GEÇ") }
                }
            }
        }
        if (me.tenantRole in setOf("OWNER", "ADMIN")) {
            item { Text("ÜYE YÖNETİMİ", color = Gold, fontWeight = FontWeight.Bold) }
            items(members) { member ->
                val mayManage = member.role != "OWNER" && member.userId != me.userId && (me.tenantRole == "OWNER" || member.role != "ADMIN")
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(member.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${member.email} · ${member.role}", color = Muted, fontSize = 11.sp)
                        if (mayManage) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val role = when (member.role) { "VIEWER" -> "TRADER"; "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"; else -> "VIEWER" }
                                scope.launch { runCatching { api.updateTenantMemberRole(member.userId, role); members = api.tenantMembers(); onMessage("Üye rolü $role olarak güncellendi") }.onFailure { onMessage("Rol güncellenemedi: ${it.message}") } }
                            }) { Text("ROLÜ DEĞİŞTİR") }
                            TextButton(onClick = {
                                scope.launch { runCatching { api.removeTenantMember(member.userId); members = api.tenantMembers(); onMessage("Üyelik iptal edildi") }.onFailure { onMessage("Üyelik iptal edilemedi: ${it.message}") } }
                            }) { Text("ÜYELİĞİ İPTAL ET", color = Red) }
                        }
                    }
                }
            }
            item { Field("Davet e-postası", email, { email = it }, KeyboardType.Email) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("TRADER", inviteRole == "TRADER") { inviteRole = "TRADER" }
                Choice("VIEWER", inviteRole == "VIEWER") { inviteRole = "VIEWER" }
                if (me.tenantRole == "OWNER") Choice("ADMIN", inviteRole == "ADMIN") { inviteRole = "ADMIN" }
                Button(onClick = { scope.launch { runCatching { api.inviteTenantMember(email, inviteRole).also { inviteResult = it.token }; onMessage("Davet oluşturuldu") }.onFailure { onMessage("Davet hatası: ${it.message}") } } }) { Text("DAVET") }
            } }
            inviteResult?.let { code -> item { DataRow("Davet kodu", code) } }
            if (me.tenantRole == "OWNER") item { TextButton(onClick = { confirmClose = true }) { Text("ÇALIŞMA ALANINI KAPAT", color = Red) } }
        }
        if (me.role == "ADMIN") {
            item { Text("MARKET MAKER", color = Gold, fontWeight = FontWeight.Bold) }
            item { DataRow("Durum", if (makerEnabled == true) "AÇIK" else if (makerEnabled == false) "KAPALI" else "yükleniyor") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = makerEnabled != false, onClick = { scope.launch { runCatching { api.killMarketMaker(); makerEnabled = false; onMessage("Market maker kill switch etkin") }.onFailure { onMessage("Kill switch hatası: ${it.message}") } } }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("KILL SWITCH") }
                OutlinedButton(enabled = makerEnabled != true, onClick = { scope.launch { runCatching { api.resumeMarketMaker(); makerEnabled = true; onMessage("Market maker yeniden başlatıldı") }.onFailure { onMessage("Başlatma hatası: ${it.message}") } } }) { Text("YENİDEN BAŞLAT") }
            } }
        }
        item { Field("Mevcut kullanıcı davet kodu", inviteCode, { inviteCode = it }) }
        item { Button(onClick = { scope.launch { runCatching { api.acceptTenantInvitation(inviteCode); onMeChanged(api.me()); onMessage("Davet kabul edildi") }.onFailure { onMessage("Davet kabul hatası: ${it.message}") } } }) { Text("DAVETİ KABUL ET") } }
        item { OutlinedButton(onClick = { scope.launch { api.logout(); onLogout() } }) { Text("GÜVENLİ ÇIKIŞ") } }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Çalışma alanı kapatılsın mı?") }, text = { Text("Bu işlem alanı kapatır ve oturumu sonlandırır.") }, confirmButton = { TextButton(onClick = { confirmClose = false; scope.launch { runCatching { api.closeWorkspace(); onLogout() }.onFailure { onMessage("Kapatma hatası: ${it.message}") } } }) { Text("KAPAT", color = Red) } }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("VAZGEÇ") } })
}

@Composable
private fun PriceChart(points: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Ink, RoundedCornerShape(10.dp)).padding(8.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: return@Canvas
        val max = points.maxOrNull() ?: return@Canvas
        val spread = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        points.forEachIndexed { i, value ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - (value - min) / spread * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        drawPath(path, Gold, style = Stroke(width = 2.5f))
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Gold.copy(alpha = .18f) else Panel, shape = RoundedCornerShape(9.dp)) {
        Text(text, color = if (selected) Gold else Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable
private fun MetricCard(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Muted, fontSize = 12.sp); Text(value, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DataRow(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, fontSize = 11.sp) }
    }
}
