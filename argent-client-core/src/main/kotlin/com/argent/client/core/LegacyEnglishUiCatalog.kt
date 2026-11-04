package com.argent.client.core

/** Aggregates legacy labels while screens migrate to typed strings. */
internal object LegacyEnglishUiCatalog {
    val values =
        (
            LegacyEnglishUiCatalogPart1.values +
                LegacyEnglishUiCatalogPart2.values +
                LegacyEnglishUiCatalogPart3.values +
                LegacyEnglishUiCatalogPart4.values +
                LegacyEnglishUiCatalogPart5.values +
                LegacyEnglishUiCatalogPart6.values
        )
}
