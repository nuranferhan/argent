package com.argent.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.AssistantUiMessage

internal fun looksLikeOrderDraftRequest(text: String): Boolean {
    val q = text.lowercase()
    return ("limit" in q || "limitli" in q) &&
        ("alış" in q || "alis" in q || "buy" in q || "sat" in q || "sell" in q) &&
        listOf("usdt", "usd", "try", "tl").any(q::contains)
}

internal fun cleanAssistantAnswer(raw: String): String {
    val metadata =
        Regex(
            "(?i)\\b(?:source|kaynak(?:\\s+verisi)?|veri\\s+kaynağı|" +
                "piyasa\\s+modu|market\\s+mode|veri\\s+zaman[ıi]|data\\s+time|timestamp)\\s*:",
        )
    return raw.lineSequence()
        .map { line ->
            var clean = line.trim().replace(Regex("(?i)^ARGENT\\s+AI\\s*:\\s*"), "")
            metadata.find(clean)?.let { marker ->
                clean = clean.substring(0, marker.range.first).trimEnd()
            }
            clean
        }
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()
}

@Composable
internal fun AssistantMessageBubble(message: AssistantUiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 1050.dp).fillMaxWidth(if (message.fromUser) 0.86f else 0.98f),
            color =
                if (message.fromUser) {
                    if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8)) Color(0xFFFFF2C6) else Color(0xFF282615)
                } else {
                    Panel
                },
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (message.fromUser) "SEN" else "ARGENT AI",
                    color = if (message.fromUser) Gold else Green,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
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
                is AssistantBlock.Heading ->
                    Text(
                        block.text,
                        color = Gold,
                        fontSize = if (block.level <= 2) 13.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                is AssistantBlock.Paragraph ->
                    Text(
                        block.text,
                        color = Color(0xFFE0E4E8),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                is AssistantBlock.Bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("•", color = Gold, fontWeight = FontWeight.Bold)
                        Text(block.text, color = Color(0xFFE0E4E8), fontSize = 12.sp, lineHeight = 18.sp)
                    }
                is AssistantBlock.Table ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.rows.forEach { row ->
                            Surface(
                                color =
                                    if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8)) {
                                        Color(
                                            0xFFF3F5F8,
                                        )
                                    } else {
                                        Color(0xFF101A20)
                                    },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    block.headers.forEachIndexed { index, heading ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                heading,
                                                color = Muted,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.weight(1.15f),
                                            )
                                            Text(
                                                row.getOrElse(index) {
                                                    "—"
                                                },
                                                color =
                                                    Color(
                                                        0xFFE0E4E8,
                                                    ),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                textAlign = TextAlign.End,
                                                modifier =
                                                    Modifier.weight(
                                                        1f,
                                                    ),
                                            )
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
        if (line.isBlank()) {
            index++
            continue
        }
        if (isTableRow(line)) {
            val tableLines = mutableListOf<String>()
            while (index < lines.size && isTableRow(lines[index])) tableLines += lines[index++]
            val rows = tableLines.map(::cells).filterNot { row -> row.all { it.matches(Regex(":?-{2,}:?")) } }
            if (rows.size > 1) {
                result += AssistantBlock.Table(rows.first(), rows.drop(1))
            } else {
                rows.firstOrNull()?.forEach { result += AssistantBlock.Paragraph(it) }
            }
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
        if (heading != null) {
            result += AssistantBlock.Heading(clean(heading.groupValues[2]), heading.groupValues[1].length)
            index++
            continue
        }
        val bullet = Regex("^(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$").matchEntire(line)
        if (bullet != null) {
            result += AssistantBlock.Bullet(clean(bullet.groupValues[1]))
            index++
            continue
        }
        val paragraph = mutableListOf(clean(line))
        index++
        while (index < lines.size && lines[index].isNotBlank() && !isTableRow(lines[index].trim()) &&
            !Regex("^(?:#{1,6}\\s+|[-*+]\\s+|\\d+[.)]\\s+)").containsMatchIn(lines[index].trim())
        ) {
            paragraph += clean(lines[index])
            index++
        }
        result += AssistantBlock.Paragraph(paragraph.joinToString(" "))
    }
    return result
}
