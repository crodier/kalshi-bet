CREATE TABLE order_projection (
  order_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  amount NUMERIC(20, 6) NOT NULL,
  filled_qty NUMERIC(20, 6) NOT NULL,
  status VARCHAR(32) NOT NULL,
  timestamp TIMESTAMP NOT NULL
); 