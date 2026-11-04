package com.argent.gateway

import com.argent.common.domain.Instruments

/** Bounds read-side query inputs before they reach SQL or allocate engine actors. */
internal object MarketReadValidation {
    fun symbol(raw: String?): String? = raw?.trim()?.uppercase()?.takeIf { Instruments.get(it) != null }

    fun boundedLimit(
        raw: String?,
        default: Int,
        maximum: Int,
    ): Int = raw?.toIntOrNull()?.coerceIn(1, maximum) ?: default
}
