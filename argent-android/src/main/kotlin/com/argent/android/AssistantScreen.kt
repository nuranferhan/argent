package com.argent.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.AssistantUiMessage
import com.argent.client.core.AssistantViewModel
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey
import com.argent.client.core.askAssistant
import com.argent.client.core.assistantStatus
import com.argent.client.core.confirmAssistantOrderDraft
import com.argent.client.core.createAssistantOrderDraft
import kotlinx.coroutines.launch
import java.util.Locale

internal sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock

    data class Paragraph(val text: String) : AssistantBlock

    data class Bullet(val text: String) : AssistantBlock

    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
internal fun AssistantScreen(
    api: ArgentApi,
    symbol: String,
    draftSide: String,
    draftType: String,
    draftQuantity: String,
    draftPrice: String,
) {
    val language = LocalUiLanguage.current
    val scope = rememberCoroutineScope()
    val viewModel = remember { AssistantViewModel() }
    val messages = viewModel.messages
    val listState = rememberLazyListState()
    var prompt by remember { viewModel.prompt }
    var enabled by remember { viewModel.enabled }
    var busy by remember { viewModel.busy }
    var pendingOrderDraft by remember { viewModel.pendingOrderDraft }
    var confirmOrderDraft by remember { viewModel.confirmOrderDraft }
    var orderConfirmPassword by remember { viewModel.orderConfirmPassword }
    LaunchedEffect(Unit) { runCatching { enabled = api.assistantStatus().enabled } }
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }
    Column(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ARGENT AI", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    UiStrings.formatted(UiTextKey.ASSISTANT_CONTEXT, language, symbol),
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
            Surface(color = Color(0x2234D399), shape = RoundedCornerShape(50)) {
                Text(
                    UiStrings.text(UiTextKey.APPROVED, language),
                    color = Green,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        Text(
            when (enabled) {
                true -> UiStrings.text(UiTextKey.ASSISTANT_ENABLED, language)
                false -> UiStrings.text(UiTextKey.ASSISTANT_DISABLED, language)
                null -> UiStrings.text(UiTextKey.ASSISTANT_CONNECTING, language)
            },
            color = Muted,
            fontSize = 10.sp,
        )
        if (messages.isEmpty()) {
            AssistantSuggestions(
                language,
                Modifier.weight(1f).fillMaxWidth(),
                onSuggestion = { prompt = it },
            )
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (busy) {
                    item {
                        Text(
                            UiStrings.text(UiTextKey.ASSISTANT_THINKING, language),
                            color = Muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }
                items(messages.size) { index ->
                    AssistantMessageBubble(messages[messages.lastIndex - index])
                }
            }
        }
        pendingOrderDraft?.let { draft ->
            Surface(color = Panel, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EMİR TASLAĞI · GÖNDERİLMEDİ", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (language == "en") {
                            "${draft.symbol} ${draft.side} LIMIT · " +
                                "${draft.quantity} @ ${draft.price} ${draft.quoteAsset}"
                        } else {
                            "${draft.symbol} ${if (draft.side == "BUY") "ALIŞ" else "SATIŞ"} LIMIT · " +
                                "${draft.quantity} @ ${draft.price} ${draft.quoteAsset}"
                        },
                        color = Color(0xFFE0E4E8),
                        fontSize = 11.sp,
                    )
                    val quoteTime =
                        java.text.SimpleDateFormat(
                            "dd.MM.yyyy HH:mm:ss",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(draft.dataAsOf))
                    Text(
                        if (language == "en") {
                            "Estimated amount ${draft.quoteNotional} ${draft.quoteAsset} · " +
                                "${draft.source} · as of $quoteTime. ${if (draft.note.contains(
                                        "anlık",
                                    )
                                ) {
                                    "The limit price was taken from the current " +
                                        "${if (draft.side == "BUY") "ask" else "bid"} quote; " +
                                        "review it before submission."
                                } else {
                                    "The limit price came from your request; review it before submission."
                                }}"
                        } else {
                            "Tahmini tutar ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · " +
                                "veri $quoteTime. ${draft.note}"
                        },
                        color = Muted,
                        fontSize = 10.sp,
                    )
                    Button(onClick = {
                        confirmOrderDraft = true
                    }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                        Text("ÖZETİ ONAYLA VE PAROLAYLA GÖNDER")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                enabled = !busy,
                placeholder = { Text("ARGENT'e sor…") },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = !busy && prompt.isNotBlank() && (enabled != false || looksLikeOrderDraftRequest(prompt)),
                onClick = {
                    val question = prompt.trim()
                    prompt = ""
                    messages.add(AssistantUiMessage(true, question))
                    busy = true
                    scope.launch {
                        try {
                            if (looksLikeOrderDraftRequest(question)) {
                                runCatching { api.createAssistantOrderDraft(question) }
                                    .onSuccess {
                                            draft ->
                                        pendingOrderDraft = draft
                                        messages.add(
                                            AssistantUiMessage(
                                                false,
                                                if (language == "en") {
                                                    "Draft ready: ${draft.side} LIMIT ${draft.quantity} " +
                                                        "${draft.symbol} at ${draft.price} ${draft.quoteAsset}. " +
                                                        "It has not been submitted; review the summary below " +
                                                        "and confirm it separately."
                                                } else {
                                                    "Taslağı hazırladım: ${draft.symbol} ${draft.side} LIMIT, " +
                                                        "${draft.quantity} @ ${draft.price} ${draft.quoteAsset}. " +
                                                        "Henüz gönderilmedi; alttaki özeti kontrol edip " +
                                                        "ayrıca onaylayabilirsin."
                                                },
                                            ),
                                        )
                                    }
                                    .onFailure {
                                        messages.add(
                                            AssistantUiMessage(
                                                false,
                                                com.argent.client.core.UiTranslations.translate(
                                                    it.message ?: if (language == "en") {
                                                        "Could not create the order draft."
                                                    } else {
                                                        "Emir taslağı oluşturulamadı."
                                                    },
                                                    language,
                                                ),
                                            ),
                                        )
                                    }
                            } else {
                                runCatching {
                                    api.askAssistant(
                                        question,
                                        symbol,
                                        draftSide,
                                        draftType,
                                        draftQuantity,
                                        draftPrice,
                                        language,
                                    )
                                }
                                    .onSuccess { result ->
                                        messages.add(AssistantUiMessage(false, cleanAssistantAnswer(result.answer)))
                                    }
                                    .onFailure {
                                        messages.add(
                                            AssistantUiMessage(
                                                false,
                                                com.argent.client.core.UiTranslations.translate(
                                                    it.message ?: if (language == "en") {
                                                        "The assistant could not reply."
                                                    } else {
                                                        "Asistan yanıt veremedi."
                                                    },
                                                    language,
                                                ),
                                            ),
                                        )
                                    }
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier =
                    Modifier.height(
                        54.dp,
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = Ink,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Sor")
                }
            }
        }
    }
    if (confirmOrderDraft) {
        AlertDialog(
            onDismissRequest = {
                confirmOrderDraft = false
                orderConfirmPassword = ""
            },
            title = { Text("Emri son kez kontrol et") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingOrderDraft?.let {
                            d ->
                        Text(
                            if (language == "en") {
                                "${d.symbol} · ${d.side} LIMIT\n${d.quantity} @ ${d.price} ${d.quoteAsset}\n" +
                                    "Estimated amount: ${d.quoteNotional} ${d.quoteAsset}"
                            } else {
                                "${d.symbol} · ${d.side} LIMIT\n${d.quantity} adet @ ${d.price} ${d.quoteAsset}\n" +
                                    "Tahmini tutar: ${d.quoteNotional} ${d.quoteAsset}"
                            },
                            color = Color(0xFFE0E4E8),
                        )
                    }
                    Text("Bu onay emri ARGENT dahili eşleştirme motoruna gönderir.", color = Muted)
                    OutlinedTextField(orderConfirmPassword, {
                        orderConfirmPassword = it
                    }, label = {
                        Text(
                            "Hesap parolası",
                        )
                    }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = orderConfirmPassword.isNotBlank(), onClick = {
                    val draft = pendingOrderDraft ?: return@TextButton
                    confirmOrderDraft = false
                    busy = true
                    scope.launch {
                        runCatching { api.confirmAssistantOrderDraft(draft.draftId, orderConfirmPassword) }
                            .onSuccess {
                                    result ->
                                pendingOrderDraft = null
                                messages.add(
                                    AssistantUiMessage(
                                        false,
                                        if (language == "en") {
                                            "Order result: ${result.status} · ${result.filledQuantity} filled · " +
                                                "${result.remainingQuantity} remaining."
                                        } else {
                                            "Emir sonucu: ${result.status} · ${result.filledQuantity} doldu · " +
                                                "${result.remainingQuantity} kaldı."
                                        },
                                    ),
                                )
                            }
                            .onFailure {
                                messages.add(
                                    AssistantUiMessage(
                                        false,
                                        com.argent.client.core.UiTranslations.translate(
                                            it.message ?: if (language == "en") {
                                                "Could not submit the order."
                                            } else {
                                                "Emir gönderilemedi."
                                            },
                                            language,
                                        ),
                                    ),
                                )
                            }
                            .also {
                                orderConfirmPassword = ""
                                busy = false
                            }
                    }
                }) { Text("ONAYLA VE GÖNDER", color = Green) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmOrderDraft = false
                    orderConfirmPassword = ""
                }) { Text("VAZGEÇ") }
            },
        )
    }
}
