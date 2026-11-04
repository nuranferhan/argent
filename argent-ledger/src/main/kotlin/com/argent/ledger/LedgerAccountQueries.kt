package com.argent.ledger

import com.argent.common.domain.Balance
import com.argent.common.domain.Position
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal

internal class LedgerAccountQueries(
    private val db: Database,
    private val readBalance: (String, String) -> Balance,
    private val readPosition: (String, String) -> Position
) {
    private fun rowToBalance(it: org.jetbrains.exposed.sql.ResultRow) = Balance(
        userId = it[Balances.userId],
        asset = it[Balances.asset],
        available = it[Balances.available].stripTrailingZeros().toPlainString(),
        held = it[Balances.held].stripTrailingZeros().toPlainString(),
        version = it[Balances.version]
    )

    private fun rowToPosition(it: org.jetbrains.exposed.sql.ResultRow) = Position(
        userId = it[Positions.userId],
        symbol = it[Positions.symbol],
        quantity = it[Positions.quantity].stripTrailingZeros().toPlainString(),
        avgEntryPrice = it[Positions.avgEntry].stripTrailingZeros().toPlainString(),
        realizedPnl = it[Positions.realizedPnl].stripTrailingZeros().toPlainString()
    )


    fun initSchema() {
        val isPostgres = db.vendor.contains("postgres", ignoreCase = true)
        transaction(db) {
            // Older local databases may already have journal rows with user_id but
            // without account. Preserve that ownership when adding the account column;
            // otherwise all historical user postings become invisible to reconciliation.
            if (isPostgres) {
                exec("""
                    DO ${'$'}${'$'}
                    BEGIN
                        IF to_regclass('journal_entries') IS NOT NULL THEN
                            ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS account VARCHAR(64);
                            IF EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema = current_schema()
                                  AND table_name = 'journal_entries'
                                  AND column_name = 'user_id'
                            ) THEN
                                UPDATE journal_entries
                                SET account = user_id
                                WHERE account IS NULL AND user_id IS NOT NULL;
                                -- Repair rows already migrated by older builds that
                                -- assigned LEGACY_UNASSIGNED before preserving user_id.
                                UPDATE journal_entries
                                SET account = user_id
                                WHERE account = 'LEGACY_UNASSIGNED' AND user_id IS NOT NULL;
                            END IF;
                            UPDATE journal_entries SET account = 'LEGACY_UNASSIGNED' WHERE account IS NULL;
                            ALTER TABLE journal_entries ALTER COLUMN account SET NOT NULL;
                            IF EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_schema = current_schema()
                                  AND table_name = 'journal_entries'
                                  AND column_name = 'user_id'
                            ) THEN
                                ALTER TABLE journal_entries ALTER COLUMN user_id DROP NOT NULL;
                            END IF;
                        END IF;
                    END
                    ${'$'}${'$'}
                """.trimIndent())
            }
            SchemaUtils.createMissingTablesAndColumns(Balances, JournalEntries, IdempotencyKeys, OrderHolds, Positions, Settlements)
            if (isPostgres) {
                exec("ALTER TABLE idempotency_keys ALTER COLUMN key TYPE VARCHAR(256)")
                exec("ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS request_hash VARCHAR(128)")
            }
        }
    }

    fun getBalance(userId: String, asset: String): Balance = transaction(db) {
        readBalance(userId, asset)
    }

    fun listBalances(userId: String): List<Balance> = transaction(db) {
        Balances.selectAll().where { Balances.userId eq userId }.map { rowToBalance(it) }
            .filter { it.availableBd().signum() != 0 || it.heldBd().signum() != 0 }
    }

    fun activeHoldOrderIds(): List<String> = transaction(db) {
        OrderHolds.selectAll()
            .filter { it[OrderHolds.remaining].signum() > 0 }
            .map { it[OrderHolds.orderId] }
    }

    fun getPosition(userId: String, symbol: String): Position = transaction(db) {
        readPosition(userId, symbol)
    }

    fun listPositions(userId: String): List<Position> = transaction(db) {
        Positions.selectAll().where { Positions.userId eq userId }.map { rowToPosition(it) }
            .filter { it.quantityBd().signum() != 0 || it.realizedPnlBd().signum() != 0 }
    }

    /**
     * Simulated derivatives without an official settlement source expire at zero.
     * Closes every position atomically; a long position is written off and a short
     * position realizes its entry value. Zero quantity makes retries idempotent.
     */
    fun verifyInvariant(): Boolean = transaction(db) {
        // Aggregate inside the database. Loading every journal row into Kotlin made
        // the public health endpoint take seconds as the journal grew.
        exec(
            """
            SELECT asset, SUM(debit) AS debit_total, SUM(credit) AS credit_total
            FROM journal_entries
            GROUP BY asset
            """.trimIndent()
        ) { resultSet ->
            var balanced = true
            while (resultSet.next()) {
                val debit = resultSet.getBigDecimal("debit_total") ?: BigDecimal.ZERO
                val credit = resultSet.getBigDecimal("credit_total") ?: BigDecimal.ZERO
                if (debit.compareTo(credit) != 0) balanced = false
            }
            balanced
        } ?: false
    }

    /**
     * Reconciles each user/asset balance (available + held) against that account's
     * net journal balance. Custody, fee and legacy accounts are controls and have
     * no spendable balance row.
     */
    fun verifyReconciliation(): Boolean = reconciliationReport().ok

    fun reconciliationReport(): LedgerService.ReconciliationReport = transaction(db) {
        val controlAccounts = setOf("SYSTEM", "LEGACY_UNASSIGNED", "BINANCE_TESTNET_CUSTODY", "BINANCE_TESTNET_FEES", "DERIVATIVE_EXPIRY_LOSS")
        val journalNet = JournalEntries.selectAll()
            .filter { it[JournalEntries.account] !in controlAccounts }
            .groupBy { it[JournalEntries.account] to it[JournalEntries.asset] }
            .mapValues { (_, rows) ->
                rows.fold(BigDecimal.ZERO) { total, row ->
                    total + row[JournalEntries.credit] - row[JournalEntries.debit]
                }
            }
        val balanceTotals = Balances.selectAll()
            .associate { row ->
                (row[Balances.userId] to row[Balances.asset]) to
                    (row[Balances.available] + row[Balances.held])
            }

        val keys = journalNet.keys + balanceTotals.keys
        val mismatches = keys.mapNotNull { (account, asset) ->
            val journal = journalNet[account to asset] ?: BigDecimal.ZERO
            val balance = balanceTotals[account to asset] ?: BigDecimal.ZERO
            if (journal.compareTo(balance) == 0) null else LedgerService.ReconciliationMismatch(
                account = account,
                asset = asset,
                journalNet = journal.stripTrailingZeros().toPlainString(),
                balanceTotal = balance.stripTrailingZeros().toPlainString()
            )
        }
        LedgerService.ReconciliationReport(
            ok = mismatches.isEmpty(),
            checkedAccountAssets = keys.size,
            mismatches = mismatches.sortedWith(compareBy({ it.account }, { it.asset }))
        )
    }

    fun journalForTx(txId: String): List<Pair<BigDecimal, BigDecimal>> = transaction(db) {
        JournalEntries.selectAll().where { JournalEntries.txId eq txId }
            .map { it[JournalEntries.debit] to it[JournalEntries.credit] }
    }

}
