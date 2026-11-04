package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGoldDim
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentSilver


private sealed interface AssistantBlock {
    data class Heading(val text: String, val level: Int) : AssistantBlock

    data class Paragraph(val text: String) : AssistantBlock

    data class Bullet(val text: String) : AssistantBlock

    data class Table(val headers: List<String>, val rows: List<List<String>>) : AssistantBlock
}

@Composable
internal fun AssistantMessageBubble(message: AssistantUiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 1050.dp).fillMaxWidth(if (message.fromUser) 0.72f else 0.96f),
            color = if (message.fromUser) ArgentGoldDim else ArgentPanel,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (message.fromUser) "SEN" else "ARGENT AI",
                    color = if (message.fromUser) ArgentGold else ArgentGreen,
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
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        assistantBlocks(markdown).forEach { block ->
            when (block) {
                is AssistantBlock.Heading ->
                    Text(
                        block.text,
                        color = ArgentGold,
                        fontSize = if (block.level <= 2) 12.sp else 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                is AssistantBlock.Paragraph ->
                    Text(
                        block.text,
                        color = ArgentSilver,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                is AssistantBlock.Bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", color = ArgentGold, fontWeight = FontWeight.Bold)
                        Text(block.text, color = ArgentSilver, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                is AssistantBlock.Table ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.rows.forEach { row ->
                            Surface(color = ArgentPanelAlt, shape = RoundedCornerShape(7.dp)) {
                                Column(
                                    Modifier.fillMaxWidth().padding(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    block.headers.forEachIndexed { index, heading ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Text(
                                                heading,
                                                color = ArgentMuted,
                                                fontSize = 9.sp,
                                                lineHeight = 12.sp,
                                                modifier = Modifier.weight(1.15f),
                                            )
                                            Text(
                                                row.getOrElse(index) {
                                                    "—"
                                                },
                                                color = ArgentSilver,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
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
