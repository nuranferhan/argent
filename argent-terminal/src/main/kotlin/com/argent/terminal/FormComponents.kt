package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentLine
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun Metric(
    label: String,
    value: String,
    negative: Boolean = false,
) {
    Column {
        Text(label, color = ArgentMuted, fontSize = 10.sp)
        Text(
            compact(value),
            color = if (negative) ArgentRed else ArgentGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun Chip(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.background(if (selected) color.copy(alpha = 0.25f) else ArgentPanelAlt, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) color else ArgentLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) { Text(text, color = if (selected) color else ArgentMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun field(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = fieldColors(),
        singleLine = true,
    )
}

@Composable
internal fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = ArgentSilver,
        unfocusedTextColor = ArgentSilver,
        focusedBorderColor = ArgentGold,
        unfocusedBorderColor = ArgentLine,
        focusedLabelColor = ArgentGold,
        unfocusedLabelColor = ArgentMuted,
    )

internal fun compact(raw: String): String =
    runCatching {
        val value = BigDecimal(raw)
        (if (value.scale() > 6) value.setScale(6, RoundingMode.HALF_UP) else value).stripTrailingZeros().toPlainString()
    }.getOrElse { raw }
