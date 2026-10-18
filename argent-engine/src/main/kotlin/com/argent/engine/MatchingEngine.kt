package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.Trade
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderAccepted
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderRejected
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
class MatchingEngine(
    private val eventBus: (DomainEvent) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val keepRecordedEvents: Boolean = true,
    private val rollbackOnMutationFailure: Boolean = true
) {
    private val books = ConcurrentHashMap<String, OrderBook>()
    private val actors = ConcurrentHashMap<String, SendChannel<EngineCommand>>()
    private val processedClientOrders = ConcurrentHashMap<String, String>()
    private val eventLog = ConcurrentLinkedQueue<DomainEvent>()
    private val seq = AtomicLong(0)
    private val fatalFailure = AtomicReference<Throwable?>(null)

    sealed class EngineCommand {
        data class Submit(val order: Order, val reply: CompletableDeferred<SubmitResult>) : EngineCommand()
        data class Cancel(val orderId: String, val reply: CompletableDeferred<Order?>) : EngineCommand()
        data class GetDepth(val levels: Int, val reply: CompletableDeferred<OrderBookDepth>) : EngineCommand()
        data class Snapshot(val reply: CompletableDeferred<List<Order>>) : EngineCommand()
        data class Restore(val order: Order, val reply: CompletableDeferred<Boolean>) : EngineCommand()
        data class Clear(val reply: CompletableDeferred<Boolean>) : EngineCommand()
        data class Replay(val events: List<DomainEvent>, val reply: CompletableDeferred<List<Order>>) : EngineCommand()
    }

    data class SubmitResult(
        val order: Order,
        val trades: List<Trade>,
        val accepted: Boolean,
        val reason: String? = null
    )

    private fun nextSeq(): Long = seq.incrementAndGet()

    private fun publish(event: DomainEvent) {
        if (keepRecordedEvents) eventLog.add(event)
        try {
            eventBus(event)
        } catch (cause: Exception) {
            if (keepRecordedEvents) eventLog.remove(event)
            throw EventPersistenceException("Could not durably record ${event::class.simpleName}", cause)
        }
    }

    fun isHealthy(): Boolean = fatalFailure.get() == null

    private fun ensureHealthy() {
        fatalFailure.get()?.let {
            throw IllegalStateException("Matching engine is unavailable after a state mutation failure; restart to replay durable events", it)
        }
    }

    private fun restoreSnapshot(book: OrderBook, snapshot: List<Order>) {
        book.clear()
        snapshot.forEach(book::restoreResting)
    }

    private fun getOrCreateActor(symbol: String): SendChannel<EngineCommand> {
        return actors.computeIfAbsent(symbol) {
            scope.actor(capacity = Channel.UNLIMITED) {
                val book = books.computeIfAbsent(symbol) { OrderBook(symbol) }
                for (cmd in channel) {
                    val mutating = cmd is EngineCommand.Submit || cmd is EngineCommand.Cancel ||
                        cmd is EngineCommand.Restore || cmd is EngineCommand.Clear || cmd is EngineCommand.Replay
                    val snapshot = if (mutating && rollbackOnMutationFailure) book.snapshotOrders() else null
                    val clientOrdersSnapshot = if (snapshot != null) {
                        processedClientOrders.filterKeys { it.startsWith("${symbol}:") }
                    } else emptyMap()
                    try {
                        if (mutating) ensureHealthy()
                        when (cmd) {
                        is EngineCommand.Submit -> handleSubmit(book, cmd)
                        is EngineCommand.Cancel -> {
                            val cancelled = book.cancel(cmd.orderId)
                            if (cancelled != null) {
                                publish(
                                    OrderCancelled(
                                        aggregateId = symbol,
                                        sequence = nextSeq(),
                                        orderId = cmd.orderId
                                    )
                                )
                            }
                            cmd.reply.complete(cancelled)
                        }
                        is EngineCommand.GetDepth -> cmd.reply.complete(book.depth(cmd.levels))
                        is EngineCommand.Snapshot -> cmd.reply.complete(book.snapshotOrders())
                        is EngineCommand.Restore -> {
                            book.restoreResting(cmd.order)
                            cmd.order.clientOrderId?.let { cid ->
                                processedClientOrders.putIfAbsent(clientOrderKey(cmd.order.symbol, cmd.order.userId, cid), cmd.order.id)
                            }
                            cmd.reply.complete(true)
                        }
                        is EngineCommand.Clear -> {
                            book.clear()
                            processedClientOrders.keys.removeIf { it.startsWith("${symbol}:") }
                            cmd.reply.complete(true)
                        }
                        is EngineCommand.Replay -> {
                            val recovered = mutableListOf<Order>()
                            for (event in cmd.events) {
                                when (event) {
                                    is OrderSubmitted -> {
                                        val eventBook = books.computeIfAbsent(event.order.symbol) { OrderBook(event.order.symbol) }
                                        recovered += submitInto(eventBook, event.order).order
                                    }
                                    is OrderCancelled -> {
                                        val eventBook = books.computeIfAbsent(event.aggregateId) { OrderBook(event.aggregateId) }
                                        val cancelled = eventBook.cancel(event.orderId)
                                        if (cancelled != null) {
                                            publish(OrderCancelled(aggregateId = event.aggregateId, sequence = nextSeq(), orderId = event.orderId))
                                            recovered += cancelled
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            cmd.reply.complete(recovered)
                        }
                    }
                    } catch (cause: Exception) {
                        if (snapshot != null) {
                            restoreSnapshot(book, snapshot)
                            processedClientOrders.keys.removeIf { it.startsWith("${symbol}:") }
                            processedClientOrders.putAll(clientOrdersSnapshot)
                        }
                        if (snapshot != null) {
                            fatalFailure.compareAndSet(null, cause)
                        }
                        when (cmd) {
                            is EngineCommand.Submit -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.Cancel -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.GetDepth -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.Snapshot -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.Restore -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.Clear -> cmd.reply.completeExceptionally(cause)
                            is EngineCommand.Replay -> cmd.reply.completeExceptionally(cause)
                        }
                    }
                }
            }
        }
    }

    private fun handleSubmit(book: OrderBook, cmd: EngineCommand.Submit) {
        cmd.reply.complete(submitInto(book, cmd.order))
    }

    private fun submitInto(book: OrderBook, order: Order): SubmitResult {
        publish(OrderSubmitted(aggregateId = order.symbol, sequence = nextSeq(), order = order))

        val clientId = order.clientOrderId
        if (clientId != null) {
            val key = clientOrderKey(order.symbol, order.userId, clientId)
            val existing = processedClientOrders.putIfAbsent(key, order.id)
            if (existing != null && existing != order.id) {
                val rejected = order.copy(status = OrderStatus.REJECTED)
                publish(
                    OrderRejected(
                        aggregateId = order.symbol,
                        sequence = nextSeq(),
                        orderId = order.id,
                        reason = "Duplicate clientOrderId"
                    )
                )
                return SubmitResult(rejected, emptyList(), false, "Duplicate clientOrderId: $existing")
            }
        }

        val result = book.match(order)
        val accepted = isAccepted(result.order)
        if (!accepted) {
            publish(
                OrderRejected(
                    aggregateId = order.symbol,
                    sequence = nextSeq(),
                    orderId = order.id,
                    reason = result.reason ?: "Rejected"
                )
            )
        } else {
            if (result.order.status == OrderStatus.ACCEPTED ||
                result.order.status == OrderStatus.PARTIALLY_FILLED ||
                result.order.status == OrderStatus.FILLED
            ) {
                publish(OrderAccepted(aggregateId = order.symbol, sequence = nextSeq(), order = result.order))
            }
            result.trades.forEach { t ->
                publish(TradeExecuted(aggregateId = order.symbol, sequence = nextSeq(), trade = t))
            }
        }
        return SubmitResult(
            order = result.order,
            trades = result.trades,
            accepted = accepted,
            reason = result.reason
        )
    }

    suspend fun submit(order: Order): SubmitResult {
        val reply = CompletableDeferred<SubmitResult>()
        getOrCreateActor(order.symbol).send(EngineCommand.Submit(order, reply))
        return reply.await()
    }

    suspend fun cancel(symbol: String, orderId: String): Order? {
        val reply = CompletableDeferred<Order?>()
        getOrCreateActor(symbol).send(EngineCommand.Cancel(orderId, reply))
        return reply.await()
    }

    suspend fun depth(symbol: String, levels: Int = 10): OrderBookDepth {
        val reply = CompletableDeferred<OrderBookDepth>()
        getOrCreateActor(symbol).send(EngineCommand.GetDepth(levels, reply))
        return reply.await()
    }

    suspend fun snapshot(symbol: String): List<Order> {
        val reply = CompletableDeferred<List<Order>>()
        getOrCreateActor(symbol).send(EngineCommand.Snapshot(reply))
        return reply.await()
    }

    suspend fun restore(order: Order): Boolean {
        val reply = CompletableDeferred<Boolean>()
        getOrCreateActor(order.symbol).send(EngineCommand.Restore(order, reply))
        return reply.await()
    }

    suspend fun clear(symbol: String) {
        val reply = CompletableDeferred<Boolean>()
        getOrCreateActor(symbol).send(EngineCommand.Clear(reply))
        reply.await()
    }

    private fun isAccepted(order: Order): Boolean = when (order.status) {
        OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED -> true
        OrderStatus.CANCELLED -> order.filledBd() > java.math.BigDecimal.ZERO
        else -> false
    }

    private fun clientOrderKey(symbol: String, userId: String, clientOrderId: String) =
        "$symbol:$userId:$clientOrderId"

    fun recordedEvents(): List<DomainEvent> = if (keepRecordedEvents) eventLog.toList() else emptyList()

    suspend fun replay(events: List<DomainEvent>): List<Order> {
        val reply = CompletableDeferred<List<Order>>()
        getOrCreateActor(REPLAY_ACTOR).send(EngineCommand.Replay(events, reply))
        return reply.await()
    }

    fun shutdown() {
        actors.values.forEach { it.close() }
        scope.cancel()
    }

    private companion object {
        const val REPLAY_ACTOR = "__argent_event_replay__"
    }

    class EventPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)
}
