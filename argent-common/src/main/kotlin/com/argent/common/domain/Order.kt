package com.argent.common.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Serializable
enum class OrderSide { BUY, SELL }

@Serializable
enum class OrderType { LIMIT, MARKET, IOC, FOK }

@Serializable
enum class OrderStatus {
    PENDING,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELLED,
    REJECTED,
    EXPIRED
}

@Serializable
data class Order(
    val id: String = UUID.randomUUID().toString(),
    val clientOrderId: String? = null,
    val userId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: String? = null,
    val quantity: String,
    val filledQuantity: String = "0",
    val remainingQuantity: String = quantity,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    val sequence: Long = 0
) {
    fun priceBd(): BigDecimal = price?.let { BigDecimal(it) } ?: BigDecimal.ZERO
    fun quantityBd(): BigDecimal = BigDecimal(quantity)
    fun filledBd(): BigDecimal = BigDecimal(filledQuantity)
    fun remainingBd(): BigDecimal = BigDecimal(remainingQuantity)
    fun isActive(): Boolean = status == OrderStatus.ACCEPTED || status == OrderStatus.PARTIALLY_FILLED
}

@Serializable
data class Trade(
    val id: String = UUID.randomUUID().toString(),
    val symbol: String,
    val price: String,
    val quantity: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val makerOrderId: String,
    val takerOrderId: String,
    val executedAt: Long = Instant.now().toEpochMilli()
) {
    fun priceBd(): BigDecimal = BigDecimal(price)
    fun quantityBd(): BigDecimal = BigDecimal(quantity)
    fun notional(): BigDecimal = priceBd().multiply(quantityBd())
}
