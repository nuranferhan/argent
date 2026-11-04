package com.argent.gateway

import com.argent.gateway.market.CandleStore
import com.argent.gateway.trading.TradingService
import com.argent.ledger.LedgerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val maintenanceLog = LoggerFactory.getLogger("GatewayMaintenance")

internal fun startInstrumentMaintenance(
    scope: CoroutineScope,
    catalog: InstrumentCatalogStore,
    trading: TradingService,
    ledger: LedgerService,
) {
    scope.launch {
        while (true) {
            runCatching {
                catalog.deactivateExpired()
                trading.cancelNonTradableOrders()
                catalog.expiredDerivativeSymbols().forEach(ledger::expireWorthlessDerivative)
            }.onFailure { maintenanceLog.error("Expired instrument order cleanup failed", it) }
            delay(30_000)
        }
    }
}

internal fun startCandleRetention(
    scope: CoroutineScope,
    store: CandleStore,
) {
    scope.launch {
        while (true) {
            delay(24 * 60 * 60 * 1_000L)
            runCatching { store.prune() }
                .onFailure { maintenanceLog.warn("Market candle retention cleanup failed", it) }
        }
    }
}
