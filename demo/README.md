# Project demo

## HTTP API

Run the scripts from the repository root with Bash and `curl`. Use the running stack from the [local setup](../README.md#run-locally), or start the application with `make run-local` in another terminal. To show authenticated access, use an active employee account. On a fresh local database, follow the [initial administrator setup](../README.md#initial-administrator-and-employee-access).

```sh
./demo/api/health.sh
./demo/api/employee-access.sh admin@example.test
```

1. `health.sh` shows that `GET /livez` and `GET /readyz` return HTTP 200 and report `UP`.
2. `employee-access.sh` shows HTTP 401 for an anonymous `GET /api/v1/auth/me`. Then `curl` prompts for the employee's password, and the same request returns HTTP 200 with the employee's ID, email and role. Pass the account email used during setup.

The scripts show each response and exit with an error if it differs from the expected status or health result. They default to `http://127.0.0.1:8080`. Set `BASE_URL` for a different port, for example:

```sh
BASE_URL=http://127.0.0.1:8081 ./demo/api/health.sh
```

Use HTTPS for authenticated requests outside local development. The password is entered at the `curl` prompt.

### Catalog and stock

With the local stack running and an ADMIN account provisioned, run the following from the repository root. The script also needs `jq` and OpenSSL.

```sh
./demo/api/catalog-stock.sh admin@example.test
```

Enter the account password at the prompt. The script creates a fictional customer, a product and a warehouse with a fresh SKU and code on each run. It reads zero stock, records a 10-unit receipt and a correction of −2, then shows a rejected correction and the two persisted movements. The final physical and available stock are both 8. Run it against a local development database. The demo records stay in that database for inspection.

| API path under `/api/v1` | Methods and roles |
|---|---|
| `/customers`, `/products`, `/warehouses` | `GET` for ADMIN/OPERATOR. `POST` for ADMIN. |
| `/customers/{id}`, `/products/{id}`, `/warehouses/{id}` | `GET` for ADMIN/OPERATOR. `PUT` for ADMIN. |
| `/products/{id}/deactivate`, `/warehouses/{id}/deactivate` | `POST` for ADMIN. Existing stock and history stay readable. |
| `/warehouses/{warehouseId}/stock` | `GET` for ADMIN/OPERATOR. Lists recorded levels. |
| `/warehouses/{warehouseId}/stock/{productId}` | `GET` for ADMIN/OPERATOR. An existing catalog pair without a stock row returns zero quantities. |
| `…/stock/{productId}/receipts` | `POST` for ADMIN/OPERATOR with `quantity` and an optional `note`. |
| `…/stock/{productId}/adjustments` | `POST` for ADMIN with a nonzero signed `quantityDelta` and a required `note`. Record a receipt first. |
| `…/stock/{productId}/movements`, `…/movements/{movementId}` | `GET` for ADMIN/OPERATOR. Creation responses link to the recorded movement. |

List endpoints accept `page` from 0 to 1,000,000 and `size` from 1 to 100. The defaults are page 0 and size 20. Warehouse codes and product SKUs use uppercase ASCII letters, digits, hyphens and underscores, starting with a letter or digit. Quantities are whole units and movement notes are limited to 500 characters. Stock changes need an active product and warehouse and must preserve `onHand >= reserved`.

For your own write requests, first call `GET /api/v1/auth/csrf` with Basic credentials. Keep the response cookie and send the JSON `token` in the header named by `headerName`, along with the cookie and Basic credentials. The demo script handles this sequence.

## Local PostgreSQL

With JDK 25 and Docker running, run `setup` once from the repository root after preparing `.env` using `make init-env`.

```sh
./demo/sql/run.sh setup
```

This starts the local PostgreSQL container, creates `order_inventory_demo`, runs the app's Flyway migrations on that database, and inserts fictional records from [seed.sql](sql/seed.sql). The demo database lives alongside `order_inventory` in the same container. Your application and the HTTP demo keep using `order_inventory`. On later runs, reuse the demo database by going straight to the queries below.

```sh
./demo/sql/run.sh orders
./demo/sql/run.sh stock
./demo/sql/run.sh consistency
```

- [orders.sql](sql/orders.sql) shows order totals and repeat customers. Expect `DEMO-002` to total `45.50` PLN and `DEMO-003` to total `23.00` PLN.
- [stock.sql](sql/stock.sql) shows how many units are on hand, reserved and available per warehouse. Expect `WIDGET` to have 7 available units in NORTH and 4 in SOUTH.
- [consistency.sql](sql/consistency.sql) compares reservations with confirmed order items and physical stock with recorded movements. Both results should be empty.

The read commands use `psql` inside the existing `db` container and run the selected file in a read-only transaction. You can also open any of the query files in your SQL editor and run individual statements against `order_inventory_demo` using the local port in `.env` (5432 by default), the `inventory_local` user and `DB_PASSWORD`.

The seed contains an inactive employee for foreign keys. Use the [initial administrator setup](../README.md#initial-administrator-and-employee-access) for the HTTP demo. The integration tests use the same `seed.sql` on their own disposable PostgreSQL instance.
