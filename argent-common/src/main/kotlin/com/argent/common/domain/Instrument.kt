package com.argent.common.domain

import kotlinx.serialization.Serializable

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
        Instrument("SISE", "SISE", "TRY", AssetClass.EQUITY, 0, 2, "0.01", "1", "Şişecam"),
        Instrument("AAPL", "AAPL", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Apple"),
        Instrument("MSFT", "MSFT", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Microsoft"),
        Instrument("NVDA", "NVDA", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "NVIDIA"),
        Instrument("AMZN", "AMZN", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Amazon"),
        Instrument("GOOGL", "GOOGL", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Alphabet"),
        Instrument("META", "META", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Meta Platforms"),
        Instrument("TSLA", "TSLA", "USD", AssetClass.US_EQUITY, 6, 2, "0.01", "0.001", "Tesla"),
        Instrument("SPY", "SPY", "USD", AssetClass.FUND, 6, 2, "0.01", "0.001", "S&P 500 ETF · paper"),
        Instrument("QQQ", "QQQ", "USD", AssetClass.FUND, 6, 2, "0.01", "0.001", "Nasdaq-100 ETF · paper"),
        Instrument("VTI", "VTI", "USD", AssetClass.FUND, 6, 2, "0.01", "0.001", "Total US Market ETF · paper"),
        Instrument("AFT", "AFT", "TRY", AssetClass.FUND, 6, 4, "0.0001", "0.0001", "Ak Portföy Yeni Teknolojiler Fonu · paper"),
        Instrument("TI2", "TI2", "TRY", AssetClass.FUND, 6, 4, "0.0001", "0.0001", "İş Portföy BIST 30 Endeks Fonu · paper"),
        Instrument("AAPL-20261218-C-350", "AAPL-20261218-C-350", "USD", AssetClass.OPTION, 0, 2, "0.01", "1", "AAPL 18 Ara 2026 350 Call · simüle kontrat"),
        Instrument("AAPL-20261218-P-330", "AAPL-20261218-P-330", "USD", AssetClass.OPTION, 0, 2, "0.01", "1", "AAPL 18 Ara 2026 330 Put · simüle kontrat"),
        Instrument("XU030-202612", "XU030-202612", "TRY", AssetClass.VIOP, 0, 2, "0.01", "1", "BIST 30 Aralık 2026 VİOP · simüle kontrat")
    )

    private val bySymbol = all.associateBy { it.symbol }

    fun get(symbol: String): Instrument? = bySymbol[symbol.uppercase()]

    fun require(symbol: String): Instrument =
        get(symbol) ?: throw IllegalArgumentException("Unknown symbol: $symbol")

    fun quoteAsset(symbol: String): String = require(symbol).quoteAsset

    fun baseAsset(symbol: String): String = require(symbol).baseAsset
}
