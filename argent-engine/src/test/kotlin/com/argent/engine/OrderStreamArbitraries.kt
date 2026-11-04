package com.argent.engine

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int

internal fun arbOrderStream(): Arb<List<Order>> =
    arbitrary {
        val count = Arb.int(2..24).bind()
        (0 until count).map { index ->
            val buy = index % 2 == 0
            Order(
                id = "ord-$index",
                userId = if (buy) "b$index" else "s$index",
                symbol = "THYAO",
                side = if (buy) OrderSide.BUY else OrderSide.SELL,
                type = OrderType.LIMIT,
                price = (300 + (index % 5)).toString(),
                quantity = ((index % 7) + 1).toString(),
            )
        }
    }
