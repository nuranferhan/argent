package com.argent.ledger

import org.jetbrains.exposed.sql.Table

internal object Balances : Table("balances") {
        val userId = varchar("user_id", 64)
        val asset = varchar("asset", 32)
        val available = decimal("available", 36, 18)
        val held = decimal("held", 36, 18)
        val version = long("version")
        override val primaryKey = PrimaryKey(userId, asset)
    }

internal object JournalEntries : Table("journal_entries") {
        val id = varchar("id", 64)
        val txId = varchar("tx_id", 64)
        val account = varchar("account", 64)
        val asset = varchar("asset", 32)
        val debit = decimal("debit", 36, 18)
        val credit = decimal("credit", 36, 18)
        val description = varchar("description", 256)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(id)
    }

internal object IdempotencyKeys : Table("idempotency_keys") {
        val key = varchar("key", 256)
        val result = text("result")
        val requestHash = varchar("request_hash", 128).nullable()
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(key)
    }

internal object OrderHolds : Table("order_holds") {
        val orderId = varchar("order_id", 64)
        val userId = varchar("user_id", 64)
        val asset = varchar("asset", 32)
        val remaining = decimal("remaining", 36, 18)
        override val primaryKey = PrimaryKey(orderId)
    }

internal object Positions : Table("positions") {
        val userId = varchar("user_id", 64)
        val symbol = varchar("symbol", 32)
        val quantity = decimal("quantity", 36, 18)
        val avgEntry = decimal("avg_entry", 36, 18)
        val realizedPnl = decimal("realized_pnl", 36, 18)
        override val primaryKey = PrimaryKey(userId, symbol)
    }

internal object Settlements : Table("settlements") {
        val tradeId = varchar("trade_id", 64)
        val createdAt = long("created_at")
        override val primaryKey = PrimaryKey(tradeId)
    }
