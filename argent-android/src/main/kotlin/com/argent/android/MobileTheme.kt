package com.argent.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text as MaterialText

internal val LocalUiLanguage = androidx.compose.runtime.compositionLocalOf { "tr" }

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    textAlign: TextAlign? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val readableColor = if (MobilePaletteHolder.current.ink == Color(0xFFF3F5F8) && (color == Color(0xFFE0E4E8) || color == Color(0xFFE5E8ED))) AppText else color
    val readableText =
        com.argent.client.core.UiNumberFormatting.limitFractionDigits(
            com.argent.client.core.UiTranslations.translate(text, LocalUiLanguage.current),
        )
    MaterialText(
        readableText,
        modifier = modifier, color = readableColor, fontSize = fontSize, fontWeight = fontWeight,
        fontFamily = fontFamily, textAlign = textAlign, lineHeight = lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    )
}

internal data class MobilePalette(val ink: Color, val panel: Color, val line: Color, val gold: Color, val green: Color, val red: Color, val muted: Color, val text: Color)

internal object MobilePaletteHolder {
    var current =
        MobilePalette(
            Color(0xFF0B0E12),
            Color(0xFF151A21),
            Color(0xFF29313C),
            Color(0xFFE0B82F),
            Color(0xFF00C98A),
            Color(0xFFFF5065),
            Color(0xFF98A3B3),
            Color(0xFFE5E8ED),
        )
}

internal val Ink: Color get() = MobilePaletteHolder.current.ink
internal val Panel: Color get() = MobilePaletteHolder.current.panel
internal val Line: Color get() = MobilePaletteHolder.current.line
internal val Gold: Color get() = MobilePaletteHolder.current.gold
internal val Green: Color get() = MobilePaletteHolder.current.green
internal val Red: Color get() = MobilePaletteHolder.current.red
internal val Muted: Color get() = MobilePaletteHolder.current.muted
internal val AppText: Color get() = MobilePaletteHolder.current.text
