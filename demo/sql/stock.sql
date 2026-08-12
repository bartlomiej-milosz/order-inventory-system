-- Availability by product and warehouse, including pairs without a stock row.
SELECT p.sku, w.code AS warehouse,
       COALESCE(s.on_hand, 0) AS on_hand, COALESCE(s.reserved, 0) AS reserved,
       COALESCE(s.on_hand - s.reserved, 0) AS available
FROM inventory.products p
CROSS JOIN inventory.warehouses w
LEFT JOIN inventory.stock_levels s ON s.product_id = p.id AND s.warehouse_id = w.id
ORDER BY p.sku, w.code;
