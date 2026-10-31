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


private data class AssistantUiMessage(val fromUser: Boolean, val text: String, val metadata: String? = null)

private sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock
    data class Paragraph(val text: String) : AssistantBlock
    data class Bullet(val text: String) : AssistantBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
internal fun AssistantScreen(api: ArgentApi, symbol: String, draftSide: String, draftType: String, draftQuantity: String, draftPrice: String) {
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
