#!/usr/bin/env bash
set -euo pipefail

# Claude Code CLI. The devcontainer feature installs it first; this enforces the same version used
# by agent-sandbox/Dockerfile before installing plugins into the vscode user's configuration.
CLAUDE_CODE_NPM_VERSION="2.1.220"
if [ "$(claude --version 2>/dev/null || true)" != "${CLAUDE_CODE_NPM_VERSION} (Claude Code)" ]; then
  npm install --global --no-audit --no-fund "@anthropic-ai/claude-code@${CLAUDE_CODE_NPM_VERSION}"
fi
test "$(claude --version)" = "${CLAUDE_CODE_NPM_VERSION} (Claude Code)"

# Codex CLI. Keep this pin aligned with agent-sandbox/Dockerfile so both development
# environments provide the same reproducible version.
CODEX_NPM_VERSION="0.146.0"
if [ "$(codex --version 2>/dev/null || true)" != "codex-cli ${CODEX_NPM_VERSION}" ]; then
  npm install --global --no-audit --no-fund "@openai/codex@${CODEX_NPM_VERSION}"
fi
test "$(codex --version)" = "codex-cli ${CODEX_NPM_VERSION}"

# OpenSpec CLI. Keep this pin aligned with agent-sandbox/Dockerfile.
OPENSPEC_NPM_VERSION="1.6.0"
if [ "$(openspec --version 2>/dev/null || true)" != "${OPENSPEC_NPM_VERSION}" ]; then
  npm install --global --no-audit --no-fund "@fission-ai/openspec@${OPENSPEC_NPM_VERSION}"
fi
test "$(openspec --version)" = "${OPENSPEC_NPM_VERSION}"

# OpenWiki CLI. Keep this pin aligned with the repository's OpenWiki update workflow.
OPENWIKI_NPM_VERSION="0.2.3"
if ! grep -Fq "openwiki@${OPENWIKI_NPM_VERSION}" <<<"$(npm list --global --depth=0 openwiki 2>/dev/null || true)"; then
  npm install --global --no-audit --no-fund "openwiki@${OPENWIKI_NPM_VERSION}"
fi
grep -Fq "openwiki@${OPENWIKI_NPM_VERSION}" <<<"$(npm list --global --depth=0 openwiki)"
openwiki --help >/dev/null

# Superpowers plugins for both agent runtimes. Install only when the enabled plugin is absent.
if ! grep -Fq 'superpowers@claude-plugins-official' <<<"$(claude plugin list --json)"; then
  claude plugin install superpowers@claude-plugins-official --scope user
fi
grep -Fq 'superpowers@claude-plugins-official' <<<"$(claude plugin list --json)"

if ! grep -Fq '"pluginId": "superpowers@openai-curated"' <<<"$(codex plugin list --json)"; then
  codex plugin add superpowers@openai-curated --json >/dev/null
fi
grep -Fq '"pluginId": "superpowers@openai-curated"' <<<"$(codex plugin list --json)"

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
