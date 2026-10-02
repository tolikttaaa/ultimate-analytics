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

if [[ ! -f "$docker_dir/.env" ]]; then
    cp "$docker_dir/.env.example" "$docker_dir/.env"
    echo "Created infra/docker/.env from .env.example"
fi

case "${1:-}" in
    --db-only)
        compose up --detach --wait postgres
        echo "Postgres is ready on $(compose port postgres 5432). Start the app with: ./gradlew :app:bootRun"
        ;;
    "")
        compose up --detach --build --wait --wait-timeout 300
        echo "App is ready on http://$(compose port app 8080)"
        ;;
    *)
        usage
        exit 2
        ;;
esac
