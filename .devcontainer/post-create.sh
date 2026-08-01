#!/usr/bin/env bash
set -euo pipefail

# ClawTeam CLI (github.com/HKUDS/ClawTeam): multi-agent swarm orchestration on top of
# Claude Code. pipx over `pip install` since Ubuntu's Python is PEP 668 externally-managed.
if ! command -v pipx >/dev/null 2>&1; then
  sudo apt-get update && sudo apt-get install -y --no-install-recommends pipx
fi
pipx ensurepath
pipx install clawteam

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
