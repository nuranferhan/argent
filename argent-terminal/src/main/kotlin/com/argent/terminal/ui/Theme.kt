package com.argent.terminal.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class ArgentPalette(val bg: Color, val panel: Color, val panelAlt: Color, val line: Color, val silver: Color, val muted: Color, val green: Color, val red: Color, val gold: Color, val blue: Color, val goldDim: Color, val greenDim: Color, val redDim: Color)
private val Dark = ArgentPalette(Color(0xFF07090C), Color(0xFF10151C), Color(0xFF161C25), Color(0xFF2A3340), Color(0xFFC9D1D9), Color(0xFF8B95A5), Color(0xFF0ECB81), Color(0xFFF6465D), Color(0xFFD4AF37), Color(0xFF4C8DFF), Color(0x33D4AF37), Color(0x220ECB81), Color(0x22F6465D))
private val Light = ArgentPalette(Color(0xFFF3F5F8), Color.White, Color(0xFFE8ECF1), Color(0xFFD1D8E0), Color(0xFF26313D), Color(0xFF647181), Color(0xFF087F55), Color(0xFFC6283D), Color(0xFF9A7410), Color(0xFF245FC2), Color(0x339A7410), Color(0x22087F55), Color(0x22C6283D))
private object PaletteHolder { var current = Dark }
@Composable fun ArgentTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    PaletteHolder.current = p
    androidx.compose.material3.MaterialTheme(colorScheme = if (dark) darkColorScheme(primary=p.gold,onPrimary=p.bg,secondary=p.silver,background=p.bg,surface=p.panel,onBackground=p.silver,onSurface=p.silver,error=p.red) else lightColorScheme(primary=p.gold,onPrimary=Color.White,secondary=p.silver,background=p.bg,surface=p.panel,onBackground=p.silver,onSurface=p.silver,error=p.red), content=content)
}
val ArgentBg: Color get() = PaletteHolder.current.bg
val ArgentPanel: Color get() = PaletteHolder.current.panel
val ArgentPanelAlt: Color get() = PaletteHolder.current.panelAlt
val ArgentLine: Color get() = PaletteHolder.current.line
val ArgentSilver: Color get() = PaletteHolder.current.silver
val ArgentMuted: Color get() = PaletteHolder.current.muted
val ArgentGreen: Color get() = PaletteHolder.current.green
val ArgentRed: Color get() = PaletteHolder.current.red
val ArgentGold: Color get() = PaletteHolder.current.gold
val ArgentBlue: Color get() = PaletteHolder.current.blue
val ArgentGoldDim: Color get() = PaletteHolder.current.goldDim
val ArgentGreenDim: Color get() = PaletteHolder.current.greenDim
val ArgentRedDim: Color get() = PaletteHolder.current.redDim
val ArgentColors get() = if (PaletteHolder.current.bg == Dark.bg) darkColorScheme(primary=Dark.gold,onPrimary=Dark.bg,secondary=Dark.silver,background=Dark.bg,surface=Dark.panel,onBackground=Dark.silver,onSurface=Dark.silver,error=Dark.red) else lightColorScheme(primary=Light.gold,onPrimary=Color.White,secondary=Light.silver,background=Light.bg,surface=Light.panel,onBackground=Light.silver,onSurface=Light.silver,error=Light.red)
