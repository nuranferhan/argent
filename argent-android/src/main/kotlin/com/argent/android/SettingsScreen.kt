package com.argent.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.MeResponse
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey

@Composable
internal fun SettingsPage(
    me: MeResponse,
    dark: Boolean,
    fontScale: Float,
    refresh: Int,
    marketRefresh: Int,
    onPreview: (Boolean, Float) -> Unit,
    onLanguagePreview: (String) -> Unit,
    onSave: (Boolean, Float, Int, Int, String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onSecureLogout: () -> Unit,
) {
    val currentLanguage = LocalUiLanguage.current
    var d by remember { mutableStateOf(dark) }
    var size by remember { mutableStateOf(fontScale) }
    var interval by remember { mutableStateOf(refresh) }
    var marketInterval by remember { mutableStateOf(marketRefresh) }
    var language by remember { mutableStateOf(currentLanguage) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                UiStrings.text(UiTextKey.SETTINGS, language).uppercase(),
                color = Gold,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(UiStrings.text(UiTextKey.ACCOUNT, language), fontWeight = FontWeight.Bold)
                    Text(me.displayName, color = Muted)
                    Text(me.email, color = Muted)
                    Text("${UiStrings.text(UiTextKey.ROLE, language)}: ${me.tenantRole}", color = Muted)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(UiStrings.text(UiTextKey.LANGUAGE, language), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Choice(if (language == "tr") "✓ Türkçe" else "Türkçe", language == "tr") {
                            language = "tr"
                            onLanguagePreview(language)
                        }
                        Choice(if (language == "en") "✓ English" else "English", language == "en") {
                            language = "en"
                            onLanguagePreview(language)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(UiStrings.text(UiTextKey.APPEARANCE, language), fontWeight = FontWeight.Bold)
                    Row {
                        Choice(
                            if (d) {
                                "✓ ${UiStrings.text(
                                    UiTextKey.DARK,
                                    language,
                                )}"
                            } else {
                                UiStrings.text(UiTextKey.DARK, language)
                            },
                            d,
                        ) {
                            d = true
                            onPreview(d, size)
                        }
                        Choice(
                            if (!d) {
                                "✓ ${UiStrings.text(
                                    UiTextKey.LIGHT,
                                    language,
                                )}"
                            } else {
                                UiStrings.text(UiTextKey.LIGHT, language)
                            },
                            !d,
                        ) {
                            d = false
                            onPreview(d, size)
                        }
                    }
                    Text(UiStrings.formatted(UiTextKey.TEXT_SIZE, language, (size * 100).toInt()))
                    Slider(value = size, onValueChange = {
                        size = it
                        onPreview(d, size)
                    }, valueRange = .85f..1.3f)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(UiStrings.text(UiTextKey.MARKET_DATA, language), fontWeight = FontWeight.Bold)
                    Text(UiStrings.text(UiTextKey.FINNHUB_AUTO_REFRESH, language), color = Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            60 to UiTextKey.MINUTE_1,
                            300 to UiTextKey.MINUTES_5,
                            900 to UiTextKey.MINUTES_15,
                        ).forEach {
                                (seconds, key) ->
                            val label = UiStrings.text(key, language)
                            Choice(
                                if (marketInterval == seconds) "✓ $label" else label,
                                marketInterval == seconds,
                            ) { marketInterval = seconds }
                        }
                    }
                    Text(
                        UiStrings.formatted(UiTextKey.GENERAL_REFRESH, language, interval),
                        color = Muted,
                        fontSize = 11.sp,
                    )
                    Slider(value = interval.toFloat(), onValueChange = {
                        interval = it.toInt().coerceAtLeast(1)
                    }, valueRange = 1f..10f, steps = 8)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(UiStrings.text(UiTextKey.ORDER_SAFETY, language), fontWeight = FontWeight.Bold)
                    Text(UiStrings.text(UiTextKey.AI_ORDER_CONFIRMATION, language), color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) {
                Text(UiStrings.text(UiTextKey.WORKSPACE_SETTINGS, language))
            }
        }
        item {
            Button(onClick = {
                onSave(d, size, interval, marketInterval, language)
            }) { Text(UiStrings.text(UiTextKey.SAVE_SETTINGS, language)) }
        }
        item { Text(UiStrings.text(UiTextKey.MARKET_COVERAGE, language), color = Gold, fontWeight = FontWeight.Bold) }
        item { Text(UiStrings.text(UiTextKey.MARKET_COVERAGE_DESCRIPTION, language), color = Muted) }
        item {
            OutlinedButton(
                onClick = onSecureLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
            ) {
                Text(UiStrings.text(UiTextKey.SECURE_SIGN_OUT, language))
            }
        }
    }
}
