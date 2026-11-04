package com.argent.engine

import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/** Bounded idempotency keys for client order submissions, partitioned by symbol. */
internal class ClientOrderRegistry {
    private data class Entry(val orderId: String, val insertedAt: Long)

    private class SymbolEntries {
        val entries = LinkedHashMap<String, Entry>()
    }

    private val bySymbol = ConcurrentHashMap<String, SymbolEntries>()

    fun putIfAbsent(
        symbol: String,
        key: String,
        orderId: String,
    ): String? {
        val bucket = bySymbol.computeIfAbsent(symbol) { SymbolEntries() }
        return synchronized(bucket) {
            evictExpired(bucket.entries)
            bucket.entries[key]?.orderId ?: run {
                bucket.entries[key] = Entry(orderId, System.currentTimeMillis())
                while (bucket.entries.size > MAX_CLIENT_ORDERS_PER_SYMBOL) {
                    bucket.entries.remove(bucket.entries.keys.first())
                }
                null
            }
        }
    }

    fun remove(
        symbol: String,
        key: String,
        orderId: String,
    ) {
        bySymbol[symbol]?.let { bucket ->
            synchronized(bucket) {
                if (bucket.entries[key]?.orderId == orderId) bucket.entries.remove(key)
                if (bucket.entries.isEmpty()) bySymbol.remove(symbol, bucket)
            }
        }
    }

    fun clear(symbol: String) {
        bySymbol.remove(symbol)
    }

    fun snapshot(symbol: String): Map<String, String> {
        val bucket = bySymbol[symbol] ?: return emptyMap()
        return synchronized(bucket) {
            evictExpired(bucket.entries)
            bucket.entries.mapValues { it.value.orderId }
        }
    }

    fun restore(
        symbol: String,
        snapshot: Map<String, String>,
    ) {
        if (snapshot.isEmpty()) {
            bySymbol.remove(symbol)
        } else {
            val bucket = bySymbol.computeIfAbsent(symbol) { SymbolEntries() }
            synchronized(bucket) {
                bucket.entries.clear()
                snapshot.entries.toList().takeLast(MAX_CLIENT_ORDERS_PER_SYMBOL).forEach { (key, value) ->
                    bucket.entries[key] = Entry(value, System.currentTimeMillis())
                }
            }
        }
    }

    private fun evictExpired(entries: LinkedHashMap<String, Entry>) {
        val expiredBefore = System.currentTimeMillis() - CLIENT_ORDER_TTL_MS
        // LinkedHashMap preserves insertion order, so expired entries form a
        // prefix. Evicting that prefix avoids a full per-symbol scan per submit.
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.insertedAt >= expiredBefore) break
            iterator.remove()
        }
    }

    private companion object {
        const val MAX_CLIENT_ORDERS_PER_SYMBOL = 10_000
        const val CLIENT_ORDER_TTL_MS = 24 * 60 * 60 * 1_000L
    }
}
