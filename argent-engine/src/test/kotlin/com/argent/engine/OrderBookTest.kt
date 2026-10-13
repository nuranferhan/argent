package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal

class OrderBookTest : FunSpec({

    test("simple limit match – buy takes sell at maker price") {
        val book = OrderBook("BTCUSDT")
        val sell = Order(
            userId = "seller",
            symbol = "BTCUSDT",
            side = OrderSide.SELL,
            type = OrderType.LIMIT,
            price = "100000",
            quantity = "0.5"
        )
        book.match(sell)

        val buy = Order(
            userId = "buyer",
            symbol = "BTCUSDT",
            side = OrderSide.BUY,
            type = OrderType.LIMIT,
            price = "100000",
            quantity = "0.3"
        )
        val result = book.match(buy)

        result.trades.size shouldBe 1
        result.trades[0].price shouldBe "100000"
        result.trades[0].quantity shouldBe "0.3"
        book.bestAsk() shouldBe BigDecimal("100000")
        book.depth(5).asks[0].quantity shouldBe "0.2"
    }

    test("price-time priority – earlier order fills first") {
        val book = OrderBook("THYAO")
        book.match(Order(userId = "s1", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "300", quantity = "100"))
        book.match(Order(userId = "s2", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "300", quantity = "50"))

        val result = book.match(
            Order(userId = "b1", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.LIMIT, price = "300", quantity = "120")
        )

        result.trades.size shouldBe 2
        result.trades[0].sellUserId shouldBe "s1"
        result.trades[0].quantity shouldBe "100"
        result.trades[1].sellUserId shouldBe "s2"
        result.trades[1].quantity shouldBe "20"
    }

    test("no cross – order rests") {
        val book = OrderBook("BTCUSDT")
        val result = book.match(
            Order(userId = "b", symbol = "BTCUSDT", side = OrderSide.BUY, type = OrderType.LIMIT, price = "99000", quantity = "1")
        )
        result.trades shouldBe emptyList()
        book.bestBid() shouldBe BigDecimal("99000")
        book.bestAsk() shouldBe null
    }

    test("cancel removes order") {
        val book = OrderBook("ETHUSDT")
        val order = Order(userId = "u", symbol = "ETHUSDT", side = OrderSide.BUY, type = OrderType.LIMIT, price = "3000", quantity = "2")
        book.match(order)
        book.bestBid() shouldNotBe null
        val cancelled = book.cancel(order.id)
        cancelled shouldNotBe null
        cancelled!!.status shouldBe OrderStatus.CANCELLED
        book.bestBid() shouldBe null
    }

    test("market buy walks multiple ask levels") {
        val book = OrderBook("BTCUSDT")
        book.match(Order(userId = "s1", symbol = "BTCUSDT", side = OrderSide.SELL, type = OrderType.LIMIT, price = "100", quantity = "1"))
        book.match(Order(userId = "s2", symbol = "BTCUSDT", side = OrderSide.SELL, type = OrderType.LIMIT, price = "101", quantity = "1"))
        val result = book.match(
            Order(userId = "b", symbol = "BTCUSDT", side = OrderSide.BUY, type = OrderType.MARKET, quantity = "1.5")
        )
        result.trades.size shouldBe 2
        result.order.status shouldBe OrderStatus.FILLED
        result.trades[0].price shouldBe "100"
        result.trades[1].price shouldBe "101"
        result.trades[1].quantity shouldBe "0.5"
    }

    test("FOK rejects when not fully fillable and does not take liquidity") {
        val book = OrderBook("THYAO")
        book.match(Order(userId = "s", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "10", quantity = "5"))
        val result = book.match(
            Order(userId = "b", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.FOK, price = "10", quantity = "10")
        )
        result.trades shouldBe emptyList()
        result.order.status shouldBe OrderStatus.CANCELLED
        book.depth().asks[0].quantity shouldBe "5"
    }

    test("IOC fills partial and does not rest") {
        val book = OrderBook("THYAO")
        book.match(Order(userId = "s", symbol = "THYAO", side = OrderSide.SELL, type = OrderType.LIMIT, price = "10", quantity = "4"))
        val result = book.match(
            Order(userId = "b", symbol = "THYAO", side = OrderSide.BUY, type = OrderType.IOC, price = "10", quantity = "10")
        )
        result.trades.size shouldBe 1
        result.order.status shouldBe OrderStatus.CANCELLED
        result.order.filledQuantity shouldBe "4"
        book.bestAsk() shouldBe null
        book.bestBid() shouldBe null
    }

    test("self-trade is prevented") {
        val book = OrderBook("GARAN")
        book.match(Order(userId = "me", symbol = "GARAN", side = OrderSide.SELL, type = OrderType.LIMIT, price = "100", quantity = "10"))
        val result = book.match(
            Order(userId = "me", symbol = "GARAN", side = OrderSide.BUY, type = OrderType.LIMIT, price = "100", quantity = "10")
        )
        result.trades shouldBe emptyList()
        book.bestAsk() shouldBe BigDecimal("100")
        book.bestBid() shouldBe null
    }
})
