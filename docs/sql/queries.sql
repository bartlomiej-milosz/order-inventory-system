-- Order summaries filtered by status and sorted newest first. LEFT JOIN keeps empty drafts.
SELECT o.order_number, c.name AS customer, w.code AS warehouse, o.status,
       COUNT(i.id) AS item_count, COALESCE(SUM(i.quantity * i.unit_price), 0) AS total_pln
FROM inventory.sales_orders o
JOIN inventory.customers c ON c.id = o.customer_id
JOIN inventory.warehouses w ON w.id = o.warehouse_id
LEFT JOIN inventory.order_items i ON i.order_id = o.id
WHERE o.status IN ('DRAFT', 'CONFIRMED', 'FULFILLED')
GROUP BY o.id, c.name, w.code
ORDER BY o.created_at DESC, o.order_number;

-- Customers with more than one order.
SELECT c.name AS customer, COUNT(*) AS order_count
FROM inventory.customers c
JOIN inventory.sales_orders o ON o.customer_id = c.id
GROUP BY c.id
HAVING COUNT(*) > 1
ORDER BY order_count DESC, customer;

-- Availability by product and warehouse, including pairs without a stock row.
SELECT p.sku, w.code AS warehouse,
       COALESCE(s.on_hand, 0) AS on_hand, COALESCE(s.reserved, 0) AS reserved,
       COALESCE(s.on_hand - s.reserved, 0) AS available
FROM inventory.products p
CROSS JOIN inventory.warehouses w
LEFT JOIN inventory.stock_levels s ON s.product_id = p.id AND s.warehouse_id = w.id
ORDER BY p.sku, w.code;

-- Reservation discrepancies. FULL JOIN also catches a missing stock row.
WITH confirmed AS (
    SELECT i.product_id, o.warehouse_id, SUM(i.quantity) AS expected_reserved
    FROM inventory.sales_orders o
    JOIN inventory.order_items i ON i.order_id = o.id
    WHERE o.status = 'CONFIRMED'
    GROUP BY i.product_id, o.warehouse_id
)
SELECT COALESCE(s.product_id, c.product_id) AS product_id,
       COALESCE(s.warehouse_id, c.warehouse_id) AS warehouse_id,
       COALESCE(s.reserved, 0) AS actual_reserved, COALESCE(c.expected_reserved, 0) AS expected_reserved
FROM inventory.stock_levels s
FULL JOIN confirmed c ON c.product_id = s.product_id AND c.warehouse_id = s.warehouse_id
WHERE COALESCE(s.reserved, 0) <> COALESCE(c.expected_reserved, 0)
ORDER BY product_id, warehouse_id;

-- Physical stock discrepancies.
SELECT s.id AS stock_level_id, s.on_hand, COALESCE(SUM(m.quantity_delta), 0) AS expected_on_hand
FROM inventory.stock_levels s
LEFT JOIN inventory.stock_movements m ON m.stock_level_id = s.id
GROUP BY s.id
HAVING s.on_hand <> COALESCE(SUM(m.quantity_delta), 0)
ORDER BY s.id;
