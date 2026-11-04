package com.argent.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.MeResponse
import com.argent.client.core.TenantDto
import com.argent.client.core.TenantMemberDto
import com.argent.client.core.acceptTenantInvitation
import com.argent.client.core.closeWorkspace
import com.argent.client.core.inviteTenantMember
import com.argent.client.core.killMarketMaker
import com.argent.client.core.marketMakerState
import com.argent.client.core.me
import com.argent.client.core.removeTenantMember
import com.argent.client.core.resumeMarketMaker
import com.argent.client.core.switchTenant
import com.argent.client.core.tenantMembers
import com.argent.client.core.tenants
import com.argent.client.core.updateTenantMemberRole
import kotlinx.coroutines.launch

@Composable
internal fun WorkspacePage(
    api: ArgentApi,
    me: MeResponse,
    onMeChanged: (MeResponse) -> Unit,
    onMessage: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf(emptyList<TenantMemberDto>()) }
    var tenants by remember { mutableStateOf(emptyList<TenantDto>()) }
    var email by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("TRADER") }
    var inviteCode by remember { mutableStateOf("") }
    var makerEnabled by remember { mutableStateOf<Boolean?>(null) }
    var inviteResult by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            members = api.tenantMembers()
            tenants = api.tenants()
        }
        runCatching { makerEnabled = api.marketMakerState().enabled }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(
                onClick = onBack,
            ) { Text(if (LocalUiLanguage.current == "en") "← Settings" else "← Ayarlar") }
        }
        item {
            MetricCard(
                "Aktif rol",
                "${me.tenantRole} · ${me.email}",
                tenants.firstOrNull()?.tenantName ?: "Çalışma alanı",
            )
        }
        item { Text("ÇALIŞMA ALANLARI", color = Gold, fontWeight = FontWeight.Bold) }
        items(tenants) { tenant ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tenant.tenantName, fontWeight = FontWeight.Bold)
                        Text(tenant.role, color = Muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                api.switchTenant(tenant.tenantId)
                                onMeChanged(api.me())
                            }.onFailure { onMessage("Geçiş başarısız: ${it.message}") }
                        }
                    }) { Text("GEÇ") }
                }
            }
        }
        if (me.tenantRole in setOf("OWNER", "ADMIN")) {
            item { Text("ÜYE YÖNETİMİ", color = Gold, fontWeight = FontWeight.Bold) }
            items(members) { member ->
                val mayManage =
                    member.role != "OWNER" && member.userId != me.userId &&
                        (me.tenantRole == "OWNER" || member.role != "ADMIN")
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(member.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${member.email} · ${member.role}", color = Muted, fontSize = 11.sp)
                        if (mayManage) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    val role =
                                        when (member.role) {
                                            "VIEWER" -> "TRADER"
                                            "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"
                                            else -> "VIEWER"
                                        }
                                    scope.launch {
                                        runCatching {
                                            api.updateTenantMemberRole(member.userId, role)
                                            members = api.tenantMembers()
                                            onMessage("Üye rolü $role olarak güncellendi")
                                        }.onFailure { onMessage("Rol güncellenemedi: ${it.message}") }
                                    }
                                }) { Text("ROLÜ DEĞİŞTİR") }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            api.removeTenantMember(member.userId)
                                            members = api.tenantMembers()
                                            onMessage("Üyelik iptal edildi")
                                        }.onFailure { onMessage("Üyelik iptal edilemedi: ${it.message}") }
                                    }
                                }) { Text("ÜYELİĞİ İPTAL ET", color = Red) }
                            }
                        }
                    }
                }
            }
            item { Field("Davet e-postası", email, { email = it }, KeyboardType.Email) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("TRADER", inviteRole == "TRADER") { inviteRole = "TRADER" }
                    Choice("VIEWER", inviteRole == "VIEWER") { inviteRole = "VIEWER" }
                    if (me.tenantRole == "OWNER") Choice("ADMIN", inviteRole == "ADMIN") { inviteRole = "ADMIN" }
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                api.inviteTenantMember(email, inviteRole).also { inviteResult = it.token }
                                onMessage("Davet oluşturuldu")
                            }.onFailure { onMessage("Davet hatası: ${it.message}") }
                        }
                    }) { Text("DAVET") }
                }
            }
            inviteResult?.let { code -> item { DataRow("Davet kodu", code) } }
            if (me.tenantRole == "OWNER") {
                item {
                    TextButton(onClick = { confirmClose = true }) { Text("ÇALIŞMA ALANINI KAPAT", color = Red) }
                }
            }
        }
        if (me.role == "ADMIN") {
            item { Text("MARKET MAKER", color = Gold, fontWeight = FontWeight.Bold) }
            item {
                DataRow(
                    "Durum",
                    if (makerEnabled == true) {
                        "AÇIK"
                    } else if (makerEnabled == false) {
                        "KAPALI"
                    } else {
                        "yükleniyor"
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = makerEnabled != false, onClick = {
                        scope.launch {
                            runCatching {
                                api.killMarketMaker()
                                makerEnabled = false
                                onMessage("Market maker kill switch etkin")
                            }.onFailure {
                                onMessage("Kill switch hatası: ${it.message}")
                            }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("KILL SWITCH") }
                    OutlinedButton(enabled = makerEnabled != true, onClick = {
                        scope.launch {
                            runCatching {
                                api.resumeMarketMaker()
                                makerEnabled = true
                                onMessage("Market maker yeniden başlatıldı")
                            }.onFailure { onMessage("Başlatma hatası: ${it.message}") }
                        }
                    }) { Text("YENİDEN BAŞLAT") }
                }
            }
        }
        item { Field("Mevcut kullanıcı davet kodu", inviteCode, { inviteCode = it }) }
        item {
            Button(onClick = {
                scope.launch {
                    runCatching {
                        api.acceptTenantInvitation(inviteCode)
                        onMeChanged(api.me())
                        onMessage("Davet kabul edildi")
                    }.onFailure { onMessage("Davet kabul hatası: ${it.message}") }
                }
            }) { Text("DAVETİ KABUL ET") }
        }
    }
    if (confirmClose) {
        AlertDialog(onDismissRequest = {
            confirmClose = false
        }, title = {
            Text("Çalışma alanı kapatılsın mı?")
        }, text = { Text("Bu işlem alanı kapatır ve oturumu sonlandırır.") }, confirmButton = {
            TextButton(onClick = {
                confirmClose = false
                scope.launch {
                    runCatching {
                        api.closeWorkspace()
                        onLogout()
                    }.onFailure { onMessage("Kapatma hatası: ${it.message}") }
                }
            }) {
                Text("KAPAT", color = Red)
            }
        }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("VAZGEÇ") } })
    }
}
