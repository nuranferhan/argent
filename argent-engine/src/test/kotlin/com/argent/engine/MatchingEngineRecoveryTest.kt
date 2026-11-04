package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class MatchingEngineRecoveryTest : FunSpec({
    test("replay can return regenerated fills without retaining an in-memory event history") {
        runBlocking {
            val orders =
                listOf(
                    Order(
                        id = "replay-sell",
                        userId = "s",
                        symbol = "EREGL",
                        side = OrderSide.SELL,
                        type = OrderType.LIMIT,
                        price = "20",
                        quantity = "10",
                    ),
                    Order(
                        id = "replay-buy",
                        userId = "b",
                        symbol = "EREGL",
                        side = OrderSide.BUY,
                        type = OrderType.LIMIT,
                        price = "21",
                        quantity = "4",
                    ),
                )
            val live = MatchingEngine(keepRecordedEvents = true)
            orders.forEach { live.submit(it) }
            val history = live.recordedEvents().filter { it is OrderSubmitted || it is OrderCancelled }
            val replayedEvents = mutableListOf<DomainEvent>()
            val recovery =
                MatchingEngine(
                    eventBus = replayedEvents::add,
                    keepRecordedEvents = false,
                    rollbackOnMutationFailure = false,
                )

            recovery.replay(history)

            replayedEvents.filterIsInstance<TradeExecuted>().map { it.trade.id to it.trade.quantity } shouldBe
                live.recordedEvents().filterIsInstance<TradeExecuted>().map { it.trade.id to it.trade.quantity }
            recovery.recordedEvents() shouldBe emptyList()
            live.shutdown()
            recovery.shutdown()
        }
    }

    test("clearing a symbol before replay also clears its idempotency state") {
        runBlocking {
            val original =
                Order(
                    id = "restored",
                    userId = "u",
                    clientOrderId = "client-1",
                    symbol = "THYAO",
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    price = "300",
                    quantity = "2",
                )
            val engine = MatchingEngine(keepRecordedEvents = true)
            engine.submit(original)
            val events = engine.recordedEvents().filterIsInstance<OrderSubmitted>()
            engine.clear("THYAO")
            engine.replay(events)

            engine.depth("THYAO").bids.single().quantity shouldBe "2"
            engine.shutdown()
        }
    }

    test("parallel same-symbol submits and cancels preserve book invariants") {
        runBlocking {
            val engine = MatchingEngine()
            try {
                coroutineScope {
                    (0 until 400).map { index ->
                        async(Dispatchers.Default) {
                            val buy = index % 2 == 0
                            submitWhenCapacityReturns {
                                engine.submit(
                                    Order(
                                        id = "parallel-$index",
                                        userId = "account-$index",
                                        symbol = "THYAO",
                                        side = if (buy) OrderSide.BUY else OrderSide.SELL,
                                        type = OrderType.LIMIT,
                                        price = if (buy) "99" else "101",
                                        quantity = "1",
                                    ),
                                )
                            }
                        }
                    }.awaitAll()
                }
                val resting = engine.snapshot("THYAO")
                resting.size shouldBe 400
                engine.depth("THYAO").bids.sumOf { it.quantity.toBigDecimal() } shouldBe "200".toBigDecimal()
                engine.depth("THYAO").asks.sumOf { it.quantity.toBigDecimal() } shouldBe "200".toBigDecimal()

                coroutineScope {
                    resting.filterIndexed { index, _ -> index % 2 == 0 }.map { order ->
                        async(Dispatchers.Default) {
                            submitWhenCapacityReturns { engine.cancel("THYAO", order.id) }
                        }
                    }.awaitAll()
                }
                engine.snapshot("THYAO").size shouldBe 200
                engine.isHealthy() shouldBe true
            } finally {
                engine.shutdown()
            }
        }
    }

    test("event persistence outage rolls back the book and fails closed") {
        runBlocking {
            val engine = MatchingEngine(eventBus = { throw IllegalStateException("injected event-store outage") })
            try {
                var rejected = false
                try {
                    engine.submit(
                        Order(
                            userId = "u",
                            symbol = "THYAO",
                            side = OrderSide.BUY,
                            type = OrderType.LIMIT,
                            price = "100",
                            quantity = "1",
                        ),
                    )
                } catch (_: Exception) {
                    rejected = true
                }
                rejected shouldBe true
                engine.snapshot("THYAO") shouldBe emptyList()
                engine.isHealthy() shouldBe false
                var failClosed = false
                try {
                    engine.submit(
                        Order(
                            userId = "u2",
                            symbol = "THYAO",
                            side = OrderSide.BUY,
                            type = OrderType.LIMIT,
                            price = "99",
                            quantity = "1",
                        ),
                    )
                } catch (_: IllegalStateException) {
                    failClosed = true
                }
                failClosed shouldBe true
            } finally {
                engine.shutdown()
            }
        }
    }

    test("cancel racing a crossing order has exactly one serialized outcome") {
        runBlocking {
            val engine = MatchingEngine()
            try {
                repeat(100) { round ->
                    val restingAsk =
                        Order(
                            id = "race-ask-$round",
                            userId = "seller-$round",
                            symbol = "THYAO",
                            side = OrderSide.SELL,
                            type = OrderType.LIMIT,
                            price = "100",
                            quantity = "1",
                        )
                    engine.submit(restingAsk).accepted shouldBe true

                    val (cancelled, incoming) =
                        coroutineScope {
                            val cancel = async(Dispatchers.Default) { engine.cancel("THYAO", restingAsk.id) }
                            val submit =
                                async(Dispatchers.Default) {
                                    engine.submit(
                                        Order(
                                            id = "race-bid-$round",
                                            userId = "buyer-$round",
                                            symbol = "THYAO",
                                            side = OrderSide.BUY,
                                            type = OrderType.LIMIT,
                                            price = "100",
                                            quantity = "1",
                                        ),
                                    )
                                }
                            cancel.await() to submit.await()
                        }

                    if (cancelled != null) {
                        cancelled.status shouldBe OrderStatus.CANCELLED
                        incoming.trades.size shouldBe 0
                        incoming.order.status shouldBe OrderStatus.ACCEPTED
                        engine.cancel("THYAO", incoming.order.id)?.status shouldBe OrderStatus.CANCELLED
                    } else {
                        incoming.trades.size shouldBe 1
                        incoming.order.status shouldBe OrderStatus.FILLED
                        engine.snapshot("THYAO").none {
                            it.id == restingAsk.id || it.id == incoming.order.id
                        } shouldBe true
                    }
                }
                engine.snapshot("THYAO").size shouldBe 0
                engine.isHealthy() shouldBe true
            } finally {
                engine.shutdown()
            }
        }
    }
})

private suspend fun <T> submitWhenCapacityReturns(action: suspend () -> T): T {
    repeat(10_000) {
        try {
            return action()
        } catch (_: MatchingEngine.EngineOverloadedException) {
            yield()
        }
    }
    error("Engine mailbox did not accept the command after retries")
}
