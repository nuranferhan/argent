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


private data class AssistantUiMessage(val fromUser: Boolean, val text: String, val metadata: String? = null)

private sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock
    data class Paragraph(val text: String) : AssistantBlock
    data class Bullet(val text: String) : AssistantBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
internal fun AssistantTab(
    api: ArgentApi,
    symbol: String,
    draftSide: String,
    draftType: String,
    draftQuantity: String,
    draftPrice: String,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val language = LocalUiLanguage.current
    val messages = remember { androidx.compose.runtime.mutableStateListOf<AssistantUiMessage>() }
    val listState = rememberLazyListState()
    var prompt by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingOrderDraft by remember { mutableStateOf<AssistantOrderDraftDto?>(null) }
    var confirmOrderDraft by remember { mutableStateOf(false) }
    var orderConfirmPassword by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { runCatching { enabled = api.assistantStatus().enabled } }
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ARGENT AI", color = ArgentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Portföy · emirler · $symbol", color = ArgentMuted, fontSize = 10.sp)
            }
            Surface(color = ArgentGreenDim, shape = RoundedCornerShape(50)) {
                Text(if (language == "en") "APPROVED" else "ONAYLI", color = ArgentGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
            }
        }
        Text(
            when (enabled) {
                true -> "Yanıtlar ve emir taslakları · Emir yalnızca parola ile açık onaydan sonra gönderilir"
                false -> "Asistan kapalı · Gateway AI anahtarını kontrol et"
                null -> "Asistan bağlantısı kontrol ediliyor…"
            }, color = ArgentMuted, fontSize = 10.sp
        )
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Nasıl yardımcı olabilirim?", color = ArgentSilver, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Hesap verilerin veya seçili piyasa hakkında sor.", color = ArgentMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
                (if (language == "en") listOf("My cancelled orders from the last 7 days", "Check my risk and data-feed alerts", "How concentrated is my portfolio?") else listOf("Son 7 gündeki iptal edilen emirlerim", "Risk ve veri akışı uyarılarımı kontrol et", "Portföyümde yoğunlaşma ne durumda?")).forEach { suggestion ->
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
        pendingOrderDraft?.let { draft ->
            Surface(color = ArgentPanelAlt, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EMİR TASLAĞI · GÖNDERİLMEDİ", color = ArgentGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (language == "en") "${draft.symbol} ${draft.side} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}" else "${draft.symbol} ${if (draft.side == "BUY") "ALIŞ" else "SATIŞ"} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}", color = ArgentSilver, fontSize = 11.sp)
                    val quoteTime = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(draft.dataAsOf))
                    Text(if (language == "en") "Estimated amount ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · as of ${quoteTime}. ${if (draft.note.contains("anlık")) "The limit price was taken from the current ${if (draft.side == "BUY") "ask" else "bid"} quote; review it before submission." else "The limit price came from your request; review it before submission."}" else "Tahmini tutar ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · veri ${quoteTime}. ${draft.note}", color = ArgentMuted, fontSize = 10.sp)
                    Button(onClick = { confirmOrderDraft = true }, colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = ArgentBg)) { Text("ÖZETİ ONAYLA VE PAROLAYLA GÖNDER") }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, enabled = !busy,
                placeholder = { Text("ARGENT'e sor…") }, minLines = 1, maxLines = 3,
                modifier = Modifier.weight(1f), colors = fieldColors()
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
                                    messages.add(AssistantUiMessage(false, result.answer))
                                }
                                .onFailure { messages.add(AssistantUiMessage(false, com.argent.terminal.api.UiTranslations.translate(it.message ?: if (language == "en") "The assistant could not reply." else "Asistan yanıt veremedi.", language))) }
                        }
                    } finally { busy = false }
                }
            }, modifier = Modifier.height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = ArgentBg)) {
                Text(if (busy) "…" else "Gönder")
            }
        }
    }
    if (confirmOrderDraft) AlertDialog(
        onDismissRequest = { confirmOrderDraft = false; orderConfirmPassword = "" },
        title = { Text("Emri son kez kontrol et") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pendingOrderDraft?.let { d -> Text(if (language == "en") "${d.symbol} · ${d.side} LIMIT\n${d.quantity} @ ${d.price} ${d.quoteAsset}\nEstimated amount: ${d.quoteNotional} ${d.quoteAsset}" else "${d.symbol} · ${d.side} LIMIT\n${d.quantity} adet @ ${d.price} ${d.quoteAsset}\nTahmini tutar: ${d.quoteNotional} ${d.quoteAsset}", color = ArgentSilver) }
                Text("Bu onay emri ARGENT dahili eşleştirme motoruna gönderir.", color = ArgentMuted)
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
        }) { Text("ONAYLA VE GÖNDER", color = ArgentGreen) } },
        dismissButton = { TextButton(onClick = { confirmOrderDraft = false; orderConfirmPassword = "" }) { Text("VAZGEÇ") } }
    )
}

private fun looksLikeOrderDraftRequest(text: String): Boolean {
    val q = text.lowercase()
    return ("limit" in q || "limitli" in q) &&
        ("alış" in q || "alis" in q || "buy" in q || "sat" in q || "sell" in q) &&
        listOf("usdt", "usd", "try", "tl").any(q::contains)
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
