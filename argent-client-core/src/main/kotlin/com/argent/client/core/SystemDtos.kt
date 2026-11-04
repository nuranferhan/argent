package com.argent.client.core

import kotlinx.serialization.Serializable

@Serializable
data class DepositRequest(
    val userId: String,
    val asset: String,
    val amount: String,
    val idempotencyKey: String? = null,
)

@Serializable
data class WithdrawRequest(
    val asset: String,
    val amount: String,
    val idempotencyKey: String? = null,
)

@Serializable
data class HealthDto(
    val status: String,
    val invariantOk: Boolean,
    val redis: Boolean,
    val postgres: Boolean,
    val rabbit: Boolean = false,
    val matchingEngine: Boolean = true,
    val executionMode: String = "INTERNAL_MATCHING",
    val cryptoMarketData: String = "CONNECTING",
    val bistMarketData: String = "UNAVAILABLE",
)
