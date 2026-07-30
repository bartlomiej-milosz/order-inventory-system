.DEFAULT_GOAL := help

.PHONY: help init-env start-stack start-db run-local stop-stack stack-status stack-logs app-shell check-readiness bootstrap-admin validate-db-migrations repair-db-migrations format-java verify-build
.PHONY: env up db run down ps logs shell health verify

help:
	@printf '%s\n' \
		'make init-env         Create .env with a random local database password' \
		'make start-stack      Build and start the backend and PostgreSQL in Docker' \
		'make start-db         Start PostgreSQL for development in the IDE' \
		'make run-local        Start PostgreSQL in Docker and run the backend on your machine' \
		'make bootstrap-admin        Create the first ADMIN from an explicit password file and exit' \
		'make stop-stack       Stop the stack and keep the database volume' \
		'make stack-status     Show container status' \
		'make stack-logs       Follow logs for the local stack' \
		'make app-shell        Open a shell in the backend container' \
		'make check-readiness  Check backend readiness inside the container' \
		'make validate-db-migrations Validate Flyway history in the local database' \
		'make repair-db-migrations   Repair local Flyway history after reviewing migration changes' \
		'make format-java      Format Java source and test files with google-java-format' \
		'make verify-build     Check Java formatting and run Maven verification'

init-env:
	@if [ -e .env ] || [ -L .env ]; then \
		printf '%s\n' '.env already exists.'; \
	else \
		db_password=$$(openssl rand -hex 24) && \
		( \
			umask 077 && set -C && \
			sed "s/^DB_PASSWORD=$$/DB_PASSWORD=$$db_password/" .env.example > .env \
		) && printf '%s\n' 'Created .env.'; \
	fi

start-stack:
	docker compose --profile app up --build --wait

start-db:
	docker compose up --wait db

run-local: start-db
	@set -a && . ./.env && exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

bootstrap-admin: start-db
	@set -a && . ./.env && exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,bootstrap-admin -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false

stop-stack:
	docker compose --profile app down

stack-status:
	docker compose --profile app ps

stack-logs:
	docker compose --profile app logs --follow

app-shell:
	docker compose --profile app exec app sh

check-readiness:
	@docker compose --profile app exec -T app curl -fsS http://127.0.0.1:8080/readyz && printf '\n'

validate-db-migrations: start-db
	@set -a && . ./.env && export FLYWAY_URL="jdbc:postgresql://127.0.0.1:$${DB_PORT:-5432}/order_inventory" && exec ./mvnw flyway:validate

repair-db-migrations: start-db
	@set -a && . ./.env && export FLYWAY_URL="jdbc:postgresql://127.0.0.1:$${DB_PORT:-5432}/order_inventory" && exec ./mvnw flyway:repair

format-java:
	./mvnw spotless:apply

verify-build:
	./mvnw spotless:check verify

# Keep existing commands usable.
env: init-env
up: start-stack
db: start-db
run: run-local
down: stop-stack
ps: stack-status
logs: stack-logs
shell: app-shell
health: check-readiness
format: format-java
verify: verify-build
