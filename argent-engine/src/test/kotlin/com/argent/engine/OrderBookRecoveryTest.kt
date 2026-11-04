package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class OrderBookRecoveryTest : FunSpec({
    test("restore, cancel rollback, clear and sequence maintain book indexes") {
        val book = OrderBook("THYAO")
        val restored =
            Order(
                userId = "buyer",
                symbol = "THYAO",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = "300",
                quantity = "2",
                sequence = 7,
            )
        book.restoreResting(restored)
        book.restoreResting(restored)
        book.nextSequence() shouldBe 8
        book.midPrice() shouldBe null
        book.snapshotOrders().single().id shouldBe restored.id

        val undo = mutableListOf<() -> Unit>()
        book.cancel(restored.id, undo)?.status shouldBe OrderStatus.CANCELLED
        book.bestBid() shouldBe null
        book.rollback(undo)
        book.bestBid() shouldBe BigDecimal("300")
        book.cancel("absent") shouldBe null
        book.clear()
        book.bestBid() shouldBe null
        book.snapshotOrders() shouldBe emptyList()
    }

    test("invalid limit order is rejected without mutation and rollback restores a new resting level") {
        val book = OrderBook("THYAO")
        val invalid =
            book.match(
                Order(
                    userId = "buyer",
                    symbol = "THYAO",
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    price = null,
                    quantity = "1",
                ),
            )
        invalid.order.status shouldBe OrderStatus.REJECTED
        invalid.reason shouldBe "Limit price required"
        book.snapshotOrders() shouldBe emptyList()

        val undo = mutableListOf<() -> Unit>()
        val order =
            Order(
                userId = "buyer",
                symbol = "THYAO",
                side = OrderSide.BUY,
                type = OrderType.LIMIT,
                price = "300",
                quantity = "1",
            )
        book.match(order, undo)
        book.rollback(undo)
        book.bestBid() shouldBe null
        book.snapshotOrders() shouldBe emptyList()
    }
})
