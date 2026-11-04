package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.argent.client.core.ArgentApi
import com.argent.client.core.InstrumentDto
import com.argent.client.core.MeResponse
import com.argent.client.core.TickerDto
import com.argent.terminal.ui.ArgentBg
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentSilver
import com.argent.terminal.ui.ArgentTheme
import java.util.prefs.Preferences
import androidx.compose.material3.Text as MaterialText

internal val LocalUiLanguage = androidx.compose.runtime.compositionLocalOf { "tr" }

@Composable internal fun guestLabel() = if (LocalUiLanguage.current == "en") "Guest" else "Misafir"

@Composable internal fun guestLoginMessage() =
    if (LocalUiLanguage.current == "en") "Please sign in to use this feature." else "Bu özelliği kullanmak için lütfen giriş yapın."

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
    minLines: Int = 1,
) {
    MaterialText(
        com.argent.client.core.UiNumberFormatting.limitFractionDigits(
            com.argent.client.core.UiTranslations.translate(text, LocalUiLanguage.current),
        ),
        modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
        fontFamily = fontFamily, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    )
}

fun main() =
    application {
        Window(
            icon = painterResource("argent-icon.png"),
            onCloseRequest = ::exitApplication,
            title = "ARGENT — Paper Trading Platform",
            state = rememberWindowState(width = 1560.dp, height = 940.dp),
        ) {
            App()
        }
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
    var savedDark by remember { mutableStateOf(dark) }
    var savedScale by remember { mutableStateOf(scale) }
    var savedRefresh by remember { mutableStateOf(refresh) }
    var savedMarketRefresh by remember { mutableStateOf(marketRefresh) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    ArgentTheme(dark) {
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = scale),
            LocalUiLanguage provides language,
        ) {
            Surface(Modifier.fillMaxSize(), color = ArgentBg) {
                val api = remember { ArgentApi() }
                var me by remember { mutableStateOf<MeResponse?>(null) }
                var guest by remember { mutableStateOf(false) }
                if (me == null && !guest) {
                    LoginPane(api, onAuthed = { me = it }, onGuest = { guest = true })
                } else {
                    val activeUser = me ?: MeResponse("guest", "", if (language == "en") "Guest" else "Misafir", "GUEST")
                    TradingDesk(
                        api, activeUser, { if (guest) guest = false else me = null }, {
                            me = it
                            guest = false
                        }, dark, scale, refresh, marketRefresh, guestMode = guest,
                        onSettingsPreview = { d, s ->
                            dark = d
                            scale = s
                        },
                        onLanguagePreview = { language = it },
                        onSettingsCancel = {
                            dark = savedDark
                            scale = savedScale
                            refresh = savedRefresh
                            marketRefresh = savedMarketRefresh
                            language = savedLanguage
                        },
                        onSettingsSave = { d, s, r, mr, lang ->
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
                            prefs.putBoolean("darkTheme", d)
                            prefs.putFloat("fontScale", s)
                            prefs.putInt("refreshSeconds", r)
                            prefs.putInt("marketRefreshSeconds", mr)
                            prefs.put("language", lang)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TickerMarquee(
    text: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = remember { TextStyle(color = ArgentSilver, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    val layout =
        remember(text, style, density) {
            textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1, softWrap = false)
        }
    val contentWidth = layout.size.width.toFloat().coerceAtLeast(1f)
    // Keep the tape calm and readable at roughly 8 dp/second.
    val durationMillis = with(density) { (layout.size.width.toDp().value * 125f).toInt().coerceAtLeast(12_000) }
    val transition = rememberInfiniteTransition(label = "market ticker")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis, easing = LinearEasing)),
        label = "market ticker offset",
    )
    Canvas(modifier) {
        val xOffset = -progress * contentWidth
        val copies = (size.width / contentWidth).toInt() + 2
        clipRect {
            repeat(copies) { index ->
                drawText(
                    layout,
                    topLeft = Offset(xOffset + index * contentWidth, (size.height - layout.size.height) / 2f),
                )
            }
        }
    }
}

@Composable
internal fun Watchlist(
    instruments: List<InstrumentDto>,
    tickers: Map<String, TickerDto>,
    selected: String,
    onSelect: (String) -> Unit,
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
                    Modifier.fillMaxWidth().background(
                        bg,
                        RoundedCornerShape(6.dp),
                    ).clickable { onSelect(ins.symbol) }.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(ins.symbol, color = ArgentSilver, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            ins.displayName,
                            color = ArgentMuted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        compact(px),
                        color = ArgentGold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                }
            }
        }
    }
}
