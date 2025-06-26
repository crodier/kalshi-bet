-- Insert test series
INSERT INTO series (ticker, title, category, contract_url, frequency)
VALUES 
    ('INXD', 'Will the S&P close above this level?', 'Financial', 'https://kalshi.com/series/INXD', 'daily'),
    ('BTCZ', 'Will Bitcoin close above this level?', 'Crypto', 'https://kalshi.com/series/BTCZ', 'daily'),
    ('TRUMPWIN', 'Will Trump win the 2024 election?', 'Politics', 'https://kalshi.com/series/TRUMPWIN', 'single'),
    ('DUMMY', 'Test series for development', 'Test', 'https://kalshi.com/series/DUMMY', 'single')
ON CONFLICT (ticker) DO NOTHING;

-- Insert test events  
INSERT INTO events (event_ticker, series_ticker, title, category, sub_title, status, mutually_exclusive, expected_expiration_time)
VALUES
    ('INXD-23DEC29', 'INXD', 'S&P 500 close December 29', 'Financial', 'S&P 500 daily close on Dec 29, 2023', 'open', true, 1703894400000),
    ('BTCZ-23DEC31', 'BTCZ', 'Bitcoin close December 31', 'Crypto', 'Bitcoin daily close on Dec 31, 2023', 'open', true, 1704067200000),
    ('TRUMPWIN-24NOV05', 'TRUMPWIN', 'Trump wins 2024 election', 'Politics', 'Trump wins 2024 presidential election', 'open', true, 1730851200000),
    ('DUMMY_TEST', 'DUMMY', 'Test Event', 'Test', 'Test event for development', 'open', true, 1800000000000)
ON CONFLICT (event_ticker) DO NOTHING;

-- Insert test markets
INSERT INTO markets (
    ticker, event_ticker, market_type, title, subtitle,
    open_time, close_time, expected_expiration_time, expiration_time,
    status, yes_bid, yes_ask, no_bid, no_ask,
    last_price, volume, volume_24h, liquidity, open_interest,
    notional_value, risk_limit_cents,
    strike_type, floor_strike, cap_strike,
    rules_primary, rules_secondary, response_price_units, functional_print_id
) VALUES
    -- S&P 500 market
    ('INXD-23DEC29-B5000', 'INXD-23DEC29', 'binary', 
     'Will S&P 500 close above 5000 on Dec 29?', 'S&P 500 > 5000',
     1703808000000, 1703894400000, 1703894400000, NULL,
     'open', 45, 55, 45, 55,
     50, 1000, 5000, 100000, 2500,
     100, 100000,
     'binary', NULL, NULL,
     'Will S&P 500 close above 5000', NULL, 'cents', NULL),
     
    -- Bitcoin market
    ('BTCZ-23DEC31-B50000', 'BTCZ-23DEC31', 'binary',
     'Will Bitcoin close above $50,000 on Dec 31?', 'BTC > $50,000',
     1703980800000, 1704067200000, 1704067200000, NULL,
     'open', 30, 40, 60, 70,
     35, 2000, 8000, 150000, 3500,
     100, 100000,
     'binary', NULL, NULL,
     'Will Bitcoin close above $50,000', NULL, 'cents', NULL),
     
    -- Trump election market
    ('TRUMPWIN-24NOV05', 'TRUMPWIN-24NOV05', 'binary',
     'Will Trump win the 2024 presidential election?', 'Trump wins presidency',
     1730764800000, 1730851200000, 1730851200000, NULL,
     'open', 38, 42, 58, 62,
     40, 50000, 200000, 500000, 25000,
     100, 100000,
     'binary', NULL, NULL,
     'Will Trump win the 2024 presidential election', NULL, 'cents', NULL),
     
    -- Test market
    ('DUMMY_TEST', 'DUMMY_TEST', 'binary',
     'Test Market for Development', 'Testing orderbook functionality',
     1700000000000, 1800000000000, 1800000000000, NULL,
     'open', 48, 52, 48, 52,
     50, 100, 500, 10000, 250,
     100, 100000,
     'binary', NULL, NULL,
     'Test market for development purposes', NULL, 'cents', NULL)
ON CONFLICT (ticker) DO NOTHING;

-- Insert some initial open orders for order book
INSERT INTO orders (
    order_id, client_order_id, user_id, side, action, market_ticker,
    order_type, quantity, filled_quantity, remaining_quantity,
    price, avg_fill_price, status, time_in_force,
    created_time, updated_time, expiration_time
) VALUES
    -- DUMMY_TEST market orders
    ('ORD-INIT-001', 'client-001', 'USER-MARKET-MAKER', 'yes', 'buy', 'DUMMY_TEST',
     'limit', 100, 0, 100, 45, NULL, 'open', 'GTC',
     1700001000000, 1700001000000, NULL),
     
    ('ORD-INIT-002', 'client-002', 'USER-MARKET-MAKER', 'yes', 'buy', 'DUMMY_TEST',
     'limit', 200, 0, 200, 48, NULL, 'open', 'GTC',
     1700002000000, 1700002000000, NULL),
     
    ('ORD-INIT-003', 'client-003', 'USER-MARKET-MAKER', 'yes', 'sell', 'DUMMY_TEST',
     'limit', 150, 0, 150, 52, NULL, 'open', 'GTC',
     1700003000000, 1700003000000, NULL),
     
    ('ORD-INIT-004', 'client-004', 'USER-MARKET-MAKER', 'yes', 'sell', 'DUMMY_TEST',
     'limit', 100, 0, 100, 55, NULL, 'open', 'GTC',
     1700004000000, 1700004000000, NULL),
     
    -- INXD market orders
    ('ORD-INIT-005', 'client-005', 'USER-MARKET-MAKER', 'yes', 'buy', 'INXD-23DEC29-B5000',
     'limit', 50, 0, 50, 44, NULL, 'open', 'GTC',
     1700005000000, 1700005000000, NULL),
     
    ('ORD-INIT-006', 'client-006', 'USER-MARKET-MAKER', 'yes', 'sell', 'INXD-23DEC29-B5000',
     'limit', 50, 0, 50, 56, NULL, 'open', 'GTC',
     1700006000000, 1700006000000, NULL),
     
    -- BTCZ market orders
    ('ORD-INIT-007', 'client-007', 'USER-MARKET-MAKER', 'yes', 'buy', 'BTCZ-23DEC31-B50000',
     'limit', 75, 0, 75, 29, NULL, 'open', 'GTC',
     1700007000000, 1700007000000, NULL),
     
    ('ORD-INIT-008', 'client-008', 'USER-MARKET-MAKER', 'yes', 'sell', 'BTCZ-23DEC31-B50000',
     'limit', 75, 0, 75, 41, NULL, 'open', 'GTC',
     1700008000000, 1700008000000, NULL),
     
    -- TRUMPWIN market orders
    ('ORD-INIT-009', 'client-009', 'USER-MARKET-MAKER', 'yes', 'buy', 'TRUMPWIN-24NOV05',
     'limit', 500, 0, 500, 37, NULL, 'open', 'GTC',
     1700009000000, 1700009000000, NULL),
     
    ('ORD-INIT-010', 'client-010', 'USER-MARKET-MAKER', 'yes', 'sell', 'TRUMPWIN-24NOV05',
     'limit', 500, 0, 500, 43, NULL, 'open', 'GTC',
     1700010000000, 1700010000000, NULL),
     
    -- NO side orders for DUMMY_TEST
    ('ORD-INIT-011', 'client-011', 'USER-MARKET-MAKER', 'no', 'buy', 'DUMMY_TEST',
     'limit', 120, 0, 120, 47, NULL, 'open', 'GTC',
     1700011000000, 1700011000000, NULL),
     
    ('ORD-INIT-012', 'client-012', 'USER-MARKET-MAKER', 'no', 'buy', 'DUMMY_TEST',
     'limit', 180, 0, 180, 49, NULL, 'open', 'GTC',
     1700012000000, 1700012000000, NULL),
     
    ('ORD-INIT-013', 'client-013', 'USER-MARKET-MAKER', 'no', 'sell', 'DUMMY_TEST',
     'limit', 140, 0, 140, 51, NULL, 'open', 'GTC',
     1700013000000, 1700013000000, NULL),
     
    ('ORD-INIT-014', 'client-014', 'USER-MARKET-MAKER', 'no', 'sell', 'DUMMY_TEST',
     'limit', 160, 0, 160, 54, NULL, 'open', 'GTC',
     1700014000000, 1700014000000, NULL),
     
    -- NO side orders for INXD market
    ('ORD-INIT-015', 'client-015', 'USER-MARKET-MAKER', 'no', 'buy', 'INXD-23DEC29-B5000',
     'limit', 60, 0, 60, 53, NULL, 'open', 'GTC',
     1700015000000, 1700015000000, NULL),
     
    ('ORD-INIT-016', 'client-016', 'USER-MARKET-MAKER', 'no', 'sell', 'INXD-23DEC29-B5000',
     'limit', 70, 0, 70, 57, NULL, 'open', 'GTC',
     1700016000000, 1700016000000, NULL),
     
    -- NO side orders for BTCZ market
    ('ORD-INIT-017', 'client-017', 'USER-MARKET-MAKER', 'no', 'buy', 'BTCZ-23DEC31-B50000',
     'limit', 80, 0, 80, 68, NULL, 'open', 'GTC',
     1700017000000, 1700017000000, NULL),
     
    ('ORD-INIT-018', 'client-018', 'USER-MARKET-MAKER', 'no', 'sell', 'BTCZ-23DEC31-B50000',
     'limit', 90, 0, 90, 72, NULL, 'open', 'GTC',
     1700018000000, 1700018000000, NULL),
     
    -- NO side orders for TRUMPWIN market
    ('ORD-INIT-019', 'client-019', 'USER-MARKET-MAKER', 'no', 'buy', 'TRUMPWIN-24NOV05',
     'limit', 400, 0, 400, 56, NULL, 'open', 'GTC',
     1700019000000, 1700019000000, NULL),
     
    ('ORD-INIT-020', 'client-020', 'USER-MARKET-MAKER', 'no', 'sell', 'TRUMPWIN-24NOV05',
     'limit', 600, 0, 600, 63, NULL, 'open', 'GTC',
     1700020000000, 1700020000000, NULL)
ON CONFLICT (order_id) DO NOTHING;

-- Insert MARKET_MAKER market for automated market making
INSERT INTO series (ticker, title, category, contract_url, frequency)
VALUES 
    ('MM', 'Market Maker Test Series', 'Test', 'https://kalshi.com/series/MM', 'single')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO events (event_ticker, series_ticker, title, category, sub_title, status, mutually_exclusive, expected_expiration_time)
VALUES
    ('MM_TEST', 'MM', 'Market Maker Test Event', 'Test', 'Automated market making test', 'open', true, 2000000000000)
ON CONFLICT (event_ticker) DO NOTHING;

INSERT INTO markets (
    ticker, event_ticker, market_type, title, subtitle,
    open_time, close_time, expected_expiration_time, expiration_time,
    status, yes_bid, yes_ask, no_bid, no_ask,
    last_price, volume, volume_24h, liquidity, open_interest,
    notional_value, risk_limit_cents,
    strike_type, floor_strike, cap_strike,
    rules_primary, rules_secondary, response_price_units, functional_print_id
) VALUES
    ('MARKET_MAKER', 'MM_TEST', 'binary',
     'Market Maker Test Market', 'Automated market making',
     1700000000000, 2000000000000, 2000000000000, NULL,
     'open', 45, 55, 45, 55,
     50, 0, 0, 0, 0,
     100, 100000,
     'binary', NULL, NULL,
     'Market maker test market', NULL, 'cents', NULL)
ON CONFLICT (ticker) DO NOTHING;

-- Initial orders for MARKET_MAKER market
INSERT INTO orders (
    order_id, client_order_id, user_id, side, action, market_ticker,
    order_type, quantity, filled_quantity, remaining_quantity,
    price, avg_fill_price, status, time_in_force,
    created_time, updated_time, expiration_time
) VALUES
    -- YES side orders for MARKET_MAKER
    ('ORD-MM-001', 'mm-client-001', 'USER-MARKET-MAKER', 'yes', 'buy', 'MARKET_MAKER',
     'limit', 100, 0, 100, 45, NULL, 'open', 'GTC',
     1700021000000, 1700021000000, NULL),
     
    ('ORD-MM-002', 'mm-client-002', 'USER-MARKET-MAKER', 'yes', 'buy', 'MARKET_MAKER',
     'limit', 150, 0, 150, 43, NULL, 'open', 'GTC',
     1700022000000, 1700022000000, NULL),
     
    ('ORD-MM-003', 'mm-client-003', 'USER-MARKET-MAKER', 'yes', 'sell', 'MARKET_MAKER',
     'limit', 120, 0, 120, 52, NULL, 'open', 'GTC',
     1700023000000, 1700023000000, NULL),
     
    ('ORD-MM-004', 'mm-client-004', 'USER-MARKET-MAKER', 'yes', 'sell', 'MARKET_MAKER',
     'limit', 100, 0, 100, 55, NULL, 'open', 'GTC',
     1700024000000, 1700024000000, NULL),
     
    -- NO side orders for MARKET_MAKER
    ('ORD-MM-005', 'mm-client-005', 'USER-MARKET-MAKER', 'no', 'buy', 'MARKET_MAKER',
     'limit', 110, 0, 110, 48, NULL, 'open', 'GTC',
     1700025000000, 1700025000000, NULL),
     
    ('ORD-MM-006', 'mm-client-006', 'USER-MARKET-MAKER', 'no', 'buy', 'MARKET_MAKER',
     'limit', 130, 0, 130, 45, NULL, 'open', 'GTC',
     1700026000000, 1700026000000, NULL),
     
    ('ORD-MM-007', 'mm-client-007', 'USER-MARKET-MAKER', 'no', 'sell', 'MARKET_MAKER',
     'limit', 140, 0, 140, 55, NULL, 'open', 'GTC',
     1700027000000, 1700027000000, NULL),
     
    ('ORD-MM-008', 'mm-client-008', 'USER-MARKET-MAKER', 'no', 'sell', 'MARKET_MAKER',
     'limit', 160, 0, 160, 57, NULL, 'open', 'GTC',
     1700028000000, 1700028000000, NULL)
ON CONFLICT (order_id) DO NOTHING;