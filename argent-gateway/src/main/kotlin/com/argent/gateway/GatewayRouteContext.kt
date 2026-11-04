package com.argent.gateway

import com.argent.core.ArgentMatchingCore
import com.argent.engine.MatchingEngine
import com.argent.gateway.assistant.AssistantOrderDraftService
import com.argent.gateway.assistant.AssistantPortfolioHistoryStore
import com.argent.gateway.assistant.ReadOnlyAssistantService
import com.argent.gateway.auth.AuthService
import com.argent.gateway.config.AppConfig
import com.argent.gateway.event.PostgresEventStore
import com.argent.gateway.market.ExternalMarketDataService
import com.argent.gateway.market.PriceAlertStore
import com.argent.gateway.mm.LiquidityProvider
import com.argent.gateway.order.OrderRepository
import com.argent.gateway.ratelimit.RedisRateLimiter
import com.argent.gateway.trading.BinanceTestnetExecutionService
import com.argent.gateway.trading.TradingService
import com.argent.ledger.LedgerService
import com.argent.market.BinanceSpotTestnet
import com.argent.market.EventBroker
import com.argent.market.MarketDataService
import com.argent.risk.RiskService
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import javax.sql.DataSource

internal data class GatewayRouteContext(
    val config: AppConfig,
    val dataSource: DataSource,
    val instrumentCatalog: InstrumentCatalogStore,
    val broker: EventBroker,
    val limiter: RedisRateLimiter,
    val auth: AuthService,
    val externalMarketData: ExternalMarketDataService,
    val priceAlertStore: PriceAlertStore,
    val trading: TradingService,
    val portfolioSnapshot: suspend (String, Map<String, BigDecimal>) -> com.argent.common.domain.PortfolioSnapshot,
    val market: MarketDataService,
    val assistant: ReadOnlyAssistantService,
    val assistantPortfolioHistory: AssistantPortfolioHistoryStore,
    val assistantOrderDrafts: AssistantOrderDraftService,
    val orders: OrderRepository,
    val eventStore: PostgresEventStore,
    val core: ArgentMatchingCore,
    val engine: MatchingEngine,
    val ledger: LedgerService,
    val risk: RiskService,
    val binanceTestnet: BinanceSpotTestnet,
    val binanceTestnetExecution: BinanceTestnetExecutionService,
    val liquidityProvider: LiquidityProvider?,
    val json: Json,
)
