package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import com.argent.common.events.OrderCancelled
import com.argent.common.events.OrderSubmitted
import com.argent.common.events.TradeExecuted
import com.argent.common.events.DomainEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MatchingEngineTest : FunSpec({

    test("FOK that cannot fill is not accepted") {
        runBlocking {
            val engine = MatchingEngine()
            engine.submit(
                Order(userId = "s", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "10", quantity = "1")
            )
            val result = engine.submit(
                Order(userId = "b", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.FOK, price = "10", quantity = "40")
            )
            result.accepted shouldBe false
            engine.shutdown()
        }
    }

    test("FOK does not count the submitting user's own liquidity") {
        runBlocking {
            val engine = MatchingEngine()
            engine.submit(Order(userId = "buyer", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "10", quantity = "8"))
            engine.submit(Order(userId = "other", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "10", quantity = "2"))

            val result = engine.submit(Order(userId = "buyer", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.FOK, price = "10", quantity = "5"))

            result.accepted shouldBe false
            result.trades.size shouldBe 0
            engine.depth("THYAO").asks.single().quantity shouldBe "10"
            engine.shutdown()
        }
    }

    test("duplicate clientOrderId is rejected") {
        runBlocking {
            val engine = MatchingEngine()
            val a = Order(
                userId = "u",
                clientOrderId = "c1",
                symbol = "THYAO",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = "10",
                quantity = "1"
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

            val result = engine.submit(
                Order(userId = "u", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "10", quantity = "1")
            )

            result.accepted shouldBe true
            persistedEvents.isNotEmpty() shouldBe true
            engine.recordedEvents() shouldBe emptyList()
            engine.shutdown()
        }
    }

    test("event sourcing replay rebuilds the same book") {
        runBlocking {
            val engine = MatchingEngine()
            engine.submit(Order(userId = "s", symbol = "ASELS", side = OrderSide.SELL, type = OrderType.LIMIT, price = "50", quantity = "8"))
            engine.submit(Order(userId = "b", symbol = "ASELS", side = OrderSide.BUY, type = OrderType.LIMIT, price = "50", quantity = "3"))
            val liveDepth = engine.depth("ASELS")
            val events = engine.recordedEvents()

            val replayed = MatchingEngine()
            replayed.replay(events.filterIsInstance<OrderSubmitted>())
            replayed.depth("ASELS").asks.map { it.price to it.quantity } shouldBe liveDepth.asks.map { it.price to it.quantity }
            engine.shutdown()
            replayed.shutdown()
        }
    }

    test("cancel between submits replays in sequence") {
        runBlocking {
            val engine = MatchingEngine()
            val sell = Order(id = "s1", userId = "s", symbol = "SISE", side = OrderSide.SELL, type = OrderType.LIMIT, price = "40", quantity = "10")
            engine.submit(sell)
            engine.cancel("SISE", "s1")
            engine.submit(Order(id = "s2", userId = "s", symbol = "SISE", side = OrderSide.SELL, type = OrderType.LIMIT, price = "41", quantity = "4"))
            val live = engine.depth("SISE")
            val replayed = MatchingEngine()
            replayed.replay(engine.recordedEvents().filter { it is OrderSubmitted || it is OrderCancelled })
            replayed.depth("SISE").asks.map { it.price to it.quantity } shouldBe live.asks.map { it.price to it.quantity }
            engine.shutdown()
            replayed.shutdown()
        }
    }

    test("same order stream is deterministic") {
        checkAll(25, arbStream()) { orders ->
            runBlocking {
                val a = MatchingEngine()
                val b = MatchingEngine()
                val tradesA = orders.flatMap { a.submit(it).trades.map { t -> listOf(t.price, t.quantity, t.buyOrderId, t.sellOrderId) } }
                val tradesB = orders.flatMap { b.submit(it).trades.map { t -> listOf(t.price, t.quantity, t.buyOrderId, t.sellOrderId) } }
                tradesA shouldBe tradesB
                a.depth("THYAO").bids.map { it.price to it.quantity } shouldBe b.depth("THYAO").bids.map { it.price to it.quantity }
                a.shutdown()
                b.shutdown()
            }
        }
    }

    test("replay of submits produces identical trades") {
        runBlocking {
            val orders = listOf(
                Order(id = "s1", userId = "s", symbol = "EREGL", side = OrderSide.SELL, type = OrderType.LIMIT, price = "20", quantity = "10"),
                Order(id = "b1", userId = "b", symbol = "EREGL", side = OrderSide.BUY, type = OrderType.LIMIT, price = "21", quantity = "4")
            )
            val e1 = MatchingEngine()
            val t1 = orders.flatMap { e1.submit(it).trades.map { t -> t.id to t.quantity } }
            val e2 = MatchingEngine()
            e2.replay(orders.map { OrderSubmitted(aggregateId = it.symbol, order = it) })
            val t2 = e2.recordedEvents().filterIsInstance<TradeExecuted>().map { it.trade.id to it.trade.quantity }
            t1 shouldBe t2
            e1.shutdown()
            e2.shutdown()
        }
    }

    test("replay can return regenerated fills without retaining an in-memory event history") {
        runBlocking {
            val orders = listOf(
                Order(id = "replay-sell", userId = "s", symbol = "EREGL", side = OrderSide.SELL, type = OrderType.LIMIT, price = "20", quantity = "10"),
                Order(id = "replay-buy", userId = "b", symbol = "EREGL", side = OrderSide.BUY, type = OrderType.LIMIT, price = "21", quantity = "4")
            )
            val live = MatchingEngine()
            orders.forEach { live.submit(it) }
            val history = live.recordedEvents().filter { it is OrderSubmitted || it is OrderCancelled }
            val replayedEvents = mutableListOf<DomainEvent>()
            val recovery = MatchingEngine(eventBus = replayedEvents::add, keepRecordedEvents = false)

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
            val original = Order(id = "restored", userId = "u", clientOrderId = "client-1", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "300", quantity = "2")
            val engine = MatchingEngine()
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
                            engine.submit(
                                Order(
                                    id = "parallel-$index",
                                    userId = "account-$index",
                                    symbol = "THYAO",
                                    side = if (buy) OrderSide.BUY else OrderSide.SELL,
                                    type = OrderType.LIMIT,
                                    price = if (buy) "99" else "101",
                                    quantity = "1"
                                )
                            )
                        }
                    }.awaitAll()
                }
                val resting = engine.snapshot("THYAO")
                resting.size shouldBe 400
                engine.depth("THYAO").bids.sumOf { it.quantity.toBigDecimal() } shouldBe "200".toBigDecimal()
                engine.depth("THYAO").asks.sumOf { it.quantity.toBigDecimal() } shouldBe "200".toBigDecimal()

                coroutineScope {
                    resting.filterIndexed { index, _ -> index % 2 == 0 }.map { order ->
                        async(Dispatchers.Default) { engine.cancel("THYAO", order.id) }
                    }.awaitAll()
                }
                engine.snapshot("THYAO").size shouldBe 200
                engine.isHealthy() shouldBe true
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
                    val restingAsk = Order(
                        id = "race-ask-$round",
                        userId = "seller-$round",
                        symbol = "THYAO",
                        side = OrderSide.SELL,
                        type = OrderType.LIMIT,
                        price = "100",
                        quantity = "1"
                    )
                    engine.submit(restingAsk).accepted shouldBe true

                    val (cancelled, incoming) = coroutineScope {
                        val cancel = async(Dispatchers.Default) { engine.cancel("THYAO", restingAsk.id) }
                        val submit = async(Dispatchers.Default) {
                            engine.submit(
                                Order(
                                    id = "race-bid-$round",
                                    userId = "buyer-$round",
                                    symbol = "THYAO",
                                    side = OrderSide.BUY,
                                    type = OrderType.LIMIT,
                                    price = "100",
                                    quantity = "1"
                                )
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
                        engine.snapshot("THYAO").none { it.id == restingAsk.id || it.id == incoming.order.id } shouldBe true
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

private fun arbStream(): Arb<List<Order>> = arbitrary {
    val n = Arb.int(2..24).bind()
    (0 until n).map { i ->
        val buy = i % 2 == 0
        Order(
            id = "ord-$i",
            userId = if (buy) "b$i" else "s$i",
            symbol = "THYAO",
            side = if (buy) OrderSide.BUY else OrderSide.SELL,
            type = OrderType.LIMIT,
            price = (300 + (i % 5)).toString(),
            quantity = ((i % 7) + 1).toString()
        )
    }
}
