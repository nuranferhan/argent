package com.argent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.MeResponse

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArgentMobile() }
    }
}

@Composable
private fun ArgentMobile() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("argent-ui", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var scale by remember { mutableStateOf(prefs.getFloat("scale", 1f)) }
    var refresh by remember { mutableStateOf(prefs.getInt("refresh", 4)) }
    var marketRefresh by remember { mutableStateOf(prefs.getInt("marketRefresh", 60).coerceIn(60, 900)) }
    var language by remember {
        mutableStateOf((prefs.getString("language", "tr") ?: "tr").takeIf { it in setOf("tr", "en") } ?: "tr")
    }
    var savedLanguage by remember { mutableStateOf(language) }
    var savedDark by remember { mutableStateOf(dark) }
    var savedScale by remember { mutableStateOf(scale) }
    var savedRefresh by remember { mutableStateOf(refresh) }
    var savedMarketRefresh by remember { mutableStateOf(marketRefresh) }
    val density = LocalDensity.current
    val palette =
        if (dark) {
            MobilePalette(
                Color(0xFF0B0E12),
                Color(0xFF151A21),
                Color(0xFF29313C),
                Color(0xFFE0B82F),
                Color(0xFF00C98A),
                Color(0xFFFF5065),
                Color(0xFF98A3B3),
                Color(0xFFE5E8ED),
            )
        } else {
            MobilePalette(
                Color(0xFFF3F5F8),
                Color.White,
                Color(0xFFD1D8E0),
                Color(0xFF9A7410),
                Color(0xFF087F55),
                Color(0xFFC6283D),
                Color(0xFF647181),
                Color(0xFF26313D),
            )
        }
    MobilePaletteHolder.current = palette
    MaterialTheme(
        colorScheme =
            if (dark) {
                darkColorScheme(
                    primary = Gold,
                    background = Ink,
                    surface = Panel,
                    onBackground = AppText,
                    onSurface = AppText,
                    error = Red,
                )
            } else {
                lightColorScheme(
                    primary = Gold,
                    background = Ink,
                    surface = Panel,
                    onBackground = AppText,
                    onSurface = AppText,
                    error = Red,
                )
            },
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = scale),
            LocalUiLanguage provides language,
        ) {
            val api = remember { ArgentApi() }
            var me by remember { mutableStateOf<MeResponse?>(null) }
            var guest by remember { mutableStateOf(false) }
            if (me == null && !guest) {
                LoginScreen(api, onLogin = { me = it }, onGuest = { guest = true })
            } else {
                val activeUser = me ?: MeResponse("guest", "", if (language == "en") "Guest" else "Misafir", "GUEST")
                MainScreen(api, activeUser, isGuest = guest, onMeChanged = {
                    me = it
                    guest = false
                }, onLogout = {
                    if (guest) guest = false else me = null
                }, dark = dark, fontScale = scale, refreshSeconds = refresh, marketRefreshSeconds = marketRefresh, onSettingsPreview = {
                        d,
                        s,
                    ->
                    dark = d
                    scale = s
                }, onLanguagePreview = { language = it }, onSettingsCancel = {
                    dark = savedDark
                    scale = savedScale
                    refresh = savedRefresh
                    marketRefresh = savedMarketRefresh
                    language = savedLanguage
                }, onSettingsSave = {
                        d,
                        s,
                        r,
                        mr,
                        lang,
                    ->
                    dark = d
                    scale = s
                    refresh = r
                    marketRefresh = mr
                    language = lang
                    savedDark = d
                    savedScale = s
                    savedRefresh = r
                    savedMarketRefresh = mr
                    savedLanguage = lang
                    prefs.edit().putBoolean(
                        "dark",
                        d,
                    ).putFloat(
                        "scale",
                        s,
                    ).putInt("refresh", r).putInt("marketRefresh", mr).putString("language", lang).apply()
                })
            }
        }
    }
}

internal enum class Page(val title: String) {
    TRADE(
        "Piyasa",
    ),
    PORTFOLIO("Portföy"),
    ORDERS("Emirler"),
    ASSISTANT("AI"),
    WORKSPACE("Çalışma alanı"),
    SETTINGS("Ayarlar"),
}

internal fun Page.localizedTitle(language: String): String =
    when (this) {
        Page.TRADE -> if (language == "en") "Markets" else "Piyasa"
        Page.PORTFOLIO -> if (language == "en") "Portfolio" else "Portföy"
        Page.ORDERS -> if (language == "en") "Orders" else "Emirler"
        Page.ASSISTANT -> "AI"
        Page.WORKSPACE -> if (language == "en") "Workspace" else "Çalışma alanı"
        Page.SETTINGS -> if (language == "en") "Settings" else "Ayarlar"
    }

@Composable
internal fun GuestAccessPage(
    section: String,
    onLogin: () -> Unit,
) {
    val english = LocalUiLanguage.current == "en"
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (english) "Sign in required" else "Giriş yapmanız gerekiyor",
            color = Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (english) {
                "Sign in to access $section."
            } else {
                "$section bölümüne erişmek için lütfen kullanıcı girişi yapın."
            },
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onLogin) { Text(if (english) "Go to sign in" else "Giriş ekranına dön") }
    }
}
