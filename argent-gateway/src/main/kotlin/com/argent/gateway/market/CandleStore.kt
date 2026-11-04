package com.argent.gateway.market

import com.argent.market.MarketCandle
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class CandleStore(private val db: Database) {
    object Candles : Table("market_candles") {
        val symbol = varchar("symbol", 32)
        val interval = varchar("candle_interval", 8)
        val openTime = long("open_time")
        val closeTime = long("close_time")
        val open = decimal("open_price", 38, 18)
        val high = decimal("high_price", 38, 18)
        val low = decimal("low_price", 38, 18)
        val close = decimal("close_price", 38, 18)
        val volume = decimal("volume", 38, 18)
        override val primaryKey = PrimaryKey(symbol, interval, openTime)
    }

    fun initSchema() = transaction(db) { SchemaUtils.createMissingTablesAndColumns(Candles) }

    /** Closed candles are written in one transaction and upserted by the stable candle key. */
    fun persistClosed(candles: List<MarketCandle>) {
        if (candles.isEmpty()) return
        transaction(db) {
            val isPostgres = db.vendor.contains("postgres", ignoreCase = true)
            val sql =
                if (isPostgres) {
                    """
                    INSERT INTO market_candles (symbol, candle_interval, open_time, close_time, open_price, high_price, low_price, close_price, volume)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (symbol, candle_interval, open_time) DO UPDATE SET
                    close_time = EXCLUDED.close_time, open_price = EXCLUDED.open_price, high_price = EXCLUDED.high_price,
                    low_price = EXCLUDED.low_price, close_price = EXCLUDED.close_price, volume = EXCLUDED.volume
                    """.trimIndent()
                } else {
                    """
                    MERGE INTO market_candles (symbol, candle_interval, open_time, close_time, open_price, high_price, low_price, close_price, volume)
                    KEY (symbol, candle_interval, open_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                }
            val statement =
                TransactionManager.current().connection.prepareStatement(
                    sql,
                    false,
                )
            try {
                candles.forEach { candle ->
                    statement.set(1, candle.symbol)
                    statement.set(2, candle.interval)
                    statement.set(3, candle.openTime)
                    statement.set(4, candle.closeTime)
                    statement.set(5, candle.open.toBigDecimal())
                    statement.set(6, candle.high.toBigDecimal())
                    statement.set(7, candle.low.toBigDecimal())
                    statement.set(8, candle.close.toBigDecimal())
                    statement.set(9, candle.volume.toBigDecimal())
                    statement.addBatch()
                }
                statement.executeBatch()
            } finally {
                statement.closeIfPossible()
            }
        }
    }

    fun history(
        symbol: String,
        interval: String,
        limit: Int = 500,
    ): List<MarketCandle> =
        transaction(db) {
            Candles.selectAll()
                .where { (Candles.symbol eq symbol.uppercase()) and (Candles.interval eq interval) }
                .orderBy(Candles.openTime to SortOrder.DESC)
                .limit(limit.coerceIn(1, 2_000))
                .map { row ->
                    MarketCandle(
                        row[Candles.symbol], row[Candles.interval], row[Candles.openTime], row[Candles.closeTime],
                        row[Candles.open].stripTrailingZeros().toPlainString(),
                        row[Candles.high].stripTrailingZeros().toPlainString(),
                        row[Candles.low].stripTrailingZeros().toPlainString(),
                        row[Candles.close].stripTrailingZeros().toPlainString(),
                        row[Candles.volume].stripTrailingZeros().toPlainString(),
                    )
                }.asReversed()
        }

    /** Retain one-minute bars for 30 days and higher intervals for five years. */
    fun prune(now: Long = System.currentTimeMillis()) =
        transaction(db) {
            val minuteCutoff = now - MINUTE_RETENTION_MS
            val longCutoff = now - LONG_RETENTION_MS
            exec(
                "DELETE FROM market_candles WHERE (candle_interval = '1m' AND open_time < $minuteCutoff) OR (candle_interval <> '1m' AND open_time < $longCutoff)",
            )
        }

    private companion object {
        const val MINUTE_RETENTION_MS = 30L * 24 * 60 * 60 * 1_000
        const val LONG_RETENTION_MS = 5L * 365 * 24 * 60 * 60 * 1_000
    }
}
