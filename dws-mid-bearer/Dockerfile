# syntax=docker/dockerfile:1

# --- build stage ---
# TinyGo is pinned to 0.34.0: 0.35 and later build a module that fails at request
# time with "module closed with exit_code(0)" under http-wasm-guest-tinygo v0.4.0
# (spikes/wasm-auth-middleware/FINDINGS.md). Do not bump without re-running the probe.
FROM tinygo/tinygo:0.34.0 AS build
# The tinygo image's default user cannot write to /src.
USER root
WORKDIR /src

# Cache module downloads separately from source.
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN tinygo build -o /bearer.wasm -scheduler=none --no-debug -target=wasi .

# --- delivery stage ---
# Carries one file. An init container copies it into a shared volume that the
# Dapr sidecar mounts read-only; busybox is only here for `cp`.
FROM busybox:1.37.0
COPY --from=build --chmod=0644 /bearer.wasm /bearer.wasm
USER 65532:65532
