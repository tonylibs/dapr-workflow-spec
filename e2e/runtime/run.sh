#!/usr/bin/env bash
# Real-runtime e2e: boots the compose stack, seeds the `order` catalog definition into
# Redis, starts a workflow instance through the orchestrator's REST API and asserts the
# in-stock branch runs to COMPLETED with `charged: true`.
#
# Requires: docker compose v2, curl, jq.
set -euo pipefail
cd "$(dirname "$0")"

DEFINITION=../catalog/order/definition.yaml
BASE_URL=${BASE_URL:-http://localhost:8080}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-180}

cleanup() {
  if [[ "${1:-}" == "fail" ]]; then
    echo "--- docker compose logs (tail) ---"
    docker compose logs --tail 100 || true
  fi
  docker compose down -v --remove-orphans || true
}
trap 'cleanup fail' ERR

echo "==> building images"
docker compose build

echo "==> starting redis and seeding the definition"
docker compose up -d --wait redis
# The Configuration API is read-only for apps; write the key straight into Redis,
# exactly as the controller does in-cluster.
docker compose exec -T redis redis-cli -x set definition < "$DEFINITION"

echo "==> starting the stack"
docker compose up -d

echo "==> waiting for the orchestrator to become ready"
deadline=$((SECONDS + TIMEOUT_SECONDS))
until curl -fsS "$BASE_URL/actuator/health/readiness" >/dev/null 2>&1; do
  if ((SECONDS >= deadline)); then
    echo "orchestrator did not become ready within ${TIMEOUT_SECONDS}s" >&2
    false
  fi
  sleep 2
done

echo "==> starting an order instance (in-stock branch)"
instance=$(curl -fsS -X POST "$BASE_URL/workflows/order/instances" \
  -H 'Content-Type: application/json' \
  -d '{"item":"widget"}' | jq -r .instanceId)
echo "    instanceId=$instance"

echo "==> polling instance status"
deadline=$((SECONDS + TIMEOUT_SECONDS))
while :; do
  state=$(curl -fsS "$BASE_URL/workflows/instances/$instance")
  status=$(jq -r .runtimeStatus <<<"$state")
  echo "    runtimeStatus=$status"
  case "$status" in
    COMPLETED) break ;;
    FAILED | TERMINATED)
      echo "workflow ended in $status: $state" >&2
      false
      ;;
  esac
  if ((SECONDS >= deadline)); then
    echo "workflow did not complete within ${TIMEOUT_SECONDS}s: $state" >&2
    false
  fi
  sleep 2
done

echo "==> asserting the flow's output"
jq -e '.output.inStock == true and .output.charged == true' <<<"$state" >/dev/null || {
  echo "unexpected output: $state" >&2
  false
}

echo "==> PASS: order flow completed as expected"
trap - ERR
cleanup
