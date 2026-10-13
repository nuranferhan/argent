package com.argent.common.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Balance(
    val userId: String,
    val asset: String,
    val available: String = "0",
    val held: String = "0",
    val version: Long = 0
) {
    fun availableBd(): BigDecimal = BigDecimal(available)
    fun heldBd(): BigDecimal = BigDecimal(held)
    fun totalBd(): BigDecimal = availableBd() + heldBd()
}

@Serializable
data class Position(
    val userId: String,
    val symbol: String,
    val quantity: String = "0",
    val avgEntryPrice: String = "0",
    val realizedPnl: String = "0"
) {
    fun quantityBd(): BigDecimal = BigDecimal(quantity)
    fun avgEntryBd(): BigDecimal = BigDecimal(avgEntryPrice)
    fun realizedPnlBd(): BigDecimal = BigDecimal(realizedPnl)

    fun unrealizedPnl(mark: BigDecimal): BigDecimal {
        if (quantityBd().signum() == 0) return BigDecimal.ZERO
        return (mark - avgEntryBd()).multiply(quantityBd())
    }
}

@Serializable
data class PortfolioSnapshot(
    val userId: String,
    val balances: List<Balance>,
    val positions: List<Position>,
    val equity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val marginUsed: String,
    val marginRatio: String,
    val riskScore: Int,
    val marginCall: Boolean,
    val valuations: List<PortfolioValuation> = emptyList()
)

@Serializable
data class PortfolioValuation(
    val quoteAsset: String,
    val equity: String,
    val realizedPnl: String,
    val unrealizedPnl: String,
    val grossExposure: String,
    val marginUsed: String,
    val marginRatio: String,
    val priceComplete: Boolean,
    val pnlComplete: Boolean
)
