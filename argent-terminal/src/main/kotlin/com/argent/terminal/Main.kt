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


internal val LocalUiLanguage = androidx.compose.runtime.compositionLocalOf { "tr" }
@Composable internal fun guestLabel() = if (LocalUiLanguage.current == "en") "Guest" else "Misafir"
@Composable internal fun guestLoginMessage() = if (LocalUiLanguage.current == "en") "Please sign in to use this feature." else "Bu özelliği kullanmak için lütfen giriş yapın."

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1
) {
    MaterialText(
        com.argent.terminal.api.UiTranslations.translate(text, LocalUiLanguage.current).replace(Regex("(?<=\\d)[.,](\\d{7,})")) { match ->
            match.value.replace(',', '.').toBigDecimalOrNull()
                ?.setScale(6, RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: match.value
        },
        modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        fontFamily = fontFamily, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines
    )
}

fun main() = application {
    Window(icon = painterResource("argent-icon.png"), onCloseRequest = ::exitApplication, title = "ARGENT — Paper Trading Platform", state = rememberWindowState(width = 1560.dp, height = 940.dp)) { App() }
}

@Composable
private fun App() {
    val prefs = remember { Preferences.userRoot().node("com/argent/terminal") }
    var dark by remember { mutableStateOf(prefs.getBoolean("darkTheme", true)) }
    var scale by remember { mutableStateOf(prefs.getFloat("fontScale", 1f)) }
    var refresh by remember { mutableStateOf(prefs.getInt("refreshSeconds", 3)) }
    var marketRefresh by remember { mutableStateOf(prefs.getInt("marketRefreshSeconds", 60).coerceIn(60, 900)) }
    var language by remember { mutableStateOf(prefs.get("language", "tr").takeIf { it in setOf("tr", "en") } ?: "tr") }
    var savedLanguage by remember { mutableStateOf(language) }
    var savedDark by remember { mutableStateOf(dark) }; var savedScale by remember { mutableStateOf(scale) }
    var savedRefresh by remember { mutableStateOf(refresh) }; var savedMarketRefresh by remember { mutableStateOf(marketRefresh) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    ArgentTheme(dark) { CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = scale), LocalUiLanguage provides language) {
        Surface(Modifier.fillMaxSize(), color = ArgentBg) {
            val api = remember { ArgentApi() }; var me by remember { mutableStateOf<MeResponse?>(null) }; var guest by remember { mutableStateOf(false) }
            if (me == null && !guest) LoginPane(api, onAuthed = { me = it }, onGuest = { guest = true })
            else {
                val activeUser = me ?: MeResponse("guest", "", if (language == "en") "Guest" else "Misafir", "GUEST")
                TradingDesk(api, activeUser, { if (guest) guest = false else me = null }, { me = it; guest = false }, dark, scale, refresh, marketRefresh, guestMode = guest,
                onSettingsPreview = { d, s -> dark = d; scale = s },
                onLanguagePreview = { language = it },
                onSettingsCancel = { dark = savedDark; scale = savedScale; refresh = savedRefresh; marketRefresh = savedMarketRefresh; language = savedLanguage },
                onSettingsSave = { d, s, r, mr, lang ->
                    dark = d; scale = s; refresh = r; marketRefresh = mr; language = lang
                    savedDark = d; savedScale = s; savedRefresh = r; savedMarketRefresh = mr; savedLanguage = lang
                    prefs.putBoolean("darkTheme", d); prefs.putFloat("fontScale", s); prefs.putInt("refreshSeconds", r); prefs.putInt("marketRefreshSeconds", mr); prefs.put("language", lang)
                })
            }
        }
    } }
}

@Composable
private fun SettingsDialog(me: MeResponse, dark: Boolean, scale: Float, refresh: Int, marketRefresh: Int, language: String, onClose: () -> Unit, onPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onCancel: () -> Unit, onSave: (Boolean, Float, Int, Int, String) -> Unit) {
    var d by remember { mutableStateOf(dark) }; var s by remember { mutableStateOf(scale) }; var r by remember { mutableStateOf(refresh) }; var mr by remember { mutableStateOf(marketRefresh) }; var lang by remember { mutableStateOf(language) }
    fun cancel() { onCancel(); onClose() }
    AlertDialog(onDismissRequest = ::cancel, title = { Text("Ayarlar") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("HESAP", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("${me.displayName} · ${me.email} · ${me.role}", color = ArgentMuted, fontSize = 11.sp)
        Text("DİL", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row {
            TextButton(onClick = { lang = "tr"; onLanguagePreview(lang) }) { Text(if (lang == "tr") "✓ Türkçe" else "Türkçe") }
            TextButton(onClick = { lang = "en"; onLanguagePreview(lang) }) { Text(if (lang == "en") "✓ English" else "English") }
        }
        Text("GÖRÜNÜM", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Row {
            TextButton(onClick = { d = true; onPreview(d, s) }) { Text(if (d) "✓ Koyu tema" else "Koyu tema") }
            TextButton(onClick = { d = false; onPreview(d, s) }) { Text(if (!d) "✓ Açık tema" else "Açık tema") }
        }
        Text("Metin boyutu ${(s * 100).toInt()}%"); Slider(value = s, onValueChange = { s = it; onPreview(d, s) }, valueRange = .85f..1.3f)
        Text("PİYASA VERİSİ", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("Finnhub kotasyonları ve haberler ekran açıkken otomatik yenilenir.", color = ArgentMuted, fontSize = 10.sp)
        Row { listOf(60 to "1 dk", 300 to "5 dk", 900 to "15 dk").forEach { (seconds, label) -> TextButton(onClick = { mr = seconds }) { Text(if (mr == seconds) "✓ $label" else label) } } }
        Text("Genel veri kontrolü: $r sn"); Slider(value = r.toFloat(), onValueChange = { r = it.toInt().coerceAtLeast(1) }, valueRange = 1f..10f, steps = 8)
        Text("EMİR GÜVENLİĞİ", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text("AI emir taslakları özet ve hesap parolasıyla onaylanır; AI kendi başına emir göndermez.", color = ArgentMuted, fontSize = 10.sp)
    } }, confirmButton = { TextButton(onClick = { onSave(d, s, r, mr, lang); onClose() }) { Text("Kaydet") } }, dismissButton = { TextButton(onClick = ::cancel) { Text("İptal") } })
}
@Composable
private fun LoginPane(api: ArgentApi, onAuthed: (MeResponse) -> Unit, onGuest: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF101820)) {
                    Image(painterResource("argent-icon.png"), contentDescription = "ARGENT simgesi", modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ARGENT", color = ArgentGold, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Paper Trading Platform", color = ArgentMuted, fontSize = 13.sp)
                }
            }
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
                                "register" -> api.register(email, password, name.ifBlank { email.substringBefore("@") })
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
            Button(enabled = !busy, onClick = {
                busy = true; error = null; api.baseUrl = host.trimEnd('/')
                scope.launch {
                    try { googleDesktopSignIn(api); onAuthed(api.me()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Google ile giriş başarısız." }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().height(42.dp), colors = ButtonDefaults.buttonColors(containerColor = ArgentPanelAlt, contentColor = ArgentSilver)) {
                Text("Google ile devam et")
            }
            if (mode != "invitation") TextButton(onClick = { mode = if (mode == "login") "register" else "login" }) {
                Text(if (mode == "login") "Hesap oluştur" else "Giriş ekranına dön", color = ArgentBlue)
            }
            TextButton(onClick = { mode = if (mode == "invitation") "login" else "invitation" }) {
                Text(if (mode == "invitation") "Giriş ekranına dön" else "Davet koduyla katıl", color = ArgentBlue)
            }
            TextButton(enabled = !busy, onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
                Text(if (LocalUiLanguage.current == "en") "Continue as guest" else "Misafir olarak giriş yap", color = ArgentBlue)
            }
        }
    }
}
@Composable
private fun TickerMarquee(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = remember { TextStyle(color = ArgentSilver, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    val layout = remember(text, style, density) {
        textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1, softWrap = false)
    }
    val contentWidth = layout.size.width.toFloat().coerceAtLeast(1f)
    val durationMillis = with(density) { (layout.size.width.toDp().value * 28.6f).toInt().coerceAtLeast(5_000) }
    val transition = rememberInfiniteTransition(label = "market ticker")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis, easing = LinearEasing)),
        label = "market ticker offset"
    )
    Canvas(modifier) {
        val xOffset = -progress * contentWidth
        val copies = (size.width / contentWidth).toInt() + 2
        clipRect {
            repeat(copies) { index ->
                drawText(layout, topLeft = Offset(xOffset + index * contentWidth, (size.height - layout.size.height) / 2f))
            }
        }
    }
}
@Composable
private fun TradingDesk(api: ArgentApi, me: MeResponse, onLogout: () -> Unit, onMeChanged: (MeResponse) -> Unit, darkTheme: Boolean, fontScale: Float, refreshSeconds: Int, marketRefreshSeconds: Int, guestMode: Boolean = false, onSettingsPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onSettingsCancel: () -> Unit, onSettingsSave: (Boolean, Float, Int, Int, String) -> Unit) {
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
    LaunchedEffect(symbol, refreshSeconds) {
        chart.clear()
        technicalAnalysis = null
        runCatching { api.history(symbol).forEach { it.price.toFloatOrNull()?.let(chart::add) } }
        launch { api.orderBookFlow(symbol).collect { book = it } }
        launch {
            while (true) {
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
            if (!guestMode) api.orderUpdatesFlow().collect { update ->
                flash = "Emir güncellemesi · ${'$'}{update.orderId.take(8)} · ${'$'}{update.status} · ${'$'}{update.symbol}"
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
            delay(refreshSeconds * 1_000L)
        }
    }

    Column(Modifier.fillMaxSize().background(ArgentBg)) {
        TopBar(
            me, tickers, health, onLogout, api, symbol, scope, guestMode,
            onFlash = { flash = it },
            onMeChanged = onMeChanged,
            onWorkspaceClosed = onLogout,
            onSettings = { settingsOpen = true }
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
                ChartPanel(symbol, tickers[symbol], chart, book, technicalAnalysis)
                OrderBookPanel(book) { clicked -> price = clicked }
            }
            if (!guestMode) Ticket(
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
        if (settingsOpen) SettingsDialog(me, darkTheme, fontScale, refreshSeconds, marketRefreshSeconds, LocalUiLanguage.current, { settingsOpen = false }, onSettingsPreview, onLanguagePreview, onSettingsCancel, onSettingsSave)
        BottomDock(tab, { tab = it }, bottomDockHeight, { bottomDockHeight = it }, portfolio, orders, fills, blotter, tickers, api, me, symbol, side, type, qty, price, scope, marketRefreshSeconds, guestMode, onRequireLogin = { flash = loginRequiredMessage }) { flash = it }
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
    onSettings: () -> Unit
) {
    var workspaceOpen by remember { mutableStateOf(false) }
    var lastTestnetOrder by remember { mutableStateOf<BinanceTestnetOrderDto?>(null) }
    val loginRequiredMessage = guestLoginMessage()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(ArgentPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource("argent-icon.png"), contentDescription = "ARGENT", modifier = Modifier.size(30.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        Spacer(Modifier.width(20.dp))
        val marketTickerText = tickers.values.sortedBy { it.symbol }
            .joinToString("     •     ") { "${it.symbol} ${compact(it.price)}" }
        if (marketTickerText.isNotBlank()) {
            TickerMarquee(
                text = "$marketTickerText     •     ",
                modifier = Modifier.weight(1f).height(28.dp)
            )
        } else Spacer(Modifier.weight(1f))
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
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (guestMode) guestLabel() else "${me.displayName} · ${me.role}",
            color = ArgentMuted,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onSettings) { Text("Ayarlar", color = ArgentGold) }
        TextButton(onClick = { if (guestMode) onFlash(loginRequiredMessage) else workspaceOpen = true }) { Text(if (LocalUiLanguage.current == "en") "Workspace" else "Çalışma alanı", color = ArgentGold) }
        TextButton(onClick = {
            if (guestMode) { onLogout(); return@TextButton }
            scope.launch {
                api.logout()
                onLogout()
            }
        }) { Text(if (guestMode) { if (LocalUiLanguage.current == "en") "Sign in" else "Giriş yap" } else if (LocalUiLanguage.current == "en") "Sign out" else "Çıkış", color = ArgentRed) }
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
    val language = LocalUiLanguage.current
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
    var testnetPrice by remember { mutableStateOf("") }
    var testnetPriceManual by remember { mutableStateOf(false) }
    var testnetPriceLoading by remember { mutableStateOf(false) }
    var bistCsv by remember { mutableStateOf("") }
    var bistReplaySpeed by remember { mutableStateOf("1000") }
    var bistReplay by remember { mutableStateOf<BistReplayDto?>(null) }
    val canManage = me.tenantRole == "OWNER" || me.tenantRole == "ADMIN"

    LaunchedEffect(testnetSymbol, testnet?.configured) {
        if (testnet?.configured == true && testnetSymbol.matches(Regex("[A-Z0-9]{5,20}"))) {
            while (true) {
                testnetPriceLoading = true
                runCatching { api.binanceSpotTestnetPrice(testnetSymbol) }.onSuccess { quote ->
                    if (!testnetPriceManual) testnetPrice = quote.price
                }
                testnetPriceLoading = false
                delay(30_000)
            }
        }
    }
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
                val message = com.argent.terminal.api.UiTranslations.translate(failure.message ?: "İşlem başarısız", language)
                error = message
                onFlash(message)
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
                        marketMaker?.let { state ->
                            TextButton(onClick = {
                                runAction {
                                    if (state.enabled) api.killMarketMaker() else api.resumeMarketMaker()
                                    marketMaker = api.marketMakerState()
                                }
                            }) { Text(if (state.enabled) "KILL SWITCH" else "Yeniden başlat", color = if (state.enabled) ArgentRed else ArgentGreen) }
                        }
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
                            OutlinedTextField(testnetSymbol, { testnetSymbol = it.uppercase(); testnetPriceManual = false }, label = { Text("Sembol") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            TextButton(onClick = { testnetSide = if (testnetSide == "BUY") "SELL" else "BUY" }) { Text(testnetSide, color = if (testnetSide == "BUY") ArgentGreen else ArgentRed) }
                            TextButton(onClick = { testnetType = if (testnetType == "LIMIT") "MARKET" else "LIMIT" }) { Text(testnetType, color = ArgentGold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(testnetQuantity, { testnetQuantity = it }, label = { Text("Miktar") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            if (testnetType == "LIMIT") OutlinedTextField(testnetPrice, { testnetPrice = it; testnetPriceManual = true }, label = { Text(if (testnetPriceLoading) "Testnet fiyatı yükleniyor…" else "Testnet referans fiyatı") }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            if (testnetType == "LIMIT") TextButton(enabled = !testnetPriceLoading, onClick = {
                                scope.launch {
                                    testnetPriceLoading = true
                                    runCatching { api.binanceSpotTestnetPrice(testnetSymbol) }
                                        .onSuccess { testnetPrice = it.price; testnetPriceManual = false }
                                        .onFailure { error = it.message ?: "Testnet fiyatı alınamadı" }
                                    testnetPriceLoading = false
                                }
                            }) { Text(if (testnetPriceLoading) "…" else "Fiyatı yenile", color = ArgentBlue) }
                            if (testnetType == "LIMIT") TextButton(onClick = { testnetTif = when (testnetTif) { "GTC" -> "IOC"; "IOC" -> "FOK"; else -> "GTC" } }) { Text(testnetTif, color = ArgentGold) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(enabled = !testnetSubmitting && !testnetPriceLoading && (testnetType != "LIMIT" || (testnetPrice.toBigDecimalOrNull()?.signum() ?: 0) > 0), onClick = {
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
                                        onFlash(if (language == "en") "Testnet ${result.status} · ${if (result.ledgerSynced) "fill posted to ARGENT ledger" else "ARGENT ledger review required"} · #${result.orderId}" else "Testnet ${result.status} · ${if (result.ledgerSynced) "fill ARGENT defterine işlendi" else "ARGENT defterinde inceleme gerekiyor"} · #${result.orderId}")
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
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(ins.symbol, color = ArgentSilver, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(ins.displayName, color = ArgentMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(compact(px), color = ArgentGold, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 64.dp))
                }
            }
        }
    }
}
