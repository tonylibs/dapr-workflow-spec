#!/usr/bin/env bash
set -euo pipefail

chart_dir="${1:-charts/dws}"
case_id="${RANDOM}${RANDOM}"
network="dws-gateway-cors-${case_id}"
upstream="dws-gateway-upstream-${case_id}"
gateway="dws-gateway-under-test-${case_id}"
work_dir="$(mktemp -d)"

cleanup() {
  docker rm -f "$gateway" "$upstream" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work_dir"
}
trap cleanup EXIT

helm template dws "$chart_dir" \
  --show-only templates/admin-gateway/configmap.yaml \
  --api-versions dapr.io/v1alpha1 \
  --set dapr.enabled=false \
  --set postgresql.enabled=false \
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws \
  --set adminGateway.enabled=true \
  --set adminGateway.corsOrigins[0]=https://console.example.test \
  | sed -n '/^  default.conf: |$/,$p' \
  | tail -n +2 \
  | sed 's/^    //' \
  | sed -E 's|http://[^/]+:3500|http://upstream:8080|' \
  > "$work_dir/gateway.conf"

printf '%s\n' \
  'server {' \
  '  listen 8080;' \
  '  location / {' \
  '    add_header Access-Control-Allow-Origin "*" always;' \
  '    return 200 "$args\n";' \
  '  }' \
  '}' \
  > "$work_dir/upstream.conf"

docker network create "$network" >/dev/null
docker run -d --name "$upstream" --network "$network" --network-alias upstream \
  -v "$work_dir/upstream.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.29-alpine >/dev/null
docker run -d --name "$gateway" --network "$network" --network-alias gateway \
  -v "$work_dir/gateway.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.29-alpine >/dev/null

for _ in $(seq 1 20); do
  if docker run --rm --network "$network" curlimages/curl:8.14.1 \
    -fsS http://gateway/healthz >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

response="$(docker run --rm --network "$network" curlimages/curl:8.14.1 \
  -sS -D - \
  -H 'Origin: https://console.example.test' \
  -H 'Authorization: Bearer test-token' \
  'http://gateway/workflows?dryRun=true' \
  | tr -d '\r')"

origin_headers="$(printf '%s\n' "$response" | grep -i '^Access-Control-Allow-Origin:')"
printf 'observed origin headers:\n%s\n' "$origin_headers"
test "$(printf '%s\n' "$origin_headers" | wc -l)" -eq 1
test "$origin_headers" = 'Access-Control-Allow-Origin: https://console.example.test'
test "$(printf '%s\n' "$response" | tail -n 1)" = 'dryRun=true'
