-- Add symbol column to order_projection table
ALTER TABLE order_projection ADD COLUMN IF NOT EXISTS symbol VARCHAR(32) NOT NULL DEFAULT '';