package com.argent.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.argent.client.core.ArgentApi
import com.argent.client.core.DepthLevelDto
import com.argent.client.core.InstrumentDto
import com.argent.client.core.OrderBookDto
import com.argent.client.core.TickerDto

@Preview(showBackground = true)
@Composable
private fun TradePagePreview() {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Gold,
                background = Ink,
                surface = Panel,
                onBackground = Color(0xFFE5E8ED),
                onSurface = Color(0xFFE5E8ED),
                error = Red,
            ),
    ) {
        val instruments =
            listOf(
                InstrumentDto("THYAO", "THYAO", "TRY", "EQUITY", "Türk Hava Yolları", "0.05", "1"),
                InstrumentDto("GARAN", "GARAN", "TRY", "EQUITY", "Garanti BBVA", "0.02", "1"),
                InstrumentDto("BTCUSDT", "BTC", "USDT", "CRYPTO", "Bitcoin / USDT", "0.01", "0.001"),
            )
        val ticker = TickerDto("THYAO", "305.50", "305.25", "305.50", "1500000", "SANDBOX")
        val book =
            OrderBookDto(
                "THYAO",
                listOf(DepthLevelDto("305.25", "1000", 2), DepthLevelDto("305.00", "2500", 5)),
                listOf(DepthLevelDto("305.50", "800", 1), DepthLevelDto("305.75", "3000", 4)),
                "305.25",
                "305.50",
            )
        TradePage(
            api = remember { ArgentApi() },
            instruments = instruments,
            symbol = "THYAO",
            onSymbol = {},
            ticker = ticker,
            book = book,
            marketTrades = emptyList(),
            analysis = null,
            prices = listOf(300f, 301f, 302.5f, 302f, 304f, 303.5f, 305f, 305.5f),
            busy = false,
            onMessage = {},
            onBusy = {},
            onRefresh = {},
            marketRefreshSeconds = 60,
            onDraftChange = { _, _, _, _ -> },
        )
    }
}
