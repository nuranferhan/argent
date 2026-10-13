package com.argent.gateway.order

import com.argent.common.domain.Order
import com.argent.common.domain.OrderSide
import com.argent.common.domain.OrderStatus
import com.argent.common.domain.OrderType
import com.argent.common.domain.Trade
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant

class OrderRepository(private val db: Database) {

    object Orders : Table("orders") {
        val id = varchar("id", 64)
        val clientOrderId = varchar("client_order_id", 128).nullable()
        val userId = varchar("user_id", 64)
        val symbol = varchar("symbol", 32)
        val side = varchar("side", 8)
        val type = varchar("type", 16)
        val price = varchar("price", 64).nullable()
        val quantity = varchar("quantity", 64)
        val filledQuantity = varchar("filled_quantity", 64)
        val remainingQuantity = varchar("remaining_quantity", 64)
        val status = varchar("status", 32)
        val createdAt = long("created_at")
        val updatedAt = long("updated_at")
        val sequence = long("sequence").default(0)
        override val primaryKey = PrimaryKey(id)
    }

    object Trades : Table("trades") {
        val id = varchar("id", 64)
        val symbol = varchar("symbol", 32)
        val price = varchar("price", 64)
        val quantity = varchar("quantity", 64)
        val buyOrderId = varchar("buy_order_id", 64)
        val sellOrderId = varchar("sell_order_id", 64)
        val buyUserId = varchar("buy_user_id", 64)
        val sellUserId = varchar("sell_user_id", 64)
        val makerOrderId = varchar("maker_order_id", 64)
        val takerOrderId = varchar("taker_order_id", 64)
        val executedAt = long("executed_at")
        override val primaryKey = PrimaryKey(id)
    }

    fun initSchema() {
        val isPostgres = db.vendor.contains("postgres", ignoreCase = true)
        transaction(db) {
            // Preserve rows from earlier schemas before adding required maker/taker columns.
            if (isPostgres) {
                exec("""
                    DO ${'$'}${'$'}
                    BEGIN
                        IF to_regclass('trades') IS NOT NULL THEN
                            ALTER TABLE trades ADD COLUMN IF NOT EXISTS maker_order_id VARCHAR(64);
                            ALTER TABLE trades ADD COLUMN IF NOT EXISTS taker_order_id VARCHAR(64);
                            UPDATE trades SET maker_order_id = 'LEGACY_UNASSIGNED' WHERE maker_order_id IS NULL;
                            UPDATE trades SET taker_order_id = 'LEGACY_UNASSIGNED' WHERE taker_order_id IS NULL;
                            ALTER TABLE trades ALTER COLUMN maker_order_id SET NOT NULL;
                            ALTER TABLE trades ALTER COLUMN taker_order_id SET NOT NULL;
                        END IF;
                    END
                    ${'$'}${'$'}
                """.trimIndent())
            }
            SchemaUtils.createMissingTablesAndColumns(Orders, Trades)
            exec(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_user_client
                ON orders (user_id, client_order_id)
                """.trimIndent()
            )
        }
    }

    fun save(order: Order) = transaction(db) {
        val existing = Orders.selectAll().where { Orders.id eq order.id }.count() > 0
        if (existing) {
            Orders.update({ Orders.id eq order.id }) {
                it[filledQuantity] = order.filledQuantity
                it[remainingQuantity] = order.remainingQuantity
                it[status] = order.status.name
                it[updatedAt] = Instant.now().toEpochMilli()
                it[sequence] = order.sequence
            }
        } else {
            Orders.insert {
                it[id] = order.id
                it[clientOrderId] = order.clientOrderId
                it[userId] = order.userId
                it[symbol] = order.symbol
                it[side] = order.side.name
                it[type] = order.type.name
                it[price] = order.price
                it[quantity] = order.quantity
                it[filledQuantity] = order.filledQuantity
                it[remainingQuantity] = order.remainingQuantity
                it[status] = order.status.name
                it[createdAt] = order.createdAt
                it[updatedAt] = order.updatedAt
                it[sequence] = order.sequence
            }
        }
    }

    fun saveTrade(trade: Trade) = transaction(db) {
        val exists = Trades.selectAll().where { Trades.id eq trade.id }.count() > 0
        if (exists) return@transaction
        Trades.insert {
            it[id] = trade.id
            it[symbol] = trade.symbol
            it[price] = trade.price
            it[quantity] = trade.quantity
            it[buyOrderId] = trade.buyOrderId
            it[sellOrderId] = trade.sellOrderId
            it[buyUserId] = trade.buyUserId
            it[sellUserId] = trade.sellUserId
            it[makerOrderId] = trade.makerOrderId
            it[takerOrderId] = trade.takerOrderId
            it[executedAt] = trade.executedAt
        }
    }

    fun findById(orderId: String): Order? = transaction(db) {
        Orders.selectAll().where { Orders.id eq orderId }.map { rowToOrder(it) }.firstOrNull()
    }

    fun findTrade(tradeId: String): Trade? = transaction(db) {
        Trades.selectAll().where { Trades.id eq tradeId }.map { rowToTrade(it) }.firstOrNull()
    }

    fun filledFor(orderId: String): BigDecimal = transaction(db) {
        Trades.selectAll()
            .where { (Trades.buyOrderId eq orderId) or (Trades.sellOrderId eq orderId) }
            .fold(BigDecimal.ZERO) { acc, row -> acc + BigDecimal(row[Trades.quantity]) }
    }

    fun findByClientOrderId(userId: String, clientOrderId: String): Order? = transaction(db) {
        Orders.selectAll()
            .where { (Orders.userId eq userId) and (Orders.clientOrderId eq clientOrderId) }
            .map { rowToOrder(it) }
            .firstOrNull()
    }

    fun findActive(): List<Order> = transaction(db) {
        Orders.selectAll()
            .where { Orders.status inList listOf(OrderStatus.ACCEPTED.name, OrderStatus.PARTIALLY_FILLED.name) }
            .orderBy(Orders.createdAt to SortOrder.ASC, Orders.sequence to SortOrder.ASC, Orders.id to SortOrder.ASC)
            .map { rowToOrder(it) }
    }

    fun findActiveByUser(userId: String): List<Order> = transaction(db) {
        Orders.selectAll()
            .where {
                (Orders.userId eq userId) and
                    (Orders.status inList listOf(OrderStatus.ACCEPTED.name, OrderStatus.PARTIALLY_FILLED.name))
            }
            .orderBy(Orders.createdAt to SortOrder.ASC, Orders.sequence to SortOrder.ASC, Orders.id to SortOrder.ASC)
            .map { rowToOrder(it) }
    }

    fun findPending(): List<Order> = transaction(db) {
        Orders.selectAll()
            .where { Orders.status eq OrderStatus.PENDING.name }
            .orderBy(Orders.createdAt to SortOrder.ASC, Orders.sequence to SortOrder.ASC, Orders.id to SortOrder.ASC)
            .map { rowToOrder(it) }
    }

    fun findByUser(userId: String, limit: Int = 100): List<Order> = transaction(db) {
        Orders.selectAll()
            .where { Orders.userId eq userId }
            .orderBy(Orders.createdAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { rowToOrder(it) }
    }

    fun findTrades(symbol: String? = null, userId: String? = null, limit: Int = 100): List<Trade> = transaction(db) {
        val query = when {
            symbol != null && userId != null -> Trades.selectAll().where {
                (Trades.symbol eq symbol) and ((Trades.buyUserId eq userId) or (Trades.sellUserId eq userId))
            }
            symbol != null -> Trades.selectAll().where { Trades.symbol eq symbol }
            userId != null -> Trades.selectAll().where {
                (Trades.buyUserId eq userId) or (Trades.sellUserId eq userId)
            }
            else -> Trades.selectAll()
        }
        query.orderBy(Trades.executedAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { rowToTrade(it) }
    }

    private fun rowToOrder(row: org.jetbrains.exposed.sql.ResultRow) = Order(
        id = row[Orders.id],
        clientOrderId = row[Orders.clientOrderId],
        userId = row[Orders.userId],
        symbol = row[Orders.symbol],
        side = OrderSide.valueOf(row[Orders.side]),
        type = OrderType.valueOf(row[Orders.type]),
        price = row[Orders.price],
        quantity = row[Orders.quantity],
        filledQuantity = row[Orders.filledQuantity],
        remainingQuantity = row[Orders.remainingQuantity],
        status = OrderStatus.valueOf(row[Orders.status]),
        createdAt = row[Orders.createdAt],
        updatedAt = row[Orders.updatedAt],
        sequence = row[Orders.sequence]
    )

    private fun rowToTrade(row: org.jetbrains.exposed.sql.ResultRow) = Trade(
        id = row[Trades.id],
        symbol = row[Trades.symbol],
        price = row[Trades.price],
        quantity = row[Trades.quantity],
        buyOrderId = row[Trades.buyOrderId],
        sellOrderId = row[Trades.sellOrderId],
        buyUserId = row[Trades.buyUserId],
        sellUserId = row[Trades.sellUserId],
        makerOrderId = row[Trades.makerOrderId],
        takerOrderId = row[Trades.takerOrderId],
        executedAt = row[Trades.executedAt]
    )
}
