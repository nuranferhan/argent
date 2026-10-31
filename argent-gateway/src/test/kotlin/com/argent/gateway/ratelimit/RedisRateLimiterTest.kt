package com.argent.gateway.ratelimit

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RedisRateLimiterTest : StringSpec({
    "keeps rate limiting available when Redis cannot be reached" {
        val limiter = RedisRateLimiter("redis://127.0.0.1:1", limit = 2, windowSeconds = 60)
        try {
            limiter.allow("ip:a") shouldBe true
            limiter.allow("ip:a") shouldBe true
            limiter.allow("ip:a") shouldBe false
            limiter.allow("ip:b") shouldBe true
        } finally {
            limiter.close()
        }
    }
})
