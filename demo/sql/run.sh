#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

database=order_inventory_demo
sql=demo/sql
psql=(docker compose exec -T db psql -X -U inventory_local -d "$database"
    -v ON_ERROR_STOP=1 -P pager=off --single-transaction)

case ${1:-} in
    setup)
        set -a
        # shellcheck disable=SC1091
        source ./.env
        set +a
        : "${DB_PASSWORD:?Set DB_PASSWORD in .env}"
        docker compose up --wait db
        # createdb refuses to replace an existing database.
        docker compose exec -T db createdb -U inventory_local "$database"

        cleanup_failed_setup() {
            local result=$?
            if (( result != 0 )); then
                docker compose exec -T db dropdb -U inventory_local --if-exists "$database" || true
            fi
        }
        trap cleanup_failed_setup EXIT

        FLYWAY_PASSWORD="$DB_PASSWORD" ./mvnw -B -ntp flyway:migrate \
            -Dflyway.url="jdbc:postgresql://127.0.0.1:${DB_PORT:-5432}/$database" \
            -Dflyway.user=inventory_local -Dflyway.createSchemas=true
        "${psql[@]}" -f - < "$sql/seed.sql"
        printf 'Demo database %s is ready.\n' "$database"
        ;;
    orders|stock|consistency)
        "${psql[@]}" -c 'SET TRANSACTION READ ONLY' -f - < "$sql/$1.sql"
        ;;
    *)
        printf 'Usage: %s {setup|orders|stock|consistency}\n' "$0" >&2
        exit 2
        ;;
esac
