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
    --data '{"name":"Order demo customer","email":"draft-buyer@example.test"}'
customer=$(jq -er .id "$response_file")
request POST 'Create a sample warehouse' "$api/warehouses" 201 "${write_auth[@]}" \
    --data "{\"code\":\"DRAFT-$suffix\",\"name\":\"Order demo warehouse\"}"
warehouse=$(jq -er .id "$response_file")
request POST 'Create an original catalog product' "$api/products" 201 "${write_auth[@]}" \
    --data "{\"sku\":\"ORIG-$suffix\",\"name\":\"Original widget\",\"unitPrice\":12.50}"
product=$(jq -er .id "$response_file")
request POST 'Create a second catalog product' "$api/products" 201 "${write_auth[@]}" \
    --data "{\"sku\":\"EXTRA-$suffix\",\"name\":\"Extra pack\",\"unitPrice\":2.00}"
extra=$(jq -er .id "$response_file")

stock="$api/warehouses/$warehouse/stock/$product"
request GET 'Read stock before preparing the draft' "$stock" 200 "${auth[@]}"
stock_before=$(jq -c '{onHand,reserved,available,updatedAt}' "$response_file")
request POST 'Open an empty draft order' "$api/orders" 201 "${write_auth[@]}" \
    --data "{\"orderNumber\":\"DRAFT-$suffix\",\"customerId\":\"$customer\",\"warehouseId\":\"$warehouse\"}"
order_id=$(jq -er .order.id "$response_file")
order="$api/orders/$order_id"
jq -e '.order.status == "DRAFT" and .order.total == 0 and (.items | length) == 0' "$response_file" >/dev/null
request POST 'Snapshot the original product and order three units' "$order/items" 201 "${write_auth[@]}" \
    --data "{\"productId\":\"$product\",\"quantity\":3}"
item="$order/items/$(jq -er .id "$response_file")"
request POST 'Reject a second line for the same product' "$order/items" 409 "${write_auth[@]}" \
    --data "{\"productId\":\"$product\",\"quantity\":1}"
request PUT 'Change the SKU, name and price in the catalog' "$api/products/$product" 200 "${write_auth[@]}" \
    --data "{\"sku\":\"NEW-$suffix\",\"name\":\"Updated widget\",\"unitPrice\":99.99}"
request PUT 'Change quantity while keeping the saved snapshot' "$item" 200 "${write_auth[@]}" \
    --data '{"quantity":4}'
jq -e --arg sku "ORIG-$suffix" '.quantity == 4 and .productSku == $sku and .productName == "Original widget" and .unitPrice == 12.5 and .lineTotal == 50' "$response_file" >/dev/null
request POST 'Add a second line' "$order/items" 201 "${write_auth[@]}" \
    --data "{\"productId\":\"$extra\",\"quantity\":2}"
extra_item="$order/items/$(jq -er .id "$response_file")"
request PUT 'Update the draft note and verify its total' "$order" 200 "${write_auth[@]}" \
    --data "{\"customerId\":\"$customer\",\"warehouseId\":\"$warehouse\",\"note\":\"Draft checked with the customer\"}"
jq -e '.order.total == 54 and (.items | length) == 2' "$response_file" >/dev/null
request DELETE 'Remove the second line from the draft' "$extra_item" 204 "${write_auth[@]}"
request GET 'Read the final draft and its remaining line' "$order" 200 "${auth[@]}"
jq -e '.order.status == "DRAFT" and .order.total == 50 and (.items | length) == 1' "$response_file" >/dev/null
request GET 'Find the draft by status, customer and warehouse' \
    "$api/orders?status=DRAFT&customerId=$customer&warehouseId=$warehouse&size=1" 200 "${auth[@]}"
jq -e --arg id "$order_id" '.totalElements == 1 and .items[0].id == $id and .items[0].total == 50' "$response_file" >/dev/null
request GET 'Verify stock and reservations are unchanged' "$stock" 200 "${auth[@]}"
[[ $(jq -c '{onHand,reserved,available,updatedAt}' "$response_file") == "$stock_before" ]]
