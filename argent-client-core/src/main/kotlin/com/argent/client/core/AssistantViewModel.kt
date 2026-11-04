package com.argent.client.core

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** UI state shared by the Android and desktop assistant screens. */
class AssistantViewModel {
    val messages = mutableStateListOf<AssistantUiMessage>()
    val prompt = mutableStateOf("")
    val enabled = mutableStateOf<Boolean?>(null)
    val busy = mutableStateOf(false)
    val pendingOrderDraft = mutableStateOf<AssistantOrderDraftDto?>(null)
    val confirmOrderDraft = mutableStateOf(false)
    val orderConfirmPassword = mutableStateOf("")
}

data class AssistantUiMessage(
    val fromUser: Boolean,
    val text: String,
    val metadata: String? = null,
)
