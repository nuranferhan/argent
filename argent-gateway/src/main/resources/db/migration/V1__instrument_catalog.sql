CREATE TABLE instrument_catalog (
    symbol VARCHAR(64) PRIMARY KEY,
    base_asset VARCHAR(64) NOT NULL,
    quote_asset VARCHAR(16) NOT NULL,
    asset_class VARCHAR(24) NOT NULL,
    qty_scale INTEGER NOT NULL CHECK (qty_scale BETWEEN 0 AND 18),
    price_scale INTEGER NOT NULL CHECK (price_scale BETWEEN 0 AND 18),
    tick_size VARCHAR(64) NOT NULL,
    lot_size VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expiry_date DATE
);
