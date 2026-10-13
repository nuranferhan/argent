package com.argent.engine.bench;

import com.argent.common.domain.Order;
import com.argent.common.domain.OrderSide;
import com.argent.common.domain.OrderType;
import com.argent.engine.OrderBook;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Benchmarks the synchronous matching core without gateway, persistence, or network overhead. */
@State(Scope.Thread)
public class OrderBookJmhBenchmark {
    private OrderBook book;
    private long sequence;

    @Setup(Level.Invocation)
    public void prepareCrossingBook() {
        book = new OrderBook("BTCUSDT");
        sequence = 0;
        for (int i = 0; i < 100; i++) {
            book.match(new Order(
                "maker-" + i, null, "seller-" + i, "BTCUSDT", OrderSide.SELL,
                OrderType.LIMIT, Integer.toString(100 + i), "1", "0", "1",
                com.argent.common.domain.OrderStatus.PENDING, 1L, 1L, i + 1L
            ));
        }
    }

    @Benchmark
    public Object matchAcrossOneHundredPriceLevels() {
        long id = ++sequence;
        return book.match(new Order(
            "taker-" + id, null, "buyer-" + id, "BTCUSDT", OrderSide.BUY,
            OrderType.LIMIT, "200", "100", "0", "100",
            com.argent.common.domain.OrderStatus.PENDING, 2L, 2L, id + 100L
        ));
    }

    @Benchmark
    public Object addNonCrossingLimitOrder() {
        long id = ++sequence;
        return book.match(new Order(
            "bid-" + id, null, "buyer-" + id, "BTCUSDT", OrderSide.BUY,
            OrderType.LIMIT, "99", "1", "0", "1",
            com.argent.common.domain.OrderStatus.PENDING, 2L, 2L, id + 100L
        ));
    }
}
