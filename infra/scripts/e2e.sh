#!/usr/bin/env bash
# Runs the Playwright smoke test (spec 12) against a fresh stack of its own: a separate compose project with its own
# ports, image tag and volumes, removed again afterwards. The local stack of dev-up.sh is not touched.
#
#   ./infra/scripts/e2e.sh                 # build, start, test, remove
#   ./infra/scripts/e2e.sh --headed        # extra arguments go to `playwright test`
#   ./infra/scripts/e2e.sh --config playwright.guide.config.ts   # the user guide's screenshots (docs/guide/)
#
# Needs Docker with compose, Node.js and Playwright's Chromium (`cd frontend && npx playwright install chromium`).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT=ultimate-analytics-e2e
export APP_PORT="${E2E_APP_PORT:-18081}"
export POSTGRES_PORT="${E2E_POSTGRES_PORT:-15433}"
export APP_IMAGE_TAG=e2e

compose() {
  docker compose -p "$PROJECT" -f "$ROOT/infra/docker/docker-compose.yml" --env-file "$ROOT/infra/docker/.env.example" "$@"
}

cleanup() {
  compose down --volumes --remove-orphans > /dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup
echo "Starting a fresh stack on http://127.0.0.1:$APP_PORT ..."
compose up -d --build --wait --wait-timeout 300

cd "$ROOT/frontend"
[ -x node_modules/.bin/playwright ] || npm ci
E2E_BASE_URL="http://127.0.0.1:$APP_PORT" npx playwright test "$@"
