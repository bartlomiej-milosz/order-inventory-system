#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z $1 || $1 == *:* || $1 == *$'\n'* || $1 == *$'\r'* ]]; then
    printf 'Usage: %s ADMIN_EMAIL\n' "$0" >&2
    exit 2
fi
command -v jq >/dev/null || { printf 'Install jq to run this demo.\n' >&2; exit 2; }
IFS= read -r -s -p 'Administrator password: ' password
printf '\n' >&2

# shellcheck source=demo/api/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
work=$(mktemp -d)
trap 'rm -f -- "$response_file"; rm -rf -- "$work"' EXIT
# Use curl's config file so the password is absent from curl's command-line arguments.
credentials=$1:$password
credentials=${credentials//\\/\\\\}
credentials=${credentials//\"/\\\"}
(umask 077; printf 'user = "%s"\n' "$credentials" > "$work/curl.conf")
unset password credentials

api=/api/v1
auth=(--config "$work/curl.conf")
request GET 'Verify the signed-in employee' "$api/auth/me" 200 "${auth[@]}"
jq -e '.role == "ADMIN"' "$response_file" >/dev/null || { printf 'Use an ADMIN account for this demo.\n' >&2; exit 1; }

# Spring sends the token in the body and its matching cookie in the same response.
curl --disable --silent --show-error --fail --max-time 15 --proto '=http,https' \
    "${auth[@]}" --cookie-jar "$work/cookies" --output "$work/csrf.json" -- "$base_url$api/auth/csrf"
csrf_header=$(jq -er .headerName "$work/csrf.json")
csrf_token=$(jq -er .token "$work/csrf.json")
write_auth=("${auth[@]}" --cookie "$work/cookies" --header "$csrf_header: $csrf_token"
    --header 'Content-Type: application/json')

suffix=$(openssl rand -hex 6 | tr '[:lower:]' '[:upper:]')
request POST 'Create a sample customer' "$api/customers" 201 "${write_auth[@]}" \
    --data '{"name":"Demo Retail","email":"buyer@example.test"}'
request POST 'Create a sample product' "$api/products" 201 "${write_auth[@]}" \
    --data "{\"sku\":\"DEMO-$suffix\",\"name\":\"Demo widget\",\"unitPrice\":12.50}"
product_id=$(jq -er .id "$response_file")
request POST 'Create a sample warehouse' "$api/warehouses" 201 "${write_auth[@]}" \
    --data "{\"code\":\"DEMO-$suffix\",\"name\":\"Demo warehouse\"}"
warehouse_id=$(jq -er .id "$response_file")

stock="$api/warehouses/$warehouse_id/stock/$product_id"
request GET 'Read the initial zero stock' "$stock" 200 "${auth[@]}"
jq -e '.onHand == 0 and .reserved == 0 and .available == 0' "$response_file" >/dev/null
request POST 'Receive ten units' "$stock/receipts" 201 "${write_auth[@]}" \
    --data '{"quantity":10,"note":"Initial delivery"}'
request POST 'Adjust by two damaged units' "$stock/adjustments" 201 "${write_auth[@]}" \
    --data '{"quantityDelta":-2,"note":"Damaged packaging"}'
request POST 'Reject a correction that would make stock negative' "$stock/adjustments" 409 "${write_auth[@]}" \
    --data '{"quantityDelta":-9,"note":"Invalid correction example"}'
request GET 'Verify the current stock' "$stock" 200 "${auth[@]}"
jq -e '.onHand == 8 and .reserved == 0 and .available == 8' "$response_file" >/dev/null
request GET 'Read movement history' "$stock/movements?size=2" 200 "${auth[@]}"
jq -e '.totalElements == 2 and ([.items[].quantityDelta] | add) == 8' "$response_file" >/dev/null
