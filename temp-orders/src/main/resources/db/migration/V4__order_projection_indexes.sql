-- Index to speed up queries filtering by user_id
CREATE INDEX IF NOT EXISTS idx_order_projection_user_id ON order_projection(user_id);

-- Index to speed up queries filtering by user_id and ordering by timestamp (for pagination)
CREATE INDEX IF NOT EXISTS idx_order_projection_user_id_timestamp ON order_projection(user_id, timestamp DESC); 