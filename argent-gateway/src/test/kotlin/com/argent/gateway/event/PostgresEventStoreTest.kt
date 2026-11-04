package com.argent.gateway.event

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import com.argent.common.events.OrderSubmitted
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database

class PostgresEventStoreTest : StringSpec({
    "checkpoint retains resting orders and limits recovery to events after its watermark" {
        val database =
            Database.connect(
                "jdbc:h2:mem:checkpoint_${System.nanoTime()};DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        val store = PostgresEventStore(database).also { it.initSchema() }
        val first =
            Order(
                userId = "seller",
                symbol = "THYAO",
                side = OrderSide.SELL,
                type = OrderType.LIMIT,
                price = "300",
                quantity = "2",
            )
        store.append(OrderSubmitted(aggregateId = "THYAO", sequence = 1, order = first))
        store.saveBookSnapshot("THYAO", listOf(first))
        val checkpoint = store.loadBookSnapshot("THYAO") ?: error("Expected the saved checkpoint")
        val next =
            Order(
                userId = "buyer",
                symbol = "THYAO",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = "299",
                quantity = "1",
            )
        store.append(OrderSubmitted(aggregateId = "THYAO", sequence = 2, order = next))

        checkpoint.orders.map { it.id } shouldBe listOf(first.id)
        store.loadForAggregateAfter("THYAO", checkpoint.lastEventSequence)
            .map { it.aggregateId } shouldBe listOf("THYAO")
        store.loadForAggregateAfter("THYAO", Long.MAX_VALUE) shouldBe emptyList()
    }
})
