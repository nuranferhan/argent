package com.argent.core

import com.argent.common.events.DomainEvent
import com.argent.engine.MatchingEngine
import com.argent.ledger.LedgerService
import javax.sql.DataSource

/** Reusable matching + double-entry ledger core for paper-trading products. */
class ArgentMatchingCore(
    dataSource: DataSource,
    private val durableEventSink: (DomainEvent) -> Unit = {}
) {
    val ledger: LedgerService = LedgerService(dataSource, durableEventSink).also { it.initSchema() }
    val matchingEngine: MatchingEngine = newMatchingEngine()

    /** Creates an isolated engine for recovery, replay, or test workloads. */
    fun newMatchingEngine(
        eventSink: (DomainEvent) -> Unit = durableEventSink,
        keepRecordedEvents: Boolean = false,
        rollbackOnMutationFailure: Boolean = true
    ): MatchingEngine = MatchingEngine(
        eventBus = eventSink,
        keepRecordedEvents = keepRecordedEvents,
        rollbackOnMutationFailure = rollbackOnMutationFailure
    )
}
