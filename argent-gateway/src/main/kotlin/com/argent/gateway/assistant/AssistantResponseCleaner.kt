package com.argent.gateway.assistant

internal object AssistantResponseCleaner {
    fun cleanUserFacingAnswer(raw: String): String {
        val metadata =
            Regex(
                "(?i)\\b(?:source|kaynak(?:\\s+verisi)?|veri\\s+kaynağı|piyasa\\s+modu|market\\s+mode|veri\\s+zaman[ıi]|data\\s+time|timestamp)\\s*:",
            )
        return raw.lineSequence()
            .map { line ->
                var clean = line.trim().replace(Regex("(?i)^ARGENT\\s+AI\\s*[:·-]?\\s*"), "")
                metadata.find(clean)?.let { marker -> clean = clean.substring(0, marker.range.first).trimEnd() }
                clean
            }
            .filter(String::isNotBlank)
            .joinToString("\n")
            .trim()
    }
}
