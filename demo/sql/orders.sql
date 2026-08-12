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
