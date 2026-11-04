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
    private val orderLocations = HashMap<String, OrderLocation>()
    private val sequence = AtomicLong(0)

    private data class OrderLocation(val side: OrderSide, val price: BigDecimal)

    fun nextSequence(): Long = sequence.incrementAndGet()

    fun bestBid(): BigDecimal? = firstOccupied(bids)

    fun bestAsk(): BigDecimal? = firstOccupied(asks)

    fun midPrice(): BigDecimal? {
        val bid = bestBid() ?: return null
        val ask = bestAsk() ?: return null
        return (bid + ask).divide(BigDecimal(2))
    }

    @JvmOverloads
    fun match(
        order: Order,
        undoLog: MutableList<() -> Unit>? = null,
    ): MatchResult {
        val limitPrice = aggressiveLimit(order)
        if (order.type != OrderType.MARKET && order.price == null) {
            return MatchResult(
                trades = emptyList(),
                order = order.copy(status = OrderStatus.REJECTED, updatedAt = now()),
                reason = "Limit price required",
            )
        }

        val oppositeBook = if (order.side == OrderSide.BUY) asks else bids
        val crossesOwnLiquidity = wouldTradeWithOwnOrder(oppositeBook, order, limitPrice)
        if (crossesOwnLiquidity) {
            return MatchResult(
                trades = emptyList(),
                order =
                    order.copy(
                        filledQuantity = "0",
                        remainingQuantity = order.quantity,
                        status = OrderStatus.CANCELLED,
                        updatedAt = now(),
                    ),
                reason = "Self-trade prevention: incoming order would cross your own resting order",
            )
        }

        if (order.type == OrderType.FOK) {
            val available = availableLiquidity(order.side, limitPrice, order.userId)
            if (available < order.quantityBd()) {
                return MatchResult(
                    trades = emptyList(),
                    order = order.copy(status = OrderStatus.CANCELLED, updatedAt = now()),
                    reason = "FOK cannot be fully filled",
                )
            }
        }

        val trades = mutableListOf<Trade>()
        var remaining = order.quantityBd()
        var filled = BigDecimal.ZERO
        val bookSide = if (order.side == OrderSide.BUY) asks else bids

        // This book is mutated by one symbol actor, so its ordered map can be traversed
        // directly. Copying entries here made even a non-crossing insert O(book size).
        priceLoop@ for (entry in bookSide.entries) {
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
                    orderLocations.remove(resting.id)
                    val removedIndex = i
                    undoLog?.add {
                        queue.add(removedIndex.coerceAtMost(queue.size), resting)
                        orderLocations[resting.id] =
                            OrderLocation(
                                if (order.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY,
                                entry.key,
                            )
                    }
                    queue.removeAt(i)
                    continue
                }

                val trade =
                    Trade(
                        id = deterministicTradeId(order.id, resting.id, filled),
                        symbol = symbol,
                        price = entry.key.toPlainString(),
                        quantity = tradeQty.toPlainString(),
                        buyOrderId = if (order.side == OrderSide.BUY) order.id else resting.id,
                        sellOrderId = if (order.side == OrderSide.SELL) order.id else resting.id,
                        buyUserId = if (order.side == OrderSide.BUY) order.userId else resting.userId,
                        sellUserId = if (order.side == OrderSide.SELL) order.userId else resting.userId,
                        makerOrderId = resting.id,
                        takerOrderId = order.id,
                    )
                trades.add(trade)

                val newRemaining = resting.remainingBd() - tradeQty
                if (newRemaining <= BigDecimal.ZERO) {
                    val index = i
                    orderLocations.remove(resting.id)
                    undoLog?.add {
                        queue.add(index.coerceAtMost(queue.size), resting)
                        orderLocations[resting.id] =
                            OrderLocation(
                                if (order.side == OrderSide.BUY) OrderSide.SELL else OrderSide.BUY,
                                entry.key,
                            )
                    }
                    queue.removeAt(i)
                } else {
                    val updatedResting =
                        resting.copy(
                            remainingQuantity = newRemaining.toPlainString(),
                            filledQuantity = (resting.filledBd() + tradeQty).toPlainString(),
                            status = OrderStatus.PARTIALLY_FILLED,
                            updatedAt = now(),
                        )
                    val index = i
                    undoLog?.add { queue[index] = resting }
                    queue[i] = updatedResting
                    i++
                }
                remaining -= tradeQty
                filled += tradeQty
            }
            if (queue.isEmpty()) {
                undoLog?.add { bookSide[entry.key] = queue }
                bookSide.remove(entry.key)
            }
        }

        val rest = remaining > BigDecimal.ZERO && order.type == OrderType.LIMIT
        val status =
            when {
                filled.compareTo(BigDecimal.ZERO) == 0 && rest -> OrderStatus.ACCEPTED
                remaining.compareTo(BigDecimal.ZERO) == 0 -> OrderStatus.FILLED
                rest -> OrderStatus.PARTIALLY_FILLED
                filled.compareTo(BigDecimal.ZERO) == 0 -> OrderStatus.CANCELLED
                else -> OrderStatus.CANCELLED
            }

        val updated =
            order.copy(
                filledQuantity = filled.toPlainString(),
                remainingQuantity = remaining.max(BigDecimal.ZERO).toPlainString(),
                status = status,
                updatedAt = now(),
                sequence = if (order.sequence == 0L) nextSequence() else order.sequence,
            )

        if (rest) {
            val bookSide = if (order.side == OrderSide.BUY) bids else asks
            val queue = bookSide.computeIfAbsent(order.priceBd()) { ArrayDeque() }
            undoLog?.add {
                check(queue.lastOrNull()?.id == updated.id) { "Order book undo order is not LIFO" }
                queue.removeLast()
                orderLocations.remove(updated.id)
                if (queue.isEmpty()) bookSide.remove(order.priceBd())
            }
            queue.addLast(updated)
            orderLocations[updated.id] = OrderLocation(order.side, order.priceBd())
        }

        val reason =
            when {
                status == OrderStatus.CANCELLED && filled.compareTo(BigDecimal.ZERO) == 0 -> "No liquidity"
                else -> null
            }
        return MatchResult(trades, updated, reason)
    }

    fun restoreResting(
        order: Order,
        undoLog: MutableList<() -> Unit>? = null,
    ) {
        if (order.remainingBd() <= BigDecimal.ZERO) return
        if (order.price == null) return
        val bookSide = if (order.side == OrderSide.BUY) bids else asks
        val queue = bookSide.computeIfAbsent(order.priceBd()) { ArrayDeque() }
        if (queue.any { it.id == order.id }) return
        undoLog?.add {
            check(queue.lastOrNull()?.id == order.id) { "Order book undo order is not LIFO" }
            queue.removeLast()
            orderLocations.remove(order.id)
            if (queue.isEmpty()) bookSide.remove(order.priceBd())
        }
        queue.addLast(order)
        orderLocations[order.id] = OrderLocation(order.side, order.priceBd())
        if (order.sequence > sequence.get()) sequence.set(order.sequence)
    }

    fun clear() {
        bids.clear()
        asks.clear()
        orderLocations.clear()
        sequence.set(0)
    }

    fun cancel(
        orderId: String,
        undoLog: MutableList<() -> Unit>? = null,
    ): Order? {
        cancelFrom(bids, orderId, undoLog)?.let { return it }
        cancelFrom(asks, orderId, undoLog)?.let { return it }
        return null
    }

    fun rollback(undoLog: List<() -> Unit>) {
        undoLog.asReversed().forEach { it() }
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

    fun availableLiquidity(
        side: OrderSide,
        limitPrice: BigDecimal,
        excludedUserId: String? = null,
    ): BigDecimal {
        val bookSide = if (side == OrderSide.BUY) asks else bids
        var total = BigDecimal.ZERO
        for ((price, queue) in bookSide) {
            if (!priceCrosses(side, limitPrice, price)) break
            total +=
                queue.fold(BigDecimal.ZERO) { acc, o ->
                    if (o.userId == excludedUserId) acc else acc + o.remainingBd()
                }
        }
        return total
    }

    private fun cancelFrom(
        book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>,
        orderId: String,
        undoLog: MutableList<() -> Unit>?,
    ): Order? {
        val location = orderLocations[orderId] ?: return null
        val queue = book[location.price] ?: return null
        val index = queue.indexOfFirst { it.id == orderId }
        if (index < 0) return null
        val order = queue[index]
        undoLog?.add {
            queue.add(index.coerceAtMost(queue.size), order)
            orderLocations[order.id] = location
        }
        queue.removeAt(index)
        orderLocations.remove(orderId)
        if (queue.isEmpty()) {
            undoLog?.add { book[location.price] = queue }
            book.remove(location.price)
        }
        return order.copy(status = OrderStatus.CANCELLED, updatedAt = now())
    }

    private fun occupiedLevels(
        book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>,
        levels: Int,
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

    private fun aggressiveLimit(order: Order): BigDecimal =
        when (order.type) {
            OrderType.MARKET ->
                order.price?.let(::BigDecimal)
                    ?: if (order.side == OrderSide.BUY) MAX_PRICE else MIN_PRICE
            else -> order.priceBd()
        }

    private fun priceCrosses(
        side: OrderSide,
        limit: BigDecimal,
        resting: BigDecimal,
    ): Boolean = if (side == OrderSide.BUY) resting <= limit else resting >= limit

    /** Respect price-time priority: reject only when an own resting order would actually be reached. */
    private fun wouldTradeWithOwnOrder(
        book: ConcurrentSkipListMap<BigDecimal, ArrayDeque<Order>>,
        incoming: Order,
        limitPrice: BigDecimal,
    ): Boolean {
        var remaining = incoming.quantityBd()
        for ((price, queue) in book) {
            if (!priceCrosses(incoming.side, limitPrice, price)) break
            for (resting in queue) {
                val available = resting.remainingBd().max(BigDecimal.ZERO)
                if (available.signum() == 0) continue
                if (resting.userId == incoming.userId) return true
                remaining -= available
                if (remaining.signum() <= 0) return false
            }
        }
        return false
    }

    private fun deterministicTradeId(
        takerId: String,
        makerId: String,
        filledSoFar: BigDecimal,
    ): String = UUID.nameUUIDFromBytes("$takerId|$makerId|${filledSoFar.toPlainString()}".toByteArray()).toString()

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private val MAX_PRICE = BigDecimal("1000000000000")
        private val MIN_PRICE = BigDecimal.ZERO
    }
}

data class MatchResult(
    val trades: List<Trade>,
    val order: Order,
    val reason: String? = null,
)

@kotlinx.serialization.Serializable
data class DepthLevel(val price: String, val quantity: String, val orderCount: Int)

@kotlinx.serialization.Serializable
data class OrderBookDepth(
    val symbol: String,
    val bids: List<DepthLevel>,
    val asks: List<DepthLevel>,
    val bestBid: String? = null,
    val bestAsk: String? = null,
)
