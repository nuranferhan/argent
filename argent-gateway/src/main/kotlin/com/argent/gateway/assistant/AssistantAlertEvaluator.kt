package com.argent.gateway.assistant

import com.argent.common.domain.PortfolioSnapshot
import com.argent.market.TickerUpdate
import java.math.BigDecimal

internal object AssistantAlertEvaluator {
    private val balanceChangeAlertPct =
        System.getenv("ARGENT_AI_BALANCE_CHANGE_ALERT_PCT")?.toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 } ?: BigDecimal("10")
    private val concentrationAlertPct =
        System.getenv("ARGENT_AI_CONCENTRATION_ALERT_PCT")?.toIntOrNull()?.coerceIn(1, 100) ?: 80

    fun asksForAlerts(question: String) =
        listOf("uyarı", "risk kontrol", "olağandışı", "veri akışı", "akış kesildi", "alert", "risk check", "unusual", "data feed", "feed stopped").any(
            question.lowercase()::contains,
        )

    fun currentAlerts(
        portfolio: PortfolioSnapshot,
        ticker: TickerUpdate?,
        marketMode: String,
        now: Long,
        baseline: AssistantPortfolioHistoryStore.Baseline?,
        english: Boolean = false,
    ): List<String> {
        val findings = mutableListOf<String>()
        if (marketMode.contains("DOWN", true) || marketMode.contains("DISCONNECTED", true) || marketMode.contains("UNAVAILABLE", true) || marketMode.contains("STALE", true)) {
            findings += if (english) "Market data feed appears disconnected or delayed: $marketMode" else "Piyasa veri akışı bağlantısız veya gecikmeli görünüyor: $marketMode"
        }
        if (ticker != null && now - ticker.timestamp > 60_000L) {
            findings += if (english) "The ${ticker.symbol} quote is over 60 seconds old and may be stale." else "${ticker.symbol} kotasyonu 60 saniyeden eski; fiyat güncel olmayabilir."
        }
        if (portfolio.riskScore >= concentrationAlertPct) {
            findings += if (english) "High position concentration: the largest position is about ${portfolio.riskScore}% of total position value." else "Pozisyon yoğunlaşması yüksek: en büyük pozisyonun toplam pozisyon büyüklüğündeki payı yaklaşık %${portfolio.riskScore}."
        }
        val held = portfolio.balances.filter { runCatching { BigDecimal(it.held).signum() > 0 }.getOrDefault(false) }
        held.forEach {
            findings += if (english) "${it.held} ${it.asset} is reserved." else "${it.asset} varlığında ${it.held} bloke bakiye bulunuyor."
        }
        if (baseline == null || baseline.sampledAt >= now / (5 * 60 * 1000L) * (5 * 60 * 1000L)) {
            findings += if (english) "No earlier snapshot is available today, so unusual balance changes cannot be assessed." else "Bugün için önceki karşılaştırma kaydı yok; olağandışı bakiye değişimi değerlendirilemiyor."
        } else {
            val previousBalances = baseline.snapshot.balances.associateBy { it.asset }
            portfolio.balances.forEach { current ->
                val previous = previousBalances[current.asset] ?: return@forEach
                val before = previous.totalBd()
                if (before.signum() == 0) return@forEach
                val deltaPct =
                    current.totalBd().subtract(before).abs()
                        .multiply(BigDecimal("100")).divide(before.abs(), 2, java.math.RoundingMode.HALF_UP)
                if (deltaPct >= balanceChangeAlertPct) {
                    findings += if (english) "${current.asset} total balance changed ${deltaPct.stripTrailingZeros().toPlainString()}% from today's first snapshot; threshold is ${balanceChangeAlertPct.stripTrailingZeros().toPlainString()}%." else "${current.asset} toplam bakiyesi ilk gün içi ölçüme göre %${deltaPct.stripTrailingZeros().toPlainString()} değişti; değişim eşiği %${balanceChangeAlertPct.stripTrailingZeros().toPlainString()}."
                }
            }
        }
        return findings.ifEmpty {
            listOf(
                if (english) "No configured alert threshold is currently breached." else "Mevcut verilerde tanımlı bir uyarı eşiğini aşan durum görünmüyor.",
            )
        }
    }
}
