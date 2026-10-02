#!/usr/bin/env bash
# Validates the Helm chart skeleton (spec 7.5) the way CI does: helm lint and helm template with the example values
# and the real server profile configuration.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
chart="$root/infra/helm/ultimate-analytics"
args=(-f "$chart/values-server.example.yaml" --set-file "config.applicationServerYml=$root/infra/config/application-server.yml")

helm lint --strict "$chart" "${args[@]}"
helm template analytics "$chart" "${args[@]}" > /dev/null
# Without an existing Secret the chart creates one from username and password.
helm template analytics "$chart" "${args[@]}" --set database.existingSecret= \
    --set database.username=ultimate --set database.password=example > /dev/null
echo "Helm chart OK"
