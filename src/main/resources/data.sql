-- ============================================================
-- ArtsyVibe seed data
-- Runs automatically on every startup via:
--   spring.sql.init.mode=always
--   spring.jpa.defer-datasource-initialization=true
-- ddl-auto=create wipes tables first, so no duplicate-key risk.
-- ============================================================

-- ── 1. Product categories ────────────────────────────────────
INSERT INTO product_categories (category_id, name) OVERRIDING SYSTEM VALUE VALUES
  (1, 'Ceramics'),
  (2, 'Jewelry'),
  (3, 'Textiles'),
  (4, 'Woodwork'),
  (5, 'Paintings'),
  (6, 'Glasswork');

-- ── 2. Users (base table for JOINED inheritance) ────────────
-- Admins: IDs 1
-- Artisans: IDs 2-4
-- Customers: IDs 5-7
INSERT INTO users (user_id, name, email, password, role, profile_picture) OVERRIDING SYSTEM VALUE VALUES
  (1,  'Alice Admin',   'alice@artsyvibe.com',   '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'ADMIN', 'https://cdn-icons-png.flaticon.com/512/149/149071.png'),
  (2,  'Elena Craft',   'elena@artsyvibe.com',   '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'ARTISAN', 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=100&h=100&fit=crop&crop=face'),
  (3,  'Marco Pottery', 'marco@artsyvibe.com',   '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'ARTISAN', 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&h=100&fit=crop&crop=face'),
  (4,  'Zara Weaves',   'zara@artsyvibe.com',    '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'ARTISAN', 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&h=100&fit=crop&crop=face'),
  (5,  'John Doe',      'john@example.com',      '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'CUSTOMER', 'https://cdn-icons-png.flaticon.com/512/149/149071.png'),
  (6,  'Sarah Miller',  'sarah@example.com',     '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'CUSTOMER', 'https://cdn-icons-png.flaticon.com/512/149/149071.png'),
  (7,  'Tom Brown',     'tom@example.com',       '$2a$10$FIVPPbrxrRXHIsD.jIwtkOYaWLhZ1YYpu2mtAzbi2aUlceQT728wu', 'CUSTOMER', 'https://cdn-icons-png.flaticon.com/512/149/149071.png');


-- ── 3. Admins (joined subtable) ──────────────────────────────
INSERT INTO admins (user_id) OVERRIDING SYSTEM VALUE VALUES (1);

-- ── 4. Artisans (joined subtable) ───────────────────────────
INSERT INTO artisans (user_id, shop_name, biography) OVERRIDING SYSTEM VALUE VALUES
  (2, 'Elena''s Clay Studio',  'Elena has been shaping ceramics and glasswork for over a decade, inspired by Mediterranean craftsmanship.'),
  (3, 'Marco''s Pottery House','Marco brings Italian heritage to every wheel-thrown piece, specializing in functional stoneware.'),
  (4, 'Zara Textile Co.',      'Zara hand-weaves natural fibers into vibrant tapestries and wearable art rooted in West African tradition.');

-- ── 5. Customers (joined subtable) ───────────────────────────
INSERT INTO customers (user_id, address, phone) OVERRIDING SYSTEM VALUE VALUES
  (5, '12 Maple Street, Manama', '+973-3311-2200'),
  (6, '7 Rose Avenue, Riffa',    '+973-3322-4455'),
  (7, '3 Cedar Lane, Muharraq',  '+973-3399-8877');

-- ── 6. Products (regular, non-auction) ───────────────────────
INSERT INTO products (id, title, description, price, category, image_url, stock_quantity, artisan_name, is_auction_item, adding_date, artisan_id, category_id)
OVERRIDING SYSTEM VALUE VALUES
  (1,  'Handmade Ceramic Bowl',       'A beautifully glazed stoneware bowl, food-safe and microwave-friendly. Each piece is unique.',                        85.00,  'ceramics',  'https://images.unsplash.com/photo-1565193566173-7a0ee3dbe261?w=600', 12, 'Elena Craft',   false, '2025-01-10', 2, 1),
  (2,  'Sterling Silver Ring',         'Delicate hammered silver ring with a natural stone inset. Available in sizes 5-10.',                                   120.00, 'jewelry',   'https://images.unsplash.com/photo-1605100804763-247f67b3557e?w=600', 20, 'Elena Craft',   false, '2025-01-15', 2, 2),
  (3,  'Hand-Woven Wall Tapestry',    'A striking 60×90 cm tapestry in earthy tones, woven on a traditional loom from organic cotton.',                       195.00, 'textiles',  'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600', 5,  'Zara Weaves',   false, '2025-01-20', 4, 3),
  (4,  'Olive Wood Serving Board',    'Hand-carved olive wood charcuterie board with natural grain patterns. Approx 40×25 cm.',                               65.00,  'woodwork',  'https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=600', 8,  'Marco Pottery', false, '2025-02-01', 3, 4),
  (5,  'Watercolour Coastal Scene',   'Original 30×40 cm watercolour painting of a Bahraini coastal sunrise, signed by the artist.',                          350.00, 'paintings', 'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600', 1,  'Elena Craft',   false, '2025-02-05', 2, 5),
  (6,  'Artisan Coffee Mug Set (4)',  'Set of four wheel-thrown mugs in a matching smoke glaze. Dishwasher safe.',                                             48.00,  'ceramics',  'https://images.unsplash.com/photo-1733046889345-25938f38c8e7?w=600', 15, 'Marco Pottery', false, '2025-02-10', 3, 1),
  (7,  'Gold-Leaf Pendant Necklace',  '18 k gold-plated pendant on a 45 cm sterling silver chain. Inspired by geometric Islamic patterns.',                   95.00,  'jewelry',   'https://images.unsplash.com/photo-1599643478518-a784e5dc4c8f?w=600', 18, 'Zara Weaves',   false, '2025-02-15', 4, 2),
  (8,  'Abstract Canvas Print',       'Acrylic abstract in terracotta and indigo on stretched canvas, 50×70 cm. Ready to hang.',                               220.00, 'paintings', 'https://images.unsplash.com/photo-1541961017774-22349e4a1262?w=600', 3,  'Elena Craft',   false, '2025-02-20', 2, 5),
  (9,  'Natural Linen Table Runner',  'Hand-loomed linen table runner, 40×180 cm. Natural undyed fabric with fringed ends.',                                  78.00,  'textiles',  'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=600', 10, 'Zara Weaves',   false, '2025-03-01', 4, 3),
  (10, 'Walnut Cheese Board Set',     'Solid walnut board with engraved floral border, paired with two hand-forged cheese knives.',                            145.00, 'woodwork',  'https://images.unsplash.com/photo-1541585452919-45b8f1a7069d?w=600', 6,  'Marco Pottery', false, '2025-03-05', 3, 4),
  (11, 'Speckled Stoneware Vase',     'Tall 30 cm vase in warm speckled glaze. Perfect for dried or fresh florals.',                                           62.00,  'ceramics',  'https://images.unsplash.com/photo-1490312278390-ab64016b5873?w=600', 9,  'Marco Pottery', false, '2025-03-10', 3, 1),
  (12, 'Macramé Boho Throw Blanket', 'Hand-knotted macramé throw in cream and rust, 130×170 cm. 100% recycled cotton.',                                      135.00, 'textiles',  'https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?w=600', 7,  'Zara Weaves',   false, '2025-03-15', 4, 3);

-- ── 7. Auction products ───────────────────────────────────────
INSERT INTO products (id, title, description, price, category, image_url, stock_quantity, artisan_name, is_auction_item, adding_date, artisan_id, category_id)
OVERRIDING SYSTEM VALUE VALUES
  (13, 'Antique-Style Raku Vase',      'One-of-a-kind raku-fired vase with metallic sheen and crackle glaze. Museum-quality finish.',                         0.00, 'ceramics',  'https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?w=600', 1, 'Marco Pottery', true, '2025-03-20', 3, 1),
  (14, 'Hand-Blown Glass Bowl',         'Swirling cobalt and amber art glass bowl, approx 25 cm diameter. Signed and numbered.',                               0.00, 'glasswork', 'https://images.unsplash.com/photo-1610701596007-11502861dcfa?w=600', 1, 'Elena Craft',   true, '2025-03-22', 2, 6),
  (15, 'Vintage Indigo Ikat Textile',  'Rare hand-dyed ikat cloth panel, 80×200 cm, sourced and reimagined by Zara from a heritage pattern.',                 0.00, 'textiles',  'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600', 1, 'Zara Weaves',   true, '2025-04-01', 4, 3),
  (16, 'Original Oil Landscape',       'Large 80×100 cm oil painting of the Bahraini desert at dusk. Rich impasto texture.',                                  0.00, 'paintings', 'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600', 1, 'Elena Craft',   true, '2025-04-05', 2, 5),
  (17, 'Hand-Carved Cedar Jewellery Box','Intricate geometric carvings on a cedar jewellery box with velvet lining. Approx 25×15×10 cm.',                    0.00, 'woodwork',  'https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=600', 1, 'Marco Pottery', true, '2025-04-10', 3, 4),
  (18, 'Mosaic Glass Wall Art',         'Hand-cut mosaic panel in stained glass, 60×60 cm, depicting an abstract garden scene.',                              0.00, 'glasswork', 'https://images.unsplash.com/photo-1610701596007-11502861dcfa?w=600', 1, 'Elena Craft',   true, '2025-04-15', 2, 6);

-- ── 8. Auctions ───────────────────────────────────────────────
INSERT INTO auctions (id, product_id, starting_bid, current_highest_bid, highest_bidder_name, start_time, end_time, status, admin_id)
OVERRIDING SYSTEM VALUE VALUES
  (1, 13, 150.00, 210.00, 'Sarah Miller', '2026-05-01 10:00:00', '2026-05-25 20:00:00', 'LIVE',     1),
  (2, 14, 200.00, 275.00, 'John Doe',     '2026-05-03 10:00:00', '2026-05-28 20:00:00', 'LIVE',     1),
  (3, 15, 180.00, 180.00, NULL,           '2026-05-05 10:00:00', '2026-05-30 20:00:00', 'LIVE',     1),
  (4, 16, 500.00, 620.00, 'Tom Brown',    '2026-04-01 10:00:00', '2026-04-20 20:00:00', 'ENDED',    1),
  (5, 17, 120.00, 195.00, 'Sarah Miller', '2026-04-10 10:00:00', '2026-05-01 20:00:00', 'ENDED',    1),
  (6, 18, 300.00, 300.00, NULL,           '2026-05-20 10:00:00', '2026-06-15 20:00:00', 'UPCOMING', 1);

-- ── 9. Bids ───────────────────────────────────────────────────
INSERT INTO bids (bid_id, bid_amount, bid_time, customer_id, auction_id) OVERRIDING SYSTEM VALUE VALUES
  (1,  160.00, '2025-05-02 14:30:00', 5, 1),
  (2,  185.00, '2025-05-05 09:15:00', 6, 1),
  (3,  210.00, '2025-05-08 16:45:00', 6, 1),
  (4,  220.00, '2025-05-04 11:00:00', 5, 2),
  (5,  250.00, '2025-05-06 13:20:00', 5, 2),
  (6,  275.00, '2025-05-09 10:05:00', 5, 2),
  (7,  520.00, '2025-04-17 15:00:00', 7, 4),
  (8,  580.00, '2025-04-22 12:30:00', 7, 4),
  (9,  620.00, '2025-05-01 08:50:00', 7, 4),
  (10, 130.00, '2025-04-21 17:10:00', 6, 5),
  (11, 165.00, '2025-04-25 11:30:00', 6, 5),
  (12, 195.00, '2025-05-05 14:00:00', 6, 5);

-- ── 10. Orders ────────────────────────────────────────────────
INSERT INTO orders (order_id, date, status, total_amount, shipping_address, customer_id) OVERRIDING SYSTEM VALUE VALUES
  (1, '2025-03-10', 'delivered', 250.00, '12 Maple Street, Manama', 5),
  (2, '2025-03-25', 'shipped',   195.00, '12 Maple Street, Manama', 5),
  (3, '2025-04-02', 'processing',210.00, '7 Rose Avenue, Riffa',    6),
  (4, '2025-04-18', 'delivered', 145.00, '3 Cedar Lane, Muharraq',  7),
  (5, '2025-05-01', 'pending',   113.00, '7 Rose Avenue, Riffa',    6);

-- ── 11. Order items ───────────────────────────────────────────
INSERT INTO order_items (order_item_id, quantity, price, order_id, product_id) OVERRIDING SYSTEM VALUE VALUES
  (1,  1, 85.00,  1, 1),
  (2,  1, 120.00, 1, 2),
  (3,  1, 45.00,  1, 6),
  (4,  1, 195.00, 2, 3),
  (5,  1, 62.00,  3, 11),
  (6,  2, 148.00, 3, 6),
  (7,  1, 145.00, 4, 10),
  (8,  1, 78.00,  5, 9),
  (9,  1, 35.00,  5, 6);

-- ── 12. Payments ─────────────────────────────────────────────
INSERT INTO payments (payment_id, transaction_details, date, status, amount, payment_method, order_id) OVERRIDING SYSTEM VALUE VALUES
  (1, 'TXN-20250310-001', '2025-03-10', 'completed', 250.00, 'Credit Card', 1),
  (2, 'TXN-20250325-002', '2025-03-25', 'completed', 195.00, 'BenefitPay',  2),
  (3, 'TXN-20250402-003', '2025-04-02', 'pending',   210.00, 'Credit Card', 3),
  (4, 'TXN-20250418-004', '2025-04-18', 'completed', 145.00, 'BenefitPay',  4),
  (5, 'TXN-20250501-005', '2025-05-01', 'pending',   113.00, 'Credit Card', 5);

-- ── 13. Reviews ───────────────────────────────────────────────
INSERT INTO reviews (review_id, rating, comment, date, customer_id, product_id) OVERRIDING SYSTEM VALUE VALUES
  (1, 5, 'Absolutely stunning bowl! The glaze is gorgeous and it feels solid in hand.',                '2025-03-18', 5, 1),
  (2, 5, 'Perfect ring, exactly as described. Arrived beautifully packaged.',                          '2025-03-20', 5, 2),
  (3, 4, 'The tapestry is even more beautiful in person. Delivery was quick.',                         '2025-04-02', 5, 3),
  (4, 5, 'My new favourite serving board. The grain is beautiful and it wipes clean easily.',          '2025-04-25', 7, 10),
  (5, 4, 'Great mug set, the smoke glaze is really unique. One mug had a tiny chip on the base.',     '2025-04-10', 6, 6),
  (6, 5, 'The linen runner is exactly what I wanted — natural, elegant, and good quality.',            '2025-05-03', 6, 9);

-- ── 14. Wishlists ─────────────────────────────────────────────
INSERT INTO wishlists (wishlist_id, date_created, customer_id) OVERRIDING SYSTEM VALUE VALUES
  (1, '2025-02-01', 5),
  (2, '2025-02-15', 6),
  (3, '2025-03-01', 7);

-- ── 15. Wishlist items ────────────────────────────────────────
INSERT INTO wishlist_items (wishlist_item_id, date_added, wishlist_id, product_id) OVERRIDING SYSTEM VALUE VALUES
  (1,  '2025-02-05', 1, 3),
  (2,  '2025-02-10', 1, 5),
  (3,  '2025-02-20', 1, 8),
  (4,  '2025-02-16', 2, 1),
  (5,  '2025-02-18', 2, 7),
  (6,  '2025-03-05', 2, 11),
  (7,  '2025-03-02', 3, 4),
  (8,  '2025-03-10', 3, 10),
  (9,  '2025-03-12', 3, 12);

-- ── Reset sequences so auto-generated IDs continue from max ──
SELECT setval('product_categories_category_id_seq', (SELECT MAX(category_id) FROM product_categories));
SELECT setval('users_user_id_seq',                  (SELECT MAX(user_id)     FROM users));
SELECT setval('products_id_seq',                    (SELECT MAX(id)          FROM products));
SELECT setval('auctions_id_seq',                    (SELECT MAX(id)          FROM auctions));
SELECT setval('bids_bid_id_seq',                    (SELECT MAX(bid_id)      FROM bids));
SELECT setval('orders_order_id_seq',                (SELECT MAX(order_id)    FROM orders));
SELECT setval('order_items_order_item_id_seq',      (SELECT MAX(order_item_id) FROM order_items));
SELECT setval('payments_payment_id_seq',            (SELECT MAX(payment_id)  FROM payments));
SELECT setval('reviews_review_id_seq',              (SELECT MAX(review_id)   FROM reviews));
SELECT setval('wishlists_wishlist_id_seq',          (SELECT MAX(wishlist_id) FROM wishlists));
SELECT setval('wishlist_items_wishlist_item_id_seq',(SELECT MAX(wishlist_item_id) FROM wishlist_items));
