package com.argent.gateway.assistant

import kotlinx.serialization.Serializable

internal data class ChatRequest(val model: String, val messages: List<ChatMessage>)

data class AssistantDraftOrder(val side: String, val type: String, val quantity: String, val price: String?)

@Serializable
internal data class ChatMessage(val role: String, val content: String)

@Serializable
internal data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
internal data class ChatChoice(val message: ChatMessage)
