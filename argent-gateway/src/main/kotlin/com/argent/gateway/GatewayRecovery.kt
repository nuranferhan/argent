package com.argent.gateway

import com.argent.common.domain.OrderStatus
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import com.argent.core.ArgentMatchingCore
import com.argent.engine.MatchingEngine
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.trading.TradingService
import com.argent.ledger.LedgerService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ArgentGatewayRecovery")

internal fun recoverGatewayState(
    core: ArgentMatchingCore,
    eventStore: PostgresEventStore,
    orders: OrderRepository,
    ledger: LedgerService,
    trading: TradingService,
    engine: MatchingEngine,
) = runBlocking {
    val recoveryStartedAt = System.nanoTime()
    val pendingAtStartup = orders.findPending()
    val recoverySymbols = pendingAtStartup.mapTo(mutableSetOf()) { it.symbol }
    log.info("Restoring active books from SQL order snapshots; replaying only {} pending symbols", recoverySymbols.size)
    val snapshots = recoverySymbols.associateWith(eventStore::loadBookSnapshot)
    val history =
        recoverySymbols.flatMap { symbol ->
            val checkpoint = snapshots[symbol]
            eventStore.loadForAggregateAfter(symbol, checkpoint?.lastEventSequence ?: 0L)
        }.sortedBy { it.sequence }
    log.info("Loaded {} recovery events in {} ms", history.size, (System.nanoTime() - recoveryStartedAt) / 1_000_000)
    val replayableOrderIds =
        history.filterIsInstance<OrderSubmitted>().map { it.order.id }.toSet() +
            snapshots.values.filterNotNull().flatMap { snapshot -> snapshot.orders.map { it.id } }
    val replayedEvents = mutableListOf<DomainEvent>()
    // Replay does not mutate the durable event store. Collect regenerated fills
    // for settlement recovery without retaining another engine event history.
    val recoveryEngine =
        core.newMatchingEngine(
            eventSink = replayedEvents::add,
            keepRecordedEvents = false,
            rollbackOnMutationFailure = false,
        )
    val replayStartedAt = System.nanoTime()
    snapshots.values.filterNotNull().flatMap { it.orders }.forEach { recoveryEngine.restore(it) }
    val replayResults = recoveryEngine.replay(history)
    val recoveredBookOrders = recoverySymbols.flatMap { recoveryEngine.snapshot(it) }
    val recoveredOrders = (replayResults + recoveredBookOrders).distinctBy { it.id }
    log.info(
        "Replayed {} events into {} order snapshots in {} ms",
        history.size,
        recoveredOrders.size,
        (System.nanoTime() - replayStartedAt) / 1_000_000,
    )
    val persistStartedAt = System.nanoTime()
    orders.saveAll(recoveredOrders.associateBy { it.id }.values)
    log.info("Persisted recovered orders in {} ms", (System.nanoTime() - persistStartedAt) / 1_000_000)
    val persistedTrades = orders.findTradesForRecovery().map { TradeExecuted(aggregateId = it.symbol, trade = it) }
    val unsettled =
        (replayedEvents.filterIsInstance<TradeExecuted>() + persistedTrades)
            .distinctBy { it.trade.id }
            .filterNot { ledger.isTradeSettled(it.trade.id) }
    trading.recoverUnsettled(unsettled)
    recoveryEngine.shutdown()

    orders.findPending().forEach { pending ->
        if (pending.id in replayableOrderIds) {
            error(
                "Order ${pending.id} remains pending after event replay; refusing startup",
            )
        }
        log.error("Rejecting pending order without durable submission event id={}", pending.id)
        orders.save(pending.copy(status = OrderStatus.REJECTED))
        ledger.releaseRemainingHold(pending.id)
    }
    ledger.activeHoldOrderIds().forEach { orderId ->
        val order = orders.findById(orderId)
        if (order == null || order.status !in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED)) {
            log.error("Releasing orphaned funds hold orderId={}", orderId)
            ledger.releaseRemainingHold(orderId)
        }
    }
    val active =
        (
            orders.findActive().filterNot { it.symbol in recoverySymbols } +
                recoveredOrders.filter {
                    it.status in setOf(OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED)
                }
        ).distinctBy { it.id }
    log.info("Recovering {} active orders", active.size)
    active.forEach { engine.restore(it) }
    (recoverySymbols + active.map { it.symbol }).forEach { symbol ->
        eventStore.saveBookSnapshot(symbol, active.filter { it.symbol == symbol })
    }
}
