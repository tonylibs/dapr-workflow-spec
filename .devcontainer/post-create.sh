#!/usr/bin/env bash
set -euo pipefail

# Claude Code CLI. The devcontainer feature installs it first; this enforces the same version used
# by agent-sandbox/Dockerfile before installing plugins into the vscode user's configuration.
CLAUDE_CODE_NPM_VERSION="2.1.220"
if [ "$(claude --version 2>/dev/null || true)" != "${CLAUDE_CODE_NPM_VERSION} (Claude Code)" ]; then
  # The claude-code devcontainer feature preinstalls this package as root, so a plain
  # (vscode-user) npm install can't overwrite its root-owned global dir.
  sudo env "PATH=$PATH" npm install --global --no-audit --no-fund "@anthropic-ai/claude-code@${CLAUDE_CODE_NPM_VERSION}"
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

# Dapr CLI. kubectl/helm come from the kubectl-helm-minikube devcontainer feature; the Dapr CLI
# has no equivalent feature, so install it here (same script the helm.yml CI workflow uses).
DAPR_CLI_VERSION="1.15.1"
if [ "$(dapr --version 2>/dev/null | awk '/^CLI version/ {print $3}')" != "${DAPR_CLI_VERSION}" ]; then
  wget -q https://raw.githubusercontent.com/dapr/cli/master/install/install.sh -O - | /bin/bash -s "${DAPR_CLI_VERSION}"
fi
dapr --version | grep -Fq "CLI version: ${DAPR_CLI_VERSION}"

# OpenWiki CLI. Keep this pin aligned with the repository's OpenWiki update workflow.
OPENWIKI_NPM_VERSION="0.2.3"
if ! grep -Fq "openwiki@${OPENWIKI_NPM_VERSION}" <<<"$(npm list --global --depth=0 openwiki 2>/dev/null || true)"; then
  npm install --global --no-audit --no-fund "openwiki@${OPENWIKI_NPM_VERSION}"
fi
grep -Fq "openwiki@${OPENWIKI_NPM_VERSION}" <<<"$(npm list --global --depth=0 openwiki)"
# openwiki@0.2.3 currently ships a broken langchain/@langchain-core dependency pairing that
# crashes on startup; don't let that abort the rest of this script's setup.
openwiki --help >/dev/null || echo "WARN: openwiki --help failed (broken upstream package) — continuing"

# Superpowers plugins for both agent runtimes. Install only when the enabled plugin is absent.
# A fresh ~/.claude config has no marketplaces registered, so the official one must be added
# before `plugin install` can resolve it.
if ! grep -Fq '"claude-plugins-official"' <<<"$(claude plugin marketplace list --json)"; then
  claude plugin marketplace add anthropics/claude-plugins-official
fi
if ! grep -Fq 'superpowers@claude-plugins-official' <<<"$(claude plugin list --json)"; then
  claude plugin install superpowers@claude-plugins-official --scope user
fi
grep -Fq 'superpowers@claude-plugins-official' <<<"$(claude plugin list --json)"

if ! grep -Fq '"name": "claude-plugins-official"' <<<"$(codex plugin marketplace list --json)"; then
  codex plugin marketplace add anthropics/claude-plugins-official --json >/dev/null
fi
if ! grep -Fq '"pluginId": "superpowers@claude-plugins-official"' <<<"$(codex plugin list --json)"; then
  codex plugin add superpowers@claude-plugins-official --json >/dev/null
fi
grep -Fq '"pluginId": "superpowers@claude-plugins-official"' <<<"$(codex plugin list --json)"

# ClawTeam CLI (github.com/HKUDS/ClawTeam): multi-agent swarm orchestration on top of
# Claude Code. pipx over `pip install` since Ubuntu's Python is PEP 668 externally-managed.
if ! command -v pipx >/dev/null 2>&1; then
  sudo apt-get update && sudo apt-get install -y --no-install-recommends pipx
fi
pipx ensurepath
pipx install clawteam

# dws-call-openapi (Node/TypeScript, pnpm)
# CI=true: postCreateCommand has no TTY, and pnpm otherwise prompts before purging an
# existing node_modules dir (e.g. one left over from a bind-mounted host workspace).
if [ -f dws-call-openapi/package.json ]; then
  (cd dws-call-openapi && CI=true pnpm install)
fi

# dws-admin (Node/TypeScript, pnpm)
if [ -f dws-admin/package.json ]; then
  (cd dws-admin && CI=true pnpm install)
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
