# Shared Basic and CSRF credentials for the interactive write demos.
prepare_write_session() {
    if [[ $# -ne 1 || -z $1 || $1 == *:* || $1 == *$'\n'* || $1 == *$'\r'* ]]; then
        printf 'Usage: %s ADMIN_EMAIL\n' "$0" >&2
        exit 2
    fi
    command -v jq >/dev/null || { printf 'Install jq to run this demo.\n' >&2; exit 2; }

    local password credentials csrf_header csrf_token
    IFS= read -r -s -p 'Administrator password: ' password
    printf '\n' >&2
    write_session_dir=$(mktemp -d)
    trap 'rm -f -- "$response_file"; rm -rf -- "$write_session_dir"' EXIT
    credentials=$1:$password
    credentials=${credentials//\\/\\\\}
    credentials=${credentials//\"/\\\"}
    (umask 077; printf 'user = "%s"\n' "$credentials" > "$write_session_dir/curl.conf")

    auth=(--config "$write_session_dir/curl.conf")
    request GET 'Verify the signed-in employee' /api/v1/auth/me 200 "${auth[@]}"
    jq -e '.role == "ADMIN"' "$response_file" >/dev/null || { printf 'Use an ADMIN account for this demo.\n' >&2; exit 1; }

    curl --disable --silent --show-error --fail --max-time 15 --proto '=http,https' \
        "${auth[@]}" --cookie-jar "$write_session_dir/cookies" --output "$write_session_dir/csrf.json" -- "$base_url/api/v1/auth/csrf"
    csrf_header=$(jq -er .headerName "$write_session_dir/csrf.json")
    csrf_token=$(jq -er .token "$write_session_dir/csrf.json")
    write_auth=("${auth[@]}" --cookie "$write_session_dir/cookies" --header "$csrf_header: $csrf_token"
        --header 'Content-Type: application/json')
}
