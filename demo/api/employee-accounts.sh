#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: %s ADMIN_EMAIL {create EMPLOYEE_EMAIL ROLE | role USER_ID ROLE | deactivate USER_ID}\n' "$0" >&2
    exit 2
}

[[ $# -ge 3 ]] || usage
admin=$1
action=$2
target=$3
case $action in
    create|role)
        [[ $# -eq 4 && ( $4 == ADMIN || $4 == OPERATOR ) ]] || usage
        role=$4
        ;;
    deactivate) [[ $# -eq 3 ]] || usage ;;
    *) usage ;;
esac
if [[ $action != create ]]; then
    [[ $target =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]] || usage
fi

demo_dir=$(dirname "${BASH_SOURCE[0]}")
# shellcheck source=demo/api/common.sh
source "$demo_dir/common.sh"
# shellcheck source=demo/api/write-session.sh
source "$demo_dir/write-session.sh"
prepare_write_session "$admin"

users=/api/v1/users
case $action in
    create)
        IFS= read -r -s -p 'New employee password: ' employee_password
        printf '\n' >&2
        IFS= read -r -s -p 'Repeat new employee password: ' confirmation
        printf '\n' >&2
        [[ $employee_password == "$confirmation" ]] || { echo 'Passwords do not match.' >&2; exit 2; }
        printf '%s' "$employee_password" |
            jq -Rsc --arg email "$target" --arg role "$role" '{email:$email, role:$role, password:.}' |
            request POST 'Create the employee' "$users" 201 "${write_auth[@]}" --data-binary @-
        ;;
    role)
        request PUT 'Set the employee role' "$users/$target/role" 200 "${write_auth[@]}" --data "{\"role\":\"$role\"}"
        ;;
    deactivate)
        request POST 'Deactivate the employee' "$users/$target/deactivate" 204 "${write_auth[@]}"
        ;;
esac
