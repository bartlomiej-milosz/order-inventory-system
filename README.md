# Order & Inventory Management System

A Java 25 / Spring Boot backend for employees managing B2B orders across warehouses. Each order belongs to one customer and one warehouse. In the order model, confirming three units reserves them, fulfilling the order records their physical issue, and cancelling a confirmed order releases them. If a warehouse holds ten units and three are reserved, seven are available.

**What works today:** PostgreSQL migrations, eight JPA entity mappings, employee sign-in, customer, product and warehouse management, stock receipts and adjustments, movement history, draft orders with price snapshots, confirmation with transactional stock reservations, health checks, local Swagger UI, and demos for the API and SQL. Fulfillment and cancellation are planned. The SQL demo includes example orders in those states. [Editable database diagram](docs/schema-design.drawio) · [Demo and sample queries](demo/README.md)

[Run locally](#run-locally) · [Create the first administrator](#initial-administrator-and-employee-access) · [Develop and test](#develop-and-test) · [Deploy on a server](#deploy-on-a-server) · [Troubleshooting](#troubleshooting)

## Run locally

Use a macOS or Linux terminal with Git, Bash, Make, OpenSSL, curl and Docker with the Compose v2 plugin. Start the Docker engine. The full-stack path builds Java inside Docker. A host JDK is needed only to run Maven or develop in an IDE.

### 1. Start the application and database

From a new checkout:

```sh
git clone https://github.com/bartlomiej-milosz/order-inventory-system.git
cd order-inventory-system
make start-stack
make check-readiness
```

If you already have the checkout, run `make start-stack` in its root. All commands below use that directory. The first build downloads the JDK image and Maven dependencies. Make creates a private `.env` with a random local database password on first use. Compose starts PostgreSQL and the app in the background, Flyway applies migrations, and Hibernate checks the mappings. The readiness response should be `{"status":"UP"}`.

By default the API listens on host loopback port 8080 and PostgreSQL on loopback port 5432. To choose other host ports **before starting**, run `make init-env`, edit `APP_PORT` and `DB_PORT` in `.env`, then run `make start-stack`. Use the configured API port in browser and curl examples. Keep the generated `DB_PASSWORD` with the local database volume across restarts.

<a id="initial-administrator-and-employee-access"></a>

### 2. Create your login once

```sh
make local-admin
```

At the terminal prompts, enter your email, enter your chosen password, and repeat it. Password input is hidden. Use at least 14 characters and at most 72 UTF-8 bytes. Save the password for the next step. This password signs an employee into the HTTP API. The generated `DB_PASSWORD` in `.env` connects the application to PostgreSQL.

The command runs the application image once against the local database, with HTTP disabled. It passes the password through standard input to a temporary file inside that container and removes the container when it finishes. Look for **Initial administrator created** and a successful exit. The account is active, has the ADMIN role, and stores a BCrypt hash. The database must contain zero employees. Once it has an account, sign in with that account on later runs. Loading the SQL test fixture into a fresh database also fills the users table, so use a separate database for that exercise.

### 3. Make your first authenticated request

Replace the email below with the one you entered. Curl prompts for the password.

```sh
curl --user admin@example.test http://127.0.0.1:8080/api/v1/auth/me
```

Expect HTTP 200 and a response shaped like `{"id":"<uuid>","email":"admin@example.test","role":"ADMIN"}`. You can now paste the returned UUID into the employee lookup in [local Swagger UI](http://127.0.0.1:8080/swagger-ui.html). Click **Authorize**, enter the employee credentials, then use **Try it out**. The demo scripts also show the anonymous and signed-in responses side by side.

```sh
./demo/api/health.sh
./demo/api/employee-access.sh admin@example.test
```

For another port, prefix a demo command with `BASE_URL=http://127.0.0.1:8081`. The health and employee endpoints are:

| Request                    | Access            | What you get                                                                |
| -------------------------- | ----------------- | --------------------------------------------------------------------------- |
| GET /livez and GET /readyz | Public            | Process health and readiness, including database connectivity for readiness |
| GET /api/v1/auth/me        | ADMIN or OPERATOR | Your employee ID, normalized email and current role                         |
| GET /api/v1/users/{id}     | ADMIN             | Employee details, including whether the account is active                   |
| GET /api/v1/auth/csrf      | ADMIN or OPERATOR | CSRF cookie and a response containing the header name and masked token      |

For customers, products, warehouses and stock movements, see the [catalog and stock endpoint table](demo/README.md#catalog-and-stock).

HTTP Basic authenticates each request. Changes to an account's role or active flag take effect on its next request. CSRF protection on unsafe methods requires both the cookie and the token in the named header. Swagger and the OpenAPI JSON at `/v3/api-docs` are available locally. Other application routes default to denied.

### Stop and resume

Use `make stack-status` for service state and `make stack-logs` to follow logs. Press Ctrl-C to leave the log viewer. Run `make stop-stack` when finished. It keeps the database volume. The next `make start-stack` reuses your data and employee login. Run `make help` for the full command list.

## Develop and test

**Run Java on your machine.** Install JDK 25 and check `java -version`. If the full Docker stack is running, use `make stop-stack` first to free the API port. Run `make run-local` to start PostgreSQL in Docker and the application in your terminal with the Maven Wrapper. The local database and administrator are shared with the full-stack workflow. Press Ctrl-C to stop Java, then `make stop-stack` to stop PostgreSQL. For an IDE, use `make start-db`, select profile `local`, and supply `DB_PASSWORD`, `DB_PORT` and `APP_PORT` from your local `.env` in the run configuration.

**Existing password-file workflow.** The Maven target `make bootstrap-admin` also works with JDK 25. Set `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD_FILE` in `.env`. Supply a private, single-line UTF-8 file at that path and remove it after successful creation. The interactive `make local-admin` reads the email and password from its prompts.

**Verify a change.** With JDK 25 and Docker running, use:

```sh
make verify-build
```

This checks Java formatting, runs tests and packages the app. Testcontainers starts disposable PostgreSQL instances. The suite covers migrations, constraints, rollback, entity mappings, employee access, CSRF, catalog operations, draft editing, confirmation, reservation rollback, OpenAPI, and PostgreSQL lock contention. Use `make format-java` to apply the formatter. CI also builds the container and starts the local Compose stack for a smoke test. [CI workflow](.github/workflows/ci.yaml)

**Work with the schema.** Flyway manages the business tables in `inventory` and the migration history in `flyway_history`. Hibernate validates the mappings on startup. Add a new versioned migration for changes to an applied schema. Use `make validate-db-migrations` to check local migration history. The separate `make repair-db-migrations` command changes history metadata and should be used only after investigating and resolving a mismatch. It does not apply missing structural changes. [Migrations](src/main/resources/db/migration/) · [SQL reports](demo/sql/) · [Demo and test data](demo/sql/seed.sql)

**Explore orders and stock in SQL.** With JDK 25, Docker and the local environment from step 1, run the following from the repository root. Run setup once. It creates a separate `order_inventory_demo` database, migrates it and loads sample orders. The HTTP API keeps using `order_inventory`.

```sh
./demo/sql/run.sh setup
./demo/sql/run.sh orders
./demo/sql/run.sh stock
./demo/sql/run.sh consistency
```

Look for order DEMO-003 totaling 23.00 PLN and WIDGET availability of 7 in NORTH and 4 in SOUTH. Both consistency queries should return zero rows. Repeat the last three commands whenever you want to explore the data. [SQL demo guide](demo/README.md)

**Try stock operations over HTTP.** With the local stack running, sign in as an administrator and run `./demo/api/catalog-stock.sh admin@example.test`. The script uses `jq` and creates fictional catalog data in the local application database. It demonstrates a receipt, a correction, and the recorded history. [Catalog and stock demo](demo/README.md#catalog-and-stock)

**Try draft orders over HTTP.** Run `./demo/api/draft-orders.sh admin@example.test` as ADMIN with `jq` installed. It creates a draft, shows how saved line prices survive catalog edits, and checks that inventory remains unchanged. [Draft order demo](demo/README.md#draft-orders)

**Try confirmation and reservation over HTTP.** Run `./demo/api/confirm-orders.sh admin@example.test` as ADMIN with `jq` installed. The script shows two orders competing for available units and verifies that confirmation changes reservations while physical stock remains on hand. [Confirmation demo](demo/README.md#confirmation-and-reservations)

## Deploy on a server

The deployment configuration runs one application container on a Linux Docker host. A reverse proxy on that host accepts HTTPS traffic and forwards it to the application's loopback port. The application connects over TLS to a separately managed PostgreSQL 17 server. Provision the host, DNS name, proxy and database first. Run the shell commands from a checkout of the intended release on the Docker host, in a fresh shell so exported local settings cannot override the deployment environment.

### 1. Choose the application image

To build on the deployment host, give the image a tag identifying the checked-out revision:

```sh
git rev-parse --short HEAD
docker build -t "order-inventory-system:$(git rev-parse --short HEAD)" .
```

Build from a clean release checkout so the tag identifies the source. Use the resulting tag as `APP_IMAGE` below. If you publish images separately, pull your image onto the host and use its registry reference pinned by digest. The repository CI builds and tests an image. Publishing to a registry is a separate step.

### 2. Provision a new database

Use a PostgreSQL administrator with permission to create a database and roles. From the repository root on a machine with `psql`, set the connection to your external server. Replace the hostname, administrator login and CA path below with your own. Check the destination shown by the first command before running the provisioning script.

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

Use the configured `APP_PORT` in the health check. Wait for `UP` before creating the first employee. At the prompts choose the employee email and password for this installation. The bootstrap container uses the same deployment image, TLS settings and database roles as the running service. Run this command once per fresh database.

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
| Bootstrap says the users table must be empty             | An account already exists, possibly from a fixture. Use the existing login or a separate fresh demo database. The bootstrap command cannot reset a password.                |
| Login returns 401                                        | Use an active employee email and its employee password. Curl prompts when given only the email.                                                                             |
| Request returns 403                                      | Check the employee's role and the allowed endpoint. Unsafe methods also require CSRF protection.                                                                            |
| Deployment fails readiness                               | Inspect app logs. Verify DB_HOST against the certificate, CA trust, database reachability, secret permissions, and both roles' credentials and schema grants.               |
