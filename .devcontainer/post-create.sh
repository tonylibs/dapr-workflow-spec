#!/usr/bin/env bash
set -euo pipefail

# dws-call-openapi (Node/TypeScript, pnpm)
if [ -f dws-call-openapi/package.json ]; then
  (cd dws-call-openapi && pnpm install)
fi

# dws-admin (Node/TypeScript, pnpm)
if [ -f dws-admin/package.json ]; then
  (cd dws-admin && pnpm install)
fi

# dws-console (Node/TypeScript, npm)
if [ -f dws-console/package.json ]; then
  (cd dws-console && npm install)
fi

# Warm Maven wrapper caches
if [ -f dws-controller/mvnw ]; then
  (cd dws-controller && ./mvnw -q -DskipTests dependency:go-offline || true)
fi
if [ -f dws-orchestrator/mvnw ]; then
  (cd dws-orchestrator && ./mvnw -q -DskipTests dependency:go-offline || true)
fi

echo "devcontainer post-create setup complete."
