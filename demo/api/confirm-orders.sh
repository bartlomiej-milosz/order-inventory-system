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
    --data '{"name":"Reservation demo customer","email":"reservation-buyer@example.test"}'
customer=$(jq -er .id "$response_file")
request POST 'Create a sample warehouse' "$api/warehouses" 201 "${write_auth[@]}" \
    --data "{\"code\":\"RES-$suffix\",\"name\":\"Reservation demo warehouse\"}"
warehouse=$(jq -er .id "$response_file")
request POST 'Create a sample product' "$api/products" 201 "${write_auth[@]}" \
    --data "{\"sku\":\"RES-$suffix\",\"name\":\"Reservation demo widget\",\"unitPrice\":12.50}"
product=$(jq -er .id "$response_file")
stock="$api/warehouses/$warehouse/stock/$product"
request POST 'Receive ten physical units' "$stock/receipts" 201 "${write_auth[@]}" --data '{"quantity":10}'

request POST 'Create a draft for six units' "$api/orders" 201 "${write_auth[@]}" \
    --data "{\"orderNumber\":\"RES-A-$suffix\",\"customerId\":\"$customer\",\"warehouseId\":\"$warehouse\"}"
first_id=$(jq -er .order.id "$response_file")
first="$api/orders/$first_id"
request POST 'Add six units to the first draft' "$first/items" 201 "${write_auth[@]}" \
    --data "{\"productId\":\"$product\",\"quantity\":6}"
request POST 'Confirm the first order' "$first/confirm" 200 "${write_auth[@]}"
jq -e '.order.status == "CONFIRMED" and .order.confirmedAt != null' "$response_file" >/dev/null
request GET 'Verify that four units are still available' "$stock" 200 "${auth[@]}"
jq -e '.onHand == 10 and .reserved == 6 and .available == 4' "$response_file" >/dev/null

request POST 'Create a competing draft' "$api/orders" 201 "${write_auth[@]}" \
    --data "{\"orderNumber\":\"RES-B-$suffix\",\"customerId\":\"$customer\",\"warehouseId\":\"$warehouse\"}"
second_id=$(jq -er .order.id "$response_file")
second="$api/orders/$second_id"
request POST 'Ask for five units in the second draft' "$second/items" 201 "${write_auth[@]}" \
    --data "{\"productId\":\"$product\",\"quantity\":5}"
second_line="$second/items/$(jq -er .id "$response_file")"
request POST 'Reject the second confirmation because only four units are available' "$second/confirm" 409 "${write_auth[@]}"
request GET 'Verify the rejected order remains a draft' "$second" 200 "${auth[@]}"
jq -e '.order.status == "DRAFT" and .order.confirmedAt == null' "$response_file" >/dev/null
request GET 'Verify the rejected confirmation did not change stock' "$stock" 200 "${auth[@]}"
jq -e '.onHand == 10 and .reserved == 6 and .available == 4' "$response_file" >/dev/null

request PUT 'Reduce the second draft to four units' "$second_line" 200 "${write_auth[@]}" --data '{"quantity":4}'
request POST 'Confirm the second order for the remaining four units' "$second/confirm" 200 "${write_auth[@]}"
jq -e '.order.status == "CONFIRMED" and .items[0].quantity == 4' "$response_file" >/dev/null
request POST 'Reject a repeated confirmation of the first order' "$first/confirm" 409 "${write_auth[@]}"
request GET 'Verify all ten units are reserved and still physically on hand' "$stock" 200 "${auth[@]}"
jq -e '.onHand == 10 and .reserved == 10 and .available == 0' "$response_file" >/dev/null
request GET 'Verify that the receipt is the only physical movement' "$stock/movements" 200 "${auth[@]}"
jq -e '.totalElements == 1 and .items[0].type == "IN" and .items[0].quantityDelta == 10' "$response_file" >/dev/null
