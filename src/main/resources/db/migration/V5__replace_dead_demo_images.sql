-- V5: two demo product photos were deleted from Unsplash (HTTP 404). Point them at working photos of the
-- same kind of product. Only rows that still use the exact dead URL are touched.
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=600'
    WHERE image_url = 'https://images.unsplash.com/photo-1541585452919-45b8f1a7069d?w=600';
UPDATE products SET image_url = 'https://images.unsplash.com/photo-1578749556568-bc2c40e68b61?w=600'
    WHERE image_url = 'https://images.unsplash.com/photo-1490312278390-ab64016b5873?w=600';
