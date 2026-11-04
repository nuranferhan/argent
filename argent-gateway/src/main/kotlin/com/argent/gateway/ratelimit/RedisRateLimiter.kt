package com.argent.gateway.ratelimit

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled
import java.util.concurrent.ConcurrentHashMap

class RedisRateLimiter(
    private val redisUrl: String,
    private val limit: Int = 120,
    private val windowSeconds: Long = 60,
) {
    private val log = LoggerFactory.getLogger(RedisRateLimiter::class.java)

    private data class LocalBucket(var startedAtMillis: Long, var count: Int)

    private val localBuckets = ConcurrentHashMap<String, LocalBucket>()
    private val jedis: JedisPooled? =
        try {
            JedisPooled(java.net.URI(redisUrl)).also { it.ping() }
        } catch (e: Exception) {
            log.warn("Redis unavailable; using process-local rate-limit fallback: {}", e.message)
            null
        }

    fun ping(): Boolean {
        val client = jedis ?: return false
        return try {
            client.ping() == "PONG"
        } catch (failure: Exception) {
            log.warn("Redis health check failed: {}", failure.message)
            false
        }
    }

    fun allow(
        key: String,
        requestLimit: Int = limit,
        requestWindowSeconds: Long = windowSeconds,
    ): Boolean {
        require(requestLimit > 0)
        require(requestWindowSeconds > 0)
        val client = jedis ?: return allowLocal(key, requestLimit, requestWindowSeconds)
        return try {
            val redisKey = "rl:$key"
            val count =
                client.eval(
                    "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return count",
                    listOf(redisKey),
                    listOf(requestWindowSeconds.toString()),
                ) as Number
            count.toLong() <= requestLimit
        } catch (e: Exception) {
            log.warn("Redis rate-limit check failed; using process-local fallback: {}", e.message)
            allowLocal(key, requestLimit, requestWindowSeconds)
        }
    }

    private fun allowLocal(
        key: String,
        requestLimit: Int,
        requestWindowSeconds: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        val windowMillis = requestWindowSeconds * 1_000L
        val bucket =
            localBuckets.compute(key) { _, current ->
                if (current == null || now - current.startedAtMillis >= windowMillis) {
                    LocalBucket(now, 1)
                } else {
                    current.apply { count++ }
                }
            } ?: return true
        if (localBuckets.size > 10_000) {
            localBuckets.entries.removeIf { now - it.value.startedAtMillis > windowMillis * 2 }
        }
        return bucket.count <= requestLimit
    }

    fun close() {
        runCatching { jedis?.close() }
    }
}
