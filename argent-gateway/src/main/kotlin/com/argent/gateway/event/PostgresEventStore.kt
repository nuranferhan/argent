package com.argent.gateway.event

import com.argent.common.events.DomainEvent
import com.argent.common.events.EventCodec
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class PostgresEventStore(private val db: Database) {
    object Events : Table("domain_events") {
        val sequence = long("sequence").autoIncrement()
        val eventId = varchar("event_id", 64).uniqueIndex()
        val aggregateId = varchar("aggregate_id", 64)
        val type = varchar("type", 64)
        val payload = text("payload")
        val occurredAt = long("occurred_at")
        override val primaryKey = PrimaryKey(sequence)
    }

    fun initSchema() {
        transaction(db) { SchemaUtils.createMissingTablesAndColumns(Events) }
    }

    fun append(event: DomainEvent) {
        transaction(db) {
            if (Events.selectAll().where { Events.eventId eq event.eventId }.count() > 0) return@transaction
            Events.insert {
                it[eventId] = event.eventId
                it[aggregateId] = event.aggregateId
                it[type] = event::class.simpleName ?: "DomainEvent"
                it[payload] = EventCodec.encode(event)
                it[occurredAt] = event.occurredAt
            }
        }
    }

    fun loadAll(): List<DomainEvent> = transaction(db) {
        Events.selectAll().orderBy(Events.sequence to SortOrder.ASC)
            .map { EventCodec.decode(it[Events.payload]) }
    }

    fun load(aggregateId: String): List<DomainEvent> = transaction(db) {
        Events.selectAll()
            .where { Events.aggregateId eq aggregateId }
            .orderBy(Events.sequence to SortOrder.ASC)
            .map { EventCodec.decode(it[Events.payload]) }
    }
}
