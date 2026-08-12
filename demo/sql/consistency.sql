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
