package com.argent.gateway

import com.argent.ledger.LedgerService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PublicPaperBalanceTest : FunSpec({
    test("repeated public-trader setup seeds each virtual balance only once") {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:public_paper_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        try {
            val ledger = LedgerService(dataSource).also { it.initSchema() }

            repeat(2) { seedPublicPaperBalance(ledger, "new-public-trader") }

            ledger.getBalance("new-public-trader", "USDT").availableBd() shouldBe BigDecimal("25000")
            ledger.getBalance("new-public-trader", "TRY").availableBd() shouldBe BigDecimal("500000")
            ledger.verifyInvariant() shouldBe true
        } finally {
            dataSource.close()
        }
    }
})
