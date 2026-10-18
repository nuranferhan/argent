package com.argent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.api.ArgentApi
import com.argent.terminal.api.DepthLevelDto
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
import java.util.Locale

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

private enum class Page(val title: String) { TRADE("Piyasa"), PORTFOLIO("Portföy"), ORDERS("Emirler"), ASSISTANT("AI"), WORKSPACE("Çalışma alanı") }

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    var gatewayReachable by remember { mutableStateOf(true) }
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
            runCatching { portfolio = api.portfolio() }
            runCatching { orders = api.orders() }
            runCatching { fills = api.fills() }
            runCatching { health = api.health(); gatewayReachable = true }
                .onFailure { gatewayReachable = false }
            delay(4_000)
        }
    }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Page.entries.forEach { item ->
                NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Text(when (item) { Page.TRADE -> "↗"; Page.PORTFOLIO -> "◫"; Page.ORDERS -> "≡"; Page.ASSISTANT -> "✦"; Page.WORKSPACE -> "⚙" }, fontSize = 20.sp) }, label = { Text(item.title, fontSize = 10.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ARGENT", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("${me.displayName} · ${me.tenantRole}", color = Muted, fontSize = 11.sp)
            }
            val statusLine = when {
                !gatewayReachable -> "GATEWAY'E BAĞLANILAMIYOR · YENİDEN DENENİYOR"
                health == null -> "GATEWAY DURUMU KONTROL EDİLİYOR"
                else -> "${health!!.status} · ${health!!.executionMode} · CRYPTO ${health!!.cryptoMarketData} · BIST ${health!!.bistMarketData}"
            }
            Text(
                "$statusLine     •     $statusLine",
                color = if (gatewayReachable && health?.status == "UP") Green else Red,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).basicMarquee()
            )
            message?.let { Text(it, color = if (it.contains("hata", true) || it.contains("REJECTED", true)) Red else Gold, modifier = Modifier.padding(horizontal = 12.dp)) }
            when (page) {
                Page.TRADE -> TradePage(api, instruments, symbol, { symbol = it }, ticker, book, prices, trades, busy,
                    onMessage = { message = it }, onBusy = { busy = it }, onRefresh = { portfolio = api.portfolio(); orders = api.orders() })
                Page.PORTFOLIO -> PortfolioPage(portfolio)
                Page.ORDERS -> OrdersPage(orders, fills, api, onMessage = { message = it })
                Page.ASSISTANT -> AssistantScreen(api, symbol)
                Page.WORKSPACE -> WorkspacePage(api, me, { me = it; onMeChanged(it) }, onMessage = { message = it }, onLogout = onLogout)
            }
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
private fun AssistantScreen(api: ArgentApi, symbol: String) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AssistantUiMessage>() }
    val listState = rememberLazyListState()
    var prompt by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { enabled = api.assistantStatus().enabled } }
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ARGENT AI", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Portföy · emirler · $symbol", color = Muted, fontSize = 10.sp)
            }
            Surface(color = Color(0x2234D399), shape = RoundedCornerShape(50)) {
                Text("SALT OKUNUR", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        Text(
            when (enabled) {
                true -> "ARGENT verileriyle yanıtlar · Emir veremez, bakiye değiştiremez"
                false -> "Asistan kapalı · Gateway AI anahtarını kontrol et"
                null -> "Asistan bağlantısı kontrol ediliyor…"
            }, color = Muted, fontSize = 10.sp
        )
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Ne öğrenmek istersin?", color = Color(0xFFE0E4E8), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Hesap verilerin ve seçili piyasa bilgisi üzerinden sorabilirsin.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                listOf("Bugün portföyüm nasıl?", "Bekleyen emirlerimi açıkla", "$symbol piyasa özeti").forEach { suggestion ->
                    Surface(
                        color = Panel, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { prompt = suggestion }
                    ) { Text(suggestion, color = Color(0xFFE0E4E8), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) }
                }
            }
        } else {
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (busy) item { Text("ARGENT AI yanıt hazırlıyor…", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) }
                items(messages.size) { index ->
                    AssistantMessageBubble(messages[messages.lastIndex - index])
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, enabled = !busy,
                placeholder = { Text("ARGENT'e sor…") }, minLines = 1, maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(enabled = !busy && prompt.isNotBlank() && enabled != false, onClick = {
                val question = prompt.trim(); prompt = ""; messages.add(AssistantUiMessage(true, question)); busy = true
                scope.launch {
                    try {
                        runCatching { api.askAssistant(question, symbol) }
                            .onSuccess { result -> messages.add(AssistantUiMessage(false, cleanAssistantAnswer(result.answer))) }
                            .onFailure { messages.add(AssistantUiMessage(false, it.message ?: "Asistan yanıt veremedi.")) }
                    } finally { busy = false }
                }
            }, modifier = Modifier.height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp) else Text("Sor")
            }
        }
    }
}

private fun cleanAssistantAnswer(raw: String): String {
    val metadata = Regex(
        "(?i)\\b(?:source|kaynak(?:\\s+verisi)?|veri\\s+kaynağı|piyasa\\s+modu|market\\s+mode|veri\\s+zaman[ıi]|data\\s+time|timestamp)\\s*:"
    )
    return raw.lineSequence()
        .map { line ->
            var clean = line.trim().replace(Regex("(?i)^ARGENT\\s+AI\\s*:\\s*"), "")
            metadata.find(clean)?.let { marker -> clean = clean.substring(0, marker.range.first).trimEnd() }
            clean
        }
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()
}

@Composable
private fun AssistantMessageBubble(message: AssistantUiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 1050.dp).fillMaxWidth(if (message.fromUser) 0.86f else 0.98f),
            color = if (message.fromUser) Color(0xFF282615) else Panel,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (message.fromUser) "SEN" else "ARGENT AI", color = if (message.fromUser) Gold else Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                AssistantFormattedAnswer(message.text)
            }
        }
    }
}

@Composable
private fun AssistantFormattedAnswer(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        assistantBlocks(markdown).forEach { block ->
            when (block) {
                is AssistantBlock.Heading -> Text(block.text, color = Gold, fontSize = if (block.level <= 2) 13.sp else 12.sp, fontWeight = FontWeight.Bold)
                is AssistantBlock.Paragraph -> Text(block.text, color = Color(0xFFE0E4E8), fontSize = 12.sp, lineHeight = 18.sp)
                is AssistantBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("•", color = Gold, fontWeight = FontWeight.Bold)
                    Text(block.text, color = Color(0xFFE0E4E8), fontSize = 12.sp, lineHeight = 18.sp)
                }
                is AssistantBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    block.rows.forEach { row ->
                        Surface(color = Color(0xFF101A20), shape = RoundedCornerShape(8.dp)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                block.headers.forEachIndexed { index, heading ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                        Text(heading, color = Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1.15f))
                                        Text(row.getOrElse(index) { "—" }, color = Color(0xFFE0E4E8), fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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
private fun TradePage(api: ArgentApi, instruments: List<InstrumentDto>, symbol: String, onSymbol: (String) -> Unit,
                      ticker: TickerDto?, book: OrderBookDto, prices: List<Float>, trades: List<TradeDto>, busy: Boolean,
                      onMessage: (String) -> Unit, onBusy: (Boolean) -> Unit, onRefresh: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    val instrument = instruments.firstOrNull { it.symbol == symbol }
    val sourceBadge = if (instrument?.assetClass == "CRYPTO" && ticker?.source?.equals("binance", ignoreCase = true) == true) "BINANCE" else null
    val quantityValue = quantity.trim().replace(',', '.').toBigDecimalOrNull()
    val priceValue = price.trim().replace(',', '.').toBigDecimalOrNull()
    val lotSize = instrument?.lotSize?.toBigDecimalOrNull()
    val tickSize = instrument?.tickSize?.toBigDecimalOrNull()
    val validation = when {
        instrument == null -> "Enstrüman bilgisi yükleniyor."
        quantityValue == null || quantityValue.signum() <= 0 -> "Miktar pozitif bir sayı olmalı."
        lotSize != null && quantityValue.remainder(lotSize).signum() != 0 -> "Miktar lot adımına uygun değil (${instrument.lotSize})."
        type == "MARKET" && ticker?.price?.toBigDecimalOrNull() == null -> "Piyasa fiyatı henüz alınmadı."
        type != "MARKET" && (priceValue == null || priceValue.signum() <= 0) -> "Fiyat pozitif bir sayı olmalı."
        type != "MARKET" && tickSize != null && priceValue!!.remainder(tickSize).signum() != 0 -> "Fiyat tick adımına uygun değil (${instrument.tickSize})."
        else -> null
    }
    val notional = runCatching {
        val px = if (type == "MARKET") ticker?.price ?: "0" else price.ifBlank { "0" }
        java.math.BigDecimal(px).multiply(java.math.BigDecimal(quantity.ifBlank { "0" }))
            .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }.getOrElse { "—" }
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
                        Spacer(Modifier.weight(1f))
                        sourceBadge?.let { Text(it, color = Muted, fontSize = 9.sp) }
                    }
                    instrument?.let { Text("${it.displayName} · tick ${it.tickSize} · lot ${it.lotSize}", color = Muted, fontSize = 10.sp) }
                    Text("Bid ${ticker?.bid ?: book.bestBid ?: "—"}   Ask ${ticker?.ask ?: book.bestAsk ?: "—"}", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    PriceChart(prices, Modifier.fillMaxWidth().height(168.dp))
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
                    Text("Tahmini notional  $notional", color = Muted, fontSize = 11.sp)
                    validation?.let { Text(it, color = Red, fontSize = 11.sp) }
                    Button(enabled = !busy && validation == null, onClick = {
                        onBusy(true)
                        scope.launch {
                            try {
                                val normalizedQuantity = quantity.trim().replace(',', '.')
                                val normalizedPrice = price.trim().replace(',', '.').ifBlank { null }
                                require(normalizedQuantity.toBigDecimal() > java.math.BigDecimal.ZERO) { "Miktar sıfırdan büyük olmalı." }
                                val activeInstrument = requireNotNull(instrument) { "Enstrüman bilgisi yüklenemedi." }
                                lotSize?.let { require(normalizedQuantity.toBigDecimal().remainder(it).signum() == 0) { "Miktar lot adımına uygun değil (${activeInstrument.lotSize})." } }
                                if (type == "MARKET") require(ticker?.price?.toBigDecimalOrNull() != null) { "Piyasa fiyatı henüz alınmadı." }
                                if (type != "MARKET") require(!normalizedPrice.isNullOrBlank() && normalizedPrice.toBigDecimal() > java.math.BigDecimal.ZERO) { "Fiyat sıfırdan büyük olmalı." }
                                if (type != "MARKET" && tickSize != null) require(normalizedPrice!!.toBigDecimal().remainder(tickSize).signum() == 0) { "Fiyat tick adımına uygun değil (${activeInstrument.tickSize})." }
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
                    Text(ask?.let { "${it.price}  ×  ${it.quantity}" } ?: "", color = Red, modifier = Modifier.weight(1f).clickable { ask?.price?.let(onPrice) }, fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun PortfolioPage(p: PortfolioDto?) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Pozisyon yoğunlaşması", "${p?.riskScore ?: "—"}%", "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. Tek varlıkta %100; ARGENT göstergesidir, genel risk notu değildir.") }
        if (p?.valuations.isNullOrEmpty()) {
            item { MetricCard("Özkaynak", p?.equity ?: "—", "Gerçekleşen PnL ${p?.realizedPnl ?: "—"} · Açık PnL ${p?.unrealizedPnl ?: "—"}") }
        } else {
            p!!.valuations.forEach { value ->
                item { Text("${value.quoteAsset} DEĞERLEMESİ", color = Gold, fontWeight = FontWeight.Bold) }
                item { MetricCard("Özkaynak", "${value.equity} ${value.quoteAsset}", "Brüt maruziyet ${value.grossExposure} ${value.quoteAsset}") }
                item { MetricCard("Gerçekleşen PnL", "${value.realizedPnl} ${value.quoteAsset}", "Değerleme ${if (value.pnlComplete) "tam" else "kısmi"}") }
                item { MetricCard("Açık PnL", "${value.unrealizedPnl} ${value.quoteAsset}", if (value.priceComplete) "Güncel fiyatlarla hesaplandı" else "Bazı varlıklarda güncel fiyat yok; tahmin kısmi") }
            }
        }
        item { Text("BAKİYELER", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.balances.orEmpty()) { b -> DataRow(b.asset, "${b.available} kullanılabilir · ${b.held} blokeli") }
        item { Text("POZİSYONLAR", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.positions.orEmpty()) { pos -> DataRow(pos.symbol, "${pos.quantity} · ort. ${pos.avgEntryPrice} · PnL ${pos.realizedPnl}") }
    }
}

@Composable
private fun OrdersPage(orders: List<OrderDto>, fills: List<TradeDto>, api: ArgentApi, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val openOrders = orders.filter { it.status in setOf("PENDING", "ACCEPTED", "PARTIALLY_FILLED") && (it.remainingQuantity.toBigDecimalOrNull()?.signum() ?: 0) > 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("AÇIK EMİRLER", tab == 0) { tab = 0 }; Choice("İŞLEMLER", tab == 1) { tab = 1 } }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                item { Text("AÇIK EMİRLER · ${openOrders.size} bekleyen", color = Gold, fontWeight = FontWeight.Bold) }
                if (openOrders.isEmpty()) item { Text("Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemler geçmişindedir.", color = Muted) }
                items(openOrders, key = { it.id }) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("${order.symbol} ${order.side} ${order.type}", fontWeight = FontWeight.Bold); Text("${orderStatusLabel(order.status)} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "piyasa"}", color = Muted, fontSize = 11.sp) }
                            if (order.status in setOf("ACCEPTED", "PARTIALLY_FILLED")) TextButton(onClick = { scope.launch { runCatching { api.cancel(order.id, order.symbol); onMessage("${order.symbol} emri iptal edildi") }.onFailure { onMessage("İptal hatası: ${it.message}") } } }) { Text("İPTAL", color = Red) }
                        }
                    }
                }
            }
            if (tab == 1) {
                val sortedOrders = orders.sortedByDescending { it.updatedAt.takeIf { updated -> updated > 0 } ?: it.createdAt }
                item { Text("EMİR GEÇMİŞİ", color = Gold, fontWeight = FontWeight.Bold) }
                if (sortedOrders.isEmpty()) item { Text("Emir geçmişi boş.", color = Muted) }
                items(sortedOrders, key = { it.id }) { order ->
                    DataRow("${order.symbol} ${order.side} ${order.type} · ${orderStatusLabel(order.status)}", "Dolan ${order.filledQuantity}/${order.quantity} · kalan ${order.remainingQuantity} · ${order.price ?: "piyasa"}")
                }
                item { Text("GERÇEKLEŞEN İŞLEMLER", color = Gold, fontWeight = FontWeight.Bold) }
                if (fills.isEmpty()) item { Text("İşlem geçmişi boş.", color = Muted) }
                items(fills) { trade -> DataRow(trade.symbol, "${trade.quantity} @ ${trade.price}") }
            }
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
    val safePoints = points.ifEmpty { listOf(0f) }
    val rawMin = safePoints.minOrNull() ?: 0f
    val rawMax = safePoints.maxOrNull() ?: rawMin
    val padding = ((rawMax - rawMin) * 0.08f).coerceAtLeast(kotlin.math.abs(rawMax) * 0.0005f).coerceAtLeast(0.01f)
    val min = rawMin - padding
    val max = rawMax + padding
    val labels = (0..4).map { i -> max - (max - min) * i / 4f }
    Column(modifier.background(Ink, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { Text(String.format(Locale.US, "%.2f", it), color = Color(0xFFBAC4D1), fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                repeat(5) { i ->
                    val y = size.height * i / 4f
                    drawLine(Line, Offset(0f, y), Offset(size.width, y), 1f)
                }
                if (points.size >= 2) {
                    val spread = max - min
                    val path = Path()
                    points.forEachIndexed { i, value ->
                        val x = size.width * i / (points.size - 1)
                        val y = size.height - (value - min) / spread * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Gold, style = Stroke(width = 2.5f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 72.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", color = Muted, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(((points.size + 1) / 2).coerceAtLeast(1).toString(), color = Muted, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(points.size.coerceAtLeast(1).toString(), color = Muted, fontSize = 8.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
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

@Preview(showBackground = true)
@Composable
private fun TradePagePreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            background = Ink,
            surface = Panel,
            onBackground = Color(0xFFE5E8ED),
            onSurface = Color(0xFFE5E8ED),
            error = Red
        )
    ) {
        val sampleInstruments = listOf(
            InstrumentDto("THYAO", "THYAO", "TRY", "EQUITY", "Türk Hava Yolları", "0.05", "1"),
            InstrumentDto("GARAN", "GARAN", "TRY", "EQUITY", "Garanti BBVA", "0.02", "1"),
            InstrumentDto("BTCUSDT", "BTC", "USDT", "CRYPTO", "Bitcoin / USDT", "0.01", "0.001")
        )
        val sampleTicker = TickerDto("THYAO", "305.50", "305.25", "305.50", "1500000", "SANDBOX")
        val sampleBook = OrderBookDto(
            symbol = "THYAO",
            bids = listOf(
                DepthLevelDto("305.25", "1000", 2),
                DepthLevelDto("305.00", "2500", 5),
                DepthLevelDto("304.75", "1800", 3)
            ),
            asks = listOf(
                DepthLevelDto("305.50", "800", 1),
                DepthLevelDto("305.75", "3000", 4),
                DepthLevelDto("306.00", "1200", 2)
            ),
            bestBid = "305.25",
            bestAsk = "305.50"
        )
        val samplePrices = listOf(300f, 301f, 302.5f, 302f, 304f, 303.5f, 305f, 305.5f)
        val sampleTrades = listOf(
            TradeDto("1", "THYAO", "305.50", "100", "b1", "s1"),
            TradeDto("2", "THYAO", "305.25", "500", "b2", "s2")
        )

        TradePage(
            api = remember { ArgentApi() },
            instruments = sampleInstruments,
            symbol = "THYAO",
            onSymbol = {},
            ticker = sampleTicker,
            book = sampleBook,
            prices = samplePrices,
            trades = sampleTrades,
            busy = false,
            onMessage = {},
            onBusy = {},
            onRefresh = {}
        )
    }
}
