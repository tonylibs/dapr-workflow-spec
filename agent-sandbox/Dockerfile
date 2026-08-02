# Shared dev/agent-sandbox image for the dws monorepo: JDK 25, Go 1.26, Node 24 + pnpm,
# git/gh/jq, Claude Code, Codex, OpenSpec, OpenWiki, Superpowers, and ClawTeam. Every toolchain below is
# copied from the exact base image the corresponding component already builds with, so
# `docker build agent-sandbox/` matches what CI ships:
#   - JDK:  dws-orchestrator/Dockerfile      -> eclipse-temurin:25-jdk
#   - Go:   dws-call-http/Dockerfile, dws-run/Dockerfile.* -> golang:1.26 (go.mod: go 1.26)
#   - Node: dws-call-openapi/Dockerfile      -> node:24-slim
#   - pnpm: dws-call-openapi/package.json    -> "packageManager": "pnpm@11.10.0"
FROM eclipse-temurin:25-jdk AS jdk
FROM golang:1.26 AS golang
FROM node:24-slim AS node

FROM ubuntu:24.04

ARG CODEX_NPM_VERSION=0.146.0
ARG CLAUDE_CODE_NPM_VERSION=2.1.220
ARG OPENSPEC_NPM_VERSION=1.6.0
ARG OPENWIKI_NPM_VERSION=0.2.3

# gcc/libc6-dev: `go test -race` (dws-call-http, dws-run CI gate) needs cgo, which needs a C
# compiler on PATH -- Go auto-disables cgo without one.
# g++: OpenWiki's better-sqlite3 dependency can fall back to a native build when no prebuilt
# binary is available for the target platform.
RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl ca-certificates jq make unzip gnupg gcc g++ libc6-dev \
    python3 python3-pip pipx tmux \
    && rm -rf /var/lib/apt/lists/*

# --- gh CLI ---
# Official apt repo per https://github.com/cli/cli/blob/trunk/docs/install_linux.md.
# That repo (unlike Docker Hub tags) carries only the single current stable release --
# there is no history of old versions to pin against -- so, like the other apt packages
# installed above, this resolves to whatever that repo currently serves.
RUN mkdir -p -m 755 /etc/apt/keyrings \
    && curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o /etc/apt/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /etc/apt/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends gh \
    && rm -rf /var/lib/apt/lists/*

# --- JDK 25 (dws-controller, dws-orchestrator use ./mvnw, no separate Maven install needed;
#     dws-controller's cdk8s import step also needs Node at build time, see Node section below) ---
COPY --from=jdk /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# --- Go 1.26 (dws-call-http, dws-run) ---
# GOPATH=/root/go makes GOMODCACHE default to /root/go/pkg/mod, which is exactly the
# go-cache PVC mount declared in sandbox.yaml -- kept explicit here so the two stay in sync.
COPY --from=golang /usr/local/go /usr/local/go
ENV GOPATH=/root/go
ENV GOMODCACHE="${GOPATH}/pkg/mod"
ENV PATH="/usr/local/go/bin:${GOPATH}/bin:${PATH}"

# --- Node 24 + pnpm (dws-call-openapi; also needed at build time by dws-controller, whose
#     Maven build shells out to Node/npx for cdk8s import -- see dws-controller/CLAUDE.md) ---
# Copy the node image's whole /usr/local rather than hand-picking files: node/npm/npx/corepack
# are a mix of real files and symlinks with relative requires into sibling lib/ directories,
# so partial copies break corepack's own module resolution.
COPY --from=node /usr/local /usr/local

# pnpm's default global bin dir (where corepack's own pnpm shim lands); corepack refuses to
# activate unless it's on PATH.
ENV PATH="/root/.local/share/pnpm/bin:${PATH}"

# pnpm store lives on the pnpm-store PVC mount declared in sandbox.yaml
# (/root/.local/share/pnpm/store); set explicitly so it doesn't depend on XDG defaults.
# `corepack enable` with no args also tries to shim yarn, which node:24-slim doesn't ship a
# placeholder for and fails realpath-ing; scope it to pnpm only, which is all this image needs.
RUN corepack enable pnpm \
    && corepack prepare pnpm@11.10.0 --activate \
    && pnpm config set store-dir /root/.local/share/pnpm/store --global

# --- Agent CLIs ---
# Pin the published Claude Code, Codex, OpenSpec, and OpenWiki packages so image rebuilds are reproducible.
# Node is already present for the TypeScript components, so the CLIs add no additional runtime.
RUN npm install --global --no-audit --no-fund \
    "@anthropic-ai/claude-code@${CLAUDE_CODE_NPM_VERSION}" \
    "@openai/codex@${CODEX_NPM_VERSION}" \
    "@fission-ai/openspec@${OPENSPEC_NPM_VERSION}" \
    "openwiki@${OPENWIKI_NPM_VERSION}"

# --- Superpowers plugins ---
# Each agent uses its native official marketplace. A fresh ~/.claude config has no marketplaces
# registered, so the official one must be added before `plugin install` can resolve it. These
# commands install into root's user-level agent configuration, the runtime home used by sandbox.yaml.
RUN claude plugin marketplace add anthropics/claude-plugins-official \
    && claude plugin install superpowers@claude-plugins-official --scope user \
    && codex plugin add superpowers@openai-curated --json >/dev/null \
    && claude plugin list --json | grep -q 'superpowers@claude-plugins-official' \
    && codex plugin list --json | grep -q '"pluginId": "superpowers@openai-curated"'

# --- ClawTeam CLI (github.com/HKUDS/ClawTeam): multi-agent swarm orchestration on top of
#     Claude Code inside this sandbox. pipx over `pip install` since Ubuntu 24.04's Python is
#     PEP 668 externally-managed; pipx gives clawteam its own venv and links the binary onto
#     PATH without --break-system-packages.
ENV PATH="/root/.local/bin:${PATH}"
RUN pipx install clawteam

# --- optional: kubectl + dapr CLI, only if agent needs to validate against a live cluster ---
# Deferred -- see agent-sandbox/README.md "Not scaffolded yet".

# --- smoke test: fail the build if any toolchain is missing or the wrong version ---
RUN set -eux; \
    java -version 2>&1 | grep -q '"25'; \
    go version | grep -q 'go1.26'; \
    node -v | grep -q '^v24\.'; \
    pnpm -v | grep -q '^11\.10\.0$'; \
    jq --version | grep -q '^jq-1'; \
    gh --version | grep -q '^gh version'; \
    git --version; \
    make --version | head -1; \
    tmux -V; \
    claude --version | grep -q "^${CLAUDE_CODE_NPM_VERSION} (Claude Code)$"; \
    codex --version | grep -q "^codex-cli ${CODEX_NPM_VERSION}$"; \
    openspec --version | grep -q "^${OPENSPEC_NPM_VERSION}$"; \
    npm list --global --depth=0 openwiki | grep -q "openwiki@${OPENWIKI_NPM_VERSION}"; \
    openwiki --help >/dev/null; \
    clawteam --help >/dev/null

WORKDIR /workspace
