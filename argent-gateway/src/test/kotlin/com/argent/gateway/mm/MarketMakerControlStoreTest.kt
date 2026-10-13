package com.argent.gateway.mm

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.Database

class MarketMakerControlStoreTest : FunSpec({
    test("kill switch state survives service reconstruction") {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:mm_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            maximumPoolSize = 2
        })
        try {
            val database = Database.connect(dataSource)
            val firstInstance = MarketMakerControlStore(database).also { it.initSchema() }
            firstInstance.isEnabled() shouldBe true
            firstInstance.setEnabled(false)

            MarketMakerControlStore(database).isEnabled() shouldBe false
            firstInstance.setEnabled(true)
            MarketMakerControlStore(database).isEnabled() shouldBe true
        } finally {
            dataSource.close()
        }
    }
})
