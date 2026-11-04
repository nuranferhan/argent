package com.argent.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.argent.client.core.ArgentApi
import com.argent.client.core.MeResponse
import com.argent.client.core.googleAuthConfig
import com.argent.client.core.googleNonce
import com.argent.client.core.login
import com.argent.client.core.loginWithGoogle
import com.argent.client.core.me
import com.argent.client.core.register
import com.argent.client.core.registerFromInvitation
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LoginScreen(
    api: ArgentApi,
    onLogin: (MeResponse) -> Unit,
    onGuest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var gateway by remember { mutableStateOf("http://10.0.2.2:8080") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("login") }
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Ink).padding(20.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(
                rememberScrollState(),
            ).background(Panel, RoundedCornerShape(20.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF101820)) {
                    Image(
                        painterResource(com.argent.android.R.drawable.ic_launcher_foreground),
                        contentDescription = "ARGENT simgesi",
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("ARGENT", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Paper Trading Platform · Android", color = Muted)
                }
            }
            Text("Emirler dahili sandbox eşleştirmesinde işlenir.", color = Muted, fontSize = 12.sp)
            Field("Gateway URL", gateway, { gateway = it }, KeyboardType.Uri)
            Text(
                "Emülatör için 10.0.2.2; fiziksel telefon için bilgisayarın yerel ağ IP adresini kullan.",
                color = Muted,
                fontSize = 11.sp,
            )
            Field("E-posta", email, { email = it }, KeyboardType.Email)
            Field("Şifre", password, { password = it }, secret = true)
            if (mode != "login") {
                Text(
                    "Yeni hesap şifresi en az 12 karakter olmalıdır.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            if (mode != "login") Field("Görünen ad", name, { name = it })
            if (mode == "invite") Field("Davet kodu", invite, { invite = it })
            error?.let { Text(it, color = Red, fontSize = 13.sp) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    api.baseUrl = gateway.trimEnd('/')
                    scope.launch {
                        try {
                            when (mode) {
                                "register" -> api.register(email, password, name.ifBlank { email.substringBefore('@') })
                                "invite" ->
                                    api.registerFromInvitation(
                                        email,
                                        password,
                                        name.ifBlank { email.substringBefore('@') },
                                        invite.trim(),
                                    )
                                else -> api.login(email, password)
                            }
                            onLogin(api.me())
                        } catch (
                            cancelled: CancellationException,
                        ) {
                            throw cancelled
                        } catch (
                            failure: Exception,
                        ) {
                            error = failure.message ?: "Bağlantı kurulamadı."
                        } finally {
                            busy = false
                            password = ""
                        }
                    }
                },
                modifier =
                    Modifier.fillMaxWidth().height(
                        50.dp,
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Ink)
                } else {
                    Text(
                        when (mode) {
                            "invite" -> "Daveti kabul et"
                            "register" -> "Kayıt ol"
                            else -> "Giriş"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            OutlinedButton(enabled = !busy, onClick = {
                busy = true
                error = null
                api.baseUrl = gateway.trimEnd('/')
                scope.launch {
                    try {
                        val googleConfig = api.googleAuthConfig()
                        val clientId =
                            googleConfig.webClientId?.takeIf { googleConfig.configured }
                                ?: throw IllegalStateException(
                                    "Google ile giriş için gateway üzerinde GOOGLE_WEB_CLIENT_ID ayarlanmalı.",
                                )
                        val nonce = api.googleNonce().nonce
                        val option = GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build()
                        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                        val result = CredentialManager.create(context).getCredential(context, request)
                        val credential = result.credential as? CustomCredential
                        if (credential?.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            throw IllegalStateException(
                                "Google kimlik doğrulaması tamamlanamadı.",
                            )
                        }
                        api.loginWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
                        onLogin(api.me())
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
            }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Google ile devam et") }
            Text("Hesap açınca sanal bakiye tanımlanır; gerçek para kullanılmaz.", color = Muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    mode = if (mode == "register") "login" else "register"
                }) { Text(if (mode == "register") "Giriş" else "Hesap oluştur") }
                TextButton(onClick = {
                    mode = if (mode == "invite") "login" else "invite"
                }) { Text(if (mode == "invite") "Giriş" else "Davet kodu") }
            }
            TextButton(enabled = !busy, onClick = {
                api.baseUrl = gateway.trim().trimEnd('/')
                onGuest()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (LocalUiLanguage.current == "en") "Continue as guest" else "Misafir olarak giriş yap")
            }
        }
    }
}
