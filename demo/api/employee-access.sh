#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z ${1:-} || $1 == *:* ]]; then
    printf 'Usage: %s EMPLOYEE_EMAIL\n' "$0" >&2
    exit 2
fi

# shellcheck source=demo/api/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

request 'Anonymous access' /api/v1/auth/me 401
request 'Authenticated employee profile' /api/v1/auth/me 200 --user "$1"
