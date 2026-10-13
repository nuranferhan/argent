package com.argent.gateway.mm

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MarketMakerControlStore(private val db: Database) {
    object State : Table("market_maker_control") {
        val id = varchar("id", 32)
        val enabled = bool("enabled")
        val updatedAt = long("updated_at")
        override val primaryKey = PrimaryKey(id)
    }

    fun initSchema() = transaction(db) {
        SchemaUtils.createMissingTablesAndColumns(State)
        State.insertIgnore {
            it[id] = "default"
            it[enabled] = true
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    fun isEnabled(): Boolean = transaction(db) {
        State.selectAll().where { State.id eq "default" }.first()[State.enabled]
    }

    fun setEnabled(value: Boolean) = transaction(db) {
        val updated = State.update({ State.id eq "default" }) {
            it[enabled] = value
            it[updatedAt] = System.currentTimeMillis()
        }
        check(updated == 1) { "Market maker control state is not initialized" }
    }
}
