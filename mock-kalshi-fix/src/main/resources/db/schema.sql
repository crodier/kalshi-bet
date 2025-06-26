-- Enable foreign key constraints
-- PostgreSQL has foreign keys enabled by default

-- Create Series table
CREATE TABLE IF NOT EXISTS series (
    ticker VARCHAR(255) PRIMARY KEY NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(100),
    contract_url TEXT,
    fee_type VARCHAR(20) DEFAULT 'quadratic',
    fee_multiplier INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create series tags junction table
CREATE TABLE IF NOT EXISTS series_tags (
    series_ticker VARCHAR(255) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (series_ticker, tag),
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create series settlement sources junction table
CREATE TABLE IF NOT EXISTS series_settlement_sources (
    series_ticker VARCHAR(255) NOT NULL,
    source VARCHAR(200) NOT NULL,
    PRIMARY KEY (series_ticker, source),
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create Events table
CREATE TABLE IF NOT EXISTS events (
    event_ticker VARCHAR(255) PRIMARY KEY NOT NULL,
    series_ticker VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    sub_title VARCHAR(500),
    category VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'unopened'
        CHECK (status IN ('unopened', 'open', 'closed', 'settled')),
    mutually_exclusive BOOLEAN DEFAULT false,
    yes_sub_title VARCHAR(500),
    no_sub_title VARCHAR(500),
    response_price_units VARCHAR(20) DEFAULT 'cents',
    expected_expiration_time BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create Markets table
CREATE TABLE IF NOT EXISTS markets (
    ticker VARCHAR(255) PRIMARY KEY NOT NULL,
    event_ticker VARCHAR(255) NOT NULL,
    market_type VARCHAR(20) NOT NULL DEFAULT 'binary'
        CHECK (market_type IN ('binary', 'categorical', 'scalar')),
    title VARCHAR(500) NOT NULL,
    subtitle VARCHAR(500),
    open_time BIGINT,
    close_time BIGINT,
    expiration_time BIGINT,
    expected_expiration_time BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'unopened'
        CHECK (status IN ('unopened', 'open', 'closed', 'settled')),
    
    -- Price data
    yes_bid INTEGER,
    yes_ask INTEGER,
    no_bid INTEGER,
    no_ask INTEGER,
    last_price INTEGER,
    
    -- Volume data
    volume BIGINT DEFAULT 0,
    volume_24h BIGINT DEFAULT 0,
    liquidity INTEGER DEFAULT 0,
    open_interest BIGINT DEFAULT 0,
    
    -- Financial data
    notional_value INTEGER DEFAULT 100,
    risk_limit_cents INTEGER,
    
    -- Strike information
    strike_type VARCHAR(20),
    floor_strike INTEGER,
    cap_strike INTEGER,
    
    -- Rules and metadata
    rules_primary TEXT,
    rules_secondary TEXT,
    response_price_units VARCHAR(20) DEFAULT 'cents',
    functional_print_id VARCHAR(255),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (event_ticker) REFERENCES events(event_ticker) ON DELETE CASCADE
);

-- Create indexes for performance

-- Series indexes
CREATE INDEX IF NOT EXISTS idx_series_category ON series(category);
CREATE INDEX IF NOT EXISTS idx_series_created_at ON series(created_at);

-- Series junction table indexes
CREATE INDEX IF NOT EXISTS idx_series_tags_ticker ON series_tags(series_ticker);
CREATE INDEX IF NOT EXISTS idx_series_tags_tag ON series_tags(tag);
CREATE INDEX IF NOT EXISTS idx_series_sources_ticker ON series_settlement_sources(series_ticker);

-- Events indexes
CREATE INDEX IF NOT EXISTS idx_events_series ON events(series_ticker);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_category ON events(category);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_expiration ON events(expected_expiration_time);

-- Markets indexes
CREATE INDEX IF NOT EXISTS idx_markets_event ON markets(event_ticker);
CREATE INDEX IF NOT EXISTS idx_markets_status ON markets(status);
CREATE INDEX IF NOT EXISTS idx_markets_type ON markets(market_type);
CREATE INDEX IF NOT EXISTS idx_markets_open_time ON markets(open_time);
CREATE INDEX IF NOT EXISTS idx_markets_close_time ON markets(close_time);
CREATE INDEX IF NOT EXISTS idx_markets_volume ON markets(volume);
CREATE INDEX IF NOT EXISTS idx_markets_last_price ON markets(last_price);
CREATE INDEX IF NOT EXISTS idx_markets_created_at ON markets(created_at);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_events_series_status ON events(series_ticker, status);
CREATE INDEX IF NOT EXISTS idx_markets_event_status ON markets(event_ticker, status);
CREATE INDEX IF NOT EXISTS idx_markets_status_open_time ON markets(status, open_time);
CREATE INDEX IF NOT EXISTS idx_markets_status_close_time ON markets(status, close_time);

-- Create functions to automatically update the updated_at field (PostgreSQL syntax)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers to automatically update the updated_at field
CREATE TRIGGER update_series_timestamp 
    BEFORE UPDATE ON series
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_events_timestamp 
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_markets_timestamp 
    BEFORE UPDATE ON markets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create Positions table
CREATE TABLE IF NOT EXISTS positions (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    market_id VARCHAR(255) NOT NULL,
    market_ticker VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    avg_price INTEGER NOT NULL DEFAULT 0,
    side VARCHAR(10) NOT NULL CHECK (side IN ('yes', 'no')),
    realized_pnl INTEGER DEFAULT 0,
    total_cost INTEGER DEFAULT 0,
    updated_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: one position per user per market per side
    UNIQUE(user_id, market_ticker, side)
);

-- Create positions indexes
CREATE INDEX IF NOT EXISTS idx_positions_user ON positions(user_id);
CREATE INDEX IF NOT EXISTS idx_positions_market ON positions(market_ticker);
CREATE INDEX IF NOT EXISTS idx_positions_user_market ON positions(user_id, market_ticker);
CREATE INDEX IF NOT EXISTS idx_positions_updated ON positions(updated_time);

-- Create trigger to automatically update the updated_at field for positions
CREATE TRIGGER update_positions_timestamp 
    BEFORE UPDATE ON positions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create Orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(255) PRIMARY KEY NOT NULL,
    client_order_id VARCHAR(255),
    user_id VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('yes', 'no')),
    action VARCHAR(10) NOT NULL CHECK (action IN ('buy', 'sell')),
    market_ticker VARCHAR(255) NOT NULL,
    order_type VARCHAR(20) NOT NULL CHECK (order_type IN ('limit', 'market')),
    quantity INTEGER NOT NULL,
    filled_quantity INTEGER DEFAULT 0,
    remaining_quantity INTEGER NOT NULL,
    price INTEGER,
    avg_fill_price INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('open', 'partially_filled', 'filled', 'canceled', 'rejected')),
    time_in_force VARCHAR(10) DEFAULT 'GTC',
    created_time BIGINT NOT NULL,
    updated_time BIGINT NOT NULL,
    expiration_time BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create orders indexes
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_market ON orders(market_ticker);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_time);
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders(user_id, status);

-- Create Fills table
CREATE TABLE IF NOT EXISTS fills (
    fill_id VARCHAR(255) PRIMARY KEY NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    market_id VARCHAR(255) NOT NULL,
    market_ticker VARCHAR(255) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('yes', 'no')),
    price INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    is_taker BOOLEAN NOT NULL,
    filled_time BIGINT NOT NULL,
    trade_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create fills indexes
CREATE INDEX IF NOT EXISTS idx_fills_order ON fills(order_id);
CREATE INDEX IF NOT EXISTS idx_fills_user ON fills(user_id);
CREATE INDEX IF NOT EXISTS idx_fills_market ON fills(market_ticker);
CREATE INDEX IF NOT EXISTS idx_fills_time ON fills(filled_time);
CREATE INDEX IF NOT EXISTS idx_fills_trade ON fills(trade_id);

-- Create Trades table
CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(255) PRIMARY KEY NOT NULL,
    market_ticker VARCHAR(255) NOT NULL,
    taker_order_id VARCHAR(255) NOT NULL,
    maker_order_id VARCHAR(255) NOT NULL,
    price INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    created_time BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create trades indexes
CREATE INDEX IF NOT EXISTS idx_trades_market ON trades(market_ticker);
CREATE INDEX IF NOT EXISTS idx_trades_time ON trades(created_time);
CREATE INDEX IF NOT EXISTS idx_trades_taker ON trades(taker_order_id);
CREATE INDEX IF NOT EXISTS idx_trades_maker ON trades(maker_order_id);

-- Create triggers for orders
CREATE TRIGGER update_orders_timestamp 
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();