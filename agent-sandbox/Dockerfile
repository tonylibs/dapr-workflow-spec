# Shared dev/agent-sandbox image for the dws monorepo.
# TODO: pin all versions explicitly once confirmed against each component's CLAUDE.md.
FROM ubuntu:24.04

RUN apt-get update && apt-get install -y --no-install-recommends \
    git curl ca-certificates jq make unzip \
    && rm -rf /var/lib/apt/lists/*

# --- gh CLI ---
# TODO: install gh (see https://github.com/cli/cli/blob/trunk/docs/install_linux.md)

# --- JDK 25 (dws-controller, dws-orchestrator use ./mvnw, no separate Maven install needed) ---
# TODO: install JDK 25

# --- Go 1.26 (dws-call-http, dws-run) ---
# TODO: install Go 1.26, set GOPATH/GOCACHE to /cache/go (mounted PVC, see cache-pvcs.yaml)

# --- Node 24 + pnpm (dws-call-openapi) ---
# TODO: install Node 24, `corepack enable && corepack prepare pnpm@latest --activate`
# TODO: point pnpm store to /cache/pnpm-store (mounted PVC)

# --- optional: kubectl + dapr CLI, only if agent needs to validate against a live cluster ---
# TODO: add if needed

WORKDIR /workspace
