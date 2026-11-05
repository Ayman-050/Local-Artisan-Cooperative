-- V2: exact money, one-off data clean-up, and indexes.
-- Written in SQL that PostgreSQL and H2 (used by the tests) both accept.

-- 1. Money columns: double precision -> exact decimal. BHD has 3 decimal places (fils).
ALTER TABLE products ALTER COLUMN price SET DATA TYPE numeric(12,3);
ALTER TABLE order_items ALTER COLUMN price SET DATA TYPE numeric(12,3);
ALTER TABLE orders ALTER COLUMN total_amount SET DATA TYPE numeric(12,3);
ALTER TABLE payments ALTER COLUMN amount SET DATA TYPE numeric(12,3);
ALTER TABLE bids ALTER COLUMN bid_amount SET DATA TYPE numeric(12,3);
ALTER TABLE auctions ALTER COLUMN starting_bid SET DATA TYPE numeric(12,3);
ALTER TABLE auctions ALTER COLUMN current_highest_bid SET DATA TYPE numeric(12,3);
ALTER TABLE customers ALTER COLUMN total_spent SET DATA TYPE numeric(12,3);
ALTER TABLE customers ALTER COLUMN total_spent SET DEFAULT 0;

-- 2. Data clean-up that the admin pages used to redo (and write) on every page view.
UPDATE reviews SET status = 'active' WHERE status IS NULL OR LOWER(status) IN ('pending', 'approved');
UPDATE reviews SET auto_flagged = FALSE WHERE auto_flagged IS NULL;

UPDATE users SET status = 'pending' WHERE status IS NULL AND UPPER(role) = 'ARTISAN';
UPDATE users SET status = 'active' WHERE status IS NULL;
UPDATE users SET join_date = CURRENT_TIMESTAMP WHERE join_date IS NULL;

UPDATE products SET status = 'active' WHERE status IS NULL AND stock_quantity > 0;
UPDATE products SET status = 'out of stock'
    WHERE stock_quantity <= 0 AND (status IS NULL OR LOWER(status) = 'active');

UPDATE auctions SET flagged = FALSE WHERE flagged IS NULL;

-- 3. Indexes for the lookups the application does on every request. PostgreSQL does not index
--    foreign keys automatically.
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
CREATE INDEX IF NOT EXISTS idx_products_artisan ON products (artisan_id);
CREATE INDEX IF NOT EXISTS idx_orders_customer_status ON orders (customer_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_date ON orders (date);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_bids_auction ON bids (auction_id);
CREATE INDEX IF NOT EXISTS idx_reviews_product ON reviews (product_id);
CREATE INDEX IF NOT EXISTS idx_addresses_user_email ON addresses (user_email);
CREATE INDEX IF NOT EXISTS idx_wishlist_items_product ON wishlist_items (product_id);
