# Shared dev/agent-sandbox image for the dws monorepo: JDK 25, Go 1.26, Node 24 + pnpm,
# git/gh/jq. Every toolchain below is copied from the exact base image the corresponding
# component already builds with, so `docker build agent-sandbox/` matches what CI ships:
#   - JDK:  dws-orchestrator/Dockerfile      -> eclipse-temurin:25-jdk
#   - Go:   dws-call-http/Dockerfile, dws-run/Dockerfile.* -> golang:1.26 (go.mod: go 1.26)
#   - Node: dws-call-openapi/Dockerfile      -> node:24-slim
#   - pnpm: dws-call-openapi/package.json    -> "packageManager": "pnpm@11.10.0"
FROM eclipse-temurin:25-jdk AS jdk
FROM golang:1.26 AS golang
FROM node:24-slim AS node

FROM ubuntu:24.04

# gcc/libc6-dev: `go test -race` (dws-call-http, dws-run CI gate) needs cgo, which needs a C
# compiler on PATH -- Go auto-disables cgo without one.
RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl ca-certificates jq make unzip gnupg gcc libc6-dev \
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
    make --version | head -1

WORKDIR /workspace
