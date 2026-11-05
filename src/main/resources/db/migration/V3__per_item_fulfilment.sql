-- V3: each order line is fulfilled by the artisan who sells it.
-- The order's status becomes a summary of its lines (computed in OrderStatus.aggregate).

ALTER TABLE order_items ADD COLUMN fulfilment_status varchar(255);

-- Existing lines inherit their order's current status (carts and unknown statuses start as pending).
UPDATE order_items SET fulfilment_status = COALESCE(
    (SELECT CASE
                WHEN LOWER(o.status) IN ('pending', 'processing', 'shipped', 'delivered') THEN LOWER(o.status)
                ELSE 'pending'
            END
     FROM orders o
     WHERE o.order_id = order_items.order_id),
    'pending');
