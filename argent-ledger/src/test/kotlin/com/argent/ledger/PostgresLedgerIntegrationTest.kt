package com.argent.ledger

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.argent.common.events.SettlementCompleted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.math.BigDecimal
import java.util.UUID

/**
 * Opt-in test against a real PostgreSQL database. The PowerShell runner creates
 * a unique schema and obtains connection credentials from the running Compose
 * container, so this test never touches the application's tables.
 */
@EnabledIfEnvironmentVariable(named = "ARGENT_TEST_POSTGRES_URL", matches = "jdbc:postgresql://.+")
class PostgresLedgerIntegrationTest {
    @Test
    fun `concurrent reservations and settlement remain balanced after reconnect`() {
        val url = requireNotNull(System.getenv("ARGENT_TEST_POSTGRES_URL"))
        val username = requireNotNull(System.getenv("ARGENT_TEST_POSTGRES_USER"))
        val password = requireNotNull(System.getenv("ARGENT_TEST_POSTGRES_PASSWORD"))
        val schema = "argent_it_${UUID.randomUUID().toString().replace("-", "")}"
        val admin = HikariDataSource(HikariConfig().apply {
            jdbcUrl = url
            this.username = username
            this.password = password
            maximumPoolSize = 2
        })

        try {
            admin.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE SCHEMA \"$schema\"")
                    statement.execute("""
                        CREATE TABLE "$schema".balances (
                            user_id VARCHAR(64) NOT NULL,
                            asset VARCHAR(32) NOT NULL,
                            available NUMERIC(36,18) NOT NULL,
                            held NUMERIC(36,18) NOT NULL,
                            version BIGINT NOT NULL,
                            PRIMARY KEY (user_id, asset)
                        )
                    """.trimIndent())
                    statement.execute("""
                        CREATE TABLE "$schema".journal_entries (
                            id VARCHAR(64) PRIMARY KEY,
                            tx_id VARCHAR(64) NOT NULL,
                            user_id VARCHAR(64),
                            account VARCHAR(64),
                            asset VARCHAR(32) NOT NULL,
                            debit NUMERIC(36,18) NOT NULL,
                            credit NUMERIC(36,18) NOT NULL,
                            description VARCHAR(256) NOT NULL,
                            created_at BIGINT NOT NULL
                        )
                    """.trimIndent())
                    statement.execute("""
                        INSERT INTO "$schema".balances(user_id, asset, available, held, version)
                        VALUES ('legacy-user', 'TRY', 100, 0, 0)
                    """.trimIndent())
                    // This represents a database already migrated by the older
                    // code, which assigned LEGACY_UNASSIGNED despite retaining user_id.
                    statement.execute("""
                        INSERT INTO "$schema".journal_entries
                            (id, tx_id, user_id, account, asset, debit, credit, description, created_at)
                        VALUES
                            ('legacy-system', 'legacy-deposit', NULL, 'LEGACY_UNASSIGNED', 'TRY', 100, 0, 'legacy custody', 1),
                            ('legacy-user', 'legacy-deposit', 'legacy-user', 'LEGACY_UNASSIGNED', 'TRY', 0, 100, 'legacy liability', 1)
                    """.trimIndent())
                }
            }

            fun dataSource() = HikariDataSource(HikariConfig().apply {
                // Use the driver's startup parameter so every physical pooled
                // connection is pinned to the isolated schema. A SQL SET from
                // connectionInitSql can be rolled back when autoCommit=false.
                jdbcUrl = "$url${if ('?' in url) '&' else '?'}currentSchema=$schema"
                this.username = username
                this.password = password
                maximumPoolSize = 16
                isAutoCommit = false
            })

            val user = "concurrent-${schema.takeLast(12)}"
            val firstDataSource = dataSource()
            try {
                firstDataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT current_schema()").use { result ->
                            assertTrue(result.next())
                            assertEquals(schema, result.getString(1), "test connections must stay inside the temporary schema")
                        }
                    }
                }
                val ledger = LedgerService(firstDataSource) { event ->
                    if (event is SettlementCompleted) error("injected post-commit event delivery failure")
                }.also { it.initSchema() }
                assertTrue(ledger.reconciliationReport().ok, "legacy journal ownership must be restored from user_id")
                ledger.deposit(user, "TRY", BigDecimal("1000"), "seed-$schema")

                val holds = runBlocking {
                    coroutineScope {
                        (0 until 40).map { index ->
                            async(Dispatchers.IO) {
                                ledger.hold(user, "TRY", BigDecimal("25"), "$schema-order-$index")
                            }
                        }.awaitAll()
                    }
                }
                assertEquals(40, holds.count { it }, "all funded reservations should succeed")
                assertEquals(BigDecimal.ZERO, ledger.getBalance(user, "TRY").availableBd())
                assertEquals(BigDecimal("1000"), ledger.getBalance(user, "TRY").heldBd())
                assertTrue(ledger.verifyInvariant())
                assertTrue(ledger.reconciliationReport().ok, ledger.reconciliationReport().toString())

                val buyer = "$user-buyer"
                val seller = "$user-seller"
                ledger.deposit(buyer, "TRY", BigDecimal("500"), "buyer-$schema")
                ledger.deposit(seller, "THYAO", BigDecimal("5"), "seller-$schema")
                assertTrue(ledger.hold(buyer, "TRY", BigDecimal("200"), "$schema-buy"))
                assertTrue(ledger.hold(seller, "THYAO", BigDecimal("2"), "$schema-sell"))
                assertThrows(IllegalStateException::class.java) {
                    ledger.settleTrade(
                        tradeId = "$schema-trade",
                        symbol = "THYAO",
                        buyUserId = buyer,
                        sellUserId = seller,
                        baseAsset = "THYAO",
                        quoteAsset = "TRY",
                        quantity = BigDecimal("2"),
                        price = BigDecimal("100"),
                        buyOrderId = "$schema-buy",
                        sellOrderId = "$schema-sell"
                    )
                }
            } finally {
                firstDataSource.close()
            }

            val restartedDataSource = dataSource()
            try {
                val restartedLedger = LedgerService(restartedDataSource).also { it.initSchema() }
                val buyer = "$user-buyer"
                val seller = "$user-seller"
                // Replay the trade after a simulated process restart. The persisted
                // settlement key must prevent a second debit or credit.
                restartedLedger.settleTrade(
                    tradeId = "$schema-trade",
                    symbol = "THYAO",
                    buyUserId = buyer,
                    sellUserId = seller,
                    baseAsset = "THYAO",
                    quoteAsset = "TRY",
                    quantity = BigDecimal("2"),
                    price = BigDecimal("100"),
                    buyOrderId = "$schema-buy",
                    sellOrderId = "$schema-sell"
                )
                assertEquals(BigDecimal("300"), restartedLedger.getBalance(buyer, "TRY").availableBd())
                assertEquals(BigDecimal("2"), restartedLedger.getBalance(buyer, "THYAO").availableBd())
                assertEquals(BigDecimal("200"), restartedLedger.getBalance(seller, "TRY").availableBd())
                assertEquals(BigDecimal("3"), restartedLedger.getBalance(seller, "THYAO").availableBd())
                assertTrue(restartedLedger.verifyInvariant())
                assertTrue(restartedLedger.reconciliationReport().ok, restartedLedger.reconciliationReport().toString())
            } finally {
                restartedDataSource.close()
            }
        } finally {
            runCatching {
                admin.connection.use { connection ->
                    connection.createStatement().use { it.execute("DROP SCHEMA IF EXISTS \"$schema\" CASCADE") }
                }
            }
            admin.close()
        }
    }
}
