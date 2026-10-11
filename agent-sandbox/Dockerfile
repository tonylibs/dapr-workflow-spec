# Shared dev/agent-sandbox image for the dws monorepo: JDK 25, Go 1.26, Node 24 + pnpm,
# git/gh/jq, uv, Omnigent, Claude Code, Codex, GitHub Copilot CLI, Antigravity CLI (agy, pinned by tarball SHA512),
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
ARG CODEX_NPM_VERSION=0.162.1
ARG CODEX_ACP_NPM_VERSION=2.2.2
ARG CLAUDE_CODE_NPM_VERSION=2.1.296
ARG COPILOT_NPM_VERSION=1.0.95
ARG OPENSPEC_NPM_VERSION=1.6.0
ARG OPENWIKI_NPM_VERSION=0.2.3
ARG OMNIGENT_VERSION=0.17.0
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
# agy is a native binary, not an npm package. Google's install.sh has no version option (it
# always resolves the current release from its manifest), so pin the exact release instead:
# download the versioned tarball that the manifest for AGY_VERSION points at, verify its SHA512,
# and extract the `antigravity` binary as agy -- the same steps install.sh performs. The
# `agy install` handoff is skipped: it only edits shell-profile PATH/aliases, and agy is already
# on PATH via /usr/local/bin. Per-arch URLs/hashes come from
# https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_{amd64,arm64}.json
# -- refresh all four values together when bumping AGY_VERSION. Note agy can still self-update at
# runtime; the pin only fixes what the image ships.
ARG AGY_VERSION=1.3.3
ARG AGY_BUILD=5524738307653632
ARG AGY_SHA512_AMD64=abe3ddf5cf30d3adaa586acf505221aa19702f5ca71af3c8b1f9b1a5976bd8e5c151c41ea33b39c41675d25843462a3e8c382c054f580e9d7e1688919d3b5110
ARG AGY_SHA512_ARM64=1bfd3503576219a102176c13579665a2f39e84ac86a904b117d3b33f3dd751f194a862496bad6ffe914ad46d2c877ba12b6c07e6db07128b706d238262fe430c
ARG TARGETARCH=amd64
RUN set -eux; \
    case "${TARGETARCH}" in \
      amd64) agy_path=linux-x64/cli_linux_x64.tar.gz; agy_sha512="${AGY_SHA512_AMD64}" ;; \
      arm64) agy_path=linux-arm/cli_linux_arm64.tar.gz; agy_sha512="${AGY_SHA512_ARM64}" ;; \
      *) echo "unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://storage.googleapis.com/antigravity-public/antigravity-cli/${AGY_VERSION}-${AGY_BUILD}/${agy_path}" -o /tmp/agy.tar.gz; \
    echo "${agy_sha512}  /tmp/agy.tar.gz" | sha512sum -c -; \
    mkdir -p /root/.local/bin /tmp/agy; \
    tar -xzf /tmp/agy.tar.gz -C /tmp/agy antigravity; \
    install -m 0755 /tmp/agy/antigravity /root/.local/bin/agy; \
    rm -rf /tmp/agy /tmp/agy.tar.gz; \
    ln -sf /root/.local/bin/agy /usr/local/bin/agy

# --- uv + Omnigent ---
# Omnigent requires Python 3.12+, uv, git, Node 22+, npm, tmux, and bwrap on Linux. Ubuntu
# 24.04 provides Python 3.12; Node/npm/tmux/git/bwrap are installed above. Copy uv from
# Astral's official distroless image and use it to install the pinned Omnigent CLI into
# /root/.local/bin, which is already on PATH for agent tools. Include the Copilot and
# Antigravity SDK extras required by the repository's Omnigent agent configurations.
COPY --from=uv /uv /uvx /usr/local/bin/
ENV UV_TOOL_BIN_DIR=/root/.local/bin
RUN uv tool install --python 3.12 "omnigent[copilot,antigravity]==${OMNIGENT_VERSION}" \
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
        'SetEnv KUBECONFIG=/root/.kube-local/config JAVA_HOME=/opt/java/openjdk GOPATH=/root/go GOMODCACHE=/root/go/pkg/mod UV_TOOL_BIN_DIR=/root/.local/bin PATH=/root/.local/bin:/root/.local/share/pnpm/bin:/usr/local/go/bin:/root/go/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin' \
        'PermitUserEnvironment ANTHROPIC_API_KEY,CLAUDE_CODE_OAUTH_TOKEN,OPENAI_API_KEY,COPILOT_GITHUB_TOKEN,GEMINI_API_KEY,GH_TOKEN' \
        'X11Forwarding no' \
        'PrintMotd no' \
        > /etc/ssh/sshd_config.d/99-agent-sandbox.conf \
    && rm -f /etc/ssh/ssh_host_*
COPY sshd-start.sh /usr/local/bin/sshd-start
RUN chmod 755 /usr/local/bin/sshd-start

# --- Agent CLI credentials ---
# The launchers stream host tokens into agent-auth-setup after the sandbox starts.
# Bash restores the image toolchain environment and loads the private credentials file for
# login and interactive Orca terminals. sshd's SetEnv covers direct SSH commands.
COPY agent-auth-setup.sh /usr/local/bin/agent-auth-setup
COPY omnigent-copilot-bootstrap.sh /usr/local/bin/omnigent-copilot-bootstrap
COPY agent-env.sh /etc/profile.d/agent-env.sh
RUN chmod 755 /usr/local/bin/agent-auth-setup \
    && chmod 755 /usr/local/bin/omnigent-copilot-bootstrap \
    && chmod 644 /etc/profile.d/agent-env.sh \
    && printf '\n%s\n' '. /etc/profile.d/agent-env.sh' >> /etc/bash.bashrc

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
    agy --version | grep -q "^${AGY_VERSION}$"; \
    codex-acp --version | grep -q "^@agentclientprotocol/codex-acp ${CODEX_ACP_NPM_VERSION}$"; \
    openspec --version | grep -q "^${OPENSPEC_NPM_VERSION}$"; \
    npm list --global --depth=0 openwiki | grep -q "openwiki@${OPENWIKI_NPM_VERSION}"; \
    openwiki --help >/dev/null; \
    omnigent --version | grep -q "${OMNIGENT_VERSION}"; \
    omni --version | grep -q "${OMNIGENT_VERSION}"; \
    "$(uv tool dir)/omnigent/bin/python" -c 'from importlib.metadata import version; print(version("github-copilot-sdk")); print(version("google-antigravity"))'; \
    clawteam --help >/dev/null; \
    ssh-keygen -A; \
    /usr/sbin/sshd -t; \
    /usr/sbin/sshd -T | grep -q 'JAVA_HOME=/opt/java/openjdk'; \
    env -i HOME=/root /bin/bash -lc 'test "$JAVA_HOME" = /opt/java/openjdk && command -v java && command -v go && command -v node && command -v pnpm' >/dev/null; \
    rm -f /etc/ssh/ssh_host_*

EXPOSE 22

WORKDIR /workspace
CMD ["/usr/local/bin/sshd-start"]
