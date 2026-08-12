#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=demo/api/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

up_pattern='"status"[[:space:]]*:[[:space:]]*"UP"'

request 'Application liveness' /livez 200
if [[ ! $(<"$response_file") =~ $up_pattern ]]; then
    printf 'Liveness response did not report UP.\n' >&2
    exit 1
fi

request 'Application readiness' /readyz 200
if [[ ! $(<"$response_file") =~ $up_pattern ]]; then
    printf 'Readiness response did not report UP.\n' >&2
    exit 1
fi
