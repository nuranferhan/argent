package com.argent.gateway.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ClientAddressResolverTest : StringSpec({
    "ignores forwarded address from an untrusted peer" {
        ClientAddressResolver(listOf("10.0.0.0/8")).resolve("203.0.113.9", "198.51.100.1") shouldBe "203.0.113.9"
    }
    "uses client address behind a trusted proxy" {
        ClientAddressResolver(listOf("10.0.0.0/8")).resolve("10.0.0.8", "198.51.100.1") shouldBe "198.51.100.1"
    }
    "walks trusted proxy chain from the nearest peer outward" {
        ClientAddressResolver(listOf("10.0.0.0/8")).resolve("10.0.0.8", "198.51.100.1, 10.0.0.4") shouldBe "198.51.100.1"
    }
})
