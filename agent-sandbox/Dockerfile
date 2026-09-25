# Shared dev/agent-sandbox image for the dws monorepo: JDK 25, Go 1.26, Node 24 + pnpm,
# git/gh/jq, uv, Omnigent, Claude Code, Codex, GitHub Copilot CLI, Antigravity CLI (agy),
# OpenSpec, OpenWiki, Superpowers, and ClawTeam. Every toolchain below is
# copied from the exact base image the corresponding component already builds with, so
# `docker build agent-sandbox/` matches what CI ships:
#   - JDK:  dws-orchestrator/Dockerfile      -> eclipse-temurin:25-jdk
#   - Go:   dws-call-http/Dockerfile, dws-run/Dockerfile.* -> golang:1.26 (go.mod: go 1.26)
#   - Node: dws-call-openapi/Dockerfile      -> node:24-slim
#   - pnpm: dws-call-openapi/package.json    -> "packageManager": "pnpm@11.10.0"
ARG UV_VERSION=0.12.18
FROM eclipse-temurin:25-jdk AS jdk
FROM golang:1.26 AS golang
FROM node:24-slim AS node
FROM ghcr.io/astral-sh/uv:${UV_VERSION} AS uv

FROM ubuntu:24.04

ARG UV_VERSION
ARG CODEX_NPM_VERSION=0.146.0
ARG CODEX_ACP_NPM_VERSION=1.4.0
ARG CLAUDE_CODE_NPM_VERSION=2.1.220
ARG COPILOT_NPM_VERSION=1.0.88
ARG OPENSPEC_NPM_VERSION=1.6.0
ARG OPENWIKI_NPM_VERSION=0.2.3
ARG OMNIGENT_VERSION=0.14.0
ARG KUBECTL_VERSION=v1.36.1

# gcc/libc6-dev: `go test -race` (dws-call-http, dws-run CI gate) needs cgo, which needs a C
# compiler on PATH -- Go auto-disables cgo without one.
# g++: OpenWiki's better-sqlite3 dependency can fall back to a native build when no prebuilt
# binary is available for the target platform.
# bubblewrap: Omnigent's native Linux terminal wrappers use bwrap for mandatory OS sandboxing.
# apt over HTTPS: over plain HTTP, index files and .debs intermittently arrive with the right
# size but the wrong bytes ("Hash Sum mismatch") on azure.archive, archive and
# security.ubuntu.com alike. azure.archive.ubuntu.com has no HTTPS endpoint, so use the default
# archive/security mirrors. ubuntu:24.04 ships no CA bundle, so borrow the golang stage's for
# this first apt run; installing ca-certificates below replaces it with Ubuntu's own.
COPY --from=golang /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
RUN sed -i 's|http://|https://|g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update && apt-get install -y --no-install-recommends \
    git curl ca-certificates jq make unzip gnupg gcc g++ libc6-dev openssh-server \
    python3 python3-pip pipx tmux bubblewrap \
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
    && apt-get update -o Dir::Etc::sourcelist="sources.list.d/github-cli.list" -o Dir::Etc::sourceparts="-" -o APT::Get::List-Cleanup="0" \
    && apt-get install -y --no-install-recommends gh \
    && rm -rf /var/lib/apt/lists/*

# --- kubectl ---
# The sandbox talks to the host's Docker Desktop Kubernetes API through the
# read-only kubeconfig bind mounted by opensandbox/docker.toml. Keep the
# client version explicit and verify the downloaded binary before installing it.
ARG TARGETARCH=amd64
RUN curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" -o /usr/local/bin/kubectl \
    && curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl.sha256" -o /tmp/kubectl.sha256 \
    && echo "$(cat /tmp/kubectl.sha256)  /usr/local/bin/kubectl" | sha256sum --check --status \
    && chmod 755 /usr/local/bin/kubectl \
    && rm -f /tmp/kubectl.sha256

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
# Pin the published Claude Code, Codex, Codex ACP adapter, GitHub Copilot CLI, OpenSpec, and OpenWiki packages so image rebuilds are reproducible.
# Node is already present for the TypeScript components, so the CLIs add no additional runtime.
RUN npm install --global --no-audit --no-fund \
    "@anthropic-ai/claude-code@${CLAUDE_CODE_NPM_VERSION}" \
    "@openai/codex@${CODEX_NPM_VERSION}" \
    "@github/copilot@${COPILOT_NPM_VERSION}" \
    "@agentclientprotocol/codex-acp@${CODEX_ACP_NPM_VERSION}" \
    "@fission-ai/openspec@${OPENSPEC_NPM_VERSION}" \
    "openwiki@${OPENWIKI_NPM_VERSION}"

# --- Antigravity CLI (agy) ---
# agy is a native binary, not an npm package. Google's installer resolves the current release
# from its manifest and verifies the tarball's SHA512 before installing. It has no version-pin
# option (agy self-updates at runtime), so this layer tracks whatever release is current at
# build time. Install into /root/.local/bin (on PATH) and link into /usr/local/bin like omnigent.
RUN curl -fsSL https://antigravity.google/cli/install.sh -o /tmp/agy-install.sh \
    && bash /tmp/agy-install.sh --dir /root/.local/bin \
    && rm -f /tmp/agy-install.sh \
    && ln -sf /root/.local/bin/agy /usr/local/bin/agy

# --- uv + Omnigent ---
# Omnigent requires Python 3.12+, uv, git, Node 22+, npm, tmux, and bwrap on Linux. Ubuntu
# 24.04 provides Python 3.12; Node/npm/tmux/git/bwrap are installed above. Copy uv from
# Astral's official distroless image and use it to install the pinned Omnigent CLI into
# /root/.local/bin, which is already on PATH for agent tools.
COPY --from=uv /uv /uvx /usr/local/bin/
ENV UV_TOOL_BIN_DIR=/root/.local/bin
RUN uv tool install --python 3.12 "omnigent==${OMNIGENT_VERSION}" \
    && ln -sf /root/.local/bin/omnigent /usr/local/bin/omnigent \
    && ln -sf /root/.local/bin/omni /usr/local/bin/omni

# --- Superpowers plugins ---
# A fresh agent config has no Git marketplaces registered. Register Anthropic's official
# marketplace independently for each CLI, then install the same Superpowers bundle into both
# root-level agent configurations used by sandbox.yaml.
RUN claude plugin marketplace add anthropics/claude-plugins-official \
    && claude plugin install superpowers@claude-plugins-official --scope user \
    && codex plugin marketplace add anthropics/claude-plugins-official --json >/dev/null \
    && codex plugin add superpowers@claude-plugins-official --json >/dev/null \
    && claude plugin list --json | grep -q 'superpowers@claude-plugins-official' \
    && codex plugin list --json | grep -q '"pluginId": "superpowers@claude-plugins-official"'

# --- ClawTeam CLI (github.com/HKUDS/ClawTeam): multi-agent swarm orchestration on top of
#     Claude Code inside this sandbox. pipx over `pip install` since Ubuntu 24.04's Python is
#     PEP 668 externally-managed; pipx gives clawteam its own venv and links the binary onto
#     PATH without --break-system-packages.
ENV PATH="/root/.local/bin:${PATH}"
RUN pipx install clawteam

# --- SSH remote-development access ---
# The host keys are removed at image build time and regenerated by sshd-start for every
# container. Reusing image-baked host keys across independently created sandboxes would
# let one sandbox impersonate another. Root access is key-only because the image's tool
# caches and agent configuration intentionally live under /root.
RUN mkdir -p /run/sshd /root/.ssh /etc/ssh/sshd_config.d \
    && mkdir -p /root/.kube \
    && chmod 700 /root/.ssh \
    && printf '%s\n' \
        'PasswordAuthentication no' \
        'KbdInteractiveAuthentication no' \
        'PermitRootLogin prohibit-password' \
        'PubkeyAuthentication yes' \
        'SetEnv KUBECONFIG=/root/.kube-local/config' \
        'PermitUserEnvironment ANTHROPIC_API_KEY,CLAUDE_CODE_OAUTH_TOKEN,OPENAI_API_KEY,COPILOT_GITHUB_TOKEN,GEMINI_API_KEY' \
        'X11Forwarding no' \
        'PrintMotd no' \
        > /etc/ssh/sshd_config.d/99-agent-sandbox.conf \
    && rm -f /etc/ssh/ssh_host_*
COPY sshd-start.sh /usr/local/bin/sshd-start
RUN chmod 755 /usr/local/bin/sshd-start

# --- Agent CLI credentials ---
# The launchers stream host tokens into agent-auth-setup after the sandbox starts; it writes
# them to /root/.ssh/environment, which sshd loads into every session for the names allowed
# by PermitUserEnvironment above, and performs the per-CLI login steps.
COPY agent-auth-setup.sh /usr/local/bin/agent-auth-setup
RUN chmod 755 /usr/local/bin/agent-auth-setup

# --- smoke test: fail the build if any toolchain is missing or the wrong version ---
RUN set -eux; \
    java -version 2>&1 | grep -q '"25'; \
    go version | grep -q 'go1.26'; \
    node -v | grep -q '^v24\.'; \
    pnpm -v | grep -q '^11\.10\.0$'; \
    jq --version | grep -q '^jq-1'; \
    kubectl version --client=true --output=yaml >/dev/null; \
    gh --version | grep -q '^gh version'; \
    git --version; \
    make --version | head -1; \
    tmux -V; \
    bwrap --version | grep -q '^bubblewrap '; \
    uv --version | grep -q "^uv ${UV_VERSION}\\( \\|$\\)"; \
    claude --version | grep -q "^${CLAUDE_CODE_NPM_VERSION} (Claude Code)$"; \
    codex --version | grep -q "^codex-cli ${CODEX_NPM_VERSION}$"; \
    copilot --version | grep -q "${COPILOT_NPM_VERSION}"; \
    agy --version; \
    codex-acp --version | grep -q "^@agentclientprotocol/codex-acp ${CODEX_ACP_NPM_VERSION}$"; \
    openspec --version | grep -q "^${OPENSPEC_NPM_VERSION}$"; \
    npm list --global --depth=0 openwiki | grep -q "openwiki@${OPENWIKI_NPM_VERSION}"; \
    openwiki --help >/dev/null; \
    omnigent --version | grep -q "${OMNIGENT_VERSION}"; \
    omni --version | grep -q "${OMNIGENT_VERSION}"; \
    clawteam --help >/dev/null; \
    ssh-keygen -A; \
    /usr/sbin/sshd -t; \
    rm -f /etc/ssh/ssh_host_*

EXPOSE 22

WORKDIR /workspace
CMD ["/usr/local/bin/sshd-start"]
