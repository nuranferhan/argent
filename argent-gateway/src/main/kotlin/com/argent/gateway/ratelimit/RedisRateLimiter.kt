package com.argent.gateway.ratelimit

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPooled

class RedisRateLimiter(
    private val redisUrl: String,
    private val limit: Int = 120,
    private val windowSeconds: Long = 60
) {
    private val log = LoggerFactory.getLogger(RedisRateLimiter::class.java)
    private val jedis: JedisPooled? = try {
        JedisPooled(java.net.URI(redisUrl)).also { it.ping() }
    } catch (e: Exception) {
        log.warn("Redis unavailable, rate-limit disabled: {}", e.message)
        null
    }

    fun ping(): Boolean {
        val client = jedis ?: return false
        return try {
            client.ping() == "PONG"
        } catch (_: Exception) {
            false
        }
    }

    fun allow(key: String, requestLimit: Int = limit, requestWindowSeconds: Long = windowSeconds): Boolean {
        require(requestLimit > 0)
        require(requestWindowSeconds > 0)
        val client = jedis ?: return false
        return try {
            val redisKey = "rl:$key"
            val count = client.eval(
                "local count = redis.call('INCR', KEYS[1]); if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return count",
                listOf(redisKey),
                listOf(requestWindowSeconds.toString())
            ) as Number
            count.toLong() <= requestLimit
        } catch (e: Exception) {
            log.debug("Rate limit check failed: {}", e.message)
            false
        }
    }

    fun close() {
        runCatching { jedis?.close() }
    }
}
