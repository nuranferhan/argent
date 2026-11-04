package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.createPriceAlert
import com.argent.client.core.deletePriceAlert
import com.argent.client.core.externalMarketStatus
import com.argent.client.core.instruments
import com.argent.client.core.marketNews
import com.argent.client.core.nasdaqQuote
import com.argent.client.core.priceAlerts


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.ExternalMarketStatusDto
import com.argent.client.core.ExternalQuoteDto
import com.argent.client.core.MarketNewsDto
import com.argent.client.core.PriceAlertDto
import com.argent.client.core.TradeDto
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGoldDim
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal fun ExternalMarketsTab(
    api: ArgentApi,
    scope: kotlinx.coroutines.CoroutineScope,
    onFlash: (String) -> Unit,
    trades: List<TradeDto>,
    refreshSeconds: Int,
    guestMode: Boolean = false,
    onRequireLogin: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val language = LocalUiLanguage.current
    var symbols by remember { mutableStateOf(emptyList<String>()) }
    var provider by remember { mutableStateOf<ExternalMarketStatusDto?>(null) }
    var quotes by remember { mutableStateOf(emptyMap<String, ExternalQuoteDto>()) }
    var news by remember { mutableStateOf(emptyList<MarketNewsDto>()) }
    var alerts by remember { mutableStateOf(emptyList<PriceAlertDto>()) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var loadingExternalData by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("AAPL") }
    var threshold by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("ABOVE") }
    val refresh: suspend () -> Unit = {
        loadingExternalData = true
        try {
            provider = runCatching { api.externalMarketStatus() }.getOrNull()
            if (provider?.configured == true) {
                symbols =
                    runCatching {
                        api.instruments().filter { it.active && it.assetClass == "US_EQUITY" }.map { it.symbol }
                    }.getOrDefault(symbols)
                if (selected !in symbols) symbols.firstOrNull()?.let { selected = it }
                val (quoteResults, newsResult) =
                    coroutineScope {
                        val quotesJob =
                            async {
                                symbols.map {
                                        symbol ->
                                    async { runCatching { api.nasdaqQuote(symbol) } }
                                }.awaitAll()
                            }
                        val newsJob = async { runCatching { api.marketNews() } }
                        quotesJob.await() to newsJob.await()
                    }
                val receivedQuotes =
                    quoteResults.mapNotNull {
                        it.getOrNull()?.let {
                                quote ->
                            quote.symbol to quote
                        }
                    }.toMap()
                if (receivedQuotes.isNotEmpty()) quotes = receivedQuotes
                feedError = quoteResults.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
                newsResult.onSuccess { news = it }.onFailure { if (feedError == null) feedError = it.message }
            } else {
                quotes = emptyMap()
                news = emptyList()
                feedError = if (provider == null) "Gateway veri durumu alınamadı." else null
            }
            if (!guestMode) alerts = runCatching { api.priceAlerts() }.getOrDefault(alerts)
        } finally {
            loadingExternalData = false
        }
    }
    LaunchedEffect(refreshSeconds) {
        while (true) {
            refresh()
            kotlinx.coroutines.delay(refreshSeconds * 1_000L)
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "NASDAQ · GERÇEK PİYASA REFERANSI",
                        color = ArgentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                    Text(
                        if (provider?.configured == true) "Finnhub · API anahtarı bağlı; veri erişimi sağlayıcı hesabına bağlı" else "Gerçek veri kaynağı henüz yapılandırılmadı",
                        color = ArgentMuted,
                        fontSize = 10.sp,
                    )
                }
                Text("Otomatik · ${refreshSeconds / 60} dk", color = ArgentMuted, fontSize = 9.sp)
            }
            if (loadingExternalData) {
                Text(
                    "Finnhub kotasyonları ve haber akışı yükleniyor…",
                    color = ArgentMuted,
                    fontSize = 10.sp,
                )
            }
            if (provider?.configured == false) {
                Text(
                    "FINNHUB_API_KEY ekleyince hesabınızın kapsamındaki Nasdaq referans fiyatları ve haber akışı açılır. Demo fiyatı gösterilmiyor.",
                    color = ArgentSilver,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
            if (provider?.configured == true && feedError != null) {
                Text(
                    "Gerçek veri alınamadı: $feedError",
                    color = ArgentRed,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (provider?.configured == true && !loadingExternalData && quotes.isEmpty() && feedError == null) {
                Text(
                    "Kotasyon alınamadı; otomatik olarak yeniden denenecek.",
                    color = ArgentRed,
                    fontSize = 10.sp,
                )
            }
        }
        if (provider?.configured == true) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quotes.values.forEach { q ->
                        Surface(
                            Modifier.weight(1f).clickable {
                                selected = q.symbol
                            },
                            color = if (selected == q.symbol) ArgentGoldDim else ArgentPanel,
                            shape =
                                RoundedCornerShape(
                                    7.dp,
                                ),
                        ) {
                            Column(Modifier.padding(7.dp)) {
                                Text(q.symbol, color = ArgentSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "USD ${q.price}",
                                    color = ArgentGold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text("${q.changePercent ?: "—"}%", color = ArgentMuted, fontSize = 9.sp)
                                Text(
                                    "Finnhub · ${java.text.SimpleDateFormat(
                                        "HH:mm",
                                        Locale.getDefault(),
                                    ).format(java.util.Date(q.timestamp))}",
                                    color = ArgentMuted,
                                    fontSize = 8.sp,
                                )
                            }
                        }
                    }
                }
            }
            if (!guestMode) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Fiyat alarmı", color = ArgentSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf("ABOVE" to "Üstüne çıkarsa", "BELOW" to "Altına inerse").forEach { (value, label) ->
                            TextButton(onClick = {
                                direction = value
                            }) {
                                Text(
                                    if (direction == value) "✓ $label" else label,
                                    color = if (direction == value) ArgentGold else ArgentMuted,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        OutlinedTextField(
                            threshold,
                            {
                                threshold = it
                            },
                            Modifier.width(110.dp).height(48.dp),
                            placeholder = {
                                Text("USD eşik", fontSize = 10.sp)
                            },
                            singleLine = true,
                            textStyle =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                ),
                            colors = fieldColors(),
                        )
                        Button(onClick = {
                            scope.launch {
                                runCatching {
                                    api.createPriceAlert(selected, direction, threshold)
                                    threshold = ""
                                    alerts = api.priceAlerts()
                                }
                                    .onSuccess {
                                        onFlash(
                                            if (language == "en") "$selected price alert created" else "$selected fiyat alarmı kuruldu",
                                        )
                                    }.onFailure {
                                        onFlash(
                                            if (language == "en") "Could not create alert: ${it.message}" else "Alarm kurulamadı: ${it.message}",
                                        )
                                    }
                            }
                        }, enabled = threshold.toBigDecimalOrNull()?.signum() == 1) { Text("KUR") }
                    }
                    alerts.forEach { alert ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (LocalUiLanguage.current == "en") "${alert.symbol} ${if (alert.direction == "ABOVE") "≥" else "≤"} USD ${alert.threshold} · ${if (alert.state == "ACTIVE") "waiting" else "triggered @ USD ${alert.triggeredPrice}"}" else "${alert.symbol} ${if (alert.direction == "ABOVE") "≥" else "≤"} USD ${alert.threshold} · ${if (alert.state == "ACTIVE") "bekliyor" else "tetiklendi @ USD ${alert.triggeredPrice}"}",
                                color = if (alert.state == "TRIGGERED") ArgentGreen else ArgentMuted,
                                fontSize = 10.sp,
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        api.deletePriceAlert(alert.id)
                                        alerts = api.priceAlerts()
                                    }.onFailure {
                                        onFlash(if (language == "en") "Could not delete alert" else "Alarm silinemedi")
                                    }
                                }
                            }) { Text("Sil", color = ArgentRed, fontSize = 10.sp) }
                        }
                    }
                }
            }
            item { Text("PİYASA HABERLERİ", color = ArgentGold, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            if (!loadingExternalData && news.isEmpty()) {
                item {
                    Text(
                        "Haber alınamadı veya sağlayıcı boş liste döndürdü.",
                        color = if (feedError != null) ArgentRed else ArgentMuted,
                        fontSize = 10.sp,
                    )
                }
            }
            items(news.take(8)) { item ->
                Surface(color = ArgentPanel, shape = RoundedCornerShape(7.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            runCatching {
                                uriHandler.openUri(item.url)
                            }
                        }.padding(
                            8.dp,
                        ),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                10.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NewsThumbnail(api, item.imageUrl, Modifier.width(92.dp).height(60.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (LocalUiLanguage.current == "en") item.headline else (item.translatedHeadline ?: item.headline),
                                color = ArgentSilver,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${item.source} · ${item.relatedSymbols.ifBlank { "Piyasa" }} · ${java.text.SimpleDateFormat(
                                    "dd.MM HH:mm",
                                    Locale.getDefault(),
                                ).format(java.util.Date(item.publishedAt))}",
                                color = ArgentMuted,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
        }
        if (guestMode) {
            item {
                TextButton(onClick = onRequireLogin) {
                    Text(
                        if (language == "en") "Sign in to manage personal price alerts" else "Kişisel fiyat alarmı yönetmek için giriş yapın",
                    )
                }
            }
        }
        item {
            Text(
                if (language == "en") "Public market executions · ${trades.size} records" else "Herkese açık piyasa işlemleri · ${trades.size} kayıt",
                color = ArgentGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        items(trades.take(10)) { trade ->
            val time =
                trade.executedAt.takeIf {
                    it > 0
                }?.let { java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(it)) } ?: "—"
            Text(
                "${trade.symbol}  ${compact(trade.quantity)} × ${compact(trade.price)}  ·  $time",
                color = ArgentMuted,
                fontSize = 10.sp,
            )
        }
    }
}
