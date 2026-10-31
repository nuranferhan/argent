package com.argent.gateway.event

import com.argent.common.events.DomainEvent
import com.argent.common.events.EventCodec
import com.argent.common.domain.Order
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
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
        init { index("ix_domain_events_aggregate_sequence", false, aggregateId, sequence) }
    }

    object BookSnapshots : Table("order_book_snapshots") {
        val aggregateId = varchar("aggregate_id", 64)
        val lastEventSequence = long("last_event_sequence")
        val orders = text("orders_json")
        val savedAt = long("saved_at")
        override val primaryKey = PrimaryKey(aggregateId)
    }

    data class BookSnapshot(val aggregateId: String, val lastEventSequence: Long, val orders: List<Order>)
    private val snapshotJson = Json { ignoreUnknownKeys = true }

    fun initSchema() {
        transaction(db) { SchemaUtils.createMissingTablesAndColumns(Events, BookSnapshots) }
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

    /** SQL-filtered recovery reads avoid deserializing the complete event journal on every start. */
    fun loadForAggregates(aggregateIds: Set<String>): List<DomainEvent> {
        if (aggregateIds.isEmpty()) return emptyList()
        return transaction(db) {
            Events.selectAll().where { Events.aggregateId inList aggregateIds }
                .orderBy(Events.sequence to SortOrder.ASC)
                .map { EventCodec.decode(it[Events.payload]) }
        }
    }

    /** Reads only events newer than a persisted order-book checkpoint for one aggregate. */
    fun loadForAggregateAfter(aggregateId: String, sequence: Long): List<DomainEvent> = transaction(db) {
        Events.selectAll().where { (Events.aggregateId eq aggregateId) and (Events.sequence greater sequence) }
            .orderBy(Events.sequence to SortOrder.ASC)
            .map { EventCodec.decode(it[Events.payload]) }
    }

    fun loadBookSnapshot(aggregateId: String): BookSnapshot? = transaction(db) {
        BookSnapshots.selectAll().where { BookSnapshots.aggregateId eq aggregateId }.firstOrNull()?.let { row ->
            BookSnapshot(
                aggregateId = row[BookSnapshots.aggregateId],
                lastEventSequence = row[BookSnapshots.lastEventSequence],
                orders = snapshotJson.decodeFromString(ListSerializer(Order.serializer()), row[BookSnapshots.orders])
            )
        }
    }

    /** Stores the current resting orders and the event-journal watermark atomically. */
    fun saveBookSnapshot(aggregateId: String, orders: List<Order>) {
        transaction(db) {
            val maxSequence = Events.sequence.max()
            val watermark = Events.select(maxSequence)
                .where { Events.aggregateId eq aggregateId }
                .singleOrNull()?.get(maxSequence) ?: 0L
            val encoded = snapshotJson.encodeToString(ListSerializer(Order.serializer()), orders)
            val exists = BookSnapshots.selectAll().where { BookSnapshots.aggregateId eq aggregateId }.count() > 0
            if (exists) {
                BookSnapshots.update({ BookSnapshots.aggregateId eq aggregateId }) {
                    it[lastEventSequence] = watermark
                    it[BookSnapshots.orders] = encoded
                    it[savedAt] = System.currentTimeMillis()
                }
            } else {
                BookSnapshots.insert {
                    it[BookSnapshots.aggregateId] = aggregateId
                    it[lastEventSequence] = watermark
                    it[BookSnapshots.orders] = encoded
                    it[savedAt] = System.currentTimeMillis()
                }
            }
        }
    }

    fun loadByType(eventType: String): List<DomainEvent> = transaction(db) {
        Events.selectAll().where { Events.type eq eventType }
            .orderBy(Events.sequence to SortOrder.ASC)
            .map { EventCodec.decode(it[Events.payload]) }
    }
}
