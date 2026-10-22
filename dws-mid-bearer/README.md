# dws-mid-bearer

A Dapr **HTTP Wasm middleware** that adds `Authorization: Bearer <token>` to a step's calls to one
`HTTPEndpoint`, plus the tiny image that delivers it. Step images never see the token: they call the
sidecar's invocation URL and the sidecar adds the header. This is the `bearer` half of
[ADR 0010](../docs/adr/0010-use-components-as-a-shared-store.md) Decision 4a (the `basic` half is
`dws-mid-basic`); roadmap item 4.1c-bearer. Nothing deploys it yet: the controller wiring is 4.1d.

## Behavior

- **Config** (Dapr `guestConfig`, one JSON object, exactly these two string fields):

  ```json
  {"endpoint": "pets-api", "token": "<bearer token>"}
  ```

  There is no `scheme` field. Which component Dapr loads *is* the scheme.
- **Match.** A request whose URI is `/v1.0/invoke/<endpoint>/method` followed by `/`, `?` or the end gets
  `Authorization: Bearer <token>`, replacing any caller-supplied value so exactly one is forwarded. Every
  other request (another endpoint, `pets-api-2`, `/v1.0/state/...`) passes through untouched. Every path
  under the endpoint is covered, because one `HTTPEndpoint` carries one policy. The `dapr-app-id` header
  form of invocation is not matched.
- **Fails closed.** A missing, empty or whitespace-only token, a token with a control character (a Secret
  made with `echo` instead of `echo -n` ends in `\n`), a bad `endpoint`, malformed JSON, or any unknown
  field (`scheme`, `username`, `password`, `path` ...) aborts the module at load. daprd then refuses to
  load the Component and exits, so a call is never sent without credentials.
- **Silent.** The guest never logs and its failure messages are fixed strings that name a field, never
  the token or the config.

> **`ignoreErrors: true` defeats the fail-closed behavior.** Checked against daprd 1.18.1: with that flag
> daprd logs "Ignoring error processing component", skips the middleware and forwards the call with no
> `Authorization`. Never set it on a Component that loads this module.

## The 4.1d contract

| What | Value |
|---|---|
| Image | `ghcr.io/tonylibs/dws-mid-bearer`, pin by digest. Public once the package visibility is set (see below) |
| File in the image | `/bearer.wasm` (mode 0644, image runs as `65532:65532`, busybox base) |
| Init container | `command: [cp, /bearer.wasm, /mnt/dws-wasm/bearer.wasm]` into an `emptyDir` |
| Sidecar | `dapr.io/volume-mounts: "<volume>:/mnt/dws-wasm"` (read-only), classic sidecar |
| Component | `type: middleware.http.wasm`, `url: file:///mnt/dws-wasm/bearer.wasm`, `guestConfig` by `secretKeyRef`, `auth.secretStore: kubernetes`, **no `ignoreErrors`** |
| Secret key | `guestConfig` = the JSON above, written without a trailing newline |

## Why TinyGo 0.34.0

The guest SDK (`http-wasm-guest-tinygo` v0.4.0) builds modules that fail at request time with
`module closed with exit_code(0)` under TinyGo 0.35 and later. The Dockerfile pins `tinygo/tinygo:0.34.0`.
`go.mod` says `go 1.23` for the same reason (the pinned TinyGo cannot compile newer language versions), unlike
the other Go packages. Revisit when the SDK supports a newer TinyGo, and re-run the probe when you do. The
host `go` toolchain only runs vet, gofmt and the unit tests; it never produces the shipped file.

## Develop

```sh
cd dws-mid-bearer
make lint && make test      # the package gate (vet, gofmt, go test -race)
make docker                 # builds the image; TinyGo 0.34.0 runs inside it
docker run --rm --entrypoint head dws-mid-bearer:dev -c 4 /bearer.wasm | od -An -c   # \0 a s m
```

The handler logic lives in `internal/bearer` and is tested with fake `http-wasm` requests under plain
`go test`; `main.go` only wires it to the host. The plain-Go tests do not exercise the Wasm build itself
(TinyGo's `encoding/json`, `panic` at init), so run the probe when changing either.

## Probe

`probe/verify.sh` runs the image on a Dapr cluster (the current `kubectl` context, Dapr 1.18). It copies
`/bearer.wasm` with an init container exactly as 4.1d will, then asserts the header at the target, replacement of
a caller-supplied header, a second endpoint from the same pod getting nothing, foreign headers passing through,
no token in daprd logs, and that a pod whose Component has an empty token never gets a call through.

```sh
dws-mid-bearer/probe/verify.sh                                       # builds and loads the image locally
IMAGE=ghcr.io/tonylibs/dws-mid-bearer@sha256:<digest> dws-mid-bearer/probe/verify.sh   # a pushed image
```

It is derived from `spikes/wasm-auth-middleware/verify.sh`, with one deliberate difference: there is no
`path` filter, so a second path on the *same* endpoint is authenticated, as intended. Promoting it to
`scripts/` is roadmap 4.1h.

## Release

Versioned by release-please (`version.txt`, `CHANGELOG.md`, `skip-github-release`) and released by hand. See
[`docs/release-process.md`](../docs/release-process.md): merge the component's release PR, then run
`component-release` for `dws-mid-bearer`. The first push creates a **private** GHCR package; make it public once
in the package settings so an init container can pull it without a secret.
