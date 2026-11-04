package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import com.argent.common.events.DomainEvent
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MatchingEngineTest : FunSpec({

    test("FOK that cannot fill is not accepted") {
        runBlocking {
            val engine = MatchingEngine(keepRecordedEvents = true)
            engine.submit(
                Order(
                    userId = "s",
                    symbol = "THYAO",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "10",
                    quantity = "1",
                ),
            )
            val result =
                engine.submit(
                    Order(
                        userId = "b",
                        symbol = "THYAO",
                        side = OrderSide.BUY,
                        type = OrderType.FOK,
                        price = "10",
                        quantity = "40",
                    ),
                )
            result.accepted shouldBe false
            engine.shutdown()
        }
    }

    test("FOK does not count the submitting user's own liquidity") {
        runBlocking {
            val engine = MatchingEngine(keepRecordedEvents = true)
            engine.submit(
                Order(
                    userId = "buyer",
                    symbol = "THYAO",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "10",
                    quantity = "8",
                ),
            )
            engine.submit(
                Order(
                    userId = "other",
                    symbol = "THYAO",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "10",
                    quantity = "2",
                ),
            )

            val result =
                engine.submit(
                    Order(
                        userId = "buyer",
                        symbol = "THYAO",
                        side = OrderSide.BUY,
                        type = OrderType.FOK,
                        price = "10",
                        quantity = "5",
                    ),
                )

            result.accepted shouldBe false
            result.trades.size shouldBe 0
            engine.depth("THYAO").asks.single().quantity shouldBe "10"
            engine.shutdown()
        }
    }

    test("duplicate clientOrderId is rejected") {
        runBlocking {
            val engine = MatchingEngine(keepRecordedEvents = true)
            val a =
                Order(
                    userId = "u",
                    clientOrderId = "c1",
                    symbol = "THYAO",
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    price = "10",
                    quantity = "1",
                )
            val first = engine.submit(a)
            val second = engine.submit(a.copy(id = "other"))
            first.accepted shouldBe true
            second.accepted shouldBe false
            engine.shutdown()
        }
    }

    test("production mode publishes events without retaining an in-memory history") {
        runBlocking {
            val persistedEvents = mutableListOf<DomainEvent>()
            val engine = MatchingEngine(eventBus = { persistedEvents.add(it) }, keepRecordedEvents = false)

            val result =
                engine.submit(
                    Order(
                        userId = "u",
                        symbol = "THYAO",
                        side = OrderSide.BUY,
                        type = OrderType.LIMIT,
                        price = "10",
                        quantity = "1",
                    ),
                )

            result.accepted shouldBe true
            persistedEvents.isNotEmpty() shouldBe true
            engine.recordedEvents() shouldBe emptyList()
            engine.shutdown()
        }
    }

    test("in-memory event history is disabled by default") {
        runBlocking {
            val engine = MatchingEngine()
            engine.submit(
                Order(
                    userId = "u",
                    symbol = "THYAO",
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    price = "10",
                    quantity = "1",
                ),
            )
            engine.recordedEvents() shouldBe emptyList()
            engine.shutdown()
        }
    }

    test("full symbol mailbox fails fast with an overload error") {
        runBlocking {
            val eventStarted = CountDownLatch(1)
            val releaseEvent = CountDownLatch(1)
            val engine =
                MatchingEngine(eventBus = {
                    if (it is OrderSubmitted) {
                        eventStarted.countDown()
                        releaseEvent.await(10, TimeUnit.SECONDS)
                    }
                })
            try {
                val first =
                    async(Dispatchers.IO) {
                        engine.submit(
                            Order(
                                userId = "u",
                                symbol = "THYAO",
                                side = OrderSide.BUY,
                                type = OrderType.LIMIT,
                                price = "10",
                                quantity = "1",
                            ),
                        )
                    }
                eventStarted.await(5, TimeUnit.SECONDS) shouldBe true

                val queued =
                    (0 until 256).map {
                        async(start = CoroutineStart.UNDISPATCHED) { engine.depth("THYAO") }
                    }
                val overflowCause =
                    try {
                        engine.depth("THYAO")
                        null
                    } catch (cause: Throwable) {
                        cause
                    }
                (overflowCause is MatchingEngine.EngineOverloadedException) shouldBe true

                releaseEvent.countDown()
                first.await().accepted shouldBe true
                queued.awaitAll().size shouldBe 256
            } finally {
                releaseEvent.countDown()
                engine.shutdown()
            }
        }
    }

    test("event sourcing replay rebuilds the same book") {
        runBlocking {
            val engine = MatchingEngine(keepRecordedEvents = true)
            engine.submit(
                Order(
                    userId = "s",
                    symbol = "ASELS",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "50",
                    quantity = "8",
                ),
            )
            engine.submit(
                Order(
                    userId = "b",
                    symbol = "ASELS",
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    price = "50",
                    quantity = "3",
                ),
            )
            val liveDepth = engine.depth("ASELS")
            val events = engine.recordedEvents()

            val replayed = MatchingEngine()
            replayed.replay(events.filterIsInstance<OrderSubmitted>())
            replayed.depth("ASELS").asks.map { it.price to it.quantity } shouldBe
                liveDepth.asks.map { it.price to it.quantity }
            engine.shutdown()
            replayed.shutdown()
        }
    }

    test("cancel between submits replays in sequence") {
        runBlocking {
            val engine = MatchingEngine(keepRecordedEvents = true)
            val sell =
                Order(
                    id = "s1",
                    userId = "s",
                    symbol = "SISE",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "40",
                    quantity = "10",
                )
            engine.submit(sell)
            engine.cancel("SISE", "s1")
            engine.submit(
                Order(
                    id = "s2",
                    userId = "s",
                    symbol = "SISE",
                    side = OrderSide.SELL,
                    type = OrderType.LIMIT,
                    price = "41",
                    quantity = "4",
                ),
            )
            val live = engine.depth("SISE")
            val replayed = MatchingEngine()
            replayed.replay(engine.recordedEvents().filter { it is OrderSubmitted || it is OrderCancelled })
            replayed.depth("SISE").asks.map { it.price to it.quantity } shouldBe
                live.asks.map { it.price to it.quantity }
            engine.shutdown()
            replayed.shutdown()
        }
    }

    test("same order stream is deterministic") {
        checkAll(25, arbOrderStream()) { orders ->
            runBlocking {
                val a = MatchingEngine()
                val b = MatchingEngine()
                val tradesA =
                    orders.flatMap {
                        a.submit(it).trades.map {
                                t ->
                            listOf(t.price, t.quantity, t.buyOrderId, t.sellOrderId)
                        }
                    }
                val tradesB =
                    orders.flatMap {
                        b.submit(it).trades.map {
                                t ->
                            listOf(t.price, t.quantity, t.buyOrderId, t.sellOrderId)
                        }
                    }
                tradesA shouldBe tradesB
                a.depth("THYAO").bids.map { it.price to it.quantity } shouldBe
                    b.depth("THYAO").bids.map { it.price to it.quantity }
                a.shutdown()
                b.shutdown()
            }
        }
    }

    test("replay of submits produces identical trades") {
        runBlocking {
            val orders =
                listOf(
                    Order(
                        id = "s1",
                        userId = "s",
                        symbol = "EREGL",
                        side = OrderSide.SELL,
                        type = OrderType.LIMIT,
                        price = "20",
                        quantity = "10",
                    ),
                    Order(
                        id = "b1",
                        userId = "b",
                        symbol = "EREGL",
                        side = OrderSide.BUY,
                        type = OrderType.LIMIT,
                        price = "21",
                        quantity = "4",
                    ),
                )
            val e1 = MatchingEngine(keepRecordedEvents = true)
            val t1 = orders.flatMap { e1.submit(it).trades.map { t -> t.id to t.quantity } }
            val e2 = MatchingEngine(keepRecordedEvents = true)
            e2.replay(orders.map { OrderSubmitted(aggregateId = it.symbol, order = it) })
            val t2 = e2.recordedEvents().filterIsInstance<TradeExecuted>().map { it.trade.id to it.trade.quantity }
            t1 shouldBe t2
            e1.shutdown()
            e2.shutdown()
        }
    }
})
