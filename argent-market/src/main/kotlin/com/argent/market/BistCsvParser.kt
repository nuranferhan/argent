package com.argent.market

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instruments
import java.math.BigDecimal
import java.time.Instant

/** Validates user-provided historical rows for local BIST paper replay. */
internal object BistCsvParser {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ROWS = 50_000

    fun parse(csv: String): List<BistHistoricalRow> {
        require(csv.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "CSV exceeds the 2 MiB limit" }
        val lines = csv.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.size in 2..(MAX_ROWS + 1)) { "CSV must contain a header and 1-$MAX_ROWS data rows" }
        val header = lines.first().removePrefix("\uFEFF").split(',').map { it.trim().lowercase() }
        require(
            header == listOf("timestamp", "symbol", "price") ||
                header == listOf("timestamp", "symbol", "price", "volume24h"),
        ) {
            "CSV header must be timestamp,symbol,price[,volume24h]"
        }
        val rows =
            lines.drop(1).mapIndexed { index, line ->
                val columns = line.split(',').map(String::trim)
                val rowNumber = index + 1
                require(columns.size == header.size) { "Invalid CSV column count at data row $rowNumber" }
                val timestamp =
                    columns[0].toLongOrNull() ?: runCatching { Instant.parse(columns[0]).toEpochMilli() }
                        .getOrElse { throw IllegalArgumentException("Invalid timestamp at data row $rowNumber") }
                require(timestamp > 0) { "Timestamp must be Unix milliseconds or ISO-8601" }
                val symbol = columns[1].uppercase()
                val instrument = Instruments.get(symbol)
                require(instrument != null && instrument.assetClass == AssetClass.EQUITY) {
                    "Only configured BIST equity symbols are accepted: $symbol"
                }
                val price =
                    columns[2].toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("Invalid price precision or tick size at data row $rowNumber")
                require(
                    price.signum() > 0 && price.scale() <= instrument.priceScale &&
                        price.remainder(BigDecimal(instrument.tickSize)).signum() == 0,
                ) {
                    "Invalid price precision or tick size at data row $rowNumber"
                }
                val volume =
                    columns.getOrNull(3)?.takeIf(String::isNotBlank)?.also {
                        val parsed = it.toBigDecimalOrNull()
                        require(parsed != null && parsed.signum() >= 0 && parsed.scale() <= 12) {
                            "Invalid volume at data row $rowNumber"
                        }
                    }
                BistHistoricalRow(timestamp, symbol, price.stripTrailingZeros().toPlainString(), volume)
            }
        require(rows.zipWithNext().all { (previous, next) -> previous.timestamp <= next.timestamp }) {
            "CSV rows must be in ascending timestamp order"
        }
        return rows
    }
}
