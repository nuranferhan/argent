package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.client.core.MeResponse
import com.argent.client.core.OrderDto
import com.argent.client.core.PortfolioDto
import com.argent.client.core.TickerDto
import com.argent.client.core.TradeDto
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentPanelAlt

@Composable
internal fun BottomDock(
    tab: Int,
    onTab: (Int) -> Unit,
    height: androidx.compose.ui.unit.Dp,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    portfolio: PortfolioDto?,
    orders: List<OrderDto>,
    fills: List<TradeDto>,
    blotter: List<TradeDto>,
    tickers: Map<String, TickerDto>,
    api: ArgentApi,
    me: MeResponse,
    selectedSymbol: String,
    draftSide: String,
    draftType: String,
    draftQuantity: String,
    draftPrice: String,
    scope: kotlinx.coroutines.CoroutineScope,
    marketRefreshSeconds: Int,
    guestMode: Boolean = false,
    onRequireLogin: () -> Unit = {},
    onFlash: (String) -> Unit,
) {
    val currentHeight = rememberUpdatedState(height)
    val updateHeight = rememberUpdatedState(onHeightChange)
    Column(Modifier.fillMaxWidth().height(height).background(ArgentPanelAlt)) {
        Box(
            modifier =
                Modifier.fillMaxWidth().height(12.dp)
                    .pointerInput(Unit) {
                        var dragHeight = currentHeight.value
                        detectDragGestures(
                            onDragStart = { dragHeight = currentHeight.value },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragHeight = (dragHeight - dragAmount.y.toDp()).coerceIn(180.dp, 700.dp)
                                updateHeight.value(dragHeight)
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(42.dp).height(3.dp).background(ArgentMuted, RoundedCornerShape(50)))
        }
        TabRow(selectedTabIndex = tab, containerColor = ArgentPanel, contentColor = ArgentGold) {
            listOf("Portföy", "Açık emirler", "İşlemlerim", "Piyasa işleri", "ARGENT AI").forEachIndexed { i, t ->
                val title =
                    when (i) {
                        0 -> if (LocalUiLanguage.current == "en") "Portfolio" else "Portföy"
                        1 -> if (LocalUiLanguage.current == "en") "Open orders" else "Açık emirler"
                        2 -> if (LocalUiLanguage.current == "en") "My trades" else "İşlemlerim"
                        3 -> if (LocalUiLanguage.current == "en") "Market activity" else "Piyasa işleri"
                        else -> "ARGENT AI"
                    }
                Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(title, fontSize = 12.sp) })
            }
        }
        when (tab) {
            0 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else PortfolioTab(portfolio, tickers)
            1 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else OrdersTab(orders, api, scope, onFlash)
            2 -> if (guestMode) GuestAccessCard(onLogin = onRequireLogin) else TradesTab(fills, orders)
            3 -> ExternalMarketsTab(api, scope, onFlash, blotter, marketRefreshSeconds, guestMode, onRequireLogin)
            else ->
                if (guestMode) {
                    GuestAccessCard(
                        onLogin = onRequireLogin,
                    )
                } else {
                    AssistantTab(api, selectedSymbol, draftSide, draftType, draftQuantity, draftPrice, scope)
                }
        }
    }
}

@Composable
internal fun GuestAccessCard(onLogin: () -> Unit) {
    val english = LocalUiLanguage.current == "en"
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (english) "Sign in required" else "Giriş yapmanız gerekiyor",
            color = ArgentGold,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (english) "Sign in to access your portfolio, orders, trades, AI assistant and workspace." else "Portföyünüze, emirlerinize, işlemlerinize, ARGENT AI'a ve çalışma alanına erişmek için lütfen giriş yapın.",
            color = ArgentMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(
            onClick = onLogin,
        ) { Text(if (english) "Go to sign in" else "Giriş ekranına dön") }
    }
}
