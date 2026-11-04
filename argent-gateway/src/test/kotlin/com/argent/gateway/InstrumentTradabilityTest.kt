package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.common.domain.Instruments
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstrumentTradabilityTest {
    init {
        Instruments.installCatalog(
            listOf(
                Instrument("AAPL-20261218-C-350", "AAPL-20261218-C-350", "USD", AssetClass.OPTION, 0, 2, "0.01", "1", "Call", expiryDate = "2026-12-18"),
                Instrument("AAPL-20261218-P-330", "AAPL-20261218-P-330", "USD", AssetClass.OPTION, 0, 2, "0.01", "1", "Put", expiryDate = "2026-12-18"),
                Instrument("XU030-202612", "XU030-202612", "TRY", AssetClass.VIOP, 0, 2, "0.01", "1", "VIOP", expiryDate = "2026-12-31"),
            ),
        )
    }

    @Test
    fun `dated contracts are tradable before expiry and blocked on expiry`() {
        assertTrue(Instruments.isTradable("AAPL-20261218-C-350", LocalDate.parse("2026-12-17")))
        assertFalse(Instruments.isTradable("AAPL-20261218-C-350", LocalDate.parse("2026-12-18")))
        assertFalse(Instruments.isTradable("AAPL-20261218-P-330", LocalDate.parse("2026-12-19")))
        assertFalse(Instruments.isTradable("XU030-202612", LocalDate.parse("2027-01-01")))
    }

    @Test
    fun `unknown symbol is not tradable`() {
        assertFalse(Instruments.isTradable("NOT-A-SYMBOL"))
    }
}
