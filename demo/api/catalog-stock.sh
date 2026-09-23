#!/usr/bin/env bash
set -euo pipefail

demo_dir=$(dirname "${BASH_SOURCE[0]}")
# shellcheck source=demo/api/common.sh
source "$demo_dir/common.sh"
# shellcheck source=demo/api/write-session.sh
source "$demo_dir/write-session.sh"
prepare_write_session "$@"

api=/api/v1
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
