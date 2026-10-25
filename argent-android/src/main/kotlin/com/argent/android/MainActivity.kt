package com.argent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Slider
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.api.ArgentApi
import com.argent.terminal.api.DepthLevelDto
import com.argent.terminal.api.HealthDto
import com.argent.terminal.api.InstrumentDto
import com.argent.terminal.api.ExternalMarketStatusDto
import com.argent.terminal.api.ExternalQuoteDto
import com.argent.terminal.api.MarketNewsDto
import com.argent.terminal.api.PriceAlertDto
import com.argent.terminal.api.MeResponse
import com.argent.terminal.api.OrderBookDto
import com.argent.terminal.api.OrderDto
import com.argent.terminal.api.PortfolioDto
import com.argent.terminal.api.TenantDto
import com.argent.terminal.api.TenantMemberDto
import com.argent.terminal.api.TickerDto
import com.argent.terminal.api.TechnicalAnalysisDto
import com.argent.terminal.api.TradeDto
import com.argent.terminal.api.AssistantOrderDraftDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale


private val LocalUiLanguage = androidx.compose.runtime.compositionLocalOf { "tr" }

@Composable
private fun Text(
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
    val readableColor = if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8) && (color == Color(0xFFE0E4E8) || color == Color(0xFFE5E8ED))) AppText else color
    val readableText = com.argent.terminal.api.UiTranslations.translate(text, LocalUiLanguage.current)
        .replace(Regex("(?<=\\d)[.,](\\d{7,})")) { match ->
            match.value.replace(',', '.').toBigDecimalOrNull()?.setScale(6, java.math.RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: match.value
        }
    MaterialText(
        readableText,
        modifier = modifier, color = readableColor, fontSize = fontSize, fontWeight = fontWeight,
        fontFamily = fontFamily, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines
    )
}

private data class MobilePalette(val ink: Color, val panel: Color, val line: Color, val gold: Color, val green: Color, val red: Color, val muted: Color, val text: Color)
private object MobilePaletteHolder { var current = MobilePalette(Color(0xFF0B0E12), Color(0xFF151A21), Color(0xFF29313C), Color(0xFFE0B82F), Color(0xFF00C98A), Color(0xFFFF5065), Color(0xFF98A3B3), Color(0xFFE5E8ED)) }
private val Ink: Color get() = MobilePaletteHolder.current.ink
private val Panel: Color get() = MobilePaletteHolder.current.panel
private val Line: Color get() = MobilePaletteHolder.current.line
private val Gold: Color get() = MobilePaletteHolder.current.gold
private val Green: Color get() = MobilePaletteHolder.current.green
private val Red: Color get() = MobilePaletteHolder.current.red
private val Muted: Color get() = MobilePaletteHolder.current.muted
private val AppText: Color get() = MobilePaletteHolder.current.text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ArgentMobile() } }
}

@Composable
private fun ArgentMobile() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("argent-ui", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var scale by remember { mutableStateOf(prefs.getFloat("scale", 1f)) }
    var refresh by remember { mutableStateOf(prefs.getInt("refresh", 4)) }
    var marketRefresh by remember { mutableStateOf(prefs.getInt("marketRefresh", 60).coerceIn(60, 900)) }
    var language by remember { mutableStateOf((prefs.getString("language", "tr") ?: "tr").takeIf { it in setOf("tr", "en") } ?: "tr") }
    var savedLanguage by remember { mutableStateOf(language) }
    var savedDark by remember { mutableStateOf(dark) }; var savedScale by remember { mutableStateOf(scale) }
    var savedRefresh by remember { mutableStateOf(refresh) }; var savedMarketRefresh by remember { mutableStateOf(marketRefresh) }
    val density = LocalDensity.current
    val palette = if (dark) MobilePalette(Color(0xFF0B0E12), Color(0xFF151A21), Color(0xFF29313C), Color(0xFFE0B82F), Color(0xFF00C98A), Color(0xFFFF5065), Color(0xFF98A3B3), Color(0xFFE5E8ED)) else MobilePalette(Color(0xFFF3F5F8), Color.White, Color(0xFFD1D8E0), Color(0xFF9A7410), Color(0xFF087F55), Color(0xFFC6283D), Color(0xFF647181), Color(0xFF26313D))
    MobilePaletteHolder.current = palette
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary=Gold, background=Ink, surface=Panel, onBackground=AppText, onSurface=AppText, error=Red) else lightColorScheme(primary=Gold, background=Ink, surface=Panel, onBackground=AppText, onSurface=AppText, error=Red)) {
        CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = scale), LocalUiLanguage provides language) {
            val api = remember { ArgentApi() }
            var me by remember { mutableStateOf<MeResponse?>(null) }
            if (me == null) LoginScreen(api) { me = it }
            else MainScreen(api, me!!, onMeChanged = { me=it }, onLogout = { me=null }, dark=dark, fontScale=scale, refreshSeconds=refresh, marketRefreshSeconds=marketRefresh, onSettingsPreview={ d,s -> dark=d; scale=s }, onLanguagePreview={ language=it }, onSettingsCancel={ dark=savedDark; scale=savedScale; refresh=savedRefresh; marketRefresh=savedMarketRefresh; language=savedLanguage }, onSettingsSave={ d,s,r,mr,lang -> dark=d; scale=s; refresh=r; marketRefresh=mr; language=lang; savedDark=d; savedScale=s; savedRefresh=r; savedMarketRefresh=mr; savedLanguage=lang; prefs.edit().putBoolean("dark",d).putFloat("scale",s).putInt("refresh",r).putInt("marketRefresh",mr).putString("language",lang).apply() })
        }
      }
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
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Ink).padding(20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF101820)) {
                    Image(painterResource(com.argent.android.R.drawable.ic_launcher_foreground), contentDescription = "ARGENT simgesi", modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ARGENT", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Paper Trading Platform · Android", color = Muted)
                }
            }
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
                            "register" -> api.register(email, password, name.ifBlank { email.substringBefore('@') })
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
            OutlinedButton(enabled = !busy, onClick = {
                busy = true; error = null; api.baseUrl = gateway.trimEnd('/')
                scope.launch {
                    try {
                        val googleConfig = api.googleAuthConfig()
                        val clientId = googleConfig.webClientId?.takeIf { googleConfig.configured }
                            ?: throw IllegalStateException("Google ile giriş için gateway üzerinde GOOGLE_WEB_CLIENT_ID ayarlanmalı.")
                        val nonce = api.googleNonce().nonce
                        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
                        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                        val result = CredentialManager.create(context).getCredential(context, request)
                        val credential = result.credential as? CustomCredential
                        if (credential?.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw IllegalStateException("Google kimlik doğrulaması tamamlanamadı.")
                        api.loginWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
                        onLogin(api.me())
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Google ile giriş başarısız." }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Google ile devam et") }
            Text("Hesap açınca sanal bakiye tanımlanır; gerçek para kullanılmaz.", color = Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { mode = if (mode == "register") "login" else "register" }) { Text(if (mode == "register") "Giriş" else "Hesap oluştur") }
                TextButton(onClick = { mode = if (mode == "invite") "login" else "invite" }) { Text(if (mode == "invite") "Giriş" else "Davet kodu") }
            }
        }
    }
}

private enum class Page(val title: String) { TRADE("Piyasa"), PORTFOLIO("Portföy"), ORDERS("Emirler"), ASSISTANT("AI"), WORKSPACE("Çalışma alanı"), SETTINGS("Ayarlar") }

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MainScreen(api: ArgentApi, initialMe: MeResponse, onMeChanged: (MeResponse) -> Unit, onLogout: () -> Unit, dark: Boolean, fontScale: Float, refreshSeconds: Int, marketRefreshSeconds: Int, onSettingsPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onSettingsCancel: () -> Unit, onSettingsSave: (Boolean, Float, Int, Int, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf(initialMe) }
    var page by remember { mutableStateOf(Page.TRADE) }
    var previousPage by remember { mutableStateOf(Page.TRADE) }
    LaunchedEffect(page) { if (previousPage == Page.SETTINGS && page != Page.SETTINGS) onSettingsCancel(); previousPage = page }
    var instruments by remember { mutableStateOf(emptyList<InstrumentDto>()) }
    var symbol by remember { mutableStateOf("THYAO") }
    var ticker by remember { mutableStateOf<TickerDto?>(null) }
    var marketTickers by remember { mutableStateOf(emptyMap<String, TickerDto>()) }
    var technicalAnalysis by remember { mutableStateOf<TechnicalAnalysisDto?>(null) }
    var book by remember { mutableStateOf(OrderBookDto(symbol)) }
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

    LaunchedEffect(Unit) {
        runCatching { instruments = api.instruments() }
        launch { api.tickerFlow().collect { t -> marketTickers = marketTickers + (t.symbol to t); if (t.symbol == symbol) { ticker = t; t.price.toFloatOrNull()?.let { prices.add(it); if (prices.size > 120) prices.removeAt(0) } } } }
    }
    LaunchedEffect(symbol, refreshSeconds) {
        technicalAnalysis = null
        prices.clear()
        runCatching { api.history(symbol).forEach { it.price.toFloatOrNull()?.let(prices::add) } }
        launch { api.orderBookFlow(symbol).collect { book = it } }
        while (true) {
            runCatching {
                ticker = api.ticker(symbol)
                book = api.orderBook(symbol)
                technicalAnalysis = api.technicalAnalysis(symbol)
            }
            delay(refreshSeconds * 1_000L)
        }
    }
    LaunchedEffect(refreshSeconds) {
        while (true) {
            runCatching { portfolio = api.portfolio() }
            runCatching { orders = api.orders() }
            runCatching { fills = api.fills() }
            runCatching { health = api.health(); gatewayReachable = true }
                .onFailure { gatewayReachable = false }
            delay(refreshSeconds * 1_000L)
        }
    }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Page.entries.filter { it != Page.WORKSPACE }.forEach { item ->
                NavigationBarItem(selected = page == item || (page == Page.WORKSPACE && item == Page.SETTINGS), onClick = { page = item }, icon = { Text(when (item) { Page.TRADE -> "↗"; Page.PORTFOLIO -> "◫"; Page.ORDERS -> "≡"; Page.ASSISTANT -> "✦"; Page.WORKSPACE -> "⚙"; Page.SETTINGS -> "☷" }, fontSize = 20.sp) }, label = { Text(item.title, fontSize = 10.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ARGENT", color = Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("${me.displayName} · ${me.tenantRole}", color = Muted, fontSize = 11.sp)
            }
            val marketTickerText = marketTickers.values.sortedBy { it.symbol }
                .joinToString("     •     ") { "${it.symbol} ${it.price}" }
            if (marketTickerText.isNotBlank()) Text(
                "$marketTickerText     •     $marketTickerText",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                    .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
                color = Color(0xFFE0E4E8), fontSize = 10.sp, maxLines = 1, softWrap = false
            )
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0)
            )
            message?.let { Text(it, color = if (it.contains("hata", true) || it.contains("REJECTED", true)) Red else Gold, modifier = Modifier.padding(horizontal = 12.dp)) }
            when (page) {
                Page.TRADE -> TradePage(api, instruments, symbol, { symbol = it }, ticker, book, technicalAnalysis, prices, busy, marketRefreshSeconds = marketRefreshSeconds,
                    onMessage = { message = it }, onBusy = { busy = it }, onRefresh = { portfolio = api.portfolio(); orders = api.orders() },
                    onDraftChange = { side, type, quantity, price -> draftSide = side; draftType = type; draftQuantity = quantity; draftPrice = price })
                Page.PORTFOLIO -> PortfolioPage(portfolio)
                Page.ORDERS -> OrdersPage(orders, fills, api, onMessage = { message = it })
                Page.ASSISTANT -> AssistantScreen(api, symbol, draftSide, draftType, draftQuantity, draftPrice)
                Page.WORKSPACE -> WorkspacePage(api, me, { me = it; onMeChanged(it) }, onMessage = { message = it }, onLogout = onLogout, onBack = { page = Page.SETTINGS })
                Page.SETTINGS -> SettingsPage(me, dark, fontScale, refreshSeconds, marketRefreshSeconds, onSettingsPreview, onLanguagePreview, onSettingsSave, onOpenWorkspace = { page = Page.WORKSPACE })
            }
        }
    }
}

private data class AssistantUiMessage(val fromUser: Boolean, val text: String, val metadata: String? = null)

private sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock
    data class Paragraph(val text: String) : AssistantBlock
    data class Bullet(val text: String) : AssistantBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
private fun AssistantScreen(api: ArgentApi, symbol: String, draftSide: String, draftType: String, draftQuantity: String, draftPrice: String) {
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AssistantUiMessage>() }
    val listState = rememberLazyListState()
    var prompt by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingOrderDraft by remember { mutableStateOf<AssistantOrderDraftDto?>(null) }
    var confirmOrderDraft by remember { mutableStateOf(false) }
    var orderConfirmPassword by remember { mutableStateOf("") }
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
                Text(if (language == "en") "APPROVED" else "ONAYLI", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        Text(
            when (enabled) {
                true -> "Yanıtlar ve emir taslakları · Emir yalnızca parola ile açık onaydan sonra gönderilir"
                false -> "Asistan kapalı · Gateway AI anahtarını kontrol et"
                null -> "Asistan bağlantısı kontrol ediliyor…"
            }, color = Muted, fontSize = 10.sp
        )
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Ne öğrenmek istersin?", color = Color(0xFFE0E4E8), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Hesap verilerin ve seçili piyasa bilgisi üzerinden sorabilirsin.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                (if (language == "en") listOf("My cancelled orders from the last 7 days", "Check my risk and data-feed alerts", "How concentrated is my portfolio?") else listOf("Son 7 gündeki iptal edilen emirlerim", "Risk ve veri akışı uyarılarımı kontrol et", "Portföyümde yoğunlaşma ne durumda?")).forEach { suggestion ->
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
        pendingOrderDraft?.let { draft ->
            Surface(color = Panel, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EMİR TASLAĞI · GÖNDERİLMEDİ", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (language == "en") "${draft.symbol} ${draft.side} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}" else "${draft.symbol} ${if (draft.side == "BUY") "ALIŞ" else "SATIŞ"} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}", color = Color(0xFFE0E4E8), fontSize = 11.sp)
                    val quoteTime = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(draft.dataAsOf))
                    Text(if (language == "en") "Estimated amount ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · as of ${quoteTime}. ${if (draft.note.contains("anlık")) "The limit price was taken from the current ${if (draft.side == "BUY") "ask" else "bid"} quote; review it before submission." else "The limit price came from your request; review it before submission."}" else "Tahmini tutar ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · veri ${quoteTime}. ${draft.note}", color = Muted, fontSize = 10.sp)
                    Button(onClick = { confirmOrderDraft = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("ÖZETİ ONAYLA VE PAROLAYLA GÖNDER") }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, enabled = !busy,
                placeholder = { Text("ARGENT'e sor…") }, minLines = 1, maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(enabled = !busy && prompt.isNotBlank() && (enabled != false || looksLikeOrderDraftRequest(prompt)), onClick = {
                val question = prompt.trim(); prompt = ""; messages.add(AssistantUiMessage(true, question)); busy = true
                scope.launch {
                    try {
                        if (looksLikeOrderDraftRequest(question)) {
                            runCatching { api.createAssistantOrderDraft(question) }
                                .onSuccess { draft -> pendingOrderDraft = draft; messages.add(AssistantUiMessage(false, if (language == "en") "Draft ready: ${draft.side} LIMIT ${draft.quantity} ${draft.symbol} at ${draft.price} ${draft.quoteAsset}. It has not been submitted; review the summary below and confirm it separately." else "Taslağı hazırladım: ${draft.symbol} ${draft.side} LIMIT, ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}. Henüz gönderilmedi; alttaki özeti kontrol edip ayrıca onaylayabilirsin.")) }
                                .onFailure { messages.add(AssistantUiMessage(false, com.argent.terminal.api.UiTranslations.translate(it.message ?: if (language == "en") "Could not create the order draft." else "Emir taslağı oluşturulamadı.", language))) }
                        } else {
                            runCatching { api.askAssistant(question, symbol, draftSide, draftType, draftQuantity, draftPrice, language) }
                                .onSuccess { result ->
                                    messages.add(AssistantUiMessage(false, cleanAssistantAnswer(result.answer)))
                                }
                                .onFailure { messages.add(AssistantUiMessage(false, com.argent.terminal.api.UiTranslations.translate(it.message ?: if (language == "en") "The assistant could not reply." else "Asistan yanıt veremedi.", language))) }
                        }
                    } finally { busy = false }
                }
            }, modifier = Modifier.height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Ink, strokeWidth = 2.dp) else Text("Sor")
            }
        }
    }
    if (confirmOrderDraft) AlertDialog(
        onDismissRequest = { confirmOrderDraft = false; orderConfirmPassword = "" },
        title = { Text("Emri son kez kontrol et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pendingOrderDraft?.let { d -> Text(if (language == "en") "${d.symbol} · ${d.side} LIMIT\n${d.quantity} @ ${d.price} ${d.quoteAsset}\nEstimated amount: ${d.quoteNotional} ${d.quoteAsset}" else "${d.symbol} · ${d.side} LIMIT\n${d.quantity} adet @ ${d.price} ${d.quoteAsset}\nTahmini tutar: ${d.quoteNotional} ${d.quoteAsset}", color = Color(0xFFE0E4E8)) }
                Text("Bu onay emri ARGENT dahili eşleştirme motoruna gönderir.", color = Muted)
                OutlinedTextField(orderConfirmPassword, { orderConfirmPassword = it }, label = { Text("Hesap parolası") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(enabled = orderConfirmPassword.isNotBlank(), onClick = {
            val draft = pendingOrderDraft ?: return@TextButton
            confirmOrderDraft = false; busy = true
            scope.launch { runCatching { api.confirmAssistantOrderDraft(draft.draftId, orderConfirmPassword) }
                .onSuccess { result -> pendingOrderDraft = null; messages.add(AssistantUiMessage(false, if (language == "en") "Order result: ${result.status} · ${result.filledQuantity} filled · ${result.remainingQuantity} remaining." else "Emir sonucu: ${result.status} · ${result.filledQuantity} doldu · ${result.remainingQuantity} kaldı.")) }
                .onFailure { messages.add(AssistantUiMessage(false, com.argent.terminal.api.UiTranslations.translate(it.message ?: if (language == "en") "Could not submit the order." else "Emir gönderilemedi.", language))) }
                .also { orderConfirmPassword = ""; busy = false } }
        }) { Text("ONAYLA VE GÖNDER", color = Green) } },
        dismissButton = { TextButton(onClick = { confirmOrderDraft = false; orderConfirmPassword = "" }) { Text("VAZGEÇ") } }
    )
}

private fun looksLikeOrderDraftRequest(text: String): Boolean {
    val q = text.lowercase()
    return ("limit" in q || "limitli" in q) &&
        ("alış" in q || "alis" in q || "buy" in q || "sat" in q || "sell" in q) &&
        listOf("usdt", "usd", "try", "tl").any(q::contains)
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
            color = if (message.fromUser) { if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8)) Color(0xFFFFF2C6) else Color(0xFF282615) } else Panel,
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
                        Surface(color = if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8)) Color(0xFFF3F5F8) else Color(0xFF101A20), shape = RoundedCornerShape(8.dp)) {
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
                      ticker: TickerDto?, book: OrderBookDto, analysis: TechnicalAnalysisDto?, prices: List<Float>, busy: Boolean,
                      onMessage: (String) -> Unit, onBusy: (Boolean) -> Unit, onRefresh: suspend () -> Unit, marketRefreshSeconds: Int,
                      onDraftChange: (String, String, String, String) -> Unit) {
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    LaunchedEffect(symbol, side, type, quantity, price) { onDraftChange(side, type, quantity, price) }
    val instrument = instruments.firstOrNull { it.symbol == symbol }
    val sourceBadge = if (instrument?.assetClass == "CRYPTO" && ticker?.source?.equals("binance", ignoreCase = true) == true) "BINANCE" else null
    val quantityValue = quantity.trim().replace(',', '.').toBigDecimalOrNull()
    val priceValue = price.trim().replace(',', '.').toBigDecimalOrNull()
    val lotSize = instrument?.lotSize?.toBigDecimalOrNull()
    val tickSize = instrument?.tickSize?.toBigDecimalOrNull()
    val validation = when {
        instrument == null -> if (language == "en") "Loading instrument details." else "Enstrüman bilgisi yükleniyor."
        quantityValue == null || quantityValue.signum() <= 0 -> if (language == "en") "Quantity must be greater than zero." else "Miktar pozitif bir sayı olmalı."
        lotSize != null && quantityValue.remainder(lotSize).signum() != 0 -> if (language == "en") "Quantity must match the lot size (${instrument.lotSize})." else "Miktar lot adımına uygun değil (${instrument.lotSize})."
        type == "MARKET" && ticker?.price?.toBigDecimalOrNull() == null -> if (language == "en") "Waiting for a market price." else "Piyasa fiyatı henüz alınmadı."
        type != "MARKET" && (priceValue == null || priceValue.signum() <= 0) -> if (language == "en") "Price must be greater than zero." else "Fiyat pozitif bir sayı olmalı."
        type != "MARKET" && tickSize != null && priceValue!!.remainder(tickSize).signum() != 0 -> if (language == "en") "Price must match the tick size (${instrument.tickSize})." else "Fiyat tick adımına uygun değil (${instrument.tickSize})."
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
                    instrument?.let { Text(if (language == "en") "Tick size ${it.tickSize} · Lot size ${it.lotSize}" else "${it.displayName} · tick ${it.tickSize} · lot ${it.lotSize}", color = Muted, fontSize = 10.sp) }
                    Text("Bid ${ticker?.bid ?: book.bestBid ?: "—"}   Ask ${ticker?.ask ?: book.bestAsk ?: "—"}", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    PriceChart(prices, Modifier.fillMaxWidth().height(168.dp))
                }
            }
        }
        item { TechnicalAnalysisCard(analysis) }
        item { OrderBookCard(book) { price = it } }
        item { ExternalMarketSection(api, onMessage, marketRefreshSeconds) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (language == "en") "ORDER" else "EMİR", fontWeight = FontWeight.Bold, color = Gold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice(if (language == "en") "BUY" else "AL", side == "BUY") { side = "BUY" }; Choice(if (language == "en") "SELL" else "SAT", side == "SELL") { side = "SELL" } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { Choice(it, type == it) { type = it } } }
                    if (type != "MARKET") Field(if (language == "en") "Price" else "Fiyat", price, { price = it }, KeyboardType.Decimal)
                    Field(if (language == "en") "Quantity" else "Miktar", quantity, { quantity = it }, KeyboardType.Decimal)
                    Text(if (language == "en") "Estimated notional  $notional" else "Tahmini tutar  $notional", color = Muted, fontSize = 11.sp)
                    validation?.let { Text(it, color = Red, fontSize = 11.sp) }
                    Button(enabled = !busy && validation == null, onClick = {
                        onBusy(true)
                        scope.launch {
                            try {
                                val normalizedQuantity = quantity.trim().replace(',', '.')
                                val normalizedPrice = price.trim().replace(',', '.').ifBlank { null }
                                require(normalizedQuantity.toBigDecimal() > java.math.BigDecimal.ZERO) { if (language == "en") "Quantity must be greater than zero." else "Miktar sıfırdan büyük olmalı." }
                                val activeInstrument = requireNotNull(instrument) { if (language == "en") "Instrument details are unavailable." else "Enstrüman bilgisi yüklenemedi." }
                                lotSize?.let { require(normalizedQuantity.toBigDecimal().remainder(it).signum() == 0) { if (language == "en") "Quantity must match the lot size (${activeInstrument.lotSize})." else "Miktar lot adımına uygun değil (${activeInstrument.lotSize})." } }
                                if (type == "MARKET") require(ticker?.price?.toBigDecimalOrNull() != null) { if (language == "en") "Waiting for a market price." else "Piyasa fiyatı henüz alınmadı." }
                                if (type != "MARKET") require(!normalizedPrice.isNullOrBlank() && normalizedPrice.toBigDecimal() > java.math.BigDecimal.ZERO) { if (language == "en") "Price must be greater than zero." else "Fiyat sıfırdan büyük olmalı." }
                                if (type != "MARKET" && tickSize != null) require(normalizedPrice!!.toBigDecimal().remainder(tickSize).signum() == 0) { if (language == "en") "Price must match the tick size (${activeInstrument.tickSize})." else "Fiyat tick adımına uygun değil (${activeInstrument.tickSize})." }
                                val result = api.placeOrder(symbol, side, type, normalizedQuantity, normalizedPrice, UUID.randomUUID().toString())
                                onMessage(if (language == "en") "Order result: ${result.status} · ${result.filledQuantity} filled." else "${result.status} · dolan ${result.filledQuantity} · ${result.reason}")
                                onRefresh()
                            } catch (e: Exception) { onMessage(if (language == "en") "Order could not be placed. Check the order details and try again." else "Emir hatası: ${e.message}") }
                            finally { onBusy(false) }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (side == "BUY") Green else Red, contentColor = Ink)) {
                        Text(if (language == "en") { if (busy) "PROCESSING…" else if (side == "BUY") "SUBMIT BUY ORDER" else "SUBMIT SELL ORDER" } else { if (busy) "İŞLENİYOR…" else if (side == "BUY") "ALIŞ EMRİ GÖNDER" else "SATIŞ EMRİ GÖNDER" }, fontWeight = FontWeight.Bold)
                    }
                    Text(if (language == "en") "Orders stay in ARGENT’s internal paper-trading matching engine; they are not sent to a real exchange." else "Gerçek borsaya iletilmez; dahili eşleştirme paper-trading motorunda çalışır.", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ExternalMarketSection(api: ArgentApi, onMessage: (String) -> Unit, refreshSeconds: Int) {
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val symbols = listOf("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA")
    var provider by remember { mutableStateOf<ExternalMarketStatusDto?>(null) }
    var quotes by remember { mutableStateOf(emptyMap<String, ExternalQuoteDto>()) }
    var news by remember { mutableStateOf(emptyList<MarketNewsDto>()) }
    var alerts by remember { mutableStateOf(emptyList<PriceAlertDto>()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf("AAPL") }
    var threshold by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("ABOVE") }
    val refresh: suspend () -> Unit = {
        provider = runCatching { api.externalMarketStatus() }.getOrNull()
        if (provider?.configured == true) {
            val quoteResults = symbols.map { s -> runCatching { api.nasdaqQuote(s) } }
            quotes = quoteResults.mapNotNull { it.getOrNull()?.let { q -> q.symbol to q } }.toMap()
            feedError = quoteResults.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            runCatching { api.marketNews() }.onSuccess { news = it }.onFailure { if (feedError == null) feedError = it.message }
        }
        alerts = runCatching { api.priceAlerts() }.getOrDefault(alerts)
    }
    LaunchedEffect(refreshSeconds) { while (true) { refresh(); delay(refreshSeconds * 1_000L) } }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("NASDAQ · HABER · FİYAT ALARMLARI", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(if (provider?.configured == true) "Finnhub · API anahtarı bağlı; erişim sağlayıcı hesabına bağlı" else "Gerçek veri kaynağı yapılandırılmadı", color = Muted, fontSize = 10.sp)
                }
                Text("Otomatik · ${refreshSeconds / 60} dk", color = Muted, fontSize = 9.sp)
            }
            if (provider?.configured != true) {
                    Text("FINNHUB_API_KEY eklenince hesabınızın kapsamındaki gerçek Nasdaq referans fiyatları ve haberler açılır. Burada örnek fiyat kullanılmaz.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
            } else {
                feedError?.let { Text("Gerçek veri alınamadı: $it", color = Red, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quotes.values.forEach { q ->
                        Card(Modifier.clickable { selected = q.symbol }, colors = CardDefaults.cardColors(containerColor = if (selected == q.symbol) Gold.copy(alpha = .18f) else Ink)) {
                            Column(Modifier.padding(8.dp)) {
                                Text(q.symbol, color = Color(0xFFE0E4E8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("USD ${q.price}", color = Gold, fontSize = 11.sp)
                                Text("${q.changePercent ?: "—"}%", color = Muted, fontSize = 9.sp)
                                Text("Finnhub · ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(q.timestamp))}", color = Muted, fontSize = 8.sp)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Choice(if (language == "en") "ABOVE" else "ÜSTÜ", direction == "ABOVE") { direction = "ABOVE" }
                    Choice(if (language == "en") "BELOW" else "ALTI", direction == "BELOW") { direction = "BELOW" }
                    Field(if (language == "en") "USD threshold" else "USD eşik", threshold, { threshold = it }, KeyboardType.Decimal, modifier = Modifier.width(116.dp))
                    Button(enabled = threshold.toBigDecimalOrNull()?.signum() == 1, onClick = {
                        scope.launch { runCatching { api.createPriceAlert(selected, direction, threshold); threshold = ""; alerts = api.priceAlerts() }
                            .onSuccess { onMessage(if (language == "en") "$selected price alert created" else "$selected fiyat alarmı kuruldu") }.onFailure { onMessage(if (language == "en") "Could not create alert: ${it.message}" else "Alarm kurulamadı: ${it.message}") } }
                    }) { Text("KUR") }
                }
                alerts.forEach { alert ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (language == "en") "${alert.symbol} ${if (alert.direction == "ABOVE") "≥" else "≤"} USD ${alert.threshold} · ${if (alert.state == "ACTIVE") "waiting" else "triggered @ USD ${alert.triggeredPrice}"}" else "${alert.symbol} ${if (alert.direction == "ABOVE") "≥" else "≤"} USD ${alert.threshold} · ${if (alert.state == "ACTIVE") "bekliyor" else "tetiklendi @ USD ${alert.triggeredPrice}"}", color = if (alert.state == "TRIGGERED") Green else Muted, fontSize = 10.sp)
                        TextButton(onClick = { scope.launch { runCatching { api.deletePriceAlert(alert.id); alerts = api.priceAlerts() }.onFailure { onMessage(if (language == "en") "Could not delete alert" else "Alarm silinemedi") } } }) { Text("Sil", color = Red, fontSize = 10.sp) }
                    }
                }
                Text("PİYASA HABERLERİ", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                news.take(5).forEach { n ->
                    Row(Modifier.fillMaxWidth().clickable { runCatching { uriHandler.openUri(n.url) } }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        NewsThumbnail(api, n.imageUrl, Modifier.width(88.dp).height(58.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (language == "en") n.headline else (n.translatedHeadline ?: n.headline), color = Color(0xFFE0E4E8), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${n.source} · ${n.relatedSymbols.ifBlank { "Piyasa" }} · ${java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(java.util.Date(n.publishedAt))}", color = Muted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsThumbnail(api: ArgentApi, imageUrl: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        bitmap = imageUrl?.let { url ->
            val bytes = api.marketNewsImage(url)
            withContext(kotlinx.coroutines.Dispatchers.IO) { bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
        }
    }
    if (bitmap != null) Image(bitmap!!, contentDescription = "Haber görseli", modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else Box(modifier.background(Panel, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
        Text("ARGENT", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable
private fun TechnicalAnalysisCard(analysis: TechnicalAnalysisDto?) {
    val observedAt = analysis?.timestamp?.takeIf { it > 0 }?.let {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))
    } ?: "—"
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TEKNİK GÖRÜNÜM", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TechnicalValue("Trend", when (analysis?.trend) { "UPWARD" -> "Yukarı"; "DOWNWARD" -> "Aşağı"; "MIXED" -> "Karışık"; else -> "Veri bekleniyor" })
                TechnicalValue("RSI 14", analysis?.rsi14 ?: "—")
                TechnicalValue("SMA 20", analysis?.sma20 ?: "—")
                TechnicalValue("SMA 50", analysis?.sma50 ?: "—")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TechnicalValue("20 örnek değişim", analysis?.change20Pct?.let { "$it%" } ?: "—")
                TechnicalValue("Son 30 dip / tepe", "${analysis?.support30 ?: "—"} / ${analysis?.resistance30 ?: "—"}")
                TechnicalValue("Örnek", analysis?.sampleCount?.toString() ?: "0")
            }
            Text("Son fiyat gözlemleri · ${analysis?.source ?: "kaynak bekleniyor"} · $observedAt. Sabit aralıklı mum verisi değildir; yatırım tavsiyesi sayılmaz.", color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun TechnicalValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, fontSize = 9.sp)
        Text(value, color = Color(0xFFE0E4E8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
private fun SettingsPage(me: MeResponse, dark: Boolean, fontScale: Float, refresh: Int, marketRefresh: Int, onPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onSave: (Boolean, Float, Int, Int, String) -> Unit, onOpenWorkspace: () -> Unit) {
    val currentLanguage = LocalUiLanguage.current
    var d by remember { mutableStateOf(dark) }; var size by remember { mutableStateOf(fontScale) }; var interval by remember { mutableStateOf(refresh) }; var marketInterval by remember { mutableStateOf(marketRefresh) }; var language by remember { mutableStateOf(currentLanguage) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("AYARLAR", color=Gold, fontWeight=FontWeight.Bold) }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Hesap", fontWeight=FontWeight.Bold); Text(me.displayName, color=Muted); Text(me.email, color=Muted); Text("Rol: ${me.tenantRole}", color=Muted) } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(if (language == "tr") "Dil" else "Language", fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Choice(if (language == "tr") "✓ Türkçe" else "Türkçe", language == "tr") { language = "tr"; onLanguagePreview(language) }
                Choice(if (language == "en") "✓ English" else "English", language == "en") { language = "en"; onLanguagePreview(language) }
            }
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Görünüm", fontWeight=FontWeight.Bold)
            Row { Choice(if (d) "✓ Koyu" else "Koyu", d) { d=true; onPreview(d,size) }; Choice(if (!d) "✓ Açık" else "Açık", !d) { d=false; onPreview(d,size) } }
            Text("Metin boyutu ${(size*100).toInt()}%"); Slider(value=size,onValueChange={size=it;onPreview(d,size)},valueRange=.85f..1.3f)
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Piyasa verileri", fontWeight=FontWeight.Bold)
            Text("Finnhub kotasyonları ve haberler ekran açıkken otomatik yenilenir.", color=Muted, fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(60 to "1 dk", 300 to "5 dk", 900 to "15 dk").forEach { (seconds,label) -> Choice(if(marketInterval==seconds) "✓ $label" else label, marketInterval==seconds) { marketInterval=seconds } } }
            Text("Genel hesap ve emir güncellemesi: $interval sn", color=Muted, fontSize=11.sp)
            Slider(value=interval.toFloat(),onValueChange={interval=it.toInt().coerceAtLeast(1)},valueRange=1f..10f,steps=8)
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Emir güvenliği", fontWeight=FontWeight.Bold)
            Text("AI emir taslakları özet ve hesap parolasıyla onaylanır; AI kendi başına emir göndermez.", color=Muted, fontSize=11.sp)
        } } }
        item { OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) { Text(if (language == "en") "Workspace settings" else "Çalışma alanı ayarları") } }
        item { Button(onClick={onSave(d,size,interval,marketInterval,language)}) { Text("Ayarları kaydet") } }
        item { Text("Piyasa kapsamı", color=Gold, fontWeight=FontWeight.Bold) }
        item { Text("Kripto: Binance public feed. BIST ve sağlayıcı anahtarı bulunmayan ürünler: paper simülasyonu. Veri yenileme aralığı, Finnhub kota kullanımını kontrol etmeye yardımcı olur.", color=Muted) }
    }
}@Composable
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
                            Column(Modifier.weight(1f)) { Text("${order.symbol} ${order.side} ${order.type}", fontWeight = FontWeight.Bold); Text(if (LocalUiLanguage.current == "en") "${orderStatusLabel(order.status, LocalUiLanguage.current)} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "market"}" else "${orderStatusLabel(order.status, LocalUiLanguage.current)} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "piyasa"}", color = Muted, fontSize = 11.sp) }
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
                    DataRow("${order.symbol} ${order.side} ${order.type} · ${orderStatusLabel(order.status, LocalUiLanguage.current)}", if (LocalUiLanguage.current == "en") "Filled ${order.filledQuantity}/${order.quantity} · remaining ${order.remainingQuantity} · ${order.price ?: "market"}" else "Dolan ${order.filledQuantity}/${order.quantity} · kalan ${order.remainingQuantity} · ${order.price ?: "piyasa"}")
                }
                item { Text("GERÇEKLEŞEN İŞLEMLER", color = Gold, fontWeight = FontWeight.Bold) }
                if (fills.isEmpty()) item { Text("İşlem geçmişi boş.", color = Muted) }
                items(fills) { trade -> DataRow(trade.symbol, "${trade.quantity} @ ${trade.price}") }
            }
        }
    }
}

private fun orderStatusLabel(status: String, language: String = "tr"): String = when (status.uppercase()) {
    "ACCEPTED", "NEW", "PENDING" -> if (language == "en") "OPEN" else "AÇIK"
    "PARTIALLY_FILLED" -> if (language == "en") "PARTIALLY FILLED" else "KISMEN DOLDU"
    "FILLED" -> if (language == "en") "FILLED" else "DOLDU"
    "CANCELLED", "CANCELED" -> if (language == "en") "CANCELLED" else "İPTAL"
    "REJECTED" -> if (language == "en") "REJECTED" else "REDDEDİLDİ"
    "EXPIRED" -> if (language == "en") "EXPIRED" else "SÜRESİ DOLDU"
    else -> status
}

@Composable
private fun WorkspacePage(api: ArgentApi, me: MeResponse, onMeChanged: (MeResponse) -> Unit, onMessage: (String) -> Unit, onLogout: () -> Unit, onBack: () -> Unit) {
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
        item { TextButton(onClick = onBack) { Text(if (LocalUiLanguage.current == "en") "← Settings" else "← Ayarlar") } }
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
private fun Field(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = true,
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
        TradePage(
            api = remember { ArgentApi() },
            instruments = sampleInstruments,
            symbol = "THYAO",
            onSymbol = {},
            ticker = sampleTicker,
            book = sampleBook,
            analysis = null,
            prices = samplePrices,
            busy = false,
            onMessage = {},
            onBusy = {},
            onRefresh = {},
            marketRefreshSeconds = 60,
            onDraftChange = { _, _, _, _ -> }
        )
    }
}
