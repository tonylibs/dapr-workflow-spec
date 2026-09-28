# OpenShell-compatible variant of the dws agent-sandbox image.
#
# OpenShell's supervisor requires a non-root `sandbox` user/group and iproute2 + nftables
# (per-sandbox network namespace), and runs every exec as `sandbox` with HOME=/home/sandbox.
# The base image installs its toolchains and agent config under /root (mode 700 on Ubuntu),
# so this layer also makes them reachable for that user.
#
# Build from the repository root:
#   docker build -f agent-sandbox/openshell/Dockerfile --platform linux/amd64 \
#     -t ghcr.io/tonylibs/dws-agent-sandbox:openshell agent-sandbox/openshell
ARG BASE_IMAGE=ghcr.io/tonylibs/dws-agent-sandbox:latest
FROM ${BASE_IMAGE}

USER root

RUN apt-get update && apt-get install -y --no-install-recommends iproute2 nftables \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system sandbox \
    && useradd --system --gid sandbox --create-home --home-dir /home/sandbox --shell /bin/bash sandbox

# Read/execute access to /root for the uv-managed Omnigent venv and its Python, the
# /root/.local/bin CLIs that /usr/local/bin symlinks point into, and the pnpm shims on PATH.
RUN chmod 755 /root && chmod -R a+rX /root

# Agent config (Claude Code / Codex plugins such as Superpowers) is per-HOME; copy it
# so the `sandbox` user sees the same setup the root-based providers get.
RUN for p in .claude .claude.json .codex .config; do \
        if [ -e "/root/$p" ]; then cp -a "/root/$p" /home/sandbox/; fi; \
    done \
    && chown -R sandbox:sandbox /home/sandbox

# The base image points GOPATH/GOMODCACHE at /root/go, which `sandbox` cannot write.
ENV GOPATH=/home/sandbox/go
ENV GOMODCACHE=/home/sandbox/go/pkg/mod
ENV PATH="/home/sandbox/go/bin:${PATH}"

COPY policy.yaml /etc/openshell/policy.yaml

RUN set -eux; \
    id sandbox; \
    command -v ip; \
    command -v nft; \
    su sandbox -s /bin/sh -c 'omnigent --version && java -version && go version && node -v && claude --version'
