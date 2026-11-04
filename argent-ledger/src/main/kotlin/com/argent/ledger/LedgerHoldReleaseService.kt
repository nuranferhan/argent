package com.argent.ledger

import com.argent.common.events.DomainEvent
import com.argent.common.events.FundsReleased
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

internal class LedgerHoldReleaseService(
    private val db: Database,
    private val eventBus: (DomainEvent) -> Unit,
    private val releaseLocked: (String, String, BigDecimal, String) -> FundsReleased?
) {
    fun releaseRemainingHold(orderId: String) {
        val released = transaction(db) {
            val hold = OrderHolds.selectAll().where { OrderHolds.orderId eq orderId }.firstOrNull() ?: return@transaction null
            val remaining = hold[OrderHolds.remaining]
            val event = if (remaining > BigDecimal.ZERO) {
                releaseLocked(hold[OrderHolds.userId], hold[OrderHolds.asset], remaining, orderId)
            } else null
            OrderHolds.update({ OrderHolds.orderId eq orderId }) { it[OrderHolds.remaining] = BigDecimal.ZERO }
            event
        }
        released?.let(eventBus)
    }

    fun release(userId: String, asset: String, amount: BigDecimal, orderId: String) {
        val event = transaction(db) { releaseLocked(userId, asset, amount, orderId) }
        event?.let(eventBus)
    }

}
