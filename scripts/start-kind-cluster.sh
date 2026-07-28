#!/bin/bash
set -euo pipefail

# Only run in Claude Code cloud sessions
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Only run in cloud environments where KUBER_ENV=true is set
if [ "${KUBER_ENV:-}" != "true" ]; then
  exit 0
fi

CLUSTER_NAME="test-cluster"

service docker start 2>/dev/null || true

if ! kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
  kind create cluster --name "${CLUSTER_NAME}"
else
  echo "kind cluster '${CLUSTER_NAME}' already exists, skipping creation"
fi

kubectl cluster-info --context "kind-${CLUSTER_NAME}"