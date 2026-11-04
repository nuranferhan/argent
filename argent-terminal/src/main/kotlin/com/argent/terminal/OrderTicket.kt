package com.argent.terminal

import com.argent.client.core.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.InstrumentDto
import com.argent.client.core.TickerDto
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentGreen
import com.argent.terminal.ui.ArgentMuted
import com.argent.terminal.ui.ArgentPanel
import com.argent.terminal.ui.ArgentRed
import com.argent.terminal.ui.ArgentSilver
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun Ticket(
    symbol: String,
    instrument: InstrumentDto?,
    ticker: TickerDto?,
    side: String,
    type: String,
    price: String,
    qty: String,
    submitting: Boolean,
    onSide: (String) -> Unit,
    onType: (String) -> Unit,
    onPrice: (String) -> Unit,
    onQty: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val quantityValue = qty.toBigDecimalOrNull()
    val priceValue = price.toBigDecimalOrNull()
    val lotSize = instrument?.lotSize?.toBigDecimalOrNull()
    val tickSize = instrument?.tickSize?.toBigDecimalOrNull()
    val validationMessage =
        when {
            instrument == null -> "Enstrüman bilgisi yükleniyor."
            quantityValue == null || quantityValue.signum() <= 0 -> "Miktar pozitif bir sayı olmalı."
            lotSize != null && quantityValue.remainder(lotSize).signum() != 0 -> "Miktar lot adımına uygun değil (${instrument.lotSize})."
            type == "MARKET" && ticker?.price == null -> "Piyasa fiyatı henüz alınmadı."
            type != "MARKET" && (priceValue == null || priceValue.signum() <= 0) -> "Fiyat pozitif bir sayı olmalı."
            type != "MARKET" && tickSize != null && priceValue != null && priceValue.remainder(tickSize).signum() != 0 -> "Fiyat tick adımına uygun değil (${instrument.tickSize})."
            else -> null
        }
    val notional =
        runCatching {
            val px = if (type == "MARKET") ticker?.price ?: "0" else price.ifBlank { "0" }
            BigDecimal(px).multiply(BigDecimal(qty.ifBlank { "0" })).setScale(2, RoundingMode.HALF_UP).toPlainString()
        }.getOrElse { "—" }
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(ArgentPanel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("EMİR  $symbol", color = ArgentSilver, fontWeight = FontWeight.Bold)
        instrument?.let {
            Text("${it.displayName}  tick ${it.tickSize}  lot ${it.lotSize}", color = ArgentMuted, fontSize = 11.sp)
        }
        Row {
            Chip("AL", side == "BUY", ArgentGreen) { onSide("BUY") }
            Spacer(Modifier.width(8.dp))
            Chip("SAT", side == "SELL", ArgentRed) { onSide("SELL") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("LIMIT", "MARKET", "IOC", "FOK").forEach { t ->
                Chip(t, type == t, ArgentGold) { onType(t) }
            }
        }
        if (type != "MARKET") field("Fiyat", price, KeyboardType.Decimal, onPrice)
        field("Miktar", qty, KeyboardType.Decimal, onQty)
        Text("Tahmini notional  $notional", color = ArgentMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        validationMessage?.let { Text(it, color = ArgentRed, fontSize = 11.sp) }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onSubmit,
            enabled = !submitting && validationMessage == null,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (side == "BUY") ArgentGreen else ArgentRed,
                    contentColor = Color.White,
                ),
        ) {
            Text(
                if (submitting) {
                    "GÖNDERİLİYOR…"
                } else if (side == "BUY") {
                    "ALIŞ EMRİ"
                } else {
                    "SATIŞ EMRİ"
                },
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Limit deftere yazılır. Market/IOC kalanı iptal eder. FOK tamamen dolmazsa işlem açılmaz. Fiyata tıklayınca bilet dolar.",
            color = ArgentMuted,
            fontSize = 11.sp,
        )
    }
}
