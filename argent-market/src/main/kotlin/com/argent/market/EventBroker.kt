package com.argent.market

import com.argent.common.events.DomainEvent
import com.argent.common.events.EventCodec
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

class EventBroker(
    private val amqpUrl: String? = System.getenv("RABBITMQ_URL")
) {
    private val log = LoggerFactory.getLogger(EventBroker::class.java)
    private val listeners = CopyOnWriteArrayList<(DomainEvent) -> Unit>()
    private var connection: Connection? = null
    private var channel: Channel? = null

    fun start() {
        val url = amqpUrl ?: return
        try {
            val factory = ConnectionFactory().apply { setUri(url) }
            connection = factory.newConnection("argent-gateway")
            channel = connection!!.createChannel().also {
                it.exchangeDeclare("argent.events", "topic", true)
            }
            log.info("RabbitMQ event broker connected")
        } catch (e: Exception) {
            log.warn("RabbitMQ unavailable, continuing in-process only: {}", e.message)
        }
    }

    fun subscribe(listener: (DomainEvent) -> Unit) {
        listeners += listener
    }

    fun isConnected(): Boolean = connection?.isOpen == true && channel?.isOpen == true

    fun publish(event: DomainEvent) {
        listeners.forEach { runCatching { it(event) } }
        val ch = channel ?: return
        runCatching {
            val routing = event::class.simpleName ?: "event"
            ch.basicPublish(
                "argent.events",
                routing,
                null,
                EventCodec.encode(event).toByteArray(Charsets.UTF_8)
            )
        }.onFailure { log.debug("AMQP publish skipped: {}", it.message) }
    }

    fun close() {
        runCatching { channel?.close() }
        runCatching { connection?.close() }
    }
}
