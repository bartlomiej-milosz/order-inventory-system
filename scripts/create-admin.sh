#!/usr/bin/env bash
set -euo pipefail

case ${1:-} in
  local) compose=(docker compose --profile app) ;;
  deploy) compose=(docker compose --env-file .env.deploy -f compose.deploy.yaml) ;;
  *) echo "Usage: $0 local|deploy" >&2; exit 2 ;;
esac

read -r -p 'First administrator email: ' email
[[ -n $email ]] || { echo 'An email address is required.' >&2; exit 1; }
IFS= read -r -s -p 'Password (at least 14 characters, up to 72 UTF-8 bytes): ' password
printf '\n' >&2
IFS= read -r -s -p 'Repeat password: ' confirmation
printf '\n' >&2
[[ $password == "$confirmation" ]] || { echo 'Passwords do not match.' >&2; exit 1; }

# The bootstrap profile reads a regular file. Pass its contents through stdin
# and create that file only inside the one-shot container's tmpfs.
printf '%s' "$password" | "${compose[@]}" run --rm --no-deps -T \
  -e "SPRING_PROFILES_ACTIVE=$1,bootstrap-admin" -e "BOOTSTRAP_ADMIN_EMAIL=$email" \
  --entrypoint /bin/sh app -ec '
    umask 077
    BOOTSTRAP_ADMIN_PASSWORD_FILE=$(mktemp /tmp/bootstrap.XXXXXXXX)
    export BOOTSTRAP_ADMIN_PASSWORD_FILE
    trap '\''rm -f "$BOOTSTRAP_ADMIN_PASSWORD_FILE"'\'' EXIT
    cat > "$BOOTSTRAP_ADMIN_PASSWORD_FILE"
    java -jar /app/app.jar
  '
