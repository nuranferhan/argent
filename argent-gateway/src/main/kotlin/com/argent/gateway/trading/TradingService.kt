package com.argent.gateway.trading

import com.argent.common.domain.Instruments
import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import com.argent.common.domain.Trade
import com.argent.engine.MatchingEngine
import com.argent.gateway.api.OrderResultResponse
import com.argent.gateway.api.PlaceOrderRequest
import com.argent.gateway.api.TradeInfo
import com.argent.gateway.order.OrderRepository
import com.argent.ledger.LedgerService
import com.argent.market.MarketDataService
import com.argent.risk.RiskService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

class TradingService(
    private val engine: MatchingEngine,
    private val ledger: LedgerService,
    private val risk: RiskService,
    private val orders: OrderRepository,
    private val market: MarketDataService,
    private val meters: MeterRegistry = SimpleMeterRegistry()
) {
    private val accountLocks = ConcurrentHashMap<String, Mutex>()
    private val log = LoggerFactory.getLogger(TradingService::class.java)
    private val submitted = meters.counter("argent_orders_submitted_total")
    private val rejected = meters.counter("argent_orders_rejected_total")
    private val trades = meters.counter("argent_trades_executed_total")

    suspend fun <T> withAccountLock(accountId: String, action: suspend () -> T): T =
        accountLocks.computeIfAbsent(accountId) { Mutex() }.withLock { action() }

    suspend fun placeOrder(userId: String, req: PlaceOrderRequest): OrderResultResponse {
        val symbol = req.symbol.uppercase()
        Instruments.require(symbol)
        if (!req.clientOrderId.isNullOrBlank()) {
            val existing = orders.findByClientOrderId(userId, req.clientOrderId)
            if (existing != null) {
                val sameRequest = existing.symbol == symbol &&
                    existing.side.name == req.side.uppercase() &&
                    existing.type.name == req.type.uppercase() &&
                    ((existing.price == null && req.price == null) || runCatching {
                        BigDecimal(existing.price!!).compareTo(BigDecimal(req.price!!)) == 0
                    }.getOrDefault(false)) &&
                    runCatching { existing.quantityBd().compareTo(BigDecimal(req.quantity)) == 0 }.getOrDefault(false)
                require(sameRequest) { "clientOrderId is already used for a different order" }
                return OrderResultResponse(
                    orderId = existing.id,
                    status = existing.status.name,
                    filledQuantity = existing.filledQuantity,
                    remainingQuantity = existing.remainingQuantity,
                    accepted = existing.status in setOf(OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED) ||
                        (existing.status == OrderStatus.CANCELLED && existing.filledBd().signum() > 0),
                    reason = if (existing.status == OrderStatus.PENDING) {
                        "Order outcome is pending recovery; funds remain reserved"
                    } else "idempotent replay",
                    trades = emptyList()
                )
            }
        }

        val order = Order(
            clientOrderId = req.clientOrderId,
            userId = userId,
            symbol = symbol,
            side = OrderSide.valueOf(req.side.uppercase()),
            type = OrderType.valueOf(req.type.uppercase()),
            price = req.price,
            quantity = req.quantity
        )

        val markPrice = market.lastPrice(symbol)
        val check = risk.checkOrder(order, markPrice)
        if (!check.allowed) {
            orders.save(order.copy(status = OrderStatus.REJECTED))
            rejected.increment()
            return OrderResultResponse(
                orderId = order.id,
                status = OrderStatus.REJECTED.name,
                filledQuantity = "0",
                remainingQuantity = order.quantity,
                accepted = false,
                reason = check.reason ?: "Risk check failed",
                trades = emptyList()
            )
        }
        val asset = check.requiredAsset!!
        val amount = check.requiredAmount!!
        val held = try {
            ledger.hold(userId, asset, amount, order.id)
        } catch (failure: Exception) {
            runCatching { ledger.releaseRemainingHold(order.id) }
                .onFailure(failure::addSuppressed)
            throw failure
        }
        if (!held) {
            orders.save(order.copy(status = OrderStatus.REJECTED))
            rejected.increment()
            return OrderResultResponse(
                orderId = order.id,
                status = OrderStatus.REJECTED.name,
                filledQuantity = "0",
                remainingQuantity = order.quantity,
                accepted = false,
                reason = "Insufficient available balance",
                trades = emptyList()
            )
        }

        try {
            orders.save(order.copy(status = OrderStatus.PENDING))
        } catch (failure: Exception) {
            runCatching { ledger.releaseRemainingHold(order.id) }
                .onFailure(failure::addSuppressed)
            throw failure
        }
        submitted.increment()

        val result = try {
            val matchingOrder = if (order.type == OrderType.MARKET && order.side == com.argent.common.domain.OrderSide.BUY) {
                order.copy(price = risk.marketBuyLimit(markPrice!!).toPlainString())
            } else order
            engine.submit(matchingOrder)
        } catch (e: Exception) {
            log.error("Matching outcome is pending durable event replay orderId={}", order.id, e)
            throw IllegalStateException(
                "Order outcome is pending recovery. Its funds remain reserved; retry with the same clientOrderId after the gateway recovers.",
                e
            )
        }

        orders.save(result.order)
        result.trades.forEach { trade ->
            persistAndSettle(trade)
            trades.increment()
        }

        val keepHold = result.accepted &&
            (result.order.status == OrderStatus.ACCEPTED || result.order.status == OrderStatus.PARTIALLY_FILLED)
        if (!keepHold) {
            ledger.releaseRemainingHold(order.id)
        }
        if (!result.accepted) {
            rejected.increment()
        }

        return OrderResultResponse(
            orderId = result.order.id,
            status = result.order.status.name,
            filledQuantity = result.order.filledQuantity,
            remainingQuantity = result.order.remainingQuantity,
            accepted = result.accepted,
            reason = result.reason ?: "",
            trades = result.trades.map { it.toInfo() }
        )
    }

    suspend fun cancel(userId: String, orderId: String, symbol: String): Boolean {
        val existing = orders.findById(orderId) ?: return false
        if (existing.userId != userId) return false
        val cancelled = engine.cancel(symbol, orderId) ?: return false
        orders.save(cancelled.copy(status = OrderStatus.CANCELLED))
        ledger.releaseRemainingHold(orderId)
        return true
    }

    fun recoverUnsettled(events: List<com.argent.common.events.TradeExecuted>) {
        events.forEach { event ->
            log.info("Replaying settlement for trade {}", event.trade.id)
            persistAndSettle(event.trade)
        }
    }

    fun persistAndSettle(trade: Trade) {
        orders.saveTrade(trade)
        val instrument = Instruments.require(trade.symbol)
        ledger.settleTrade(
            tradeId = trade.id,
            symbol = trade.symbol,
            buyUserId = trade.buyUserId,
            sellUserId = trade.sellUserId,
            baseAsset = instrument.baseAsset,
            quoteAsset = instrument.quoteAsset,
            quantity = trade.quantityBd(),
            price = trade.priceBd(),
            buyOrderId = trade.buyOrderId,
            sellOrderId = trade.sellOrderId
        )
        applyMakerFill(trade)
    }

    private fun applyMakerFill(trade: Trade) {
        val maker = orders.findById(trade.makerOrderId) ?: return
        val filled = orders.filledFor(maker.id).min(maker.quantityBd())
        val remaining = (maker.quantityBd() - filled).max(BigDecimal.ZERO)
        val status = if (remaining.compareTo(BigDecimal.ZERO) == 0) OrderStatus.FILLED else OrderStatus.PARTIALLY_FILLED
        orders.save(
            maker.copy(
                filledQuantity = filled.stripTrailingZeros().toPlainString(),
                remainingQuantity = remaining.stripTrailingZeros().toPlainString(),
                status = status
            )
        )
        if (status == OrderStatus.FILLED) {
            ledger.releaseRemainingHold(maker.id)
        }
    }

    private fun Trade.toInfo() = TradeInfo(
        id = id,
        symbol = symbol,
        price = price,
        quantity = quantity,
        buyOrderId = buyOrderId,
        sellOrderId = sellOrderId,
        makerOrderId = makerOrderId,
        takerOrderId = takerOrderId,
        executedAt = executedAt
    )
}
