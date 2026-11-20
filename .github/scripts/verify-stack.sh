#!/usr/bin/env bash
# Run against the disposable Compose stack created by the CI job.
set -euo pipefail
export BASE_URL="http://127.0.0.1:${APP_PORT:-8080}"
email=ci-admin@example.test
password=$(openssl rand -hex 24)
printf '%s\n' "$email" "$password" "$password" | bash scripts/create-admin.sh local
for scenario in employee-access catalog-stock draft-orders confirm-orders complete-orders; do
    printf '%s\n' "$password" | python3 -m demo "$scenario" "$email"
done

differences=$(docker compose exec -T db psql -XqAt -v ON_ERROR_STOP=1 \
    -U inventory_local -d order_inventory < demo/sql/consistency.sql)
if [[ -n $differences ]]; then
    printf 'Inventory reconciliation found discrepancies:\n%s\n' "$differences" >&2
    exit 1
fi

docker compose stop db
curl -fsS --max-time 10 "$BASE_URL/livez" | jq -e '.status == "UP"'
status=$(curl -sS --max-time 60 -o /dev/null -w '%{http_code}' "$BASE_URL/readyz")
[[ $status == 503 ]] || { echo "Expected readiness HTTP 503, received $status" >&2; exit 1; }

docker compose --profile app up --wait --wait-timeout 120 db app
python3 -m demo health
