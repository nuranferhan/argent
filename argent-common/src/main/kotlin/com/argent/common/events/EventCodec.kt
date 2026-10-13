package com.argent.common.events

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object EventCodec {
    val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            polymorphic(DomainEvent::class) {
                subclass(OrderSubmitted::class)
                subclass(OrderAccepted::class)
                subclass(OrderRejected::class)
                subclass(OrderCancelled::class)
                subclass(TradeExecuted::class)
                subclass(FundsHeld::class)
                subclass(FundsReleased::class)
                subclass(SettlementCompleted::class)
                subclass(MarginCall::class)
            }
        }
    }

    fun encode(event: DomainEvent): String = json.encodeToString(DomainEvent.serializer(), event)

    fun decode(payload: String): DomainEvent = json.decodeFromString(DomainEvent.serializer(), payload)
}
