-- Create tables for H2 test database
CREATE TABLE IF NOT EXISTS series (
    ticker VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255),
    category VARCHAR(100),
    sub_category VARCHAR(100),
    tags TEXT,
    status VARCHAR(50),
    contract_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS events (
    ticker VARCHAR(255) PRIMARY KEY,
    series_ticker VARCHAR(255),
    sub_title VARCHAR(255),
    category VARCHAR(100),
    title VARCHAR(255),
    expected_resolution_date TIMESTAMP,
    expected_settlement_date TIMESTAMP,
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (series_ticker) REFERENCES series(ticker)
);

CREATE TABLE IF NOT EXISTS markets (
    ticker VARCHAR(255) PRIMARY KEY,
    event_ticker VARCHAR(255),
    market_type VARCHAR(50),
    title VARCHAR(255),
    subtitle VARCHAR(255),
    yes_subtitle VARCHAR(255),
    no_subtitle VARCHAR(255),
    open_time BIGINT,
    close_time BIGINT,
    expected_expiration_time BIGINT,
    actual_expiration_time BIGINT,
    settlement_time BIGINT,
    status VARCHAR(50),
    result VARCHAR(10),
    yes_bid INTEGER,
    yes_ask INTEGER,
    no_bid INTEGER,
    no_ask INTEGER,
    last_price INTEGER,
    previous_yes_bid INTEGER,
    previous_yes_ask INTEGER,
    previous_price INTEGER,
    volume INTEGER DEFAULT 0,
    volume_24h INTEGER DEFAULT 0,
    liquidity INTEGER DEFAULT 0,
    open_interest INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_ticker) REFERENCES events(ticker)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(36) PRIMARY KEY,
    client_order_id VARCHAR(255),
    user_id VARCHAR(255),
    market_ticker VARCHAR(255),
    side VARCHAR(10),
    action VARCHAR(10),
    type VARCHAR(10),
    status VARCHAR(20),
    yes_price INTEGER,
    no_price INTEGER,
    count INTEGER,
    remaining_count INTEGER,
    expiration_unix_ts BIGINT,
    created_time BIGINT,
    last_update_time BIGINT,
    FOREIGN KEY (market_ticker) REFERENCES markets(ticker)
);

CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36),
    market_ticker VARCHAR(255),
    side VARCHAR(10),
    yes_price INTEGER,
    no_price INTEGER,
    count INTEGER,
    created_time BIGINT,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (market_ticker) REFERENCES markets(ticker)
);

CREATE TABLE IF NOT EXISTS positions (
    position_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255),
    market_ticker VARCHAR(255),
    position INTEGER DEFAULT 0,
    realized_pnl INTEGER DEFAULT 0,
    resting_orders_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (market_ticker) REFERENCES markets(ticker),
    UNIQUE (user_id, market_ticker)
);

CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) PRIMARY KEY,
    access_key VARCHAR(255) UNIQUE,
    email VARCHAR(255),
    full_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_orders_market ON orders(market_ticker);
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_trades_market ON trades(market_ticker);
CREATE INDEX IF NOT EXISTS idx_positions_user ON positions(user_id);