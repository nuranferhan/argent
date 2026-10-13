package com.argent.terminal.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val ArgentBg = Color(0xFF07090C)
val ArgentPanel = Color(0xFF10151C)
val ArgentPanelAlt = Color(0xFF161C25)
val ArgentLine = Color(0xFF2A3340)
val ArgentSilver = Color(0xFFC9D1D9)
val ArgentMuted = Color(0xFF8B95A5)
val ArgentGreen = Color(0xFF0ECB81)
val ArgentRed = Color(0xFFF6465D)
val ArgentGold = Color(0xFFD4AF37)
val ArgentBlue = Color(0xFF4C8DFF)
val ArgentGoldDim = Color(0x33D4AF37)
val ArgentGreenDim = Color(0x220ECB81)
val ArgentRedDim = Color(0x22F6465D)

val ArgentColors = darkColorScheme(
    primary = ArgentGold,
    onPrimary = Color(0xFF111111),
    secondary = ArgentSilver,
    background = ArgentBg,
    surface = ArgentPanel,
    onBackground = ArgentSilver,
    onSurface = ArgentSilver,
    error = ArgentRed
)
