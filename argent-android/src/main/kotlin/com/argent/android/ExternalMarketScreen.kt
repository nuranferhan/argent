package com.argent.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.ExternalMarketStatusDto
import com.argent.client.core.ExternalQuoteDto
import com.argent.client.core.MarketNewsDto
import com.argent.client.core.PriceAlertDto
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey
import com.argent.client.core.createPriceAlert
import com.argent.client.core.deletePriceAlert
import com.argent.client.core.externalMarketStatus
import com.argent.client.core.instruments
import com.argent.client.core.marketNews
import com.argent.client.core.marketNewsImage
import com.argent.client.core.nasdaqQuote
import com.argent.client.core.priceAlerts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun ExternalMarketSection(
    api: ArgentApi,
    onMessage: (String) -> Unit,
    refreshSeconds: Int,
    guestMode: Boolean = false,
    onRequireLogin: () -> Unit = {},
) {
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var symbols by remember { mutableStateOf(emptyList<String>()) }
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
            symbols =
                runCatching {
                    api.instruments().filter { it.active && it.assetClass == "US_EQUITY" }.map { it.symbol }
                }.getOrDefault(symbols)
            if (selected !in symbols) symbols.firstOrNull()?.let { selected = it }
            val quoteResults = symbols.map { s -> runCatching { api.nasdaqQuote(s) } }
            quotes = quoteResults.mapNotNull { it.getOrNull()?.let { q -> q.symbol to q } }.toMap()
            feedError = quoteResults.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            runCatching { api.marketNews() }
                .onSuccess { news = it }
                .onFailure { if (feedError == null) feedError = it.message }
        }
        if (!guestMode) alerts = runCatching { api.priceAlerts() }.getOrDefault(alerts)
    }
    LaunchedEffect(refreshSeconds) {
        while (true) {
            refresh()
            delay(refreshSeconds * 1_000L)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "NASDAQ · HABER · FİYAT ALARMLARI",
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(
                        if (provider?.configured == true) {
                            "Finnhub · API anahtarı bağlı; erişim sağlayıcı hesabına bağlı"
                        } else {
                            "Gerçek veri kaynağı yapılandırılmadı"
                        },
                        color = Muted,
                        fontSize = 10.sp,
                    )
                }
                Text("Otomatik · ${refreshSeconds / 60} dk", color = Muted, fontSize = 9.sp)
            }
            if (provider?.configured != true) {
                Text(
                    "FINNHUB_API_KEY eklenince hesabınızın kapsamındaki gerçek Nasdaq referans fiyatları " +
                        "ve haberler açılır. Burada örnek fiyat kullanılmaz.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            } else {
                feedError?.let {
                    Text(
                        "Gerçek veri alınamadı: $it",
                        color = Red,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quotes.values.forEach { q ->
                        Card(
                            Modifier.clickable {
                                selected = q.symbol
                            },
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = if (selected == q.symbol) Gold.copy(alpha = .18f) else Ink,
                                ),
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    q.symbol,
                                    color = Color(0xFFE0E4E8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("USD ${q.price}", color = Gold, fontSize = 11.sp)
                                Text("${q.changePercent ?: "—"}%", color = Muted, fontSize = 9.sp)
                                Text(
                                    "Finnhub · ${java.text.SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault(),
                                    ).format(java.util.Date(q.timestamp))}",
                                    color = Muted,
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }
                if (guestMode) {
                    TextButton(onClick = onRequireLogin) {
                        Text(
                            if (language == "en") {
                                "Sign in to manage personal price alerts"
                            } else {
                                "Kişisel fiyat alarmları için giriş yapın"
                            },
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Choice(if (language == "en") "ABOVE" else "ÜSTÜ", direction == "ABOVE") { direction = "ABOVE" }
                        Choice(if (language == "en") "BELOW" else "ALTI", direction == "BELOW") { direction = "BELOW" }
                        Field(if (language == "en") "USD threshold" else "USD eşik", threshold, {
                            threshold = it
                        }, KeyboardType.Decimal, modifier = Modifier.width(116.dp))
                        Button(enabled = threshold.toBigDecimalOrNull()?.signum() == 1, onClick = {
                            scope.launch {
                                runCatching {
                                    api.createPriceAlert(selected, direction, threshold)
                                    threshold = ""
                                    alerts = api.priceAlerts()
                                }
                                    .onSuccess {
                                        onMessage(
                                            if (language == "en") {
                                                "$selected price alert created"
                                            } else {
                                                "$selected fiyat alarmı kuruldu"
                                            },
                                        )
                                    }.onFailure {
                                        onMessage(
                                            if (language == "en") {
                                                "Could not create alert: ${it.message}"
                                            } else {
                                                "Alarm kurulamadı: ${it.message}"
                                            },
                                        )
                                    }
                            }
                        }) { Text("KUR") }
                    }
                }
                if (!guestMode) {
                    alerts.forEach { alert ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${alert.symbol} ${if (alert.direction == "ABOVE") "≥" else "≤"} " +
                                    "USD ${alert.threshold} · " +
                                    if (alert.state == "ACTIVE") {
                                        UiStrings.text(UiTextKey.ALERT_ACTIVE_STATE, language)
                                    } else {
                                        UiStrings.formatted(
                                            UiTextKey.ALERT_TRIGGERED_STATE,
                                            language,
                                            alert.triggeredPrice ?: "—",
                                        )
                                    },
                                color = if (alert.state == "TRIGGERED") Green else Muted,
                                fontSize = 10.sp,
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        api.deletePriceAlert(alert.id)
                                        alerts = api.priceAlerts()
                                    }.onFailure {
                                        onMessage(
                                            if (language == "en") "Could not delete alert" else "Alarm silinemedi",
                                        )
                                    }
                                }
                            }) { Text("Sil", color = Red, fontSize = 10.sp) }
                        }
                    }
                }
                Text("PİYASA HABERLERİ", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                news.take(5).forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            runCatching {
                                uriHandler.openUri(n.url)
                            }
                        },
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NewsThumbnail(api, n.imageUrl, Modifier.width(88.dp).height(58.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (language == "en") n.headline else (n.translatedHeadline ?: n.headline),
                                color = Color(0xFFE0E4E8),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${n.source} · ${n.relatedSymbols.ifBlank { "Piyasa" }} · ${java.text.SimpleDateFormat(
                                    "dd.MM HH:mm",
                                    Locale.getDefault(),
                                ).format(java.util.Date(n.publishedAt))}",
                                color = Muted,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsThumbnail(
    api: ArgentApi,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        bitmap =
            imageUrl?.let { url ->
                val bytes = api.marketNewsImage(url)
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    bytes?.let {
                        runCatching {
                            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
            }
    }
    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            currentBitmap,
            contentDescription = "Haber görseli",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Box(modifier.background(Panel, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            Text("ARGENT", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
