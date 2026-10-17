package com.argent.gateway.trading

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import com.argent.ledger.LedgerService
import com.argent.market.BinanceSpotTestnet
import com.argent.market.BinanceTestnetException
import com.argent.market.TestnetOrder
import com.argent.market.TestnetOrderRequest
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Coordinates Binance Spot Testnet orders with the account's simulated ARGENT ledger. */
class BinanceTestnetExecutionService(
    database: Database,
    private val ledger: LedgerService,
    private val exchange: BinanceSpotTestnet
) {
    private val store = BinanceTestnetOrderStore(database)
    private val log = LoggerFactory.getLogger(BinanceTestnetExecutionService::class.java)
    private val orderLocks = ConcurrentHashMap<String, Mutex>()

    fun initSchema() = store.initSchema()

    suspend fun place(accountId: String, request: TestnetOrderRequest): TestnetOrder {
        val normalized = validate(request)
        val clientId = normalized.clientOrderId ?: "ag-${UUID.randomUUID().toString().replace("-", "")}".take(36)
        val req = normalized.copy(clientOrderId = clientId)
        val existing = store.find(clientId)
        if (existing != null) {
            require(existing.accountId == accountId && existing.matches(req)) {
                "clientOrderId is already used for a different testnet order"
            }
            val remote = existing.exchangeOrderId?.let { exchange.getOrder(existing.symbol, it) }
                ?: exchange.getOrderByClientOrderId(existing.symbol, existing.clientOrderId)
            store.acknowledge(existing.clientOrderId, remote.orderId, remote.status)
            return sync(existing.copy(exchangeOrderId = remote.orderId), remote)
        }

        val instrument = Instruments.require(req.symbol)
        val holdId = "bt-${UUID.randomUUID()}"
        val (holdAsset, holdAmount) = reserveAmount(accountId, instrument, req)
        val tracked = TrackedTestnetOrder(
            clientOrderId = clientId,
            accountId = accountId,
            symbol = req.symbol,
            side = req.side,
            type = req.type,
            quantity = req.quantity,
            price = req.price,
            timeInForce = req.timeInForce,
            holdId = holdId,
            holdAsset = holdAsset,
            holdAmount = holdAmount,
            exchangeOrderId = null,
            status = "PREPARED"
        )
        store.create(tracked)
        if (!ledger.hold(accountId, holdAsset, holdAmount, holdId)) {
            store.setStatus(clientId, "REJECTED")
            throw IllegalArgumentException("Insufficient available $holdAsset in ARGENT to reserve this testnet order")
        }
        store.setStatus(clientId, "SUBMITTING")

        val remote = try {
            exchange.placeOrder(req)
        } catch (failure: BinanceTestnetException) {
            if (failure.upstreamStatus in 400..499) {
                store.setStatus(clientId, "REJECTED")
                ledger.releaseRemainingHold(holdId)
            } else {
                // A timeout can happen after Binance accepted the order. Keep the
                // hold and client id so polling can recover the exchange order.
                store.setStatus(clientId, "SUBMISSION_UNKNOWN")
            }
            throw failure
        } catch (failure: Exception) {
            store.setStatus(clientId, "SUBMISSION_UNKNOWN")
            throw failure
        }
        store.acknowledge(clientId, remote.orderId, remote.status)
        return sync(tracked.copy(exchangeOrderId = remote.orderId), remote)
    }

    suspend fun get(accountId: String, symbol: String, orderId: Long): TestnetOrder {
        val tracked = store.find(accountId, symbol.uppercase(), orderId)
        if (tracked == null) return exchange.getOrder(symbol, orderId)
        val remote = exchange.getOrder(symbol, orderId)
        return sync(tracked, remote)
    }

    suspend fun cancel(accountId: String, symbol: String, orderId: Long): TestnetOrder {
        val tracked = store.find(accountId, symbol.uppercase(), orderId)
        if (tracked == null) return exchange.cancelOrder(symbol, orderId)
        val remote = exchange.cancelOrder(symbol, orderId)
        store.setStatus(tracked.clientOrderId, remote.status)
        return sync(tracked, remote)
    }

    suspend fun syncActive() {
        store.active().forEach { tracked ->
            runCatching {
                val remote = tracked.exchangeOrderId?.let { exchange.getOrder(tracked.symbol, it) }
                    ?: exchange.getOrderByClientOrderId(tracked.symbol, tracked.clientOrderId)
                store.acknowledge(tracked.clientOrderId, remote.orderId, remote.status)
                sync(tracked.copy(exchangeOrderId = remote.orderId), remote)
            }.onFailure { failure ->
                log.debug("Testnet order sync deferred clientOrderId={} reason={}", tracked.clientOrderId, failure.message)
            }
        }
    }

    private suspend fun sync(tracked: TrackedTestnetOrder, remote: TestnetOrder): TestnetOrder =
        orderLocks.computeIfAbsent(tracked.clientOrderId) { Mutex() }.withLock { syncLocked(tracked, remote) }

    private suspend fun syncLocked(tracked: TrackedTestnetOrder, remote: TestnetOrder): TestnetOrder {
        var syncOk = true
        try {
            val instrument = Instruments.require(tracked.symbol)
            val fills = exchange.trades(tracked.symbol, remote.orderId)
            val filledQuantity = fills.fold(BigDecimal.ZERO) { total, fill -> total + fill.qty.toBigDecimal() }
            require(filledQuantity.compareTo(remote.executedQty.toBigDecimal()) == 0) {
                "Binance testnet fill history is incomplete; keeping the local hold for retry"
            }
            remote.cummulativeQuoteQty?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { remoteQuote ->
                val filledQuote = fills.fold(BigDecimal.ZERO) { total, fill -> total + fill.quoteQty.toBigDecimal() }
                require(filledQuote.compareTo(remoteQuote) == 0) {
                    "Binance testnet quote fill history is incomplete; keeping the local hold for retry"
                }
            }
            fills.forEach { fill ->
                require(fill.orderId == remote.orderId) { "Binance testnet returned a fill for a different order" }
                val side = if (fill.isBuyer) "BUY" else "SELL"
                require(side == tracked.side) { "Binance testnet fill side does not match the submitted order" }
                ledger.settleBinanceTestnetFill(
                    executionId = "${tracked.symbol}:${fill.orderId}:${fill.id}",
                    holdId = tracked.holdId,
                    userId = tracked.accountId,
                    symbol = tracked.symbol,
                    side = side,
                    baseAsset = instrument.baseAsset,
                    quoteAsset = instrument.quoteAsset,
                    quantity = fill.qty.toBigDecimal(),
                    quoteQuantity = fill.quoteQty.toBigDecimal(),
                    commission = fill.commission.toBigDecimal(),
                    commissionAsset = fill.commissionAsset
                )
            }
        } catch (failure: Exception) {
            syncOk = false
            store.setStatus(tracked.clientOrderId, "SETTLEMENT_REVIEW")
            log.error("Binance Testnet fill could not be mirrored to ARGENT ledger clientOrderId={}", tracked.clientOrderId, failure)
        }
        if (syncOk) {
            store.setStatus(tracked.clientOrderId, remote.status)
            if (remote.status.uppercase() in TERMINAL_STATUSES) ledger.releaseRemainingHold(tracked.holdId)
        }
        return remote.copy(ledgerSynced = syncOk)
    }

    private fun validate(request: TestnetOrderRequest): TestnetOrderRequest {
        val symbol = request.symbol.uppercase()
        val instrument = Instruments.require(symbol)
        require(instrument.assetClass == AssetClass.CRYPTO) { "Only supported crypto instruments can use Binance Spot Testnet" }
        val side = request.side.uppercase()
        require(side in setOf("BUY", "SELL")) { "side must be BUY or SELL" }
        val type = request.type.uppercase()
        require(type in setOf("LIMIT", "MARKET")) { "type must be LIMIT or MARKET" }
        val quantity = request.quantity.toBigDecimalOrNull()?.stripTrailingZeros()
            ?: throw IllegalArgumentException("quantity must be decimal")
        require(quantity > BigDecimal.ZERO && quantity.remainder(instrument.lotSize.toBigDecimal()).signum() == 0) {
            "quantity must be positive and match ${instrument.symbol} lot size ${instrument.lotSize}"
        }
        if (type == "LIMIT") {
            val price = request.price?.toBigDecimalOrNull() ?: throw IllegalArgumentException("price is required for LIMIT")
            require(price > BigDecimal.ZERO && price.remainder(instrument.tickSize.toBigDecimal()).signum() == 0) {
                "price must be positive and match ${instrument.symbol} tick size ${instrument.tickSize}"
            }
            require(request.timeInForce.uppercase() in setOf("GTC", "IOC", "FOK")) { "Invalid time in force" }
        } else {
            require(request.price.isNullOrBlank()) { "price is not accepted for MARKET" }
            require(request.timeInForce.uppercase() == "GTC") { "timeInForce is only supported for LIMIT" }
        }
        return request.copy(symbol = symbol, side = side, type = type, quantity = quantity.toPlainString(), timeInForce = request.timeInForce.uppercase())
    }

    private fun reserveAmount(accountId: String, instrument: com.argent.common.domain.Instrument, request: TestnetOrderRequest): Pair<String, BigDecimal> {
        val feeBuffer = BigDecimal("1.01")
        return when {
            request.side == "BUY" && request.type == "LIMIT" ->
                instrument.quoteAsset to request.quantity.toBigDecimal().multiply(request.price!!.toBigDecimal()).multiply(feeBuffer)
            request.side == "BUY" -> {
                val available = ledger.getBalance(accountId, instrument.quoteAsset).availableBd()
                require(available > BigDecimal.ZERO) { "No available ${instrument.quoteAsset} to reserve for a market buy" }
                instrument.quoteAsset to available
            }
            else -> instrument.baseAsset to request.quantity.toBigDecimal().multiply(feeBuffer).setScale(18, RoundingMode.UP).stripTrailingZeros()
        }
    }

    private fun TrackedTestnetOrder.matches(request: TestnetOrderRequest): Boolean {
        val existingPrice = price
        val requestedPrice = request.price
        return symbol == request.symbol && side == request.side && type == request.type &&
            quantity.toBigDecimal().compareTo(request.quantity.toBigDecimal()) == 0 &&
            ((existingPrice == null && requestedPrice == null) ||
                (existingPrice != null && requestedPrice != null && existingPrice.toBigDecimal().compareTo(requestedPrice.toBigDecimal()) == 0)) &&
            timeInForce == request.timeInForce
    }

    private companion object {
        val TERMINAL_STATUSES = setOf("FILLED", "CANCELED", "REJECTED", "EXPIRED", "EXPIRED_IN_MATCH")
    }
}

private class BinanceTestnetOrderStore(private val db: Database) {
    private object Orders : Table("binance_testnet_orders") {
        val clientOrderId = varchar("client_order_id", 36)
        val accountId = varchar("account_id", 64)
        val symbol = varchar("symbol", 32)
        val side = varchar("side", 8)
        val type = varchar("order_type", 16)
        val quantity = varchar("quantity", 64)
        val price = varchar("price", 64).nullable()
        val timeInForce = varchar("time_in_force", 8)
        val holdId = varchar("hold_id", 64)
        val holdAsset = varchar("hold_asset", 32)
        val holdAmount = decimal("hold_amount", 36, 18)
        val exchangeOrderId = long("exchange_order_id").nullable()
        val status = varchar("status", 32)
        override val primaryKey = PrimaryKey(clientOrderId)
    }

    fun initSchema() = transaction(db) { SchemaUtils.createMissingTablesAndColumns(Orders) }

    fun create(order: TrackedTestnetOrder) = transaction(db) {
        Orders.insert {
            it[clientOrderId] = order.clientOrderId
            it[accountId] = order.accountId
            it[symbol] = order.symbol
            it[side] = order.side
            it[type] = order.type
            it[quantity] = order.quantity
            it[price] = order.price
            it[timeInForce] = order.timeInForce
            it[holdId] = order.holdId
            it[holdAsset] = order.holdAsset
            it[holdAmount] = order.holdAmount
            it[exchangeOrderId] = order.exchangeOrderId
            it[status] = order.status
        }
    }

    fun find(clientOrderId: String): TrackedTestnetOrder? = transaction(db) {
        Orders.selectAll().where { Orders.clientOrderId eq clientOrderId }.firstOrNull()?.toTracked()
    }

    fun find(accountId: String, symbol: String, orderId: Long): TrackedTestnetOrder? = transaction(db) {
        Orders.selectAll().where {
            (Orders.accountId eq accountId) and (Orders.symbol eq symbol) and (Orders.exchangeOrderId eq orderId)
        }.firstOrNull()?.toTracked()
    }

    fun acknowledge(clientOrderId: String, orderId: Long, status: String) = transaction(db) {
        Orders.update({ Orders.clientOrderId eq clientOrderId }) {
            it[exchangeOrderId] = orderId
            it[Orders.status] = status
        }
    }

    fun setStatus(clientOrderId: String, status: String) = transaction(db) {
        Orders.update({ Orders.clientOrderId eq clientOrderId }) { it[Orders.status] = status }
    }

    fun active(): List<TrackedTestnetOrder> = transaction(db) {
        Orders.selectAll().where {
            Orders.status inList listOf("PREPARED", "SUBMITTING", "SUBMISSION_UNKNOWN", "NEW", "PARTIALLY_FILLED", "SETTLEMENT_REVIEW")
        }.map { it.toTracked() }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toTracked() = TrackedTestnetOrder(
        clientOrderId = this[Orders.clientOrderId],
        accountId = this[Orders.accountId],
        symbol = this[Orders.symbol],
        side = this[Orders.side],
        type = this[Orders.type],
        quantity = this[Orders.quantity],
        price = this[Orders.price],
        timeInForce = this[Orders.timeInForce],
        holdId = this[Orders.holdId],
        holdAsset = this[Orders.holdAsset],
        holdAmount = this[Orders.holdAmount],
        exchangeOrderId = this[Orders.exchangeOrderId],
        status = this[Orders.status]
    )
}

private data class TrackedTestnetOrder(
    val clientOrderId: String,
    val accountId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: String,
    val price: String?,
    val timeInForce: String,
    val holdId: String,
    val holdAsset: String,
    val holdAmount: BigDecimal,
    val exchangeOrderId: Long?,
    val status: String
)
