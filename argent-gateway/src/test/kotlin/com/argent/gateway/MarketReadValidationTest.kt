package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.common.domain.Instruments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarketReadValidationTest {
    @Test
    fun `symbol validation trims normalizes and rejects unknown symbols`() {
        Instruments.installCatalog(
            listOf(
                Instrument("BTCUSDT", "BTC", "USDT", AssetClass.CRYPTO, 8, 2, "0.01", "0.00000001", "Bitcoin / USDT"),
            ),
        )
        assertEquals("BTCUSDT", MarketReadValidation.symbol(" btcusdt "))
        assertNull(MarketReadValidation.symbol("unknown"))
        assertNull(MarketReadValidation.symbol(null))
    }

    @Test
    fun `query limits use defaults and stay within configured bounds`() {
        assertEquals(100, MarketReadValidation.boundedLimit(null, default = 100, maximum = 500))
        assertEquals(1, MarketReadValidation.boundedLimit("-10", default = 100, maximum = 500))
        assertEquals(500, MarketReadValidation.boundedLimit("10000", default = 100, maximum = 500))
        assertEquals(100, MarketReadValidation.boundedLimit("invalid", default = 100, maximum = 500))
    }
}
