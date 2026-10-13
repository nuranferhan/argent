package com.argent.gateway.mm

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import com.argent.engine.MatchingEngine
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.trading.TradingService
import com.argent.ledger.LedgerService
import com.argent.market.MarketDataService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicBoolean

class LiquidityProvider(
    private val userId: String,
    private val trading: TradingService,
    private val engine: MatchingEngine,
    private val market: MarketDataService,
    private val orders: OrderRepository,
    private val ledger: LedgerService,
    private val scope: CoroutineScope,
    private val controlStore: MarketMakerControlStore,
    private val maxQuoteNotional: BigDecimal = System.getenv("MM_MAX_QUOTE_NOTIONAL")?.let(::BigDecimal) ?: BigDecimal("5000"),
    private val maxInventoryNotional: BigDecimal = System.getenv("MM_MAX_INVENTORY_NOTIONAL")?.let(::BigDecimal) ?: BigDecimal("10000000"),
    private val maxQuoteAgeMs: Long = 10_000
) {
    private val log = LoggerFactory.getLogger(LiquidityProvider::class.java)
    private val enabled = AtomicBoolean(false)
    private val controlLock = Mutex()
    @Volatile private var worker: Job? = null

    init {
        require(maxQuoteNotional.signum() > 0 && maxInventoryNotional >= maxQuoteNotional) {
            "Market-maker inventory notional cap must be at least its positive quote notional cap"
        }
    }

    val isEnabled: Boolean get() = enabled.get()
    val quoteNotionalLimit: String get() = maxQuoteNotional.stripTrailingZeros().toPlainString()
    val inventoryNotionalLimit: String get() = maxInventoryNotional.stripTrailingZeros().toPlainString()
    val quoteAgeLimitMs: Long get() = maxQuoteAgeMs

    @Synchronized
    fun start() {
        if (worker?.isActive == true) return
        enabled.set(controlStore.isEnabled())
        if (!enabled.get()) return
        launchWorker()
    }

    suspend fun resume() = controlLock.withLock {
        controlStore.setEnabled(true)
        enabled.set(true)
        if (worker?.isActive != true) launchWorker()
    }

    private fun launchWorker() {
        worker = scope.launch {
            delay(1_500)
            while (isActive) {
                if (enabled.get()) {
                    Instruments.all.forEach { instrument ->
                        runCatching { quote(instrument.symbol, instrument.assetClass, instrument.priceScale) }
                            .onFailure { log.debug("MM {}: {}", instrument.symbol, it.message) }
                    }
                }
                delay(2_500)
            }
        }
    }

    suspend fun killSwitch(): Int = controlLock.withLock {
        controlStore.setEnabled(false)
        enabled.set(false)
        worker?.cancelAndJoin()
        var cancelled = 0
        orders.findActiveByUser(userId).forEach { order ->
            if (runCatching { trading.cancel(userId, order.id, order.symbol) }.getOrDefault(false)) cancelled++
        }
        val leftovers = orders.findActiveByUser(userId)
        check(leftovers.isEmpty()) { "Market maker stopped but ${leftovers.size} resting orders could not be cancelled" }
        cancelled
    }

    private suspend fun quote(symbol: String, assetClass: AssetClass, priceScale: Int) {
        val ticker = market.lastTicker(symbol) ?: return
        val last = BigDecimal(ticker.price)
        val live = orders.findActiveByUser(userId).filter { it.symbol == symbol }
        if (System.currentTimeMillis() - ticker.timestamp > maxQuoteAgeMs) {
            live.forEach { trading.cancel(userId, it.id, symbol) }
            return
        }
        val drift = BigDecimal("0.004")
        live.forEach { order ->
            val px = order.priceBd()
            if (px.signum() <= 0) return@forEach
            val skew = (px - last).abs().divide(last, 8, RoundingMode.HALF_UP)
            if (skew > drift) {
                trading.cancel(userId, order.id, symbol)
            }
        }
        val remaining = orders.findActiveByUser(userId).filter { it.symbol == symbol }
        val instrument = Instruments.require(symbol)
        val strategyCap = if (assetClass == AssetClass.EQUITY) BigDecimal("25") else BigDecimal("0.15")
        val notionalCap = maxQuoteNotional.divide(last, instrument.qtyScale + 8, RoundingMode.DOWN)
        val lot = BigDecimal(instrument.lotSize)
        val baseQty = strategyCap.min(notionalCap)
            .divide(lot, 0, RoundingMode.DOWN).multiply(lot)
            .setScale(instrument.qtyScale, RoundingMode.DOWN)
        if (baseQty.signum() <= 0) return
        if (remaining.none { it.side.name == "BUY" }) {
            val currentBase = ledger.getBalance(userId, instrument.baseAsset).totalBd()
            val openBidQty = remaining.filter { it.side.name == "BUY" }
                .fold(BigDecimal.ZERO) { acc, order -> acc + BigDecimal(order.remainingQuantity) }
            val inventoryCap = maxInventoryNotional.divide(last, instrument.qtyScale + 8, RoundingMode.DOWN)
            val buyQty = baseQty.min((inventoryCap - currentBase - openBidQty).max(BigDecimal.ZERO))
                .divide(lot, 0, RoundingMode.DOWN).multiply(lot).setScale(instrument.qtyScale, RoundingMode.DOWN)
            if (buyQty.signum() > 0) {
            val bid = last.multiply(BigDecimal("0.9985")).setScale(priceScale, RoundingMode.HALF_UP)
            trading.placeOrder(
                userId,
                PlaceOrderRequest(symbol, "BUY", "LIMIT", bid.toPlainString(), buyQty.toPlainString(), "mm-bid-$symbol-${System.currentTimeMillis()}")
            )
            }
        }
        if (remaining.none { it.side.name == "SELL" }) {
            val availableBase = ledger.getBalance(userId, instrument.baseAsset).availableBd()
            val sellQty = baseQty.min(availableBase)
                .divide(lot, 0, RoundingMode.DOWN).multiply(lot).setScale(instrument.qtyScale, RoundingMode.DOWN)
            if (sellQty.signum() <= 0) return
            val ask = last.multiply(BigDecimal("1.0015")).setScale(priceScale, RoundingMode.HALF_UP)
            trading.placeOrder(
                userId,
                PlaceOrderRequest(symbol, "SELL", "LIMIT", ask.toPlainString(), sellQty.toPlainString(), "mm-ask-$symbol-${System.currentTimeMillis()}")
            )
        }
    }
}
