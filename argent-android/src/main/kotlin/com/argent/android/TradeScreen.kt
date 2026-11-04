package com.argent.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.argent.client.core.InstrumentDto
import com.argent.client.core.OrderBookDto
import com.argent.client.core.TechnicalAnalysisDto
import com.argent.client.core.TickerDto
import com.argent.client.core.TradeDto
import com.argent.client.core.UiStrings
import com.argent.client.core.UiTextKey
import com.argent.client.core.placeOrder
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun TradePage(
    api: ArgentApi,
    instruments: List<InstrumentDto>,
    symbol: String,
    onSymbol: (String) -> Unit,
    ticker: TickerDto?,
    book: OrderBookDto,
    marketTrades: List<TradeDto>,
    analysis: TechnicalAnalysisDto?,
    prices: List<Float>,
    busy: Boolean,
    isGuest: Boolean = false,
    guestAccessSection: String? = null,
    onSignIn: () -> Unit = {},
    onMessage: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
    onRefresh: suspend () -> Unit,
    marketRefreshSeconds: Int,
    onDraftChange: (String, String, String, String) -> Unit,
) {
    val language = LocalUiLanguage.current

    fun text(
        key: UiTextKey,
        vararg args: Any,
    ): String = UiStrings.formatted(key, language, *args)
    var chartExpanded by remember(symbol) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var side by remember { mutableStateOf("BUY") }
    var type by remember { mutableStateOf("LIMIT") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    LaunchedEffect(symbol, side, type, quantity, price) { onDraftChange(side, type, quantity, price) }
    val instrument = instruments.firstOrNull { it.symbol == symbol }
    val sourceBadge =
        if (
            instrument?.assetClass == "CRYPTO" &&
            ticker?.source?.equals("binance", ignoreCase = true) == true
        ) {
            "BINANCE"
        } else {
            null
        }
    val quantityValue = quantity.trim().replace(',', '.').toBigDecimalOrNull()
    val priceValue = price.trim().replace(',', '.').toBigDecimalOrNull()
    val lotSize = instrument?.lotSize?.toBigDecimalOrNull()
    val tickSize = instrument?.tickSize?.toBigDecimalOrNull()
    val validation =
        when {
            instrument == null -> text(UiTextKey.INSTRUMENT_DETAILS_LOADING)
            quantityValue == null || quantityValue.signum() <= 0 -> text(UiTextKey.QUANTITY_POSITIVE)
            lotSize != null && quantityValue.remainder(lotSize).signum() != 0 ->
                text(UiTextKey.QUANTITY_LOT_STEP, instrument.lotSize)
            type == "MARKET" && ticker?.price?.toBigDecimalOrNull() == null ->
                text(UiTextKey.MARKET_PRICE_WAITING)
            type != "MARKET" && (priceValue == null || priceValue.signum() <= 0) ->
                text(UiTextKey.PRICE_POSITIVE)
            type != "MARKET" &&
                tickSize != null &&
                priceValue != null &&
                priceValue.remainder(tickSize).signum() != 0 ->
                text(UiTextKey.PRICE_TICK_STEP, instrument.tickSize)
            else -> null
        }
    val notional =
        runCatching {
            val px = if (type == "MARKET") ticker?.price ?: "0" else price.ifBlank { "0" }
            java.math.BigDecimal(px).multiply(java.math.BigDecimal(quantity.ifBlank { "0" }))
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
        }.getOrElse { "—" }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                instruments.forEach {
                        instrument ->
                    Choice(instrument.symbol, symbol == instrument.symbol) {
                        onSymbol(instrument.symbol)
                        price = ""
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Text(ticker?.price ?: "—", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        sourceBadge?.let { Text(it, color = Muted, fontSize = 9.sp) }
                    }
                    instrument?.let {
                        Text(
                            if (language == "en") {
                                "${it.displayName} · Tick size ${it.tickSize} · Lot size ${it.lotSize}"
                            } else {
                                "${it.displayName} · tick ${it.tickSize} · lot ${it.lotSize}"
                            },
                            color = Muted,
                            fontSize = 10.sp,
                        )
                    }
                    Text(
                        "Bid ${ticker?.bid ?: book.bestBid ?: "—"}   Ask ${ticker?.ask ?: book.bestAsk ?: "—"}",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { chartExpanded = !chartExpanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            if (language == "en") {
                                if (chartExpanded) "Hide detailed chart" else "Expand chart"
                            } else {
                                if (chartExpanded) "Grafiği daralt" else "Grafiği genişlet"
                            },
                            color = Gold,
                            fontSize = 10.sp,
                        )
                    }
                    PriceChart(
                        prices,
                        Modifier.fillMaxWidth().height(if (chartExpanded) 220.dp else 54.dp).clickable {
                            chartExpanded = !chartExpanded
                        },
                        currentPrice = ticker?.price?.toFloatOrNull(),
                    )
                }
            }
        }
        item { TechnicalAnalysisCard(analysis) }
        item { OrderBookCard(book, readOnly = isGuest) { price = it } }
        item { PublicMarketTradesCard(marketTrades, instrument?.quoteAsset.orEmpty()) }
        guestAccessSection?.let { section ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text(UiTextKey.SECTION_SIGN_IN_REQUIRED, section),
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text(UiTextKey.GUEST_MARKET_ACCESS),
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Button(onClick = onSignIn) { Text(text(UiTextKey.SIGN_IN)) }
                    }
                }
            }
        }
        item {
            ExternalMarketSection(api, onMessage, marketRefreshSeconds, isGuest, onRequireLogin = {
                onMessage(
                    text(UiTextKey.ALERTS_SIGN_IN_REQUIRED),
                )
            })
        }
        if (isGuest && guestAccessSection == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text(UiTextKey.PAPER_TRADING),
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                        Text(
                            text(UiTextKey.GUEST_TRADE_ACCESS),
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        Button(onClick = onSignIn) {
                            Text(text(UiTextKey.SIGN_IN_TO_TRADE))
                        }
                    }
                }
            }
        } else if (!isGuest) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (language == "en") "ORDER" else "EMİR", fontWeight = FontWeight.Bold, color = Gold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(if (language == "en") "BUY" else "AL", side == "BUY") { side = "BUY" }
                            Choice(if (language == "en") "SELL" else "SAT", side == "SELL") { side = "SELL" }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { Choice(it, type == it) { type = it } }
                        }
                        if (type != "MARKET") {
                            Field(
                                if (language == "en") "Price" else "Fiyat",
                                price,
                                { price = it },
                                KeyboardType.Decimal,
                            )
                        }
                        Field(
                            if (language == "en") "Quantity" else "Miktar",
                            quantity,
                            { quantity = it },
                            KeyboardType.Decimal,
                        )
                        Text(
                            if (language == "en") "Estimated notional  $notional" else "Tahmini tutar  $notional",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                        validation?.let { Text(it, color = Red, fontSize = 11.sp) }
                        Button(
                            enabled = !busy && (validation == null || isGuest),
                            onClick = {
                                if (isGuest) {
                                    onMessage(
                                        text(UiTextKey.SIGN_IN_TO_TRADE),
                                    )
                                } else {
                                    onBusy(true)
                                    scope.launch {
                                        try {
                                            val normalizedQuantity = quantity.trim().replace(',', '.')
                                            val normalizedPrice = price.trim().replace(',', '.').ifBlank { null }
                                            require(normalizedQuantity.toBigDecimal() > java.math.BigDecimal.ZERO) {
                                                text(UiTextKey.QUANTITY_POSITIVE)
                                            }
                                            val activeInstrument =
                                                requireNotNull(instrument) {
                                                    text(UiTextKey.INSTRUMENT_DETAILS_LOADING)
                                                }
                                            lotSize?.let {
                                                require(normalizedQuantity.toBigDecimal().remainder(it).signum() == 0) {
                                                    text(UiTextKey.QUANTITY_LOT_STEP, activeInstrument.lotSize)
                                                }
                                            }
                                            if (type == "MARKET") {
                                                require(ticker?.price?.toBigDecimalOrNull() != null) {
                                                    text(UiTextKey.MARKET_PRICE_WAITING)
                                                }
                                            }
                                            if (type != "MARKET") {
                                                require(
                                                    !normalizedPrice.isNullOrBlank() &&
                                                        normalizedPrice.toBigDecimal() > java.math.BigDecimal.ZERO,
                                                ) {
                                                    text(UiTextKey.PRICE_POSITIVE)
                                                }
                                            }
                                            if (type != "MARKET" && tickSize != null) {
                                                require(
                                                    normalizedPrice?.toBigDecimal()?.remainder(tickSize)?.signum() == 0,
                                                ) {
                                                    text(UiTextKey.PRICE_TICK_STEP, activeInstrument.tickSize)
                                                }
                                            }
                                            val result =
                                                api.placeOrder(
                                                    symbol,
                                                    side,
                                                    type,
                                                    normalizedQuantity,
                                                    normalizedPrice,
                                                    UUID.randomUUID().toString(),
                                                )
                                            onMessage(
                                                text(UiTextKey.ORDER_RESULT, result.status, result.filledQuantity),
                                            )
                                            onRefresh()
                                        } catch (
                                            e: Exception,
                                        ) {
                                            onMessage(
                                                text(UiTextKey.ORDER_FAILED),
                                            )
                                        } finally {
                                            onBusy(false)
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier.fillMaxWidth().height(
                                    48.dp,
                                ),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = if (side == "BUY") Green else Red,
                                    contentColor = Ink,
                                ),
                        ) {
                            Text(
                                if (isGuest) {
                                    text(UiTextKey.SIGN_IN_TO_TRADE)
                                } else if (language == "en") {
                                    if (busy) {
                                        "PROCESSING…"
                                    } else if (side == "BUY") {
                                        "SUBMIT BUY ORDER"
                                    } else {
                                        "SUBMIT SELL ORDER"
                                    }
                                } else {
                                    if (busy) {
                                        "İŞLENİYOR…"
                                    } else if (side == "BUY") {
                                        "ALIŞ EMRİ GÖNDER"
                                    } else {
                                        "SATIŞ EMRİ GÖNDER"
                                    }
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text(UiTextKey.INTERNAL_PAPER_ENGINE),
                            color = Muted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}
