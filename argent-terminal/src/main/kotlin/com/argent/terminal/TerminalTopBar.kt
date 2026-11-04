package com.argent.terminal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.BinanceTestnetOrderDto
import com.argent.client.core.MeResponse
import com.argent.client.core.TickerDto
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentGreenDim
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentRedDim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TopBar(
    me: MeResponse,
    tickers: Map<String, TickerDto>,
    health: String,
    onLogout: () -> Unit,
    api: ArgentApi,
    selectedSymbol: String,
    scope: CoroutineScope,
    guestMode: Boolean,
    onFlash: (String) -> Unit,
    onMeChanged: (MeResponse) -> Unit,
    onWorkspaceClosed: () -> Unit,
    onSettings: () -> Unit,
) {
    var workspaceOpen by remember { mutableStateOf(false) }
    var lastTestnetOrder by remember { mutableStateOf<BinanceTestnetOrderDto?>(null) }
    val loginRequiredMessage = guestLoginMessage()
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = 56.dp,
        ).background(ArgentPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource("argent-icon.png"),
            contentDescription = "ARGENT",
            modifier = Modifier.size(30.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Spacer(Modifier.width(20.dp))
        val marketTickerText =
            tickers.values.sortedBy { it.symbol }
                .joinToString("     •     ") { "${it.symbol} ${compact(it.price)}" }
        if (marketTickerText.isNotBlank()) {
            TickerMarquee(
                text = "$marketTickerText     •     ",
                modifier = Modifier.weight(1f).height(28.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        val ok = health.startsWith("INFRA OK")
        Box(
            Modifier.widthIn(min = 180.dp, max = 260.dp)
                .background(if (ok) ArgentGreenDim else ArgentRedDim, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "$health     •     $health",
                color = if (ok) ArgentGreen else ArgentRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (guestMode) guestLabel() else "${me.displayName} · ${me.role}",
            color = ArgentMuted,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onSettings) { Text("Ayarlar", color = ArgentGold) }
        TextButton(onClick = {
            if (guestMode) onFlash(loginRequiredMessage) else workspaceOpen = true
        }) { Text(if (LocalUiLanguage.current == "en") "Workspace" else "Çalışma alanı", color = ArgentGold) }
        TextButton(onClick = {
            if (guestMode) {
                onLogout()
                return@TextButton
            }
            scope.launch {
                api.logout()
                onLogout()
            }
        }) {
            Text(
                if (guestMode) {
                    if (LocalUiLanguage.current == "en") "Sign in" else "Giriş yap"
                } else if (LocalUiLanguage.current == "en") {
                    "Sign out"
                } else {
                    "Çıkış"
                },
                color = ArgentRed,
            )
        }
    }
    if (workspaceOpen) {
        WorkspaceDialog(
            api = api,
            me = me,
            selectedSymbol = selectedSymbol,
            scope = scope,
            onClose = { workspaceOpen = false },
            onFlash = onFlash,
            onMeChanged = onMeChanged,
            onWorkspaceClosed = onWorkspaceClosed,
            testnetOrder = lastTestnetOrder,
            onTestnetOrderChanged = { lastTestnetOrder = it },
        )
    }
}
