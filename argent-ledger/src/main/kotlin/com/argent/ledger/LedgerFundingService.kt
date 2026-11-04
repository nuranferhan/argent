package com.argent.ledger

import com.argent.common.domain.Balance
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

internal class LedgerFundingService(
    private val db: Database,
    private val creditAvailable: (String, String, BigDecimal) -> Unit,
    private val lockBalance: (String, String) -> ResultRow?,
    private val writeBalance: (ResultRow, BigDecimal, BigDecimal) -> Unit,
    private val journal: (String, String, String, BigDecimal, BigDecimal, String) -> Unit,
    private val assertTxBalanced: (String) -> Unit,
    private val readBalance: (String, String) -> Balance,
    private val storedBalance: (String?, String?) -> Balance?,
    private val rememberBalance: (String?, Balance, String?) -> Unit,
    private val idempotencyFingerprint: (String, BigDecimal) -> String
) {
    fun deposit(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(idempotencyKey == null || idempotencyKey.length in 1..128) { "Idempotency key must contain 1 to 128 characters" }
        val key = idempotencyKey?.let { "deposit:$userId:$asset:$it" }
        val requestHash = idempotencyFingerprint(asset, amount)
        return transaction(db) {
            storedBalance(key, requestHash)?.let { return@transaction it }
            idempotencyKey?.let { legacy ->
                storedBalance("deposit:$userId:$legacy", requestHash)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
                storedBalance("deposit:$legacy", null)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
            }
            val txId = UUID.randomUUID().toString()
            creditAvailable(userId, asset, amount)
            journal(txId, "SYSTEM", asset, amount, BigDecimal.ZERO, "Deposit custody")
            journal(txId, userId, asset, BigDecimal.ZERO, amount, "Deposit liability")
            assertTxBalanced(txId)
            val bal = readBalance(userId, asset)
            rememberBalance(key, bal, requestHash)
            bal
        }
    }

    fun withdraw(userId: String, asset: String, amount: BigDecimal, idempotencyKey: String? = null): Balance {
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        require(idempotencyKey == null || idempotencyKey.length in 1..128) { "Idempotency key must contain 1 to 128 characters" }
        val key = idempotencyKey?.let { "withdraw:$userId:$asset:$it" }
        val requestHash = idempotencyFingerprint(asset, amount)
        return transaction(db) {
            storedBalance(key, requestHash)?.let { return@transaction it }
            idempotencyKey?.let { legacy ->
                storedBalance("withdraw:$userId:$legacy", requestHash)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
                storedBalance("withdraw:$legacy", null)?.takeIf { it.userId == userId && it.asset == asset }?.let { return@transaction it }
            }
            val current = lockBalance(userId, asset) ?: throw IllegalArgumentException("Insufficient $asset")
            if (current[Balances.available] < amount) throw IllegalArgumentException("Insufficient available $asset")
            writeBalance(current, current[Balances.available] - amount, current[Balances.held])
            val txId = UUID.randomUUID().toString()
            journal(txId, userId, asset, amount, BigDecimal.ZERO, "Withdraw liability")
            journal(txId, "SYSTEM", asset, BigDecimal.ZERO, amount, "Withdraw custody")
            assertTxBalanced(txId)
            val bal = readBalance(userId, asset)
            rememberBalance(key, bal, requestHash)
            bal
        }
    }

}
