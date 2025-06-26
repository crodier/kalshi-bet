-- SQLite schema for tests
-- Note: SQLite doesn't support some PostgreSQL features, so this is a simplified version

-- Create Series table
CREATE TABLE IF NOT EXISTS series (
    ticker TEXT PRIMARY KEY NOT NULL,
    frequency TEXT NOT NULL,
    title TEXT NOT NULL,
    category TEXT,
    contract_url TEXT,
    fee_type TEXT DEFAULT 'quadratic',
    fee_multiplier INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create series tags junction table
CREATE TABLE IF NOT EXISTS series_tags (
    series_ticker TEXT NOT NULL,
    tag TEXT NOT NULL,
    PRIMARY KEY (series_ticker, tag),
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create series settlement sources junction table
CREATE TABLE IF NOT EXISTS series_settlement_sources (
    series_ticker TEXT NOT NULL,
    source TEXT NOT NULL,
    PRIMARY KEY (series_ticker, source),
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create Events table
CREATE TABLE IF NOT EXISTS events (
    event_ticker TEXT PRIMARY KEY NOT NULL,
    series_ticker TEXT NOT NULL,
    title TEXT NOT NULL,
    sub_title TEXT,
    category TEXT,
    status TEXT NOT NULL DEFAULT 'unopened'
        CHECK (status IN ('unopened', 'open', 'closed', 'settled')),
    mutually_exclusive INTEGER DEFAULT 0,
    yes_sub_title TEXT,
    no_sub_title TEXT,
    response_price_units TEXT DEFAULT 'cents',
    expected_expiration_time INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);

-- Create Markets table
CREATE TABLE IF NOT EXISTS markets (
    ticker TEXT PRIMARY KEY NOT NULL,
    event_ticker TEXT NOT NULL,
    market_type TEXT NOT NULL DEFAULT 'binary'
        CHECK (market_type IN ('binary', 'categorical', 'scalar')),
    title TEXT NOT NULL,
    subtitle TEXT,
    open_time INTEGER,
    close_time INTEGER,
    expiration_time INTEGER,
    expected_expiration_time INTEGER,
    status TEXT NOT NULL DEFAULT 'unopened'
        CHECK (status IN ('unopened', 'open', 'closed', 'settled')),
    
    -- Price data
    yes_bid INTEGER,
    yes_ask INTEGER,
    no_bid INTEGER,
    no_ask INTEGER,
    last_price INTEGER,
    
    -- Volume data
    volume INTEGER DEFAULT 0,
    volume_24h INTEGER DEFAULT 0,
    liquidity INTEGER DEFAULT 0,
    open_interest INTEGER DEFAULT 0,
    
    -- Financial data
    notional_value INTEGER DEFAULT 100,
    risk_limit_cents INTEGER,
    
    -- Strike information
    strike_type TEXT,
    floor_strike INTEGER,
    cap_strike INTEGER,
    
    -- Rules and metadata
    rules_primary TEXT,
    rules_secondary TEXT,
    response_price_units TEXT DEFAULT 'cents',
    functional_print_id TEXT,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (event_ticker) REFERENCES events(event_ticker) ON DELETE CASCADE
);

-- Create Positions table
CREATE TABLE IF NOT EXISTS positions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    market_id TEXT NOT NULL,
    market_ticker TEXT NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    avg_price INTEGER NOT NULL DEFAULT 0,
    side TEXT NOT NULL CHECK (side IN ('yes', 'no')),
    realized_pnl INTEGER DEFAULT 0,
    total_cost INTEGER DEFAULT 0,
    updated_time INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: one position per user per market per side
    UNIQUE(user_id, market_ticker, side)
);

-- Create Orders table
CREATE TABLE IF NOT EXISTS orders (
    order_id TEXT PRIMARY KEY NOT NULL,
    client_order_id TEXT,
    user_id TEXT NOT NULL,
    side TEXT NOT NULL CHECK (side IN ('yes', 'no')),
    action TEXT NOT NULL CHECK (action IN ('buy', 'sell')),
    market_ticker TEXT NOT NULL,
    order_type TEXT NOT NULL CHECK (order_type IN ('limit', 'market')),
    quantity INTEGER NOT NULL,
    filled_quantity INTEGER DEFAULT 0,
    remaining_quantity INTEGER NOT NULL,
    price INTEGER,
    avg_fill_price INTEGER,
    status TEXT NOT NULL CHECK (status IN ('open', 'partially_filled', 'filled', 'canceled', 'rejected')),
    time_in_force TEXT DEFAULT 'GTC',
    created_time INTEGER NOT NULL,
    updated_time INTEGER NOT NULL,
    expiration_time INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Fills table
CREATE TABLE IF NOT EXISTS fills (
    fill_id TEXT PRIMARY KEY NOT NULL,
    order_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    market_id TEXT NOT NULL,
    market_ticker TEXT NOT NULL,
    side TEXT NOT NULL CHECK (side IN ('yes', 'no')),
    price INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    is_taker INTEGER NOT NULL,
    filled_time INTEGER NOT NULL,
    trade_id TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create Trades table
CREATE TABLE IF NOT EXISTS trades (
    trade_id TEXT PRIMARY KEY NOT NULL,
    market_ticker TEXT NOT NULL,
    taker_order_id TEXT NOT NULL,
    maker_order_id TEXT NOT NULL,
    price INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    created_time INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

-- Positions indexes
CREATE INDEX IF NOT EXISTS idx_positions_user ON positions(user_id);
CREATE INDEX IF NOT EXISTS idx_positions_market ON positions(market_ticker);
CREATE INDEX IF NOT EXISTS idx_positions_user_market ON positions(user_id, market_ticker);
CREATE INDEX IF NOT EXISTS idx_positions_updated ON positions(updated_time);

-- Orders indexes
CREATE INDEX IF NOT EXISTS idx_orders_user ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_market ON orders(market_ticker);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_time);
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders(user_id, status);

-- Fills indexes
CREATE INDEX IF NOT EXISTS idx_fills_order ON fills(order_id);
CREATE INDEX IF NOT EXISTS idx_fills_user ON fills(user_id);
CREATE INDEX IF NOT EXISTS idx_fills_market ON fills(market_ticker);
CREATE INDEX IF NOT EXISTS idx_fills_time ON fills(filled_time);
CREATE INDEX IF NOT EXISTS idx_fills_trade ON fills(trade_id);

-- Trades indexes
CREATE INDEX IF NOT EXISTS idx_trades_market ON trades(market_ticker);
CREATE INDEX IF NOT EXISTS idx_trades_time ON trades(created_time);
CREATE INDEX IF NOT EXISTS idx_trades_taker ON trades(taker_order_id);
CREATE INDEX IF NOT EXISTS idx_trades_maker ON trades(maker_order_id);