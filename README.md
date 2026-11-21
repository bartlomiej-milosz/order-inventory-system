# Order & Inventory Management System

A Java 25 and Spring Boot API for B2B order processing. Employees receive goods into a warehouse, reserve them for a customer, then fulfill or cancel the order. PostgreSQL keeps a physical movement history and protects stock when several employees confirm orders at the same time.

[Run locally](#run-locally) · [Try the demos](demo/README.md) · [Develop and test](#develop-and-test) · [Deploy on a server](#deploy-on-a-server) · [Troubleshooting](#troubleshooting)

## Run locally

On macOS or Linux, start Docker with Compose v2 and have Git, Bash, Make, OpenSSL and curl available. Run the commands below from the repository root after cloning. Docker builds Java inside the image.

```sh
git clone https://github.com/bartlomiej-milosz/order-inventory-system.git
cd order-inventory-system
make start-stack
make check-readiness
```

Wait for `{"status":"UP"}`. The first start generates a private `.env`, starts PostgreSQL, runs Flyway migrations and validates the JPA mappings. The API listens on `127.0.0.1:8080` and PostgreSQL on `127.0.0.1:5432`. To choose other ports before first start, run `make init-env`, edit `APP_PORT` and `DB_PORT` in `.env`, then start the stack.

<a id="initial-administrator-and-employee-access"></a>

### Create the first administrator

```sh
make local-admin
curl --user admin@example.test http://127.0.0.1:8080/api/v1/auth/me
```

At the first command, choose an employee email and a password of at least 14 Unicode characters and at most 72 UTF-8 bytes. Wait for **Initial administrator created**. For the second command, substitute that email and type the same password at the curl prompt. The response contains your ID, normalized email and `"role":"ADMIN"`.

Bootstrap runs a one-shot container with HTTP disabled and requires an empty employee table. On later starts, use your existing account. Keep the generated `DB_PASSWORD` with the database volume. It authenticates the application's database connection.

### Try an order

With Python 3.9+ on your machine, run one complete sale against the local API. Use the ADMIN email you chose above. The demo creates fictional catalog records and prints each request and result.

```sh
python3 -m demo complete-orders admin@example.test
```

The demo receives 10 units, fulfills 3, then confirms and cancels a second order for 2. The result is **7 on hand, 0 reserved and 7 available**. For a custom HTTP port, prefix the command with `BASE_URL=http://127.0.0.1:8081`. To explore endpoints interactively, open Swagger UI and select **Authorize**. [Local Swagger UI](http://127.0.0.1:8080/swagger-ui.html) · [Employee setup, HTTP scenarios and SQL reports](demo/README.md)

| Local operation | Command |
| --- | --- |
| Inspect services or follow logs | `make stack-status` or `make stack-logs` |
| Stop and keep the database volume | `make stop-stack` |
| Resume with the same accounts and orders | `make start-stack` |
| List available commands | `make help` |

## Model and transaction boundaries

One Spring Boot service uses eight business tables for employees, customers, products, warehouses, stock levels, order headers, order items and physical movements. Controllers handle HTTP, services set transaction boundaries and repositories issue queries and locks. [Database diagram](docs/schema-design.drawio) · [Schema migration](src/main/resources/db/migration/)

| Operation on one order | On hand | Reserved | Available | Movement |
| --- | ---: | ---: | ---: | --- |
| Receive 10 and draft an order for 3 | 10 | 0 | 10 | IN +10 on receipt |
| Confirm the draft | 10 | 3 | 7 | — |
| Fulfill after confirmation | 7 | 0 | 7 | OUT −3 |
| Cancel after confirmation (alternative ending) | 10 | 0 | 10 | — |

An order uses one warehouse, whole units and PLN prices. Each line keeps the product name, SKU and price from the time it was added. Confirmation locks the order and the required stock rows in a consistent order. A shortage on any line rolls back the entire reservation. The reports reconcile reserved units against confirmed lines and physical stock against movement history. Partial shipments, payments, returns and warehouse transfers are outside the scope. [Order service](src/main/java/dev/bartlomiejmilosz/inventory/orders/OrderService.java) · [Reconciliation SQL](demo/sql/consistency.sql)

| Access | Operations |
| --- | --- |
| Public | Process liveness at `/livez` and database readiness at `/readyz` |
| OPERATOR and ADMIN | View catalog, stock and movements, receive stock, create and edit drafts, confirm, fulfill and cancel orders |
| ADMIN | Also manage employees and catalog records and post stock corrections |

To add an operator, run `python3 -m demo employee-accounts admin@example.test create operator@example.test OPERATOR` with your ADMIN email. The program prompts for the administrator and employee passwords.

HTTP Basic authenticates each request. For writes, fetch `GET /api/v1/auth/csrf`, retain its cookie and send the returned token in the named header with your credentials. The demo client handles this. OpenAPI is available in the local profile at `/v3/api-docs`.

## Develop and test

| Task | From the repository root |
| --- | --- |
| Run the application on the host | Install JDK 25, stop the containerized app with `make stop-stack`, then use `make run-local`. It starts PostgreSQL and runs the Maven Wrapper. |
| Run from an IDE | `make start-db`. Select JDK 25 and the `local` profile. Supply `DB_PASSWORD`, `DB_PORT` and `APP_PORT` from `.env`. |
| Verify a change | With JDK 25 and Docker running, use `make verify-build` for Spotless, PostgreSQL integration tests and packaging. Use `make format-java` to apply formatting. |
| Check a schema change | Add a versioned Flyway migration. Run `make validate-db-migrations` to check local history. Use `make repair-db-migrations` only after diagnosing a history mismatch. |
| Bootstrap without the local app image | Set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD_FILE` in `.env` and use `make bootstrap-admin` with JDK 25. Remove the private password file afterwards. |

Testcontainers exercises migrations, rollback, lock contention and role checks on real PostgreSQL. CI also builds the Docker image, runs the Python walkthroughs, reconciles stock and checks readiness during a database outage and after recovery. The Docker build already excludes the demo directory. Keep it in your development checkout because the integration tests reuse its SQL fixtures. [CI workflow](.github/workflows/ci.yaml)

## Deploy on a server

The deployment has three parts: a reverse proxy for public HTTPS, one application container on the host's loopback port and a separately managed PostgreSQL 17 server reached over TLS. Prepare a Linux host with Git, Bash, Make, curl and Docker Compose v2, a DNS name and access to the database. Work from a clean checkout in a fresh shell so local environment variables do not override deploy settings.

### 1. Choose the application image

Build the revision you want to deploy and print the image reference for step 3.

```sh
image="order-inventory-system:$(git rev-parse --short HEAD)"
docker build -t "$image" .
printf '%s\n' "$image"
```

Set `APP_IMAGE` to this tag. If you already publish a registry image, pull it and use its digest instead.

### 2. Provision a new database

On a machine with `psql`, use a PostgreSQL administrator that can create databases and roles. Substitute your hostname, login and CA path. Verify the destination with `\conninfo` before provisioning.

```sh
export PGHOST=db.example.com PGPORT=5432 PGUSER=your_db_admin
export PGSSLMODE=verify-full PGSSLROOTCERT=/path/to/provider-ca.pem
psql -X -d postgres -c '\conninfo'
psql -X -d postgres -f deploy/provision.sql
```

Use the script once on a fresh installation. It creates `order_inventory`, prompts for the **inventory_migrator** password first and the **inventory_app** password second, then creates the schemas and grants. Flyway connects as schema owner, while application queries use the data access role. If a provider has already created these objects, apply the corresponding grants for that database and its roles. [Provisioning SQL](deploy/provision.sql)

### 3. Configure the container and secrets

On first setup, create the host configuration and its secrets directory:

```sh
umask 077
mkdir -p secrets
cp -n .env.deploy.example .env.deploy
```

Edit `.env.deploy` with these settings:

| Setting                                | Value on your host                                                                          |
| -------------------------------------- | ------------------------------------------------------------------------------------------- |
| APP_IMAGE                              | Tag from step 1 or your published image digest                                              |
| DB_HOST, DB_PORT                       | PostgreSQL hostname matching its TLS certificate and its port, reachable from the container |
| DB_NAME, DB_USER, FLYWAY_USER          | Defaults match step 2                                                                       |
| DB_CA_CERT_SOURCE                      | Host path to the database provider's trusted CA certificate in PEM format                   |
| DB_PASSWORD_FILE, FLYWAY_PASSWORD_FILE | Host paths containing the application and migration role passwords from step 2              |
| APP_PORT                               | Host loopback port for the reverse proxy, default 8080                                      |

Place the provider's CA at `secrets/db-ca.pem` when using the default paths. The following commands are for **Bash on a Linux host with rootful Docker**, on first setup. Enter the two database passwords from step 2. The files contain the exact password bytes without a trailing newline.

```bash
umask 077
read -rsp 'inventory_app password: ' app_pw && printf '\n'
printf '%s' "$app_pw" > secrets/db-password
unset app_pw
read -rsp 'inventory_migrator password: ' migration_pw && printf '\n'
printf '%s' "$migration_pw" > secrets/flyway-password
unset migration_pw
sudo chown 10001:10001 secrets/db-password secrets/flyway-password
sudo chmod 0400 secrets/db-password secrets/flyway-password
chmod 0444 secrets/db-ca.pem
```

Compose mounts the files for the container user (UID/GID 10001). With rootless Docker or user namespace mapping, grant the mapped container UID read access on the host. The CA file contains the public CA certificate. The two role password files are private. Keep `.env.deploy` and the contents of `secrets/` on the deployment host. [Deployment template](.env.deploy.example) · [Compose service](compose.deploy.yaml)

### 4. Start, bootstrap and check the deployment

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml config --quiet
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --wait
curl -fsS http://127.0.0.1:8080/readyz
make deploy-admin
```

Use your configured `APP_PORT` in the health check. Once it returns `UP`, choose the first employee email and password at the bootstrap prompts. Run bootstrap once per fresh database.

### 5. Enable public HTTPS

Point your domain's DNS at the host. For example, with Caddy installed as a host service, add a site block to its Caddyfile and replace the example domain. Use the `APP_PORT` you selected above.

```caddyfile
inventory.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Allow inbound ports 80 and 443 to Caddy for automatic HTTPS. Validate the proxy configuration, reload it, then verify readiness and sign-in through your own domain. The backend's published port stays on loopback. [Caddy reverse proxy guide](https://caddyserver.com/docs/quick-starts/reverse-proxy)

```sh
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
curl -fsS https://inventory.example.com/readyz
curl --user admin@example.test https://inventory.example.com/api/v1/auth/me
```

Use the email from step 4. A successful public check yields `UP` followed by your ADMIN profile. Swagger is disabled in the deployment profile.

**Operate and update.** Inspect service state with `docker compose --env-file .env.deploy -f compose.deploy.yaml ps`, and logs with the same prefix followed by `logs --tail=100 app`. Before updating, back up the external database and check the new migrations. Build or pull the new release, update `APP_IMAGE`, run `up -d --wait` with the same deployment file and repeat the HTTPS checks. Existing accounts persist in PostgreSQL.

## Troubleshooting

| Symptom                                                  | What to check                                                                                                                                                               |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Local startup reports a port already in use              | Choose a free APP_PORT or DB_PORT in .env, then rerun make start-stack. For IDE development, stop the containerized app before running the JVM.                             |
| PostgreSQL rejects the local password after editing .env | The database initializes credentials only on first volume creation. Restore the matching DB_PASSWORD or change the database role password through an authorized connection. |
| Bootstrap says the users table must be empty             | Sign in with the existing employee account. The first-admin command requires an empty employee table. Run SQL fixtures in their separate demo database.                |
| Login returns 401                                        | Use an active employee email and its employee password. Curl prompts when given only the email.                                                                             |
| Request returns 403                                      | Check the employee's role and the allowed endpoint. Unsafe methods also require CSRF protection.                                                                            |
| Deployment fails readiness                               | Inspect app logs. Verify DB_HOST against the certificate, CA trust, database reachability, secret permissions, and both roles' credentials and schema grants.               |
