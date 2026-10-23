package com.argent.gateway.market

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import java.util.UUID

data class PriceAlert(
    val id: String,
    val userId: String,
    val symbol: String,
    val direction: String,
    val threshold: String,
    val state: String,
    val createdAt: Long,
    val triggeredAt: Long? = null,
    val triggeredPrice: String? = null
)

class PriceAlertStore(private val db: Database) {
    private object Alerts : Table("price_alerts") {
        val id = varchar("id", 36)
        val userId = varchar("user_id", 64)
        val symbol = varchar("symbol", 16)
        val direction = varchar("direction", 8)
        val threshold = varchar("threshold", 40)
        val state = varchar("state", 12)
        val createdAt = long("created_at")
        val triggeredAt = long("triggered_at").nullable()
        val triggeredPrice = varchar("triggered_price", 40).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    fun initSchema() = transaction(db) { SchemaUtils.createMissingTablesAndColumns(Alerts) }

    fun create(userId: String, symbol: String, direction: String, threshold: String): PriceAlert = transaction(db) {
        val alert = PriceAlert(UUID.randomUUID().toString(), userId, symbol, direction, threshold, "ACTIVE", System.currentTimeMillis())
        Alerts.insert {
            it[id] = alert.id; it[Alerts.userId] = alert.userId; it[Alerts.symbol] = alert.symbol
            it[Alerts.direction] = alert.direction; it[Alerts.threshold] = alert.threshold; it[state] = alert.state
            it[createdAt] = alert.createdAt; it[triggeredAt] = null; it[triggeredPrice] = null
        }
        alert
    }

    fun list(userId: String): List<PriceAlert> = transaction(db) {
        Alerts.selectAll().where { Alerts.userId eq userId }.map(::toAlert).sortedByDescending { it.createdAt }
    }

    fun activeSymbols(): List<String> = transaction(db) {
        Alerts.selectAll().where { Alerts.state eq "ACTIVE" }.map { it[Alerts.symbol] }.distinct()
    }

    fun delete(userId: String, id: String): Boolean = transaction(db) {
        Alerts.deleteWhere { (Alerts.id eq id) and (Alerts.userId eq userId) } > 0
    }

    fun trigger(symbol: String, price: String, now: Long = System.currentTimeMillis()): Int = transaction(db) {
        val current = price.toBigDecimal()
        var count = 0
        Alerts.selectAll().where { (Alerts.symbol eq symbol) and (Alerts.state eq "ACTIVE") }.forEach { row ->
            val threshold = row[Alerts.threshold].toBigDecimal()
            val reached = when (row[Alerts.direction]) { "ABOVE" -> current >= threshold; "BELOW" -> current <= threshold; else -> false }
            if (reached) count += Alerts.update({ (Alerts.id eq row[Alerts.id]) and (Alerts.state eq "ACTIVE") }) {
                it[state] = "TRIGGERED"; it[triggeredAt] = now; it[triggeredPrice] = price
            }
        }
        count
    }

    private fun toAlert(row: ResultRow) = PriceAlert(
        id = row[Alerts.id], userId = row[Alerts.userId], symbol = row[Alerts.symbol], direction = row[Alerts.direction],
        threshold = row[Alerts.threshold], state = row[Alerts.state], createdAt = row[Alerts.createdAt],
        triggeredAt = row[Alerts.triggeredAt], triggeredPrice = row[Alerts.triggeredPrice]
    )
}
