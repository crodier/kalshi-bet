-- Table for OrderManagementActor to persist orders before sending to FIX
CREATE TABLE order_management (
  order_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  symbol VARCHAR(128) NOT NULL,
  side VARCHAR(10) NOT NULL,
  quantity NUMERIC(20, 6) NOT NULL,
  price NUMERIC(20, 6),
  order_type VARCHAR(20) NOT NULL,
  time_in_force VARCHAR(10) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  risk_amount NUMERIC(20, 6) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  kafka_published BOOLEAN NOT NULL DEFAULT FALSE,
  fix_sent BOOLEAN NOT NULL DEFAULT FALSE
);

-- Index for user-based queries
CREATE INDEX idx_order_management_user_id ON order_management(user_id);

-- Index for status queries
CREATE INDEX idx_order_management_status ON order_management(status);

-- Index for timestamp-based queries
CREATE INDEX idx_order_management_created_at ON order_management(created_at);

-- Table for tracking user risk limits
CREATE TABLE user_risk_tracking (
  user_id VARCHAR(64) PRIMARY KEY,
  total_risk_amount NUMERIC(20, 6) NOT NULL DEFAULT 0,
  last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Trigger to update updated_at on order_management
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_order_management_updated_at 
    BEFORE UPDATE ON order_management 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();