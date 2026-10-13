package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import com.argent.common.domain.Trade
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

class OrderBook(val symbol: String) {

    private val bids = ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>(compareByDescending { it })
    private val asks = ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>()
    private val sequence = AtomicLong(0)

    fun nextSequence(): Long = sequence.incrementAndGet()

    fun bestBid(): BigDecimal? = firstOccupied(bids)
    fun bestAsk(): BigDecimal? = firstOccupied(asks)

    fun midPrice(): BigDecimal? {
        val bid = bestBid() ?: return null
        val ask = bestAsk() ?: return null
        return (bid + ask).divide(BigDecimal(2))
    }

    fun match(order: Order): MatchResult {
        val limitPrice = aggressiveLimit(order)
        if (order.type != OrderType.MARKET && order.price == null) {
            return MatchResult(
                trades = emptyList(),
                order = order.copy(status = OrderStatus.REJECTED, updatedAt = now()),
                reason = "Limit price required"
            )
        }

        val oppositeBook = if (order.side == OrderSide.BUY) asks else bids
        val crossesOwnLiquidity = oppositeBook.entries
            .takeWhile { priceCrosses(order.side, limitPrice, it.key) }
            .any { (_, queue) -> queue.any { it.userId == order.userId && it.remainingBd().signum() > 0 } }
        if (crossesOwnLiquidity) {
            return MatchResult(
                trades = emptyList(),
                order = order.copy(
                    filledQuantity = "0",
                    remainingQuantity = order.quantity,
                    status = OrderStatus.CANCELLED,
                    updatedAt = now()
                ),
                reason = "Self-trade prevention: incoming order would cross your own resting order"
            )
        }

        if (order.type == OrderType.FOK) {
            val available = availableLiquidity(order.side, limitPrice, order.userId)
            if (available < order.quantityBd()) {
                return MatchResult(
                    trades = emptyList(),
                    order = order.copy(status = OrderStatus.CANCELLED, updatedAt = now()),
                    reason = "FOK cannot be fully filled"
                )
            }
        }

        val trades = mutableListOf<Trade>()
        var remaining = order.quantityBd()
        var filled = BigDecimal.ZERO
        val bookSide = if (order.side == OrderSide.BUY) asks else bids

        priceLoop@ for (entry in bookSide.entries.toList()) {
            if (remaining <= BigDecimal.ZERO) break
            if (!priceCrosses(order.side, limitPrice, entry.key)) break

            val queue = entry.value
            var i = 0
            while (i < queue.size && remaining > BigDecimal.ZERO) {
                val resting = queue[i]
                if (resting.userId == order.userId) {
                    i++
                    continue
                }
                val tradeQty = remaining.min(resting.remainingBd())
                if (tradeQty <= BigDecimal.ZERO) {
                    queue.removeAt(i)
                    continue
                }

                val trade = Trade(
                    id = deterministicTradeId(order.id, resting.id, filled),
                    symbol = symbol,
                    price = entry.key.toPlainString(),
                    quantity = tradeQty.toPlainString(),
                    buyOrderId = if (order.side == OrderSide.BUY) order.id else resting.id,
                    sellOrderId = if (order.side == OrderSide.SELL) order.id else resting.id,
                    buyUserId = if (order.side == OrderSide.BUY) order.userId else resting.userId,
                    sellUserId = if (order.side == OrderSide.SELL) order.userId else resting.userId,
                    makerOrderId = resting.id,
                    takerOrderId = order.id
                )
                trades.add(trade)

                val newRemaining = resting.remainingBd() - tradeQty
                if (newRemaining <= BigDecimal.ZERO) {
                    queue.removeAt(i)
                } else {
                    queue[i] = resting.copy(
                        remainingQuantity = newRemaining.toPlainString(),
                        filledQuantity = (resting.filledBd() + tradeQty).toPlainString(),
                        status = OrderStatus.PARTIALLY_FILLED,
                        updatedAt = now()
                    )
                    i++
                }
                remaining -= tradeQty
                filled += tradeQty
            }
            if (queue.isEmpty()) bookSide.remove(entry.key)
        }

        val rest = remaining > BigDecimal.ZERO && order.type == OrderType.LIMIT
        val status = when {
            filled.compareTo(BigDecimal.ZERO) == 0 && rest -> OrderStatus.ACCEPTED
            remaining.compareTo(BigDecimal.ZERO) == 0 -> OrderStatus.FILLED
            rest -> OrderStatus.PARTIALLY_FILLED
            filled.compareTo(BigDecimal.ZERO) == 0 -> OrderStatus.CANCELLED
            else -> OrderStatus.CANCELLED
        }

        val updated = order.copy(
            filledQuantity = filled.toPlainString(),
            remainingQuantity = remaining.max(BigDecimal.ZERO).toPlainString(),
            status = status,
            updatedAt = now(),
            sequence = if (order.sequence == 0L) nextSequence() else order.sequence
        )

        if (rest) {
            val bookSide = if (order.side == OrderSide.BUY) bids else asks
            bookSide.computeIfAbsent(order.priceBd()) { ArrayDeque() }.addLast(updated)
        }

        val reason = when {
            status == OrderStatus.CANCELLED && filled.compareTo(BigDecimal.ZERO) == 0 -> "No liquidity"
            else -> null
        }
        return MatchResult(trades, updated, reason)
    }

    fun restoreResting(order: Order) {
        if (order.remainingBd() <= BigDecimal.ZERO) return
        if (order.price == null) return
        val bookSide = if (order.side == OrderSide.BUY) bids else asks
        val queue = bookSide.computeIfAbsent(order.priceBd()) { ArrayDeque() }
        if (queue.any { it.id == order.id }) return
        queue.addLast(order)
        if (order.sequence > sequence.get()) sequence.set(order.sequence)
    }

    fun clear() {
        bids.clear()
        asks.clear()
        sequence.set(0)
    }

    fun cancel(orderId: String): Order? {
        cancelFrom(bids, orderId)?.let { return it }
        cancelFrom(asks, orderId)?.let { return it }
        return null
    }

    fun depth(levels: Int = 10): OrderBookDepth {
        val bidLevels = occupiedLevels(bids, levels)
        val askLevels = occupiedLevels(asks, levels)
        return OrderBookDepth(symbol, bidLevels, askLevels, bestBid()?.toPlainString(), bestAsk()?.toPlainString())
    }

    fun snapshotOrders(): List<Order> {
        val result = mutableListOf<Order>()
        bids.values.forEach { result.addAll(it) }
        asks.values.forEach { result.addAll(it) }
        return result
    }

    fun availableLiquidity(side: OrderSide, limitPrice: BigDecimal, excludedUserId: String? = null): BigDecimal {
        val bookSide = if (side == OrderSide.BUY) asks else bids
        var total = BigDecimal.ZERO
        for ((price, queue) in bookSide) {
            if (!priceCrosses(side, limitPrice, price)) break
            total += queue.fold(BigDecimal.ZERO) { acc, o ->
                if (o.userId == excludedUserId) acc else acc + o.remainingBd()
            }
        }
        return total
    }

    private fun cancelFrom(book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>, orderId: String): Order? {
        for ((price, queue) in book) {
            val it = queue.iterator()
            while (it.hasNext()) {
                val o = it.next()
                if (o.id == orderId) {
                    it.remove()
                    if (queue.isEmpty()) book.remove(price)
                    return o.copy(status = OrderStatus.CANCELLED, updatedAt = now())
                }
            }
        }
        return null
    }

    private fun occupiedLevels(
        book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>,
        levels: Int
    ): List<DepthLevel> {
        val result = mutableListOf<DepthLevel>()
        for ((price, q) in book) {
            if (q.isEmpty()) continue
            val total = q.fold(BigDecimal.ZERO) { acc, o -> acc + o.remainingBd() }
            result.add(DepthLevel(price.toPlainString(), total.toPlainString(), q.size))
            if (result.size >= levels) break
        }
        return result
    }

    private fun firstOccupied(book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>): BigDecimal? {
        for ((price, q) in book) {
            if (q.isNotEmpty()) return price
        }
        return null
    }

    private fun aggressiveLimit(order: Order): BigDecimal = when (order.type) {
        OrderType.MARKET -> order.price?.let(::BigDecimal)
            ?: if (order.side == OrderSide.BUY) MAX_PRICE else MIN_PRICE
        else -> order.priceBd()
    }

    private fun priceCrosses(side: OrderSide, limit: BigDecimal, resting: BigDecimal): Boolean =
        if (side == OrderSide.BUY) resting <= limit else resting >= limit

    private fun deterministicTradeId(takerId: String, makerId: String, filledSoFar: BigDecimal): String =
        UUID.nameUUIDFromBytes("$takerId|$makerId|${filledSoFar.toPlainString()}".toByteArray()).toString()

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private val MAX_PRICE = BigDecimal("1000000000000")
        private val MIN_PRICE = BigDecimal.ZERO
    }
}

data class MatchResult(
    val trades: List<Trade>,
    val order: Order,
    val reason: String? = null
)

@kotlinx.serialization.Serializable
data class DepthLevel(val price: String, val quantity: String, val orderCount: Int)

@kotlinx.serialization.Serializable
data class OrderBookDepth(
    val symbol: String,
    val bids: List<DepthLevel>,
    val asks: List<DepthLevel>,
    val bestBid: String? = null,
    val bestAsk: String? = null
)
