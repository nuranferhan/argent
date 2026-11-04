CREATE TABLE market_candles (
    symbol VARCHAR(32) NOT NULL,
    candle_interval VARCHAR(8) NOT NULL,
    open_time BIGINT NOT NULL,
    close_time BIGINT NOT NULL,
    open_price NUMERIC(38, 18) NOT NULL,
    high_price NUMERIC(38, 18) NOT NULL,
    low_price NUMERIC(38, 18) NOT NULL,
    close_price NUMERIC(38, 18) NOT NULL,
    volume NUMERIC(38, 18) NOT NULL,
    CONSTRAINT pk_market_candles PRIMARY KEY (symbol, candle_interval, open_time)
);

CREATE INDEX idx_market_candles_retention
    ON market_candles (candle_interval, open_time);
