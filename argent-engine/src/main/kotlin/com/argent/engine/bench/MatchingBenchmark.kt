package com.argent.engine.bench

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderType
import com.argent.engine.MatchingEngine
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/**
 * Standalone throughput probe (JMH-style timed loop).
 * Run: gradlew :argent-engine:run
 */
fun main() = runBlocking {
    val result = MatchingBenchmark.run()
    println("ARGENT matching benchmark")
    println("  orders      : ${result.orders}")
    println("  trades      : ${result.trades}")
    println("  elapsed ms  : ${result.elapsedMs}")
    println("  orders/sec  : ${result.ordersPerSecond}")
    println("  trades/sec  : ${result.tradesPerSecond}")
}

object MatchingBenchmark {
    data class Result(
        val orders: Int,
        val trades: Int,
        val elapsedMs: Long,
        val ordersPerSecond: Long,
        val tradesPerSecond: Long
    )

    suspend fun run(orderCount: Int = 50_000): Result {
        val engine = MatchingEngine()
        var trades = 0
        val warmup = 2_000
        repeat(warmup) { i ->
            engine.submit(limit("WARM", i, i % 2 == 0))
        }
        engine.shutdown()

        val live = MatchingEngine()
        val nanos = measureNanoTime {
            repeat(orderCount) { i ->
                val result = live.submit(limit("BTCUSDT", i, i % 2 == 0))
                trades += result.trades.size
            }
        }
        live.shutdown()
        val ms = TimeUnit.NANOSECONDS.toMillis(nanos).coerceAtLeast(1)
        return Result(
            orders = orderCount,
            trades = trades,
            elapsedMs = ms,
            ordersPerSecond = orderCount * 1000L / ms,
            tradesPerSecond = trades * 1000L / ms
        )
    }

    private fun limit(symbol: String, i: Int, buy: Boolean): Order {
        val mid = BigDecimal("100000")
        val tick = BigDecimal(i % 21 - 10)
        val price = mid + tick
        return Order(
            userId = if (buy) "buyer-${i % 64}" else "seller-${i % 64}",
            symbol = symbol,
            side = if (buy) OrderSide.BUY else OrderSide.SELL,
            type = OrderType.LIMIT,
            price = price.toPlainString(),
            quantity = "0.01"
        )
    }
}
