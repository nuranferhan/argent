package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.askAssistant
import com.argent.client.core.assistantStatus
import com.argent.client.core.confirmAssistantOrderDraft
import com.argent.client.core.createAssistantOrderDraft


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.AssistantViewModel
import com.argent.client.core.AssistantUiMessage
import com.argent.terminal.ui.ArgentBg
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGoldDim
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentGreenDim
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.launch
import java.util.Locale


@Composable
internal fun AssistantTab(
    api: ArgentApi,
    symbol: String,
    draftSide: String,
    draftType: String,
    draftQuantity: String,
    draftPrice: String,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val language = LocalUiLanguage.current
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
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ARGENT AI", color = ArgentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    UiStrings.formatted(UiTextKey.ASSISTANT_CONTEXT, language, symbol),
                    color = ArgentMuted,
                    fontSize = 10.sp,
                )
            }
            Surface(color = ArgentGreenDim, shape = RoundedCornerShape(50)) {
                Text(
                    UiStrings.text(UiTextKey.APPROVED, language),
                    color = ArgentGreen,
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
            color = ArgentMuted,
            fontSize = 10.sp,
        )
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(
                    UiStrings.text(UiTextKey.ASSISTANT_GREETING, language),
                    color = ArgentSilver,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    UiStrings.text(UiTextKey.ASSISTANT_HELP, language),
                    color = ArgentMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
                )
                (
                    AssistantPromptCatalog.suggestions(language)
                ).forEach {
                        suggestion ->
                    Surface(
                        color = ArgentPanel,
                        shape =
                            RoundedCornerShape(
                                9.dp,
                            ),
                        modifier =
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                prompt = suggestion
                            },
                    ) {
                        Text(
                            suggestion,
                            color = ArgentSilver,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 3.dp),
            ) {
                if (busy) {
                    item {
                        Text(
                                UiStrings.text(UiTextKey.ASSISTANT_THINKING, language),
                            color = ArgentMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 3.dp),
                        )
                    }
                }
                items(messages.size) { index -> AssistantMessageBubble(messages[messages.lastIndex - index]) }
            }
        }
        pendingOrderDraft?.let { draft ->
            Surface(color = ArgentPanelAlt, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "EMİR TASLAĞI · GÖNDERİLMEDİ",
                        color = ArgentGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (language == "en") "${draft.symbol} ${draft.side} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}" else "${draft.symbol} ${if (draft.side == "BUY") "ALIŞ" else "SATIŞ"} LIMIT · ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}",
                        color = ArgentSilver,
                        fontSize = 11.sp,
                    )
                    val quoteTime =
                        java.text.SimpleDateFormat(
                            "dd.MM.yyyy HH:mm:ss",
                            java.util.Locale.getDefault(),
                        ).format(java.util.Date(draft.dataAsOf))
                    Text(
                        if (language == "en") {
                            "Estimated amount ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · as of $quoteTime. ${if (draft.note.contains(
                                    "anlık",
                                )
                            ) {
                                "The limit price was taken from the current ${if (draft.side == "BUY") "ask" else "bid"} quote; review it before submission."
                            } else {
                                "The limit price came from your request; review it before submission."
                            }}"
                        } else {
                            "Tahmini tutar ${draft.quoteNotional} ${draft.quoteAsset} · ${draft.source} · veri $quoteTime. ${draft.note}"
                        },
                        color = ArgentMuted,
                        fontSize = 10.sp,
                    )
                    Button(onClick = {
                        confirmOrderDraft = true
                    }, colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = ArgentBg)) {
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
                maxLines = 3,
                modifier = Modifier.weight(1f),
                colors = fieldColors(),
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
                                                if (language == "en") "Draft ready: ${draft.side} LIMIT ${draft.quantity} ${draft.symbol} at ${draft.price} ${draft.quoteAsset}. It has not been submitted; review the summary below and confirm it separately." else "Taslağı hazırladım: ${draft.symbol} ${draft.side} LIMIT, ${draft.quantity} @ ${draft.price} ${draft.quoteAsset}. Henüz gönderilmedi; alttaki özeti kontrol edip ayrıca onaylayabilirsin.",
                                            ),
                                        )
                                    }
                                    .onFailure {
                                        messages.add(
                                            AssistantUiMessage(
                                                false,
                                                com.argent.client.core.UiTranslations.translate(
                                                    it.message ?: if (language == "en") "Could not create the order draft." else "Emir taslağı oluşturulamadı.",
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
                                        messages.add(AssistantUiMessage(false, result.answer))
                                    }
                                    .onFailure {
                                        messages.add(
                                            AssistantUiMessage(
                                                false,
                                                com.argent.client.core.UiTranslations.translate(
                                                    it.message ?: if (language == "en") "The assistant could not reply." else "Asistan yanıt veremedi.",
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
                colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = ArgentBg),
            ) {
                Text(if (busy) "…" else "Gönder")
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
                            if (language == "en") "${d.symbol} · ${d.side} LIMIT\n${d.quantity} @ ${d.price} ${d.quoteAsset}\nEstimated amount: ${d.quoteNotional} ${d.quoteAsset}" else "${d.symbol} · ${d.side} LIMIT\n${d.quantity} adet @ ${d.price} ${d.quoteAsset}\nTahmini tutar: ${d.quoteNotional} ${d.quoteAsset}",
                            color = ArgentSilver,
                        )
                    }
                    Text("Bu onay emri ARGENT dahili eşleştirme motoruna gönderir.", color = ArgentMuted)
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
                                        if (language == "en") "Order result: ${result.status} · ${result.filledQuantity} filled · ${result.remainingQuantity} remaining." else "Emir sonucu: ${result.status} · ${result.filledQuantity} doldu · ${result.remainingQuantity} kaldı.",
                                    ),
                                )
                            }
                            .onFailure {
                                messages.add(
                                    AssistantUiMessage(
                                        false,
                                        com.argent.client.core.UiTranslations.translate(
                                            it.message ?: if (language == "en") "Could not submit the order." else "Emir gönderilemedi.",
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
                }) { Text("ONAYLA VE GÖNDER", color = ArgentGreen) }
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

private fun looksLikeOrderDraftRequest(text: String): Boolean {
    val q = text.lowercase()
    return ("limit" in q || "limitli" in q) &&
        ("alış" in q || "alis" in q || "buy" in q || "sat" in q || "sell" in q) &&
        listOf("usdt", "usd", "try", "tl").any(q::contains)
}
