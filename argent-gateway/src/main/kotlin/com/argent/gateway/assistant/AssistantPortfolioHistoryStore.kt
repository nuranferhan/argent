package com.argent.gateway.assistant

import com.argent.common.domain.PortfolioSnapshot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.ZoneId

/** Keeps a small, durable intraday baseline for the read-only assistant. */
class AssistantPortfolioHistoryStore(private val db: Database) {
    object Snapshots : Table("assistant_portfolio_snapshots") {
        val accountId = varchar("account_id", 64)
        val sampledAt = long("sampled_at")
        val payload = text("payload")
        override val primaryKey = PrimaryKey(accountId, sampledAt)
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Baseline(val snapshot: PortfolioSnapshot, val sampledAt: Long)

    fun initSchema() = transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(Snapshots)
    }

    /** Records at most one sample per account per five-minute bucket, then returns the first sample today. */
    fun recordAndGetDailyBaseline(accountId: String, snapshot: PortfolioSnapshot, now: Long): Baseline? = transaction(db) {
        val bucket = now / SAMPLE_INTERVAL_MS * SAMPLE_INTERVAL_MS
        Snapshots.insertIgnore {
            it[Snapshots.accountId] = accountId
            it[sampledAt] = bucket
            it[payload] = json.encodeToString(snapshot)
        }
        val zone = ZoneId.of("Europe/Istanbul")
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        Snapshots.selectAll()
            .where { (Snapshots.accountId eq accountId) and (Snapshots.sampledAt greaterEq dayStart) }
            .orderBy(Snapshots.sampledAt to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.let { Baseline(json.decodeFromString(it[Snapshots.payload]), it[Snapshots.sampledAt]) }
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 5 * 60 * 1000L
    }
}
