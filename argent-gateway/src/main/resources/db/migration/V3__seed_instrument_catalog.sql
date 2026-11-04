INSERT INTO instrument_catalog
    (symbol, base_asset, quote_asset, asset_class, qty_scale, price_scale, tick_size, lot_size, display_name, active, expiry_date)
VALUES
    ('BTCUSDT', 'BTC', 'USDT', 'CRYPTO', 8, 2, '0.01', '0.00000001', 'Bitcoin / USDT', TRUE, NULL),
    ('ETHUSDT', 'ETH', 'USDT', 'CRYPTO', 8, 2, '0.01', '0.00000001', 'Ethereum / USDT', TRUE, NULL),
    ('SOLUSDT', 'SOL', 'USDT', 'CRYPTO', 6, 2, '0.01', '0.000001', 'Solana / USDT', TRUE, NULL),
    ('THYAO', 'THYAO', 'TRY', 'EQUITY', 0, 2, '0.01', '1', 'Türk Hava Yolları', TRUE, NULL),
    ('GARAN', 'GARAN', 'TRY', 'EQUITY', 0, 2, '0.01', '1', 'Garanti BBVA', TRUE, NULL),
    ('ASELS', 'ASELS', 'TRY', 'EQUITY', 0, 2, '0.01', '1', 'Aselsan', TRUE, NULL),
    ('EREGL', 'EREGL', 'TRY', 'EQUITY', 0, 2, '0.01', '1', 'Ereğli Demir Çelik', TRUE, NULL),
    ('SISE', 'SISE', 'TRY', 'EQUITY', 0, 2, '0.01', '1', 'Şişecam', TRUE, NULL),
    ('AAPL', 'AAPL', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Apple', TRUE, NULL),
    ('MSFT', 'MSFT', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Microsoft', TRUE, NULL),
    ('NVDA', 'NVDA', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'NVIDIA', TRUE, NULL),
    ('AMZN', 'AMZN', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Amazon', TRUE, NULL),
    ('GOOGL', 'GOOGL', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Alphabet', TRUE, NULL),
    ('META', 'META', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Meta Platforms', TRUE, NULL),
    ('TSLA', 'TSLA', 'USD', 'US_EQUITY', 6, 2, '0.01', '0.001', 'Tesla', TRUE, NULL),
    ('SPY', 'SPY', 'USD', 'FUND', 6, 2, '0.01', '0.001', 'S&P 500 ETF · paper', TRUE, NULL),
    ('QQQ', 'QQQ', 'USD', 'FUND', 6, 2, '0.01', '0.001', 'Nasdaq-100 ETF · paper', TRUE, NULL),
    ('VTI', 'VTI', 'USD', 'FUND', 6, 2, '0.01', '0.001', 'Total US Market ETF · paper', TRUE, NULL),
    ('AFT', 'AFT', 'TRY', 'FUND', 6, 4, '0.0001', '0.0001', 'Ak Portföy Yeni Teknolojiler Fonu · paper', TRUE, NULL),
    ('TI2', 'TI2', 'TRY', 'FUND', 6, 4, '0.0001', '0.0001', 'İş Portföy BIST 30 Endeks Fonu · paper', TRUE, NULL),
    ('AAPL-20261218-C-350', 'AAPL-20261218-C-350', 'USD', 'OPTION', 0, 2, '0.01', '1', 'AAPL 18 Ara 2026 350 Call · simüle kontrat', TRUE, DATE '2026-12-18'),
    ('AAPL-20261218-P-330', 'AAPL-20261218-P-330', 'USD', 'OPTION', 0, 2, '0.01', '1', 'AAPL 18 Ara 2026 330 Put · simüle kontrat', TRUE, DATE '2026-12-18'),
    ('XU030-202612', 'XU030-202612', 'TRY', 'VIOP', 0, 2, '0.01', '1', 'BIST 30 Aralık 2026 VİOP · simüle kontrat', TRUE, DATE '2026-12-31')
ON CONFLICT (symbol) DO NOTHING;
