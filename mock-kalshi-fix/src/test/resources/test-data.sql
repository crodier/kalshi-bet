-- Insert test users
INSERT INTO users (user_id, access_key, email, full_name) VALUES
('test-user', 'test-user-key', 'test@example.com', 'Test User');
INSERT INTO users (user_id, access_key, email, full_name) VALUES
('market-maker', 'market-maker-key', 'mm@example.com', 'Market Maker');

-- Insert test series
INSERT INTO series (ticker, title, category, sub_category, tags, status) VALUES
('DUMMY', 'Dummy Test Series', 'Test', 'Testing', 'test,dummy', 'ACTIVE');
INSERT INTO series (ticker, title, category, sub_category, tags, status) VALUES
('INXD', 'S&P 500 Daily', 'Finance', 'Indices', 'finance,sp500,index', 'ACTIVE');

-- Insert test events  
INSERT INTO events (ticker, series_ticker, sub_title, category, title, status) VALUES
('DUMMY-TEST', 'DUMMY', 'Test Event', 'Test', 'Dummy Test Event', 'ACTIVE');
INSERT INTO events (ticker, series_ticker, sub_title, category, title, status) VALUES
('INXD-23DEC29', 'INXD', 'S&P 500 close on Dec 29, 2023', 'Finance', 'S&P 500 Daily Dec 29', 'ACTIVE');

-- Insert test markets
INSERT INTO markets (
    ticker, event_ticker, market_type, title, subtitle, 
    yes_subtitle, no_subtitle,
    open_time, close_time, status, 
    yes_bid, yes_ask, no_bid, no_ask, last_price,
    previous_yes_bid, previous_yes_ask, previous_price,
    volume, volume_24h, liquidity, open_interest
) VALUES
('DUMMY_TEST', 'DUMMY-TEST', 'BINARY', 'Will this test pass?', 'Resolution: Test passes',
 'Test passes', 'Test fails',
 1703808000000, 1703894400000, 'ACTIVE',
 45, 55, 45, 55, 50,
 40, 60, 45,
 1000, 500, 10000, 100);

INSERT INTO markets (
    ticker, event_ticker, market_type, title, subtitle, 
    yes_subtitle, no_subtitle,
    open_time, close_time, status, 
    yes_bid, yes_ask, no_bid, no_ask, last_price,
    previous_yes_bid, previous_yes_ask, previous_price,
    volume, volume_24h, liquidity, open_interest
) VALUES
('INXD-23DEC29-B5000', 'INXD-23DEC29', 'BINARY', 'S&P 500 closes above 5000', 'S&P 500 > 5000 on Dec 29',
 'Above 5000', 'Below 5000', 
 1703808000000, 1703894400000, 'ACTIVE',
 30, 35, 65, 70, 32,
 28, 37, 30,
 5000, 2500, 50000, 500);

-- Insert some initial orders for order book
-- DUMMY_TEST market orders
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-1', 'market-maker', 'DUMMY_TEST', 'yes', 'buy', 'limit', 'resting', 45, 55, 100, 100, 1703808100000);
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-2', 'market-maker', 'DUMMY_TEST', 'yes', 'sell', 'limit', 'resting', 55, 45, 100, 100, 1703808200000);
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-3', 'market-maker', 'DUMMY_TEST', 'no', 'buy', 'limit', 'resting', 45, 55, 50, 50, 1703808300000);
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-4', 'market-maker', 'DUMMY_TEST', 'no', 'sell', 'limit', 'resting', 55, 45, 50, 50, 1703808400000);

-- INXD market orders  
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-5', 'market-maker', 'INXD-23DEC29-B5000', 'yes', 'buy', 'limit', 'resting', 30, 70, 200, 200, 1703808500000);
INSERT INTO orders (order_id, user_id, market_ticker, side, action, type, status, yes_price, no_price, count, remaining_count, created_time) VALUES
('test-order-6', 'market-maker', 'INXD-23DEC29-B5000', 'yes', 'sell', 'limit', 'resting', 35, 65, 200, 200, 1703808600000);

-- Insert initial positions
INSERT INTO positions (position_id, user_id, market_ticker, position, resting_orders_count) VALUES
('pos-1', 'market-maker', 'DUMMY_TEST', 0, 4);
INSERT INTO positions (position_id, user_id, market_ticker, position, resting_orders_count) VALUES
('pos-2', 'market-maker', 'INXD-23DEC29-B5000', 0, 2);