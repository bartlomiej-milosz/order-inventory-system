# Order & Inventory Management System

A Spring Boot backend for managing B2B orders, stock levels, reservations, and stock movements. The repository contains the application infrastructure, the initial PostgreSQL schema, JPA mappings and employee authentication.

## Scope and current status

The v1 design has **8 domain tables** and supports multiple warehouses, with one warehouse per order. Confirmation reserves stock; fulfillment decreases physical stock; cancellation releases the reservation. Reservations are represented by CONFIRMED order items and the reserved counter, without a separate reservation table.

- [Database diagram](docs/schema-design.drawio) — editable schema and design notes.
- [SQL examples](docs/sql/queries.sql) — order totals, availability and stock reconciliation.

Flyway V1 creates the eight domain tables, now mapped with JPA and checked by Hibernate at startup. Employees authenticate against `inventory.users` through HTTP Basic. Order, catalog and stock operations are planned for later milestones.

## Local development

Use Java 25 and Docker with the Compose plugin. Maven is provided by the wrapper.

Create `.env` once from `.env.example` and set a random development password. Use `openssl rand -hex 24` to generate one. Keep the same credentials while using the same local database volume. The Postgres image reads its initial password only when initializing an empty volume.

**PostgreSQL in Docker, application in the IDE or Maven**

```sh
docker compose up --wait db
set -a
. ./.env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

For an IDE run configuration, set `SPRING_PROFILES_ACTIVE=local` and `DB_PASSWORD`. Set `DB_PORT` or `APP_PORT` if you changed the respective port in `.env`.

**Entire stack in Docker**

```sh
docker compose --profile app up --build --wait
curl -fsS http://127.0.0.1:8080/readyz
```

The API and PostgreSQL ports are bound to loopback. Swagger UI is available in the `local` profile at `http://127.0.0.1:8080/swagger-ui.html`. Use `docker compose --profile app down` to stop the stack and keep the database volume.

## Initial administrator and employee access

Bootstrap is opt-in and requires an empty `inventory.users` table. A normal application startup never creates accounts. Passwords are stored as BCrypt hashes with a `{bcrypt}` prefix and cost 12; the bootstrap password must contain at least 14 Unicode characters and at most 72 UTF-8 bytes.

Create a temporary password file without putting the password in shell history (Python 3 is used only for this local prompt):

```sh
umask 077
mkdir -p secrets
python3 - <<'PYTHON'
from getpass import getpass
from pathlib import Path
with Path("secrets/bootstrap-admin-password").open("x", encoding="utf-8") as file:
    file.write(getpass("Initial administrator password: "))
PYTHON
```

Then run the one-shot command and remove the file after success:

```sh
BOOTSTRAP_ADMIN_EMAIL=admin@example.test \
BOOTSTRAP_ADMIN_PASSWORD_FILE="$PWD/secrets/bootstrap-admin-password" \
make bootstrap-admin && rm secrets/bootstrap-admin-password && make run-local
```

The `bootstrap-admin` Spring profile disables the HTTP server, creates the administrator in a transaction, closes the context and exits. It refuses to overwrite or promote existing accounts. Concurrent bootstrap attempts are serialized by a table lock. Full account management is planned for a later release.

| Endpoint | Access | Purpose |
|---|---|---|
| `GET /api/v1/auth/me` | ADMIN or OPERATOR | ID, normalized email and role of the current employee |
| `GET /api/v1/auth/csrf` | ADMIN or OPERATOR | CSRF header name and masked token; also sets the CSRF cookie |
| `GET /api/v1/users/{id}` | ADMIN | Read one employee account without its password hash |

```sh
curl --user admin@example.test http://127.0.0.1:8080/api/v1/auth/me
```

`curl` prompts for the password. The API checks credentials and current account state on every request; it does not persist authentication in an HTTP session. CSRF protection remains enabled for unsafe methods, using an HttpOnly, SameSite=Strict cookie and a token returned by `/api/v1/auth/csrf`. In `deploy`, the CSRF cookie is also Secure. Future write requests must send both the cookie and the returned token in the named header. Providing a CSRF token does not grant access to an unimplemented endpoint.

Errors use `application/problem+json` with `type`, `title`, `status`, `detail` and `instance`. Anonymous or invalid credentials return 401 with a Basic challenge; insufficient permission returns 403; malformed IDs return 400; unknown employee IDs return 404. Database integrity conflicts use 409. Responses omit passwords, SQL and exception traces.

## Formatting and tests

```sh
make format-java
make verify-build
```

Spotless runs the same pinned version of `google-java-format` on application and test code locally and in CI. CI checks formatting before tests. Run `make format-java` to apply any required fixes locally.

The integration tests start their own PostgreSQL container through Testcontainers. They check the database, Flyway initialization, JPA mappings and lazy associations, health responses, employee authentication and authorization, CSRF, bootstrap races, constraints, and transaction rollback. Compose and Testcontainers use the same PostgreSQL release. GitHub Actions runs the tests and starts the built container with PostgreSQL for a smoke test. It also validates the deployment Compose configuration using example values and prints container logs if the smoke test fails.

## Database schema

Flyway owns schema changes and Hibernate validates entity mappings at startup. Put versioned migrations under `src/main/resources/db/migration/` and qualify application objects with `inventory.`, for example `CREATE TABLE inventory.products (...)`. Flyway keeps its history in `flyway_history`. Add a new migration for every schema change after the initial migration has been applied.

The [demo fixture](src/test/resources/sql/demo-data.sql) is loaded explicitly by tests and is never seeded during application startup. To explore the SQL examples, use a disposable PostgreSQL database with V1 applied and then load the fixture. Expected results include DEMO-002 totaling 45.50 PLN, DEMO-003 totaling 23.00 PLN, and WIDGET availability of 7 in NORTH and 4 in SOUTH. Both reconciliation queries should return no rows.

## Deployment configuration

The release image defaults to the `deploy` profile. The Compose example targets a single Docker host with an HTTPS reverse proxy and an external PostgreSQL 17 server. The application listens on host loopback. PostgreSQL connections use TLS with CA and hostname verification. Use the server hostname from its certificate for `DB_HOST`.

The security policy exposes `GET /livez` and `GET /readyz` with status only. Readiness checks database connectivity. Employee routes require HTTP Basic; all unlisted routes remain denied. Terminate HTTPS at the reverse proxy and keep the application port on loopback. HTTP Basic credentials must only travel over HTTPS outside local development.

### Provision PostgreSQL

Run the following as a database administrator in `psql` to create the dedicated database and set two role passwords interactively.

```sql
CREATE DATABASE order_inventory;
CREATE ROLE inventory_migrator LOGIN;
CREATE ROLE inventory_app LOGIN;
\password inventory_migrator
\password inventory_app
```

Connect as the administrator to `order_inventory` before applying these grants.

```sql
REVOKE CONNECT, TEMPORARY ON DATABASE order_inventory FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE order_inventory TO inventory_migrator, inventory_app;
CREATE SCHEMA flyway_history AUTHORIZATION inventory_migrator;
CREATE SCHEMA inventory AUTHORIZATION inventory_migrator;
GRANT USAGE ON SCHEMA inventory TO inventory_app;

ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA inventory
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO inventory_app;
ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA inventory
    GRANT USAGE ON SEQUENCES TO inventory_app;
```

Flyway connects as the schema owner during startup. The application's connection pool uses the data access role. Grant privileges explicitly for any existing objects if adapting these steps to a pre-provisioned database. If you choose different database or role names, use those names consistently in the SQL and deployment environment.

### Run the release image

Copy `.env.deploy.example` to `.env.deploy`, set `APP_IMAGE` to a release image by digest, and fill in the database connection settings. Provide the server CA PEM and two separate password files at the configured paths. Write each password without a trailing newline. On a Linux Docker host, file-backed Compose secrets retain their source permissions. Grant the container user (UID/GID `10001:10001`) read access to the password files without making them world-readable.

```sh
docker compose --env-file .env.deploy -f compose.deploy.yaml up -d --wait
curl -fsS http://127.0.0.1:8080/readyz
```
