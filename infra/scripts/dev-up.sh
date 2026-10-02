#!/usr/bin/env bash
# Starts the local stack (spec 7.5) with docker compose.
#   dev-up.sh            build the app image, start Postgres + app and wait until both are healthy
#   dev-up.sh --db-only  start only Postgres, e.g. to run the app with ./gradlew :app:bootRun
set -euo pipefail

docker_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docker" && pwd)"

compose() {
    docker compose -f "$docker_dir/docker-compose.yml" "$@"
}

usage() {
    echo "Usage: $(basename "$0") [--db-only]" >&2
}

# A setting as docker compose sees it: the shell environment wins over .env.
setting() {
    local name=$1 default=$2 value
    value=$(printenv "$name" || true)
    if [[ -z "$value" ]]; then
        value=$(sed -n "s/^${name}=//p" "$docker_dir/.env" | tail -n 1)
    fi
    echo "${value:-$default}"
}

# Stops early with a clear message when a host port is taken by something other than this stack.
check_port() {
    local service=$1 variable=$2 port=$3 pid
    command -v lsof >/dev/null || return 0
    if compose ps --status running --services 2>/dev/null | grep -qx "$service"; then
        return 0
    fi
    pid=$(lsof -t -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)
    if [[ -n "$pid" ]]; then
        echo "Port $port is already in use by $(basename "$(ps -o comm= -p "$pid")") (pid $pid)." >&2
        echo "Stop it, or set $variable to a free port in infra/docker/.env." >&2
        exit 1
    fi
}

if [[ ! -f "$docker_dir/.env" ]]; then
    cp "$docker_dir/.env.example" "$docker_dir/.env"
    echo "Created infra/docker/.env from .env.example"
fi

case "${1:-}" in
    --db-only)
        check_port postgres POSTGRES_PORT "$(setting POSTGRES_PORT 5432)"
        compose up --detach --wait postgres
        echo "Postgres is ready on $(compose port postgres 5432). Start the app with: ./gradlew :app:bootRun"
        ;;
    "")
        check_port postgres POSTGRES_PORT "$(setting POSTGRES_PORT 5432)"
        check_port app APP_PORT "$(setting APP_PORT 8080)"
        compose up --detach --build --wait --wait-timeout 300
        app_url="http://$(compose port app 8080)"
        echo "App is ready on $app_url (API docs: $app_url/swagger-ui.html)"
        ;;
    *)
        usage
        exit 2
        ;;
esac
