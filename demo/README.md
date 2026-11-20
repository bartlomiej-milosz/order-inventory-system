# API walkthroughs and SQL reports

Run these commands from the repository root with Python 3.9 or later. All Python imports come from the standard library. The HTTP commands call a running application. The SQL commands use Docker Compose, PostgreSQL's own tools and the project's Flyway migrations.

## Start here

Follow the [local setup](../README.md#run-locally) to start the stack and create the first ADMIN. Substitute that account's email below. The client prompts for passwords, prints request paths, status codes and key results and exits with a nonzero status if a response or business result differs from the scenario.

```sh
python3 -m demo health
python3 -m demo employee-access admin@example.test
python3 -m demo complete-orders admin@example.test
```

The expected results are two healthy probes, a `401` followed by your signed-in profile, and a completed sale with **7 units on hand, 0 reserved and two physical movements**. Use `python3 -m demo --help` to list commands. To inspect JSON bodies, add `--verbose` before the command, for example `python3 -m demo --verbose health`. The default API address is `http://127.0.0.1:8080`. For another local port use, for example, `BASE_URL=http://127.0.0.1:8081 python3 -m demo health`. Use HTTPS for employee credentials outside local development.

## Order and stock scenarios

Run each command independently. All four use ADMIN to create their sample catalog. Each run generates fresh catalog codes and order numbers and leaves its fictional records in the application database for inspection. Use a development installation.

### Catalog and stock

`python3 -m demo catalog-stock admin@example.test`

Read a new product's zero stock, receive 10 units and correct two damaged units. A further correction of −9 returns `409`. The two persisted movements explain the final balance of 8.

### Draft orders

`python3 -m demo draft-orders admin@example.test`

Add three widgets at 12.50 PLN, try a duplicate line and change the catalog price. Increasing the draft quantity to four keeps the original price and SKU. The demo also edits the header, adds and removes a second line and finds the order by all three list filters. The final total is 50.00 PLN and stock is unchanged.

### Confirmation and reservations

`python3 -m demo confirm-orders admin@example.test`

Receive 10 units and confirm an order for 6. Another order asks for 5 and gets `409`. Reducing it to 4 succeeds. At the end all 10 units are reserved, none are available and the receipt is still the only physical movement. This is a sequential walkthrough. The Java integration tests also exercise simultaneous requests.

### Fulfillment and cancellation

`python3 -m demo complete-orders admin@example.test`

Receive 10, confirm and fulfill 3, then confirm and cancel another 2. Cancelling an unconfirmed draft for 20 also succeeds. The only movements are `IN +10` and `OUT −3`, and the issue refers to the fulfilled order.

## Employee accounts

With an existing ADMIN, create an operator, copy the returned UUID and check the operator's login. The final two commands promote and then deactivate that account.

```sh
python3 -m demo employee-accounts admin@example.test create operator@example.test OPERATOR
python3 -m demo employee-access operator@example.test
EMPLOYEE_ID='<UUID from the creation response>'
python3 -m demo employee-accounts admin@example.test role "$EMPLOYEE_ID" ADMIN
python3 -m demo employee-accounts admin@example.test deactivate "$EMPLOYEE_ID"
```

Creation asks for the ADMIN password, then the new employee password twice. Use a new email for another run. Changes apply on the employee's next request. A disabled employee receives `401`. Demoting or disabling the last active ADMIN returns `409`. For the full endpoint and payload reference, use [local Swagger UI](http://127.0.0.1:8080/swagger-ui.html).

## Local PostgreSQL

Use Docker Compose and JDK 25 on the host. Run setup once. It starts PostgreSQL, creates `order_inventory_demo`, applies Flyway and loads the [fictional dataset](sql/seed.sql). A second setup refuses to replace an existing database. The API continues to use `order_inventory`.

```sh
python3 -m demo sql setup
python3 -m demo sql orders
python3 -m demo sql stock
python3 -m demo sql consistency
```

| Report | What to look for |
| --- | --- |
| [orders.sql](sql/orders.sql) | Order totals and repeat customers. DEMO-002 totals 45.50 PLN and DEMO-003 totals 23.00 PLN. |
| [stock.sql](sql/stock.sql) | WIDGET has 7 units available in NORTH and 4 in SOUTH. |
| [consistency.sql](sql/consistency.sql) | Both queries return zero rows. Any returned row identifies a difference between a stored counter and its source records. |

The Java integration tests load the same SQL files into a disposable PostgreSQL instance. Reports use `psql` inside the local database container in a read-only transaction. To explore individual queries in an editor, connect to `order_inventory_demo` on the local `DB_PORT` using `inventory_local` and `DB_PASSWORD` from `.env`.

## Extend a walkthrough

For a new HTTP scenario, add a function in [scenarios.py](scenarios.py) and register it in [the CLI](__main__.py). The [client](client.py) handles Basic authentication, cookies, CSRF and expected HTTP statuses. Check the business outcome as well, such as the balance or the order linked to a movement. For a new SQL report, add its query under `demo/sql/` and include its name in the CLI's SQL choices. The [SQL runner](sql_runner.py) handles connections and read-only execution.
