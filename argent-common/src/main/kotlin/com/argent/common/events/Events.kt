package com.argent.common.events

import com.argent.common.domain.Order
import com.argent.common.domain.Trade
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
sealed class DomainEvent {
    abstract val eventId: String
    abstract val occurredAt: Long
    abstract val aggregateId: String
    abstract val sequence: Long
}

@Serializable
@SerialName("OrderSubmitted")
data class OrderSubmitted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val order: Order
) : DomainEvent()

@Serializable
@SerialName("OrderAccepted")
data class OrderAccepted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val order: Order
) : DomainEvent()

@Serializable
@SerialName("OrderRejected")
data class OrderRejected(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val orderId: String,
    val reason: String
) : DomainEvent()

@Serializable
@SerialName("OrderCancelled")
data class OrderCancelled(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val orderId: String,
    val reason: String = "User cancelled"
) : DomainEvent()

@Serializable
@SerialName("TradeExecuted")
data class TradeExecuted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val trade: Trade
) : DomainEvent()

@Serializable
@SerialName("FundsHeld")
data class FundsHeld(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val orderId: String,
    val asset: String,
    val amount: String
) : DomainEvent()

@Serializable
@SerialName("FundsReleased")
data class FundsReleased(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val orderId: String,
    val asset: String,
    val amount: String
) : DomainEvent()

@Serializable
@SerialName("SettlementCompleted")
data class SettlementCompleted(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val tradeId: String
) : DomainEvent()

@Serializable
@SerialName("MarginCall")
data class MarginCall(
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: Long = Instant.now().toEpochMilli(),
    override val aggregateId: String,
    override val sequence: Long = 0,
    val userId: String,
    val marginRatio: String,
    val equity: String,
    val quoteAsset: String = ""
) : DomainEvent()
