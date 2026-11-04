package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.login
import com.argent.client.core.me
import com.argent.client.core.register
import com.argent.client.core.registerFromInvitation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentBlue
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentLine
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsDialog(
    me: MeResponse,
    dark: Boolean,
    scale: Float,
    refresh: Int,
    marketRefresh: Int,
    language: String,
    onClose: () -> Unit,
    onPreview: (Boolean, Float) -> Unit,
    onLanguagePreview: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: (Boolean, Float, Int, Int, String) -> Unit,
) {
    var d by remember { mutableStateOf(dark) }
    var s by remember { mutableStateOf(scale) }
    var r by remember { mutableStateOf(refresh) }
    var mr by remember { mutableStateOf(marketRefresh) }
    var lang by remember { mutableStateOf(language) }

    fun cancel() {
        onCancel()
        onClose()
    }
    AlertDialog(onDismissRequest = ::cancel, title = { Text(UiStrings.text(UiTextKey.SETTINGS, lang)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                UiStrings.text(UiTextKey.ACCOUNT, lang).uppercase(),
                color = ArgentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Text(
                "${me.displayName} · ${me.email} · ${UiStrings.text(UiTextKey.ROLE, lang)}: ${me.role}",
                color = ArgentMuted,
                fontSize = 11.sp,
            )
            Text(
                UiStrings.text(UiTextKey.LANGUAGE, lang).uppercase(),
                color = ArgentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Row {
                TextButton(onClick = {
                    lang = "tr"
                    onLanguagePreview(lang)
                }) { Text(if (lang == "tr") "✓ Türkçe" else "Türkçe") }
                TextButton(onClick = {
                    lang = "en"
                    onLanguagePreview(lang)
                }) { Text(if (lang == "en") "✓ English" else "English") }
            }
            Text(
                UiStrings.text(UiTextKey.APPEARANCE, lang).uppercase(),
                color = ArgentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Row {
                TextButton(onClick = {
                    d = true
                    onPreview(d, s)
                }) {
                    Text(
                        if (d) {
                            "✓ ${UiStrings.text(
                                UiTextKey.DARK,
                                lang,
                            )} tema"
                        } else {
                            "${UiStrings.text(UiTextKey.DARK, lang)} tema"
                        },
                    )
                }
                TextButton(onClick = {
                    d = false
                    onPreview(d, s)
                }) {
                    Text(
                        if (!d) {
                            "✓ ${UiStrings.text(
                                UiTextKey.LIGHT,
                                lang,
                            )} tema"
                        } else {
                            "${UiStrings.text(UiTextKey.LIGHT, lang)} tema"
                        },
                    )
                }
            }
            Text(UiStrings.formatted(UiTextKey.TEXT_SIZE, lang, (s * 100).toInt()))
            Slider(value = s, onValueChange = {
                s = it
                onPreview(d, s)
            }, valueRange = .85f..1.3f)
            Text(
                UiStrings.text(UiTextKey.MARKET_DATA, lang).uppercase(),
                color = ArgentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Text(UiStrings.text(UiTextKey.FINNHUB_AUTO_REFRESH, lang), color = ArgentMuted, fontSize = 10.sp)
            Row {
                listOf(60 to UiTextKey.MINUTE_1, 300 to UiTextKey.MINUTES_5, 900 to UiTextKey.MINUTES_15).forEach {
                        (seconds, key) ->
                    val label = UiStrings.text(key, lang)
                    TextButton(onClick = { mr = seconds }) { Text(if (mr == seconds) "✓ $label" else label) }
                }
            }
            Text(UiStrings.formatted(UiTextKey.GENERAL_REFRESH, lang, r))
            Slider(
                value = r.toFloat(),
                onValueChange = { r = it.toInt().coerceAtLeast(1) },
                valueRange = 1f..10f,
                steps = 8,
            )
            Text(
                UiStrings.text(UiTextKey.ORDER_SAFETY, lang).uppercase(),
                color = ArgentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
            Text(UiStrings.text(UiTextKey.AI_ORDER_CONFIRMATION, lang), color = ArgentMuted, fontSize = 10.sp)
        }
    }, confirmButton = {
        TextButton(onClick = {
            onSave(d, s, r, mr, lang)
            onClose()
        }) {
            Text(UiStrings.text(UiTextKey.SAVE_SETTINGS, lang))
        }
    }, dismissButton = { TextButton(onClick = ::cancel) { Text(if (lang == "en") "Cancel" else "İptal") } })
}

@Composable
internal fun LoginPane(
    api: ArgentApi,
    onAuthed: (MeResponse) -> Unit,
    onGuest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("http://127.0.0.1:8080") }
    var email by remember { mutableStateOf(System.getenv("BOOTSTRAP_ADMIN_EMAIL").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("Admin") }
    var invitationToken by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("login") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp)
                .background(ArgentPanel, RoundedCornerShape(14.dp))
                .border(1.dp, ArgentLine, RoundedCornerShape(14.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF101820)) {
                    Image(
                        painterResource("argent-icon.png"),
                        contentDescription = "ARGENT simgesi",
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "ARGENT",
                        color = ArgentGold,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text("Paper Trading Platform", color = ArgentMuted, fontSize = 13.sp)
                }
            }
            Text(
                "ARGENT gateway hesabınla oturum aç veya çalışma alanı davetini kabul et.",
                color = ArgentMuted,
                fontSize = 12.sp,
            )
            field("Gateway", host) { host = it }
            field("E-posta", email) { email = it }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            if (mode != "login") {
                Text("Yeni hesap şifresi en az 12 karakter olmalıdır.", color = ArgentMuted, fontSize = 11.sp)
            }
            if (mode != "login") field("Görünen ad", name) { name = it }
            if (mode == "invitation") field("Davet kodu", invitationToken) { invitationToken = it }
            error?.let { Text(it, color = ArgentRed, fontSize = 12.sp) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    api.baseUrl = host.trimEnd('/')
                    scope.launch {
                        try {
                            when (mode) {
                                "register" -> api.register(email, password, name.ifBlank { email.substringBefore("@") })
                                "invitation" ->
                                    api.registerFromInvitation(
                                        email,
                                        password,
                                        name.ifBlank { email.substringBefore("@") },
                                        invitationToken.trim(),
                                    )
                                else -> api.login(email, password)
                            }
                            onAuthed(api.me())
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message
                        } finally {
                            password = ""
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArgentGold, contentColor = Color(0xFF111111)),
            ) {
                Text(
                    when (mode) {
                        "login" -> "Giriş"
                        "invitation" -> "Daveti kabul et"
                        else -> "Kayıt ol ve giriş"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    api.baseUrl = host.trimEnd('/')
                    scope.launch {
                        try {
                            googleDesktopSignIn(api)
                            onAuthed(api.me())
                        } catch (
                            cancelled: CancellationException,
                        ) {
                            throw cancelled
                        } catch (
                            failure: Exception,
                        ) {
                            error = failure.message ?: "Google ile giriş başarısız."
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier =
                    Modifier.fillMaxWidth().height(
                        42.dp,
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = ArgentPanelAlt, contentColor = ArgentSilver),
            ) {
                Text("Google ile devam et")
            }
            if (mode != "invitation") {
                TextButton(onClick = { mode = if (mode == "login") "register" else "login" }) {
                    Text(if (mode == "login") "Hesap oluştur" else "Giriş ekranına dön", color = ArgentBlue)
                }
            }
            TextButton(onClick = { mode = if (mode == "invitation") "login" else "invitation" }) {
                Text(if (mode == "invitation") "Giriş ekranına dön" else "Davet koduyla katıl", color = ArgentBlue)
            }
            TextButton(enabled = !busy, onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (LocalUiLanguage.current == "en") "Continue as guest" else "Misafir olarak giriş yap",
                    color = ArgentBlue,
                )
            }
        }
    }
}
