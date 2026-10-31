package com.argent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Slider
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.api.ArgentApi
import com.argent.terminal.api.DepthLevelDto
import com.argent.terminal.api.HealthDto
import com.argent.terminal.api.InstrumentDto
import com.argent.terminal.api.ExternalMarketStatusDto
import com.argent.terminal.api.ExternalQuoteDto
import com.argent.terminal.api.MarketNewsDto
import com.argent.terminal.api.PriceAlertDto
import com.argent.terminal.api.MeResponse
import com.argent.terminal.api.OrderBookDto
import com.argent.terminal.api.OrderDto
import com.argent.terminal.api.PortfolioDto
import com.argent.terminal.api.TenantDto
import com.argent.terminal.api.TenantMemberDto
import com.argent.terminal.api.TickerDto
import com.argent.terminal.api.TechnicalAnalysisDto
import com.argent.terminal.api.TradeDto
import com.argent.terminal.api.AssistantOrderDraftDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale



@Composable
internal fun SettingsPage(me: MeResponse, dark: Boolean, fontScale: Float, refresh: Int, marketRefresh: Int, onPreview: (Boolean, Float) -> Unit, onLanguagePreview: (String) -> Unit, onSave: (Boolean, Float, Int, Int, String) -> Unit, onOpenWorkspace: () -> Unit, onSecureLogout: () -> Unit) {
    val currentLanguage = LocalUiLanguage.current
    var d by remember { mutableStateOf(dark) }; var size by remember { mutableStateOf(fontScale) }; var interval by remember { mutableStateOf(refresh) }; var marketInterval by remember { mutableStateOf(marketRefresh) }; var language by remember { mutableStateOf(currentLanguage) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("AYARLAR", color=Gold, fontWeight=FontWeight.Bold) }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("Hesap", fontWeight=FontWeight.Bold); Text(me.displayName, color=Muted); Text(me.email, color=Muted); Text("Rol: ${me.tenantRole}", color=Muted) } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(if (language == "tr") "Dil" else "Language", fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Choice(if (language == "tr") "✓ Türkçe" else "Türkçe", language == "tr") { language = "tr"; onLanguagePreview(language) }
                Choice(if (language == "en") "✓ English" else "English", language == "en") { language = "en"; onLanguagePreview(language) }
            }
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Görünüm", fontWeight=FontWeight.Bold)
            Row { Choice(if (d) "✓ Koyu" else "Koyu", d) { d=true; onPreview(d,size) }; Choice(if (!d) "✓ Açık" else "Açık", !d) { d=false; onPreview(d,size) } }
            Text("Metin boyutu ${(size*100).toInt()}%"); Slider(value=size,onValueChange={size=it;onPreview(d,size)},valueRange=.85f..1.3f)
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Piyasa verileri", fontWeight=FontWeight.Bold)
            Text("Finnhub kotasyonları ve haberler ekran açıkken otomatik yenilenir.", color=Muted, fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(60 to "1 dk", 300 to "5 dk", 900 to "15 dk").forEach { (seconds,label) -> Choice(if(marketInterval==seconds) "✓ $label" else label, marketInterval==seconds) { marketInterval=seconds } } }
            Text("Genel hesap ve emir güncellemesi: $interval sn", color=Muted, fontSize=11.sp)
            Slider(value=interval.toFloat(),onValueChange={interval=it.toInt().coerceAtLeast(1)},valueRange=1f..10f,steps=8)
        } } }
        item { Card(colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Emir güvenliği", fontWeight=FontWeight.Bold)
            Text("AI emir taslakları özet ve hesap parolasıyla onaylanır; AI kendi başına emir göndermez.", color=Muted, fontSize=11.sp)
        } } }
        item { OutlinedButton(onClick = onOpenWorkspace, modifier = Modifier.fillMaxWidth()) { Text(if (language == "en") "Workspace settings" else "Çalışma alanı ayarları") } }
        item { Button(onClick={onSave(d,size,interval,marketInterval,language)}) { Text("Ayarları kaydet") } }
        item { Text("Piyasa kapsamı", color=Gold, fontWeight=FontWeight.Bold) }
        item { Text("Kripto: Binance public feed. BIST ve sağlayıcı anahtarı bulunmayan ürünler: paper simülasyonu. Veri yenileme aralığı, Finnhub kota kullanımını kontrol etmeye yardımcı olur.", color=Muted) }
        item { OutlinedButton(onClick = onSecureLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) { Text(if (language == "en") "SECURE SIGN OUT" else "GÜVENLİ ÇIKIŞ") } }
    }
}@Composable
internal fun OrdersPage(orders: List<OrderDto>, fills: List<TradeDto>, api: ArgentApi, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    val openOrders = orders.filter { it.status in setOf("PENDING", "ACCEPTED", "PARTIALLY_FILLED") && (it.remainingQuantity.toBigDecimalOrNull()?.signum() ?: 0) > 0 }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Row(Modifier.padding(top = 4.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Choice("AÇIK EMİRLER", tab == 0) { tab = 0 }; Choice("İŞLEMLER", tab == 1) { tab = 1 } }
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                item { Text("AÇIK EMİRLER · ${openOrders.size} bekleyen", color = Gold, fontWeight = FontWeight.Bold) }
                if (openOrders.isEmpty()) item { Text("Bekleyen emir yok. Tamamlanan ve iptal edilen emirler İşlemler geçmişindedir.", color = Muted) }
                items(openOrders, key = { it.id }) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("${order.symbol} ${order.side} ${order.type}", fontWeight = FontWeight.Bold); Text(if (LocalUiLanguage.current == "en") "${orderStatusLabel(order.status, LocalUiLanguage.current)} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "market"}" else "${orderStatusLabel(order.status, LocalUiLanguage.current)} · ${order.filledQuantity}/${order.quantity} · ${order.price ?: "piyasa"}", color = Muted, fontSize = 11.sp) }
                            if (order.status in setOf("ACCEPTED", "PARTIALLY_FILLED")) TextButton(onClick = { scope.launch { runCatching { api.cancel(order.id, order.symbol); onMessage("${order.symbol} emri iptal edildi") }.onFailure { onMessage("İptal hatası: ${it.message}") } } }) { Text("İPTAL", color = Red) }
                        }
                    }
                }
            }
            if (tab == 1) {
                val sortedOrders = orders.sortedByDescending { it.updatedAt.takeIf { updated -> updated > 0 } ?: it.createdAt }
                item { Text("EMİR GEÇMİŞİ", color = Gold, fontWeight = FontWeight.Bold) }
                if (sortedOrders.isEmpty()) item { Text("Emir geçmişi boş.", color = Muted) }
                items(sortedOrders, key = { it.id }) { order ->
                    DataRow("${order.symbol} ${order.side} ${order.type} · ${orderStatusLabel(order.status, LocalUiLanguage.current)}", if (LocalUiLanguage.current == "en") "Filled ${order.filledQuantity}/${order.quantity} · remaining ${order.remainingQuantity} · ${order.price ?: "market"}" else "Dolan ${order.filledQuantity}/${order.quantity} · kalan ${order.remainingQuantity} · ${order.price ?: "piyasa"}")
                }
                item { Text("GERÇEKLEŞEN İŞLEMLER", color = Gold, fontWeight = FontWeight.Bold) }
                if (fills.isEmpty()) item { Text("İşlem geçmişi boş.", color = Muted) }
                items(fills) { trade -> DataRow(trade.symbol, "${trade.quantity} @ ${trade.price}") }
            }
        }
    }
}

internal fun orderStatusLabel(status: String, language: String = "tr"): String = when (status.uppercase()) {
    "ACCEPTED", "NEW", "PENDING" -> if (language == "en") "OPEN" else "AÇIK"
    "PARTIALLY_FILLED" -> if (language == "en") "PARTIALLY FILLED" else "KISMEN DOLDU"
    "FILLED" -> if (language == "en") "FILLED" else "DOLDU"
    "CANCELLED", "CANCELED" -> if (language == "en") "CANCELLED" else "İPTAL"
    "REJECTED" -> if (language == "en") "REJECTED" else "REDDEDİLDİ"
    "EXPIRED" -> if (language == "en") "EXPIRED" else "SÜRESİ DOLDU"
    else -> status
}

@Composable
internal fun WorkspacePage(api: ArgentApi, me: MeResponse, onMeChanged: (MeResponse) -> Unit, onMessage: (String) -> Unit, onLogout: () -> Unit, onBack: () -> Unit) {
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
        runCatching { members = api.tenantMembers(); tenants = api.tenants() }
        runCatching { makerEnabled = api.marketMakerState().enabled }
    }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick = onBack) { Text(if (LocalUiLanguage.current == "en") "← Settings" else "← Ayarlar") } }
        item { MetricCard("Aktif rol", "${me.tenantRole} · ${me.email}", tenants.firstOrNull()?.tenantName ?: "Çalışma alanı") }
        item { Text("ÇALIŞMA ALANLARI", color = Gold, fontWeight = FontWeight.Bold) }
        items(tenants) { tenant ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(tenant.tenantName, fontWeight = FontWeight.Bold); Text(tenant.role, color = Muted, fontSize = 11.sp) }
                    TextButton(onClick = { scope.launch { runCatching { api.switchTenant(tenant.tenantId); onMeChanged(api.me()) }.onFailure { onMessage("Geçiş başarısız: ${it.message}") } } }) { Text("GEÇ") }
                }
            }
        }
        if (me.tenantRole in setOf("OWNER", "ADMIN")) {
            item { Text("ÜYE YÖNETİMİ", color = Gold, fontWeight = FontWeight.Bold) }
            items(members) { member ->
                val mayManage = member.role != "OWNER" && member.userId != me.userId && (me.tenantRole == "OWNER" || member.role != "ADMIN")
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(member.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${member.email} · ${member.role}", color = Muted, fontSize = 11.sp)
                        if (mayManage) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                val role = when (member.role) { "VIEWER" -> "TRADER"; "TRADER" -> if (me.tenantRole == "OWNER") "ADMIN" else "VIEWER"; else -> "VIEWER" }
                                scope.launch { runCatching { api.updateTenantMemberRole(member.userId, role); members = api.tenantMembers(); onMessage("Üye rolü $role olarak güncellendi") }.onFailure { onMessage("Rol güncellenemedi: ${it.message}") } }
                            }) { Text("ROLÜ DEĞİŞTİR") }
                            TextButton(onClick = {
                                scope.launch { runCatching { api.removeTenantMember(member.userId); members = api.tenantMembers(); onMessage("Üyelik iptal edildi") }.onFailure { onMessage("Üyelik iptal edilemedi: ${it.message}") } }
                            }) { Text("ÜYELİĞİ İPTAL ET", color = Red) }
                        }
                    }
                }
            }
            item { Field("Davet e-postası", email, { email = it }, KeyboardType.Email) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("TRADER", inviteRole == "TRADER") { inviteRole = "TRADER" }
                Choice("VIEWER", inviteRole == "VIEWER") { inviteRole = "VIEWER" }
                if (me.tenantRole == "OWNER") Choice("ADMIN", inviteRole == "ADMIN") { inviteRole = "ADMIN" }
                Button(onClick = { scope.launch { runCatching { api.inviteTenantMember(email, inviteRole).also { inviteResult = it.token }; onMessage("Davet oluşturuldu") }.onFailure { onMessage("Davet hatası: ${it.message}") } } }) { Text("DAVET") }
            } }
            inviteResult?.let { code -> item { DataRow("Davet kodu", code) } }
            if (me.tenantRole == "OWNER") item { TextButton(onClick = { confirmClose = true }) { Text("ÇALIŞMA ALANINI KAPAT", color = Red) } }
        }
        if (me.role == "ADMIN") {
            item { Text("MARKET MAKER", color = Gold, fontWeight = FontWeight.Bold) }
            item { DataRow("Durum", if (makerEnabled == true) "AÇIK" else if (makerEnabled == false) "KAPALI" else "yükleniyor") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = makerEnabled != false, onClick = { scope.launch { runCatching { api.killMarketMaker(); makerEnabled = false; onMessage("Market maker kill switch etkin") }.onFailure { onMessage("Kill switch hatası: ${it.message}") } } }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("KILL SWITCH") }
                OutlinedButton(enabled = makerEnabled != true, onClick = { scope.launch { runCatching { api.resumeMarketMaker(); makerEnabled = true; onMessage("Market maker yeniden başlatıldı") }.onFailure { onMessage("Başlatma hatası: ${it.message}") } } }) { Text("YENİDEN BAŞLAT") }
            } }
        }
        item { Field("Mevcut kullanıcı davet kodu", inviteCode, { inviteCode = it }) }
        item { Button(onClick = { scope.launch { runCatching { api.acceptTenantInvitation(inviteCode); onMeChanged(api.me()); onMessage("Davet kabul edildi") }.onFailure { onMessage("Davet kabul hatası: ${it.message}") } } }) { Text("DAVETİ KABUL ET") } }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("Çalışma alanı kapatılsın mı?") }, text = { Text("Bu işlem alanı kapatır ve oturumu sonlandırır.") }, confirmButton = { TextButton(onClick = { confirmClose = false; scope.launch { runCatching { api.closeWorkspace(); onLogout() }.onFailure { onMessage("Kapatma hatası: ${it.message}") } } }) { Text("KAPAT", color = Red) } }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("VAZGEÇ") } })
}
