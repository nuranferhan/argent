package com.argent.gateway

import com.argent.common.domain.AssetClass
import com.argent.common.domain.Instrument
import com.argent.common.domain.Instruments
import java.sql.Date
import javax.sql.DataSource

/** Loads Flyway-managed instrument rows into the process cache. */
internal class InstrumentCatalogStore(private val dataSource: DataSource) {
    fun upsert(instrument: Instrument) {
        Instruments.validate(instrument)
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated =
                    connection.prepareStatement(
                        """UPDATE instrument_catalog SET base_asset = ?, quote_asset = ?, asset_class = ?, qty_scale = ?,
                        price_scale = ?, tick_size = ?, lot_size = ?, display_name = ?, active = ?, expiry_date = ?,
                        reference_price = ? WHERE symbol = ?""",
                    ).use { statement ->
                        bindInstrument(statement, instrument, includeSymbolFirst = false)
                        statement.executeUpdate()
                    }
                if (updated == 0) {
                    connection.prepareStatement(
                        """INSERT INTO instrument_catalog
                        (symbol, base_asset, quote_asset, asset_class, qty_scale, price_scale, tick_size, lot_size, display_name, active, expiry_date, reference_price)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    ).use { statement ->
                        bindInstrument(statement, instrument, includeSymbolFirst = true)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            }
        }
        loadIntoCache()
    }

    private fun bindInstrument(
        statement: java.sql.PreparedStatement,
        instrument: Instrument,
        includeSymbolFirst: Boolean,
    ) {
        var index = 1
        if (includeSymbolFirst) statement.setString(index++, instrument.symbol.trim().uppercase())
        statement.setString(index++, instrument.baseAsset)
        statement.setString(index++, instrument.quoteAsset)
        statement.setString(index++, instrument.assetClass.name)
        statement.setInt(index++, instrument.qtyScale)
        statement.setInt(index++, instrument.priceScale)
        statement.setString(index++, instrument.tickSize)
        statement.setString(index++, instrument.lotSize)
        statement.setString(index++, instrument.displayName)
        statement.setBoolean(index++, instrument.active)
        if (instrument.expiryDate == null) {
            statement.setNull(index++, java.sql.Types.DATE)
        } else {
            statement.setDate(index++, Date.valueOf(instrument.expiryDate))
        }
        statement.setString(index++, instrument.referencePrice)
        if (!includeSymbolFirst) statement.setString(index, instrument.symbol.trim().uppercase())
    }

    fun deactivateExpired(): Int {
        val changed =
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    val count =
                        connection.prepareStatement(
                            "UPDATE instrument_catalog SET active = FALSE WHERE active = TRUE AND expiry_date <= CURRENT_DATE",
                        ).use {
                            it.executeUpdate()
                        }
                    connection.commit()
                    count
                } catch (failure: Exception) {
                    connection.rollback()
                    throw failure
                }
            }
        if (changed > 0) loadIntoCache()
        return changed
    }

    fun expiredDerivativeSymbols(): List<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT symbol FROM instrument_catalog WHERE active = FALSE AND expiry_date <= CURRENT_DATE AND asset_class IN ('OPTION', 'VIOP')",
            ).use { statement ->
                statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
        }

    fun loadIntoCache() {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val instruments =
                    connection.prepareStatement(
                        "SELECT symbol, base_asset, quote_asset, asset_class, qty_scale, price_scale, tick_size, lot_size, display_name, active, expiry_date, reference_price FROM instrument_catalog",
                    ).use { statement ->
                        statement.executeQuery().use { rows ->
                            buildList {
                                while (rows.next()) add(
                                    Instrument(
                                        symbol = rows.getString("symbol"),
                                        baseAsset = rows.getString("base_asset"),
                                        quoteAsset = rows.getString("quote_asset"),
                                        assetClass = AssetClass.valueOf(rows.getString("asset_class")),
                                        qtyScale = rows.getInt("qty_scale"),
                                        priceScale = rows.getInt("price_scale"),
                                        tickSize = rows.getString("tick_size"),
                                        lotSize = rows.getString("lot_size"),
                                        displayName = rows.getString("display_name"),
                                        active = rows.getBoolean("active"),
                                        expiryDate = rows.getDate("expiry_date")?.toLocalDate()?.toString(),
                                        referencePrice = rows.getString("reference_price"),
                                    ),
                                )
                            }
                        }
                    }
                check(instruments.isNotEmpty()) { "Instrument catalog is empty; apply the instrument seed migration" }
                Instruments.installCatalog(instruments)
                connection.commit()
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            }
        }
    }
}
