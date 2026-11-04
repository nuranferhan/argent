package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.common.domain.Instruments
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstrumentCatalogStoreTest {
    @Test
    fun `catalog is loaded from database including an instrument absent from source registry`() {
        val dataSource = newDataSource()
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """CREATE TABLE instrument_catalog (
                            symbol VARCHAR(64) PRIMARY KEY, base_asset VARCHAR(64) NOT NULL,
                            quote_asset VARCHAR(16) NOT NULL, asset_class VARCHAR(24) NOT NULL,
                            qty_scale INTEGER NOT NULL, price_scale INTEGER NOT NULL,
                            tick_size VARCHAR(64) NOT NULL, lot_size VARCHAR(64) NOT NULL,
                            display_name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL,
                            expiry_date DATE NULL, reference_price VARCHAR(64) NULL
                        )""",
                    )
                    statement.execute(
                        """INSERT INTO instrument_catalog
                            (symbol,base_asset,quote_asset,asset_class,qty_scale,price_scale,tick_size,lot_size,display_name,active,expiry_date,reference_price)
                            VALUES
                            (
                                'NEWCOINUSDT','NEWCOIN','USDT','CRYPTO',4,3,'0.001','0.0001',
                                'New Coin',TRUE,NULL,'0.5'
                            )""",
                    )
                }
            }

            InstrumentCatalogStore(dataSource).loadIntoCache()

            val instrument = assertNotNull(Instruments.get("newcoinusdt"))
            assertEquals("New Coin", instrument.displayName)
            assertEquals("USDT", Instruments.quoteAsset("NEWCOINUSDT"))
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `admin catalog upsert inserts and updates without a source catalog edit`() {
        val dataSource = newDataSource()
        try {
            createCatalogTable(dataSource)
            val store = InstrumentCatalogStore(dataSource)
            val first =
                Instrument(
                    "NEWCOINUSDT",
                    "NEWCOIN",
                    "USDT",
                    AssetClass.CRYPTO,
                    4,
                    3,
                    "0.001",
                    "0.0001",
                    "New Coin",
                    referencePrice = "0.5",
                )
            store.upsert(first)
            assertEquals("New Coin", Instruments.require("NEWCOINUSDT").displayName)

            store.upsert(first.copy(displayName = "New Coin Token", active = false))

            val updated = assertNotNull(Instruments.get("NEWCOINUSDT"))
            assertEquals("New Coin Token", updated.displayName)
            assertEquals(false, updated.active)
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `expired derivative is deactivated in database and cache while future derivative stays tradable`() {
        val dataSource = newDataSource()
        try {
            createCatalogTable(dataSource)
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """INSERT INTO instrument_catalog
                            (symbol,base_asset,quote_asset,asset_class,qty_scale,price_scale,tick_size,lot_size,display_name,active,expiry_date,reference_price)
                            VALUES
                            ('OLDOPT','OLDOPT','USD','OPTION',0,2,'0.01','1','Expired option',TRUE,DATEADD('DAY',-1,CURRENT_DATE),'1'),
                            ('NEWOPT','NEWOPT','USD','OPTION',0,2,'0.01','1','Future option',TRUE,
                            DATEADD('DAY',1,CURRENT_DATE),'1')""",
                    )
                }
            }

            val store = InstrumentCatalogStore(dataSource)
            store.loadIntoCache()
            assertEquals(1, store.deactivateExpired())

            assertEquals(false, Instruments.require("OLDOPT").active)
            assertEquals(false, Instruments.isTradable("OLDOPT"))
            assertEquals(listOf("OLDOPT"), store.expiredDerivativeSymbols())
            assertEquals(true, Instruments.isTradable("NEWOPT"))
            assertEquals(0, store.deactivateExpired())
        } finally {
            dataSource.close()
        }
    }

    private fun newDataSource() =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:instrument_catalog_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                username = "sa"
                maximumPoolSize = 2
            },
        )

    private fun createCatalogTable(dataSource: HikariDataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """CREATE TABLE instrument_catalog (
                        symbol VARCHAR(64) PRIMARY KEY, base_asset VARCHAR(64) NOT NULL,
                        quote_asset VARCHAR(16) NOT NULL, asset_class VARCHAR(24) NOT NULL,
                        qty_scale INTEGER NOT NULL, price_scale INTEGER NOT NULL,
                        tick_size VARCHAR(64) NOT NULL, lot_size VARCHAR(64) NOT NULL,
                        display_name VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL,
                        expiry_date DATE NULL, reference_price VARCHAR(64) NULL
                    )""",
                )
            }
        }
    }
}
