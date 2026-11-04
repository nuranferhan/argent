package com.argent.common.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class AssetClass { CRYPTO, EQUITY, US_EQUITY, FUND, OPTION, VIOP }

@Serializable
data class Instrument(
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val assetClass: AssetClass,
    val qtyScale: Int,
    val priceScale: Int,
    val tickSize: String,
    val lotSize: String,
    val displayName: String,
    val active: Boolean = true,
    val expiryDate: String? = null,
    val referencePrice: String? = null,
)

object Instruments {
    @Volatile private var bySymbol: Map<String, Instrument> = emptyMap()

    val all: List<Instrument> get() = bySymbol.values.sortedBy { it.symbol }

    fun installCatalog(instruments: List<Instrument>) {
        require(instruments.isNotEmpty()) { "Instrument catalog cannot be empty" }
        val normalized = instruments.map { it.copy(symbol = it.symbol.trim().uppercase()) }
        require(normalized.map { it.symbol }.distinct().size == normalized.size) { "Duplicate instrument symbol" }
        normalized.forEach(::validate)
        bySymbol = normalized.associateBy { it.symbol }
    }

    fun validate(instrument: Instrument) {
        require(instrument.symbol.matches(Regex("[A-Z0-9._-]{1,64}"))) { "Invalid instrument symbol" }
        require(instrument.qtyScale in 0..18 && instrument.priceScale in 0..18)
        require(instrument.tickSize.toBigDecimalOrNull()?.signum() == 1)
        require(instrument.lotSize.toBigDecimalOrNull()?.signum() == 1)
        require(instrument.baseAsset.isNotBlank() && instrument.baseAsset.length <= 64)
        require(instrument.quoteAsset.isNotBlank() && instrument.quoteAsset.length <= 16)
        require(instrument.displayName.isNotBlank() && instrument.displayName.length <= 200)
        instrument.expiryDate?.let { LocalDate.parse(it) }
        instrument.referencePrice?.let {
            require(it.toBigDecimalOrNull()?.signum() == 1) { "Reference price must be positive" }
        }
    }

    fun get(symbol: String): Instrument? = bySymbol[symbol.uppercase()]

    fun require(symbol: String): Instrument = get(symbol) ?: throw IllegalArgumentException("Unknown symbol: $symbol")

    fun isTradable(
        symbol: String,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        val instrument = get(symbol) ?: return false
        return instrument.active && (
            instrument.expiryDate?.let {
                runCatching { LocalDate.parse(it).isAfter(today) }.getOrDefault(false)
            } ?: true
        )
    }

    fun requireTradable(
        symbol: String,
        today: LocalDate = LocalDate.now(),
    ): Instrument =
        require(symbol).also { check(isTradable(symbol, today)) { "Instrument is inactive or expired: $symbol" } }

    fun quoteAsset(symbol: String): String = require(symbol).quoteAsset

    fun baseAsset(symbol: String): String = require(symbol).baseAsset
}
