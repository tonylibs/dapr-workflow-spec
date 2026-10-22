# syntax=docker/dockerfile:1

# --- build stage ---
# Keep the stage name "build": `make wasm` builds `--target build` and copies /basic.wasm out.
# TinyGo 0.34.0 is the only version http-wasm-guest-tinygo v0.4.0 works with, and its bundled
# Go is 1.23, which is why this module is `go 1.23` (see CLAUDE.md). Do not bump either alone.
FROM tinygo/tinygo:0.34.0 AS build
# The image defaults to a non-root user that cannot write to the module and build caches.
USER root
WORKDIR /src

# Cache module downloads separately from source. go.sum is committed; no `go mod tidy` here.
COPY go.mod go.sum ./
RUN go mod download

COPY main.go ./
COPY internal/ ./internal/

# The one build command; keep it identical to TINYGO_FLAGS in the Makefile.
RUN tinygo build -o /basic.wasm -scheduler=none --no-debug -target=wasi .

# --- runtime stage ---
# Not a long-running image: an init container runs `cp /basic.wasm /mnt/dws-wasm/basic.wasm`
# into a volume that daprd mounts. busybox provides `sh` and `cp` and nothing else is added.
FROM busybox:1.37.0
COPY --from=build /basic.wasm /basic.wasm
