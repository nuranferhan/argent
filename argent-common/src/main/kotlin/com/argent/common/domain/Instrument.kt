package com.argent.common.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AssetClass { CRYPTO, EQUITY }

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
    val displayName: String
)

object Instruments {
    val all: List<Instrument> = listOf(
        Instrument("BTCUSDT", "BTC", "USDT", AssetClass.CRYPTO, 8, 2, "0.01", "0.00000001", "Bitcoin / USDT"),
        Instrument("ETHUSDT", "ETH", "USDT", AssetClass.CRYPTO, 8, 2, "0.01", "0.00000001", "Ethereum / USDT"),
        Instrument("SOLUSDT", "SOL", "USDT", AssetClass.CRYPTO, 6, 2, "0.01", "0.000001", "Solana / USDT"),
        Instrument("THYAO", "THYAO", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Türk Hava Yolları"),
        Instrument("GARAN", "GARAN", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Garanti BBVA"),
        Instrument("ASELS", "ASELS", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Aselsan"),
        Instrument("EREGL", "EREGL", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Ereğli Demir Çelik"),
        Instrument("SISE", "SISE", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Şişecam")
    )

    private val bySymbol = all.associateBy { it.symbol }

    fun get(symbol: String): Instrument? = bySymbol[symbol.uppercase()]

    fun require(symbol: String): Instrument =
        get(symbol) ?: throw IllegalArgumentException("Unknown symbol: $symbol")

    fun quoteAsset(symbol: String): String = require(symbol).quoteAsset

    fun baseAsset(symbol: String): String = require(symbol).baseAsset
}
