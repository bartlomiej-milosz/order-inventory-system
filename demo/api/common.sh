# Shared response capture and status output for the demo scripts.
base_url=${BASE_URL:-http://127.0.0.1:8080}
base_url=${base_url%/}
response_file=$(mktemp)
trap 'rm -f -- "$response_file"' EXIT

request() {
    local method=$1 label=$2 path=$3 expected_status=$4 actual_status
    shift 4

    printf '\n%s\n%s %s\n' "$label" "$method" "$path"
    actual_status=$(curl --disable --silent --show-error --max-time 15 \
        --proto '=http,https' --request "$method" --output "$response_file" --write-out '%{http_code}' \
        "$@" -- "$base_url$path") || return 1
    printf 'Expected HTTP %s, received HTTP %s\n' "$expected_status" "$actual_status"
    cat -- "$response_file"
    printf '\n'

    if [[ $actual_status != "$expected_status" ]]; then
        printf 'Unexpected response status for %s.\n' "$path" >&2
        return 1
    fi
}
