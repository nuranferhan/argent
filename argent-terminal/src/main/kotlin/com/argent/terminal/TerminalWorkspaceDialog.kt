package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.binanceSpotTestnetOrder
import com.argent.client.core.binanceSpotTestnetPrice
import com.argent.client.core.binanceSpotTestnetStatus
import com.argent.client.core.bistHistoricalReplayStatus
import com.argent.client.core.cancelBinanceSpotTestnetOrder
import com.argent.client.core.closeWorkspace
import com.argent.client.core.killMarketMaker
import com.argent.client.core.marketMakerState
import com.argent.client.core.placeBinanceSpotTestnetOrder
import com.argent.client.core.resumeMarketMaker
import com.argent.client.core.startBistHistoricalReplay
import com.argent.client.core.tenantMembers
import com.argent.client.core.tenants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.terminal.ui.ArgentBlue
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun WorkspaceDialog(
    api: ArgentApi,
    me: MeResponse,
    selectedSymbol: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onClose: () -> Unit,
    onFlash: (String) -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    onWorkspaceClosed: () -> Unit,
    testnetOrder: BinanceTestnetOrderDto?,
    onTestnetOrderChanged: (BinanceTestnetOrderDto?) -> Unit,
) {
    val language = LocalUiLanguage.current
    var tenants by remember { mutableStateOf(emptyList<TenantDto>()) }
    var members by remember { mutableStateOf(emptyList<TenantMemberDto>()) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("TRADER") }
    var inviteToken by remember { mutableStateOf("") }
    var acceptToken by remember { mutableStateOf("") }
    var acceptingInvitation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var marketMaker by remember { mutableStateOf<MarketMakerStateDto?>(null) }
    var testnet by remember { mutableStateOf<BinanceTestnetStatusDto?>(null) }
    var testnetSubmitting by remember { mutableStateOf(false) }
    var testnetSymbol by remember { mutableStateOf("BTCUSDT") }
    var testnetSide by remember { mutableStateOf("BUY") }
    var testnetType by remember { mutableStateOf("LIMIT") }
    var testnetTif by remember { mutableStateOf("GTC") }
    var testnetQuantity by remember { mutableStateOf("0.001") }
    var testnetPrice by remember { mutableStateOf("") }
    var testnetPriceManual by remember { mutableStateOf(false) }
    var testnetPriceLoading by remember { mutableStateOf(false) }
    var bistCsv by remember { mutableStateOf("") }
    var bistReplaySpeed by remember { mutableStateOf("1000") }
    var bistReplay by remember { mutableStateOf<BistReplayDto?>(null) }
    val canManage = me.tenantRole == "OWNER" || me.tenantRole == "ADMIN"

    LaunchedEffect(testnetSymbol, testnet?.configured) {
        if (testnet?.configured == true && testnetSymbol.matches(Regex("[A-Z0-9]{5,20}"))) {
            while (true) {
                testnetPriceLoading = true
                runCatching { api.binanceSpotTestnetPrice(testnetSymbol) }.onSuccess { quote ->
                    if (!testnetPriceManual) testnetPrice = quote.price
                }
                testnetPriceLoading = false
                delay(30_000)
            }
        }
    }
    LaunchedEffect(me.tenantId) {
        tenants = runCatching { api.tenants() }.getOrDefault(emptyList())
        if (canManage) members = runCatching { api.tenantMembers() }.getOrDefault(emptyList())
        if (me.role == "ADMIN") marketMaker = runCatching { api.marketMakerState() }.getOrNull()
        if (me.role == "ADMIN") testnet = runCatching { api.binanceSpotTestnetStatus() }.getOrNull()
        if (me.role == "ADMIN") bistReplay = runCatching { api.bistHistoricalReplayStatus() }.getOrNull()
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            error = null
            try {
                action()
            } catch (failure: Exception) {
                val message =
                    com.argent.client.core.UiTranslations.translate(
                        failure.message ?: "İşlem başarısız",
                        language,
                    )
                error = message
                onFlash(message)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Çalışma alanı", color = ArgentGold) },
        text = {
            Column(
                Modifier.width(600.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Aktif rol: ${me.tenantRole}", color = ArgentMuted, fontSize = 12.sp)
                if (me.role == "ADMIN") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            marketMaker?.let {
                                "Market maker ${if (it.enabled) "AÇIK" else "KAPALI"} · emir üst sınırı ${it.quoteNotionalCap} · stok sınırı ${it.inventoryNotionalCap}"
                            }
                                ?: "Market maker durumu kullanılamıyor",
                            Modifier.weight(1f),
                            color = ArgentMuted,
                            fontSize = 11.sp,
                        )
                        marketMaker?.let { state ->
                            TextButton(onClick = {
                                runAction {
                                    if (state.enabled) api.killMarketMaker() else api.resumeMarketMaker()
                                    marketMaker = api.marketMakerState()
                                }
                            }) {
                                Text(
                                    if (state.enabled) "KILL SWITCH" else "Yeniden başlat",
                                    color = if (state.enabled) ArgentRed else ArgentGreen,
                                )
                            }
                        }
                    }
                    Text(
                        "Binance Spot Testnet · gerçek para yok · gerçekleşen emirler ARGENT sanal bakiyesine yansır",
                        color = ArgentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Emir gönderilince ARGENT varlığı geçici rezerve edilir. Testnet gerçekleşmeleri ve komisyonları bakiyeye yazılır; iptal veya tamamlanmada kullanılmayan rezerv serbest kalır.",
                        color = ArgentMuted,
                        fontSize = 11.sp,
                    )
                    Text(
                        when (testnet?.configured) {
                            true -> "Testnet anahtarları yerel gateway ortamında yapılandırılmış. Emirler yalnızca Binance test ağına gönderilir."
                            false -> "Testnet kapalı. Yerel .env dosyasına yalnızca Binance Spot Testnet anahtarlarını ekleyip gateway'i yeniden başlatın."
                            null -> "Testnet ayarı okunamadı."
                        },
                        color = ArgentMuted,
                        fontSize = 11.sp,
                    )
                    if (testnet?.configured == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(testnetSymbol, {
                                testnetSymbol = it.uppercase()
                                testnetPriceManual = false
                            }, label = {
                                Text(
                                    "Sembol",
                                )
                            }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            TextButton(onClick = {
                                testnetSide = if (testnetSide == "BUY") "SELL" else "BUY"
                            }) { Text(testnetSide, color = if (testnetSide == "BUY") ArgentGreen else ArgentRed) }
                            TextButton(onClick = {
                                testnetType = if (testnetType == "LIMIT") "MARKET" else "LIMIT"
                            }) { Text(testnetType, color = ArgentGold) }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(testnetQuantity, {
                                testnetQuantity = it
                            }, label = {
                                Text(
                                    "Miktar",
                                )
                            }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            if (testnetType == "LIMIT") {
                                OutlinedTextField(testnetPrice, {
                                    testnetPrice = it
                                    testnetPriceManual = true
                                }, label = {
                                    Text(
                                        if (testnetPriceLoading) "Testnet fiyatı yükleniyor…" else "Testnet referans fiyatı",
                                    )
                                }, modifier = Modifier.weight(1f), singleLine = true, colors = fieldColors())
                            }
                            if (testnetType == "LIMIT") {
                                TextButton(enabled = !testnetPriceLoading, onClick = {
                                    scope.launch {
                                        testnetPriceLoading = true
                                        runCatching { api.binanceSpotTestnetPrice(testnetSymbol) }
                                            .onSuccess {
                                                testnetPrice = it.price
                                                testnetPriceManual = false
                                            }
                                            .onFailure { error = it.message ?: "Testnet fiyatı alınamadı" }
                                        testnetPriceLoading = false
                                    }
                                }) { Text(if (testnetPriceLoading) "…" else "Fiyatı yenile", color = ArgentBlue) }
                            }
                            if (testnetType == "LIMIT") {
                                TextButton(onClick = {
                                    testnetTif =
                                        when (testnetTif) {
                                            "GTC" -> "IOC"
                                            "IOC" -> "FOK"
                                            else -> "GTC"
                                        }
                                }) { Text(testnetTif, color = ArgentGold) }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(enabled = !testnetSubmitting && !testnetPriceLoading && (testnetType != "LIMIT" || (testnetPrice.toBigDecimalOrNull()?.signum() ?: 0) > 0), onClick = {
                                testnetSubmitting = true
                                runAction {
                                    try {
                                        val result =
                                            api.placeBinanceSpotTestnetOrder(
                                                BinanceTestnetOrderRequestDto(
                                                    symbol = testnetSymbol.trim(),
                                                    side = testnetSide,
                                                    type = testnetType,
                                                    quantity = testnetQuantity.trim(),
                                                    price = testnetPrice.takeIf { testnetType == "LIMIT" }?.trim(),
                                                    timeInForce = if (testnetType == "LIMIT") testnetTif else "GTC",
                                                    clientOrderId = "ag-${java.util.UUID.randomUUID().toString().replace(
                                                        "-",
                                                        "",
                                                    )}",
                                                ),
                                            )
                                        onTestnetOrderChanged(result)
                                        onFlash(
                                            if (language == "en") "Testnet ${result.status} · ${if (result.ledgerSynced) "fill posted to ARGENT ledger" else "ARGENT ledger review required"} · #${result.orderId}" else "Testnet ${result.status} · ${if (result.ledgerSynced) "fill ARGENT defterine işlendi" else "ARGENT defterinde inceleme gerekiyor"} · #${result.orderId}",
                                        )
                                    } finally {
                                        testnetSubmitting = false
                                    }
                                }
                            }) { Text(if (testnetSubmitting) "GÖNDERİLİYOR…" else "TESTNET EMRİ GÖNDER") }
                            testnetOrder?.let { order ->
                                TextButton(onClick = {
                                    runAction {
                                        onTestnetOrderChanged(api.binanceSpotTestnetOrder(order.symbol, order.orderId))
                                    }
                                }) { Text("Durumu sorgula", color = ArgentBlue) }
                                if (order.status in setOf("NEW", "PARTIALLY_FILLED")) {
                                    TextButton(onClick = {
                                        runAction {
                                            onTestnetOrderChanged(
                                                api.cancelBinanceSpotTestnetOrder(order.symbol, order.orderId),
                                            )
                                        }
                                    }) { Text("Test emrini iptal et", color = ArgentRed) }
                                }
                            }
                        }
                        testnetOrder?.let { order ->
                            Text(
                                "${order.symbol} ${order.side} · ${order.status} · ${order.executedQty}/${order.origQty} · ${if (order.ledgerSynced) "fill ARGENT defterine işlendi" else "ARGENT defterinde inceleme gerekiyor"} · testnet #${order.orderId}",
                                color = if (order.ledgerSynced) ArgentSilver else ArgentRed,
                                fontSize = 11.sp,
                            )
                        }
                        error?.let { Text("Testnet sonucu: $it", color = ArgentRed, fontSize = 11.sp) }
                    }
                    Text(
                        "BIST: fiyat akışı simüledir. Yalnızca kullanım hakkınız olan CSV verisini yönetici replay API'siyle içe aktarın; gerçek zamanlı BIST verisi veya BIST emri değildir.",
                        color = ArgentMuted,
                        fontSize = 10.sp,
                    )
                    OutlinedTextField(
                        value = bistCsv,
                        onValueChange = { if (it.length <= 2 * 1024 * 1024) bistCsv = it },
                        label = { Text("BIST tarihsel CSV (timestamp,symbol,price[,volume24h])") },
                        placeholder = { Text("2026-01-02T10:00:00Z,THYAO,300.25,1000") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = fieldColors(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = bistReplaySpeed,
                            onValueChange = { if (it.all(Char::isDigit) && it.length <= 6) bistReplaySpeed = it },
                            label = { Text("Hız çarpanı") },
                            modifier = Modifier.width(150.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(),
                        )
                        Button(enabled = bistCsv.isNotBlank(), onClick = {
                            runAction {
                                bistReplay = api.startBistHistoricalReplay(bistCsv, bistReplaySpeed.toIntOrNull() ?: 1000)
                                bistCsv = ""
                            }
                        }) { Text("BIST CSV replay başlat") }
                        TextButton(onClick = {
                            runAction { bistReplay = api.bistHistoricalReplayStatus() }
                        }) { Text("Durumu yenile", color = ArgentBlue) }
                    }
                    bistReplay?.let { replay ->
                        Text(
                            "Replay ${replay.state} · ${replay.processed}/${replay.rows} satır${replay.error?.let { " · $it" } ?: ""}",
                            color = if (replay.state == "FAILED") ArgentRed else ArgentMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                WorkspaceMembershipSection(
                    api = api,
                    me = me,
                    tenants = tenants,
                    members = members,
                    canManage = canManage,
                    inviteEmail = inviteEmail,
                    onInviteEmailChange = { inviteEmail = it },
                    inviteRole = inviteRole,
                    onInviteRoleChange = { inviteRole = it },
                    inviteToken = inviteToken,
                    acceptToken = acceptToken,
                    onAcceptTokenChange = { acceptToken = it },
                    acceptingInvitation = acceptingInvitation,
                    runAction = ::runAction,
                    onMeChanged = onMeChanged,
                    onFlash = onFlash,
                    onMembersChanged = { members = it },
                    onTenantsChanged = { tenants = it },
                    onInviteCreated = { inviteToken = it },
                    onAcceptingChanged = { acceptingInvitation = it },
                )
                error?.let { Text(it, color = ArgentRed, fontSize = 12.sp) }
                if (me.tenantRole == "OWNER") {
                    TextButton(onClick = { closing = true }) {
                        Text("Bu çalışma alanını kapat", color = ArgentRed)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } },
    )
    WorkspaceCloseConfirmationDialog(
        visible = closing,
        onDismiss = { closing = false },
        onConfirm = {
            closing = false
            runAction {
                api.closeWorkspace()
                onWorkspaceClosed()
            }
        },
    )
}
