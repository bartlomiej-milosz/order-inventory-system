# Order & Inventory Management System

A Spring Boot backend for managing B2B orders, stock levels, reservations, and stock movements. The repository currently contains the application infrastructure.

## Scope and current status

The v1 design has **8 domain tables** and supports multiple warehouses, with one warehouse per order. Confirmation reserves stock; fulfillment decreases physical stock; cancellation releases the reservation. Reservations are represented by CONFIRMED order items and the reserved counter, without a separate reservation table.

- [Project specification](docs/PROJECT_SPEC.md) — scope, fields, rules and constraints.
- [Database diagram](docs/schema-design.drawio) — editable schema and design notes.
- [Implementation milestones](docs/MILESTONES.md) — verified progress and remaining work.
- [Repository review](docs/REVIEW.md) — findings and verification limits.

Domain migrations, entities, order services and employee authentication are not implemented yet. The existing tests verify infrastructure only; they do not prove that the business workflow works. The next implementation step is the initial Flyway migration after completing the model checkpoint.

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

## Formatting and tests

```sh
make format-java
make verify-build
```

Spotless runs the same pinned version of `google-java-format` on application and test code locally and in CI. CI checks formatting before tests. Run `make format-java` to apply any required fixes locally.

The integration tests start their own PostgreSQL container through Testcontainers. They check the database, Flyway initialization, health responses, and anonymous access. Compose and Testcontainers use the same PostgreSQL release. GitHub Actions runs the tests and starts the built container with PostgreSQL for a smoke test. It also validates the deployment Compose configuration using example values and prints container logs if the smoke test fails.

## Database schema

Flyway owns schema changes and Hibernate validates entity mappings at startup. Put versioned migrations under `src/main/resources/db/migration/` and qualify application objects with `inventory.`, for example `CREATE TABLE inventory.products (...)`. Flyway keeps its history in `flyway_history`. Add a new migration for every schema change after the initial migration has been applied.

## Deployment configuration

The release image defaults to the `deploy` profile. The Compose example targets a single Docker host with an HTTPS reverse proxy and an external PostgreSQL 17 server. The application listens on host loopback. PostgreSQL connections use TLS with CA and hostname verification. Use the server hostname from its certificate for `DB_HOST`.

The security policy exposes `GET /livez` and `GET /readyz` with status only. Readiness checks database connectivity. All other application routes are denied in deployment until authentication and authorization are implemented. Configure the reverse proxy to expose the routes you need over HTTPS.

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
