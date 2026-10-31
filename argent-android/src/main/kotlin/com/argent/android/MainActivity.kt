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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
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


internal val LocalUiLanguage = androidx.compose.runtime.compositionLocalOf { "tr" }

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

internal data class MobilePalette(val ink: Color, val panel: Color, val line: Color, val gold: Color, val green: Color, val red: Color, val muted: Color, val text: Color)
internal object MobilePaletteHolder { var current = MobilePalette(Color(0xFF0B0E12), Color(0xFF151A21), Color(0xFF29313C), Color(0xFFE0B82F), Color(0xFF00C98A), Color(0xFFFF5065), Color(0xFF98A3B3), Color(0xFFE5E8ED)) }
internal val Ink: Color get() = MobilePaletteHolder.current.ink
internal val Panel: Color get() = MobilePaletteHolder.current.panel
internal val Line: Color get() = MobilePaletteHolder.current.line
internal val Gold: Color get() = MobilePaletteHolder.current.gold
internal val Green: Color get() = MobilePaletteHolder.current.green
internal val Red: Color get() = MobilePaletteHolder.current.red
internal val Muted: Color get() = MobilePaletteHolder.current.muted
internal val AppText: Color get() = MobilePaletteHolder.current.text
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
            var guest by remember { mutableStateOf(false) }
            if (me == null && !guest) LoginScreen(api, onLogin = { me = it }, onGuest = { guest = true })
            else {
                val activeUser = me ?: MeResponse("guest", "", if (language == "en") "Guest" else "Misafir", "GUEST")
                MainScreen(api, activeUser, isGuest = guest, onMeChanged = { me=it; guest=false }, onLogout = { if (guest) guest=false else me=null }, dark=dark, fontScale=scale, refreshSeconds=refresh, marketRefreshSeconds=marketRefresh, onSettingsPreview={ d,s -> dark=d; scale=s }, onLanguagePreview={ language=it }, onSettingsCancel={ dark=savedDark; scale=savedScale; refresh=savedRefresh; marketRefresh=savedMarketRefresh; language=savedLanguage }, onSettingsSave={ d,s,r,mr,lang -> dark=d; scale=s; refresh=r; marketRefresh=mr; language=lang; savedDark=d; savedScale=s; savedRefresh=r; savedMarketRefresh=mr; savedLanguage=lang; prefs.edit().putBoolean("dark",d).putFloat("scale",s).putInt("refresh",r).putInt("marketRefresh",mr).putString("language",lang).apply() })
            }
        }
      }
}
@Composable
private fun LoginScreen(api: ArgentApi, onLogin: (MeResponse) -> Unit, onGuest: () -> Unit) {
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
            TextButton(enabled = !busy, onClick = {
                api.baseUrl = gateway.trim().trimEnd('/')
                onGuest()
            }, modifier = Modifier.fillMaxWidth()) { Text(if (LocalUiLanguage.current == "en") "Continue as guest" else "Misafir olarak giriş yap") }
        }
    }
}

private enum class Page(val title: String) { TRADE("Piyasa"), PORTFOLIO("Portföy"), ORDERS("Emirler"), ASSISTANT("AI"), WORKSPACE("Çalışma alanı"), SETTINGS("Ayarlar") }

private fun Page.localizedTitle(language: String): String = when (this) {
    Page.TRADE -> if (language == "en") "Markets" else "Piyasa"
    Page.PORTFOLIO -> if (language == "en") "Portfolio" else "Portföy"
    Page.ORDERS -> if (language == "en") "Orders" else "Emirler"
    Page.ASSISTANT -> "AI"
    Page.WORKSPACE -> if (language == "en") "Workspace" else "Çalışma alanı"
    Page.SETTINGS -> if (language == "en") "Settings" else "Ayarlar"
}

@Composable
private fun GuestAccessPage(section: String, onLogin: () -> Unit) {
    val english = LocalUiLanguage.current == "en"
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (english) "Sign in required" else "Giriş yapmanız gerekiyor", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (english) "Sign in to access $section."
            else "$section bölümüne erişmek için lütfen kullanıcı girişi yapın.",
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onLogin) { Text(if (english) "Go to sign in" else "Giriş ekranına dön") }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MainScreen(api: ArgentApi, initialMe: MeResponse, isGuest: Boolean = false, onMeChanged: (MeResponse) -> Unit, onLogout: () -> Unit, dark: Boolean, fontScale: Float, refreshSeconds: Int, marketRefreshSeconds: Int, onSettingsPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onSettingsCancel: () -> Unit, onSettingsSave: (Boolean, Float, Int, Int, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val uiLanguage = LocalUiLanguage.current
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
        if (!isGuest) api.orderUpdatesFlow().collect { update ->
            message = "Emir güncellemesi · ${'$'}{update.symbol} · ${'$'}{update.status}"
        }
    }

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
            runCatching { health = api.health(); gatewayReachable = true }
                .onFailure { gatewayReachable = false }
            delay(refreshSeconds * 1_000L)
        }
    }
    Scaffold(containerColor = Ink, bottomBar = {
        NavigationBar(containerColor = Panel) {
            Page.entries.filter { it != Page.WORKSPACE }.forEach { item ->
                NavigationBarItem(selected = page == item || (page == Page.WORKSPACE && item == Page.SETTINGS), onClick = { page = item }, icon = { Text(when (item) { Page.TRADE -> "↗"; Page.PORTFOLIO -> "◫"; Page.ORDERS -> "≡"; Page.ASSISTANT -> "✦"; Page.WORKSPACE -> "⚙"; Page.SETTINGS -> "☷" }, fontSize = 20.sp) }, label = { Text(item.localizedTitle(LocalUiLanguage.current), fontSize = 10.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Image(painterResource(com.argent.android.R.drawable.ic_launcher_foreground), contentDescription = "ARGENT", modifier = Modifier.size(30.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Text(if (isGuest) { if (LocalUiLanguage.current == "en") "Guest · Sign in" else "Misafir · Giriş yap" } else "${me.displayName} · ${me.tenantRole}", color = Muted, fontSize = 11.sp, modifier = Modifier.clickable { if (isGuest) onLogout() })
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
                else -> health?.let { "${it.status} · ${it.executionMode} · CRYPTO ${it.cryptoMarketData} · BIST ${it.bistMarketData}" }.orEmpty()
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
                Page.TRADE -> TradePage(api, instruments, symbol, { symbol = it }, ticker, book, marketTrades, technicalAnalysis, prices, busy, isGuest = isGuest, onSignIn = onLogout, marketRefreshSeconds = marketRefreshSeconds,
                    onMessage = { message = it }, onBusy = { busy = it }, onRefresh = { portfolio = api.portfolio(); orders = api.orders() },
                    onDraftChange = { side, type, quantity, price -> draftSide = side; draftType = type; draftQuantity = quantity; draftPrice = price })
                Page.PORTFOLIO, Page.ORDERS, Page.ASSISTANT, Page.WORKSPACE -> if (isGuest) GuestAccessPage(
                    section = when (page) {
                        Page.PORTFOLIO -> if (uiLanguage == "en") "your portfolio" else "Portföy"
                        Page.ORDERS -> if (uiLanguage == "en") "your orders and trades" else "Emirler ve işlemler"
                        Page.ASSISTANT -> "ARGENT AI"
                        else -> if (uiLanguage == "en") "workspace settings" else "Çalışma alanı"
                    },
                    onLogin = onLogout
                ) else when (page) {
                    Page.PORTFOLIO -> PortfolioPage(portfolio)
                    Page.ORDERS -> OrdersPage(orders, fills, api, onMessage = { message = it })
                    Page.ASSISTANT -> AssistantScreen(api, symbol, draftSide, draftType, draftQuantity, draftPrice)
                    else -> WorkspacePage(api, me, { me = it; onMeChanged(it) }, onMessage = { message = it }, onLogout = onLogout, onBack = { page = Page.SETTINGS })
                }
                Page.SETTINGS -> SettingsPage(me, dark, fontScale, refreshSeconds, marketRefreshSeconds, onSettingsPreview, onLanguagePreview, onSettingsSave, onOpenWorkspace = { page = Page.WORKSPACE }, onSecureLogout = {
                    if (isGuest) onLogout() else scope.launch {
                        runCatching { api.logout(); onLogout() }
                            .onFailure { message = if (uiLanguage == "en") "Sign out failed: ${it.message}" else "Güvenli çıkış başarısız: ${it.message}" }
                    }
                })
            }
        }
    }
}

@Composable
private fun TradePage(api: ArgentApi, instruments: List<InstrumentDto>, symbol: String, onSymbol: (String) -> Unit,
                      ticker: TickerDto?, book: OrderBookDto, marketTrades: List<TradeDto>, analysis: TechnicalAnalysisDto?, prices: List<Float>, busy: Boolean, isGuest: Boolean = false,
                      guestAccessSection: String? = null,
                      onSignIn: () -> Unit = {},
                      onMessage: (String) -> Unit, onBusy: (Boolean) -> Unit, onRefresh: suspend () -> Unit, marketRefreshSeconds: Int,
                      onDraftChange: (String, String, String, String) -> Unit) {
    val language = LocalUiLanguage.current
    var chartExpanded by remember(symbol) { mutableStateOf(false) }
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
        type != "MARKET" && tickSize != null && priceValue != null && priceValue.remainder(tickSize).signum() != 0 -> if (language == "en") "Price must match the tick size (${instrument.tickSize})." else "Fiyat tick adımına uygun değil (${instrument.tickSize})."
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
                    instrument?.let {
                        Text(
                            if (language == "en") "${it.displayName} · Tick size ${it.tickSize} · Lot size ${it.lotSize}"
                            else "${it.displayName} · tick ${it.tickSize} · lot ${it.lotSize}",
                            color = Muted,
                            fontSize = 10.sp
                        )
                    }
                    Text("Bid ${ticker?.bid ?: book.bestBid ?: "—"}   Ask ${ticker?.ask ?: book.bestAsk ?: "—"}", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { chartExpanded = !chartExpanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (language == "en") { if (chartExpanded) "Hide detailed chart" else "Expand chart" } else { if (chartExpanded) "Grafiği daralt" else "Grafiği genişlet" }, color = Gold, fontSize = 10.sp)
                    }
                    PriceChart(prices, Modifier.fillMaxWidth().height(if (chartExpanded) 220.dp else 54.dp).clickable { chartExpanded = !chartExpanded })
                }
            }
        }
        item { TechnicalAnalysisCard(analysis) }
        item { OrderBookCard(book, readOnly = isGuest) { price = it } }
        item { PublicMarketTradesCard(marketTrades, instrument?.quoteAsset.orEmpty()) }
        guestAccessSection?.let { section -> item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (language == "en") "$section requires sign-in" else "$section için giriş yapmanız gerekiyor", color = Gold, fontWeight = FontWeight.Bold)
                    Text(if (language == "en") "Market prices, charts and public order-book data remain available to guests." else "Piyasa fiyatları, grafikler ve herkese açık emir defteri misafirler için açık kalır.", color = Muted, fontSize = 11.sp)
                    Button(onClick = onSignIn) { Text(if (language == "en") "SIGN IN" else "GİRİŞ YAP") }
                }
            }
        } }
        item { ExternalMarketSection(api, onMessage, marketRefreshSeconds, isGuest, onRequireLogin = { onMessage(if (language == "en") "Please sign in to manage personal price alerts." else "Kişisel fiyat alarmlarını yönetmek için lütfen giriş yapın.") }) }
        if (isGuest && guestAccessSection == null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (language == "en") "PAPER TRADING" else "SANAL İŞLEM", fontWeight = FontWeight.Bold, color = Gold)
                    Text(if (language == "en") "Market data, chart, order book and public executions are available in guest mode. Sign in to place an order." else "Misafir modunda piyasa verileri, grafik, emir defteri ve herkese açık işlemler görüntülenebilir. Emir vermek için giriş yapın.", color = Muted, fontSize = 11.sp)
                    Button(onClick = onSignIn) {
                        Text(if (language == "en") "SIGN IN TO TRADE" else "EMİR İÇİN GİRİŞ YAPIN")
                    }
                }
            }
        } else if (!isGuest) item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (language == "en") "ORDER" else "EMİR", fontWeight = FontWeight.Bold, color = Gold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice(if (language == "en") "BUY" else "AL", side == "BUY") { side = "BUY" }; Choice(if (language == "en") "SELL" else "SAT", side == "SELL") { side = "SELL" } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { Choice(it, type == it) { type = it } } }
                    if (type != "MARKET") Field(if (language == "en") "Price" else "Fiyat", price, { price = it }, KeyboardType.Decimal)
                    Field(if (language == "en") "Quantity" else "Miktar", quantity, { quantity = it }, KeyboardType.Decimal)
                    Text(if (language == "en") "Estimated notional  $notional" else "Tahmini tutar  $notional", color = Muted, fontSize = 11.sp)
                    validation?.let { Text(it, color = Red, fontSize = 11.sp) }
                    Button(enabled = !busy && (validation == null || isGuest), onClick = {
                        if (isGuest) {
                            onMessage(if (language == "en") "Please sign in to place an order." else "Emir vermek için lütfen giriş yapın.")
                        } else {
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
                                if (type != "MARKET" && tickSize != null) require(normalizedPrice?.toBigDecimal()?.remainder(tickSize)?.signum() == 0) { if (language == "en") "Price must match the tick size (${activeInstrument.tickSize})." else "Fiyat tick adımına uygun değil (${activeInstrument.tickSize})." }
                                val result = api.placeOrder(symbol, side, type, normalizedQuantity, normalizedPrice, UUID.randomUUID().toString())
                                onMessage(if (language == "en") "Order result: ${result.status} · ${result.filledQuantity} filled." else "${result.status} · dolan ${result.filledQuantity} · ${result.reason}")
                                onRefresh()
                            } catch (e: Exception) { onMessage(if (language == "en") "Order could not be placed. Check the order details and try again." else "Emir hatası: ${e.message}") }
                            finally { onBusy(false) }
                        }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = if (side == "BUY") Green else Red, contentColor = Ink)) {
                        Text(if (isGuest) { if (language == "en") "SIGN IN TO TRADE" else "EMİR İÇİN GİRİŞ YAPIN" } else if (language == "en") { if (busy) "PROCESSING…" else if (side == "BUY") "SUBMIT BUY ORDER" else "SUBMIT SELL ORDER" } else { if (busy) "İŞLENİYOR…" else if (side == "BUY") "ALIŞ EMRİ GÖNDER" else "SATIŞ EMRİ GÖNDER" }, fontWeight = FontWeight.Bold)
                    }
                    Text(if (language == "en") "Orders stay in ARGENT’s internal paper-trading matching engine; they are not sent to a real exchange." else "Gerçek borsaya iletilmez; dahili eşleştirme paper-trading motorunda çalışır.", color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ExternalMarketSection(api: ArgentApi, onMessage: (String) -> Unit, refreshSeconds: Int, guestMode: Boolean = false, onRequireLogin: () -> Unit = {}) {
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
        if (!guestMode) alerts = runCatching { api.priceAlerts() }.getOrDefault(alerts)
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
                if (guestMode) {
                    TextButton(onClick = onRequireLogin) { Text(if (language == "en") "Sign in to manage personal price alerts" else "Kişisel fiyat alarmları için giriş yapın") }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Choice(if (language == "en") "ABOVE" else "ÜSTÜ", direction == "ABOVE") { direction = "ABOVE" }
                    Choice(if (language == "en") "BELOW" else "ALTI", direction == "BELOW") { direction = "BELOW" }
                    Field(if (language == "en") "USD threshold" else "USD eşik", threshold, { threshold = it }, KeyboardType.Decimal, modifier = Modifier.width(116.dp))
                    Button(enabled = threshold.toBigDecimalOrNull()?.signum() == 1, onClick = {
                        scope.launch { runCatching { api.createPriceAlert(selected, direction, threshold); threshold = ""; alerts = api.priceAlerts() }
                            .onSuccess { onMessage(if (language == "en") "$selected price alert created" else "$selected fiyat alarmı kuruldu") }.onFailure { onMessage(if (language == "en") "Could not create alert: ${it.message}" else "Alarm kurulamadı: ${it.message}") } }
                    }) { Text("KUR") }
                }
                if (!guestMode) alerts.forEach { alert ->
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
    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(currentBitmap, contentDescription = "Haber görseli", modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    } else {
        Box(modifier.background(Panel, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            Text("ARGENT", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
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
private fun OrderBookCard(book: OrderBookDto, readOnly: Boolean = false, onPrice: (String) -> Unit) {
    val language = LocalUiLanguage.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${book.symbol} · ${if (language == "en") "ORDER BOOK" else "EMİR DEFTERİ"}", color = Gold, fontWeight = FontWeight.Bold)
            if (readOnly) Text(if (language == "en") "Public market depth · read only" else "Herkese açık piyasa derinliği · salt okunur", color = Muted, fontSize = 10.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (language == "en") "BUY" else "ALIŞ", color = Green); Text(if (language == "en") "SELL" else "SATIŞ", color = Red) }
            val bids = book.bids.take(5).reversed()
            val asks = book.asks.take(5)
            if (bids.isEmpty() && asks.isEmpty()) {
                Text(if (language == "en") "Waiting for public order book levels." else "Herkese açık emir defteri seviyeleri bekleniyor.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            (0 until maxOf(bids.size, asks.size)).forEach { i ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bid = bids.getOrNull(i); val ask = asks.getOrNull(i)
                    Text(bid?.let { "${it.price}  ×  ${it.quantity}" } ?: "", color = Green, modifier = Modifier.weight(1f).then(if (readOnly) Modifier else Modifier.clickable { bid?.price?.let(onPrice) }), fontSize = 11.sp)
                    Text(ask?.let { "${it.price}  ×  ${it.quantity}" } ?: "", color = Red, modifier = Modifier.weight(1f).then(if (readOnly) Modifier else Modifier.clickable { ask?.price?.let(onPrice) }), fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun PublicMarketTradesCard(trades: List<TradeDto>, quoteAsset: String) {
    val language = LocalUiLanguage.current
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (language == "en") "RECENT MARKET TRADES" else "SON PİYASA İŞLEMLERİ", color = Gold, fontWeight = FontWeight.Bold)
            Text(if (language == "en") "Public executions · not your personal trade history" else "Herkese açık gerçekleşmeler · kişisel işlem geçmişiniz değildir", color = Muted, fontSize = 10.sp)
            if (trades.isEmpty()) Text(if (language == "en") "No recent public executions." else "Yakın zamanda herkese açık gerçekleşen işlem yok.", color = Muted, fontSize = 11.sp)
            trades.take(8).forEach { trade ->
                val price = trade.price.toBigDecimalOrNull()?.setScale(6, java.math.RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: trade.price
                val quantity = trade.quantity.toBigDecimalOrNull()?.setScale(6, java.math.RoundingMode.HALF_UP)?.stripTrailingZeros()?.toPlainString() ?: trade.quantity
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trade.symbol, color = Color(0xFFE0E4E8), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Text("$quantity × $price${if (quoteAsset.isBlank()) "" else " $quoteAsset"}", color = Muted, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun PortfolioPage(p: PortfolioDto?) {
    val english = LocalUiLanguage.current == "en"
    val localValuations = p?.valuations.orEmpty()
    val currencies = buildList {
        localValuations.mapTo(this) { it.quoteAsset.uppercase(Locale.ROOT) }
        if (p?.usdValuation != null) add("USD")
    }.distinct().sortedBy { currency ->
        when (currency) { "TRY" -> 0; "USD" -> 1; "USDT" -> 2; else -> 3 }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MetricCard("Pozisyon yoğunlaşması", "${p?.riskScore ?: "—"}%", "En büyük açık pozisyonun aynı para birimindeki toplam pozisyonlara oranı. Tek varlıkta %100; ARGENT göstergesidir, genel risk notu değildir.") }
        currencies.forEach { currency ->
            val local = localValuations.firstOrNull { it.quoteAsset.equals(currency, ignoreCase = true) }
            val usd = if (currency == "USD") p?.usdValuation else null
            item {
                Text(
                    if (english) "$currency VALUATION" else "$currency DEĞERLEMESİ",
                    color = Gold,
                    fontWeight = FontWeight.Bold
                )
            }
            if (usd != null) {
                item { MetricCard(if (english) "Equity · USD" else "Özkaynak · USD", "${usd.equity} USD", "${if (english) "Gross exposure" else "Brüt maruziyet"} ${usd.grossExposure} USD") }
                item { MetricCard(if (english) "Realized PnL · USD" else "Gerçekleşen PnL · USD", "${usd.realizedPnl} USD", "${if (english) "FX reference date" else "Kur referans tarihi"}: ${usd.rateDate}") }
                item { MetricCard(if (english) "Unrealized PnL · USD" else "Açık PnL · USD", "${usd.unrealizedPnl} USD", if (usd.complete) { if (english) "Valuation is complete" else "Değerleme tam" } else { if (english) "FX conversion is partial" else "Kur dönüşümü kısmi" }) }
            } else if (local != null) {
                item { MetricCard(if (english) "Equity · $currency" else "Özkaynak · $currency", "${local.equity} $currency", "${if (english) "Gross exposure" else "Brüt maruziyet"} ${local.grossExposure} $currency") }
                item { MetricCard(if (english) "Realized PnL · $currency" else "Gerçekleşen PnL · $currency", "${local.realizedPnl} $currency", if (english) "Realized result" else "Gerçekleşen sonuç") }
                item { MetricCard(if (english) "Unrealized PnL · $currency" else "Açık PnL · $currency", "${local.unrealizedPnl} $currency", if (local.priceComplete) { if (english) "Calculated with current prices" else "Güncel fiyatlarla hesaplandı" } else { if (english) "Some current prices are unavailable" else "Bazı varlıklarda güncel fiyat yok; tahmin kısmi" }) }
            }
        }
        if (currencies.isEmpty()) {
            item { MetricCard("Özkaynak", p?.equity ?: "—", "Gerçekleşen PnL ${p?.realizedPnl ?: "—"} · Açık PnL ${p?.unrealizedPnl ?: "—"}") }
        }
        item { Text("BAKİYELER", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.balances.orEmpty()) { b -> DataRow(b.asset, "${b.available} kullanılabilir · ${b.held} blokeli") }
        item { Text("POZİSYONLAR", color = Gold, fontWeight = FontWeight.Bold) }
        items(p?.positions.orEmpty()) { pos -> DataRow(pos.symbol, "${pos.quantity} · ort. ${pos.avgEntryPrice} · PnL ${pos.realizedPnl}") }
    }
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
internal fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Gold.copy(alpha = .18f) else Panel, shape = RoundedCornerShape(9.dp)) {
        Text(text, color = if (selected) Gold else Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Field(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}

@Composable
internal fun MetricCard(title: String, value: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Muted, fontSize = 12.sp); Text(value, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun DataRow(title: String, detail: String) {
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
            marketTrades = emptyList(),
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
