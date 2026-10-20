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
    --data '{"name":"Order completion customer","email":"completion-buyer@example.test"}'
customer=$(jq -er .id "$response_file")
request POST 'Create a sample warehouse' "$api/warehouses" 201 "${write_auth[@]}" \
    --data "{\"code\":\"END-$suffix\",\"name\":\"Order completion warehouse\"}"
warehouse=$(jq -er .id "$response_file")
request POST 'Create a sample product' "$api/products" 201 "${write_auth[@]}" \
    --data "{\"sku\":\"END-$suffix\",\"name\":\"Order completion widget\",\"unitPrice\":12.50}"
product=$(jq -er .id "$response_file")
stock="$api/warehouses/$warehouse/stock/$product"
request POST 'Receive ten physical units' "$stock/receipts" 201 "${write_auth[@]}" --data '{"quantity":10}'

create_draft() {
    local number=$1 quantity=$2
    request POST "Create an order for $quantity units" "$api/orders" 201 "${write_auth[@]}" \
        --data "{\"orderNumber\":\"$number\",\"customerId\":\"$customer\",\"warehouseId\":\"$warehouse\"}"
    order_id=$(jq -er .order.id "$response_file")
    order="$api/orders/$order_id"
    request POST "Add $quantity units" "$order/items" 201 "${write_auth[@]}" \
        --data "{\"productId\":\"$product\",\"quantity\":$quantity}"
}

check_stock() {
    local label=$1 on_hand=$2 reserved=$3 available=$4
    request GET "$label" "$stock" 200 "${auth[@]}"
    jq -e --argjson on_hand "$on_hand" --argjson reserved "$reserved" --argjson available "$available" \
        '.onHand == $on_hand and .reserved == $reserved and .available == $available' "$response_file" >/dev/null
}

create_draft "END-F-$suffix" 3
fulfilled_id=$order_id
request POST 'Confirm and reserve three units' "$order/confirm" 200 "${write_auth[@]}"
check_stock 'Check that seven units are available after confirmation' 10 3 7
request POST 'Fulfill the order and issue three units' "$order/fulfill" 200 "${write_auth[@]}"
jq -e '.order.status == "FULFILLED" and .order.confirmedAt != null' "$response_file" >/dev/null
check_stock 'Check that fulfillment leaves seven on hand and zero reserved' 7 0 7
request POST 'Reject cancellation of a fulfilled order' "$order/cancel" 409 "${write_auth[@]}"

create_draft "END-C-$suffix" 2
request POST 'Confirm and reserve two units' "$order/confirm" 200 "${write_auth[@]}"
confirmed_at=$(jq -er .order.confirmedAt "$response_file")
check_stock 'Check that five units are available after the second confirmation' 7 2 5
request POST 'Cancel the confirmed order and release two units' "$order/cancel" 200 "${write_auth[@]}"
jq -e --arg time "$confirmed_at" '.order.status == "CANCELLED" and .order.confirmedAt == $time' "$response_file" >/dev/null
check_stock 'Check that seven units are available again' 7 0 7

create_draft "END-D-$suffix" 20
request POST 'Cancel a draft without a reservation' "$order/cancel" 200 "${write_auth[@]}"
jq -e '.order.status == "CANCELLED" and .order.confirmedAt == null' "$response_file" >/dev/null
check_stock 'Check that cancelling a draft leaves stock unchanged' 7 0 7

request GET 'Verify that the history contains only the receipt and fulfillment' "$stock/movements" 200 "${auth[@]}"
jq -e --arg id "$fulfilled_id" '
    .totalElements == 2
    and ([.items[] | select(.type == "IN" and .quantityDelta == 10 and .orderId == null)] | length) == 1
    and ([.items[] | select(.type == "OUT" and .quantityDelta == -3 and .orderId == $id)] | length) == 1
' "$response_file" >/dev/null
