package com.argent.risk

import com.argent.common.domain.Instruments
import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import com.argent.common.domain.PortfolioSnapshot
import com.argent.common.domain.PortfolioValuation
import com.argent.ledger.LedgerService
import java.math.BigDecimal
import java.math.RoundingMode

class RiskService(
    private val ledger: LedgerService,
    private val marketBuyBuffer: BigDecimal = BigDecimal("1.05")
) {

    data class RiskCheckResult(
        val allowed: Boolean,
        val reason: String? = null,
        val requiredAsset: String? = null,
        val requiredAmount: BigDecimal? = null
    )

    fun checkOrder(order: Order, lastPrice: BigDecimal?): RiskCheckResult {
        val instrument = try {
            Instruments.require(order.symbol)
        } catch (e: IllegalArgumentException) {
            return RiskCheckResult(false, e.message)
        }
        if (!validDecimal(order.quantity)) {
            return RiskCheckResult(false, "Quantity must fit NUMERIC(36,18): at most 18 integer digits and 18 decimal places")
        }
        if (order.quantityBd() <= BigDecimal.ZERO) {
            return RiskCheckResult(false, "Quantity must be positive")
        }
        if (order.type == OrderType.MARKET && order.price != null) {
            return RiskCheckResult(false, "Market orders must not include a client-supplied price")
        }
        if (!aligned(order.quantityBd(), BigDecimal(instrument.lotSize))) {
            return RiskCheckResult(false, "Quantity must be a multiple of lot ${instrument.lotSize}")
        }
        if (order.type != OrderType.MARKET && (order.price == null || order.priceBd() <= BigDecimal.ZERO)) {
            return RiskCheckResult(false, "Price required for ${order.type}")
        }
        if (order.price?.let { !validDecimal(it) } == true) {
            return RiskCheckResult(false, "Price must fit NUMERIC(36,18): at most 18 integer digits and 18 decimal places")
        }
        if (order.type != OrderType.MARKET && !aligned(order.priceBd(), BigDecimal(instrument.tickSize))) {
            return RiskCheckResult(false, "Price must be a multiple of tick ${instrument.tickSize}")
        }

        val (asset, amount) = when (order.side) {
            OrderSide.BUY -> {
                val px = when (order.type) {
                    OrderType.MARKET -> lastPrice ?: return RiskCheckResult(false, "No mark price for market buy")
                    else -> order.priceBd()
                }
                val notional = px.multiply(order.quantityBd())
                val padded = if (order.type == OrderType.MARKET) notional.multiply(marketBuyBuffer) else notional
                instrument.quoteAsset to padded
            }
            OrderSide.SELL -> instrument.baseAsset to order.quantityBd()
        }

        val balance = ledger.getBalance(order.userId, asset)
        if (balance.availableBd() < amount) {
            return RiskCheckResult(
                allowed = false,
                reason = "Insufficient $asset: available=${balance.available} required=$amount",
                requiredAsset = asset,
                requiredAmount = amount
            )
        }
        return RiskCheckResult(true, requiredAsset = asset, requiredAmount = amount)
    }

    fun portfolio(userId: String, prices: Map<String, BigDecimal>): PortfolioSnapshot {
        val balances = ledger.listBalances(userId)
        val positions = ledger.listPositions(userId)
        val balanceByAsset = balances.associate { it.asset to it.totalBd() }
        val positionsBySymbol = positions.associateBy { it.symbol }
        val quoteAssets = (Instruments.all.map { it.quoteAsset } + positions.mapNotNull { Instruments.get(it.symbol)?.quoteAsset })
            .filter { quote ->
                (balanceByAsset[quote]?.signum() ?: 0) != 0 || positions.any { Instruments.get(it.symbol)?.quoteAsset == quote } ||
                    Instruments.all.any { it.quoteAsset == quote && (balanceByAsset[it.baseAsset]?.signum() ?: 0) != 0 }
            }
            .distinct()
            .sorted()
        val valuations = quoteAssets.map { quoteAsset ->
            var equity = balanceByAsset[quoteAsset] ?: BigDecimal.ZERO
            var unrealized = BigDecimal.ZERO
            var realized = BigDecimal.ZERO
            var exposure = BigDecimal.ZERO
            var priceComplete = true
            var pnlComplete = true

            Instruments.all.filter { it.quoteAsset == quoteAsset }.forEach { instrument ->
                val balanceQty = balanceByAsset[instrument.baseAsset] ?: BigDecimal.ZERO
                val position = positionsBySymbol[instrument.symbol]
                val liveMark = prices[instrument.symbol]
                val mark = liveMark
                    ?: position?.avgEntryBd()?.takeIf { it.signum() > 0 }
                if (balanceQty.compareTo(position?.quantityBd() ?: BigDecimal.ZERO) != 0) {
                    pnlComplete = false
                }
                if (liveMark == null && (balanceQty.signum() != 0 || (position?.quantityBd()?.signum() ?: 0) != 0)) {
                    priceComplete = false
                }
                if (balanceQty.signum() != 0) {
                    if (mark == null) priceComplete = false else equity += balanceQty.multiply(mark)
                }
                if (position != null) {
                    realized += position.realizedPnlBd()
                    if (position.quantityBd().signum() != 0) {
                        if (mark == null) {
                            priceComplete = false
                        } else {
                            unrealized += position.unrealizedPnl(mark)
                            exposure += position.quantityBd().abs().multiply(mark)
                        }
                    }
                }
            }

            PortfolioValuation(
                quoteAsset = quoteAsset,
                equity = equity.stripTrailingZeros().toPlainString(),
                realizedPnl = realized.stripTrailingZeros().toPlainString(),
                unrealizedPnl = unrealized.stripTrailingZeros().toPlainString(),
                grossExposure = exposure.stripTrailingZeros().toPlainString(),
                marginUsed = "0",
                marginRatio = "KAPALI",
                priceComplete = priceComplete,
                pnlComplete = pnlComplete
            )
        }
        val singleCurrency = valuations.singleOrNull()
        val score = riskScore(positions, prices)
        return PortfolioSnapshot(
            userId = userId,
            balances = balances,
            positions = positions,
            equity = singleCurrency?.equity ?: "",
            realizedPnl = singleCurrency?.realizedPnl ?: "",
            unrealizedPnl = singleCurrency?.unrealizedPnl ?: "",
            marginUsed = singleCurrency?.marginUsed ?: "",
            marginRatio = singleCurrency?.marginRatio ?: "",
            riskScore = score,
            marginCall = false,
            valuations = valuations
        )
    }

    fun riskScore(positions: List<com.argent.common.domain.Position>, prices: Map<String, BigDecimal>): Int {
        if (positions.isEmpty()) return 0
        val byQuote = positions.mapNotNull { pos ->
            val instrument = Instruments.get(pos.symbol) ?: return@mapNotNull null
            val price = prices[pos.symbol] ?: pos.avgEntryBd().takeIf { it.signum() > 0 } ?: return@mapNotNull null
            instrument.quoteAsset to pos.quantityBd().abs().multiply(price)
        }.groupBy({ it.first }, { it.second })
        val maxWeight = byQuote.values.mapNotNull { notionals ->
            val total = notionals.fold(BigDecimal.ZERO) { acc, amount -> acc + amount }
            if (total.signum() == 0) null else notionals.maxOf { it.divide(total, 6, RoundingMode.HALF_UP) }
        }.maxOrNull() ?: return 0
        return maxWeight.multiply(BigDecimal(100)).toInt().coerceIn(0, 100)
    }

    fun baseAsset(symbol: String): String = Instruments.baseAsset(symbol)
    fun quoteAsset(symbol: String): String = Instruments.quoteAsset(symbol)

    fun marketBuyLimit(markPrice: BigDecimal): BigDecimal = markPrice.multiply(marketBuyBuffer)

    private fun aligned(value: BigDecimal, step: BigDecimal): Boolean {
        if (step.signum() <= 0) return true
        return value.remainder(step).compareTo(BigDecimal.ZERO) == 0
    }

    private fun validDecimal(raw: String): Boolean = runCatching {
        val value = BigDecimal(raw)
        val integerDigits = value.precision() - value.scale()
        value.precision() <= 36 && value.scale().coerceAtLeast(0) <= 18 && integerDigits <= 18
    }.getOrDefault(false)
}
