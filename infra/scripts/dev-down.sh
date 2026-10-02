#!/usr/bin/env bash
# Stops the local stack.
#   dev-down.sh            stop and remove the containers; the database and raw files are kept
#   dev-down.sh --volumes  also delete the database and raw-file volumes
set -euo pipefail

docker_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docker" && pwd)"

compose() {
    docker compose -f "$docker_dir/docker-compose.yml" "$@"
}

case "${1:-}" in
    --volumes)
        compose down --volumes
        ;;
    "")
        compose down
        ;;
    *)
        echo "Usage: $(basename "$0") [--volumes]" >&2
        exit 2
        ;;
esac
