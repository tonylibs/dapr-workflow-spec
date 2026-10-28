# dws-mid-basic

Wasm HTTP middleware that adds an HTTP Basic credential to a step's calls to one endpoint. It is
the `basic` half of ADR 0010 Decision 4a (OWS roadmap 4.1c-basic): the credential lives in a
Kubernetes Secret read by the step pod's Dapr sidecar, never in the step image. Its sibling
`dws-mid-bearer` does the same for Bearer tokens.

The package builds one artifact, `basic.wasm`, a guest for daprd's
[`middleware.http.wasm`](https://docs.dapr.io/reference/components-reference/supported-middleware/middleware-wasm/)
Component. It is shipped as the image `ghcr.io/tonylibs/dws-mid-basic`, which an init container
unpacks into the step pod.

## What it does

For a request on its endpoint's invocation path, the guest sets

```
Authorization: Basic base64(username ":" password)
```

replacing any `Authorization` value the caller sent, so the forwarded request carries exactly one.
Every other request passes through untouched: no header, URI or body is added, removed or changed,
and the guest returns "continue" for all of them. It logs nothing.

A request matches when its path, ignoring any query string, is `/v1.0/invoke/<endpoint>/method` or
starts with `/v1.0/invoke/<endpoint>/method/`. `<endpoint>` is compared exactly, so `pets` never
matches `pets-v2`. A path with a `.` or `..` segment, raw or percent-encoded (`%2e`, any case), never
matches: if daprd cleaned such a path after the guest ran, the request could be routed to another
endpoint carrying this credential, so the guest lets it go out unauthenticated instead.

## Guest config

The guest config is one JSON object with exactly these string fields. There is no `scheme` field:
loading this component is choosing `basic`.

| Field | Rule |
|---|---|
| `endpoint` | Required, non-empty, none of `/`, `?`, `#`. The name of the Dapr `HTTPEndpoint` the credential is for. |
| `username` | Required, non-empty, no `:` (RFC 7617: a user-id with a colon is invalid), no control characters. |
| `password` | Required, non-empty, no control characters. `:` is allowed and kept. |

Unknown fields (`scheme`, `token`, a typo), duplicate fields, `null`, non-string values, trailing
data and non-object JSON are all rejected. Field names are case-sensitive.

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: basic-pets
spec:
  type: middleware.http.wasm
  version: v1
  metadata:
    - name: url
      value: file:///mnt/dws-wasm/basic.wasm
    - name: guestConfig
      secretKeyRef:
        name: basic-pets          # Secret holding {"endpoint":"pets","username":"...","password":"..."}
        key: guestConfig
auth:
  secretStore: kubernetes
```

The controller composes this Component and Secret (OWS 4.1d); this package only defines the
contract.

## Failure behavior

Bad credentials fail loudly at load, not per request. `main` validates the config before it
registers the request handler and, on any problem, panics with a message that names each field and
the rule it breaks, for example:

```
dws-mid-basic: token is not a known field; username is required; password is required
```

All problems are reported together. The message never contains a configured value or any part of
the input, and for a JSON syntax error it says only `config is not valid JSON`, because
`encoding/json` errors quote the offending character. An unknown field is named only if its name
looks like an identifier (at most 32 letters, digits and underscores).

Because the handler is never registered, a host that keeps the module loaded after the panic still
fails every request instead of forwarding it without the credential: the call fails closed either
way. `wasmtest` asserts that no request reaches the echo server in this case.

**Observed daprd behavior when the guest refuses to start** (daprd 1.18.1, measured 2026-10-28 by
`scripts/verify-live.sh` with a config that has no `password`):

- daprd fails to init the Component, logs `Error processing component, daprd will exit gracefully`,
  then exits with `level=fatal msg="Fatal error from runtime: ..."`. The pod stays `Running` with
  the `daprd` container not ready and in `CrashLoopBackOff`; it never serves a request.
- A call through that sidecar fails, and the target receives nothing.
- **The field/rule message is not in daprd's log.** daprd reports only the trap:

  ```
  error creating HTTP middleware middleware.http.wasm/v1: wasm: error instantiating guest:
  module[1] function[_start] failed: wasm error: unreachable
  wasm stack trace:
      main.runtime._panic(i32,i32)
      main._start()
  ```

  The panic text goes to the guest's stderr, which daprd does not forward. So an operator sees
  *which* Component is broken (its name is in every line) but not *why*: check the Secret behind
  its `guestConfig` against the table above. The full message is visible when the same config is
  loaded by `wasmtest`, which captures the guest's stderr.

## Encoding

RFC 7617 leaves the charset unspecified unless the server announces `charset="UTF-8"`. The guest
never sees the challenge, so it always uses that defined form:

1. `username ":" password`, each part normalized to Unicode NFC (`golang.org/x/text/unicode/norm`);
2. encoded as UTF-8 (Go strings from `encoding/json` already are);
3. standard padded base64 (`base64.StdEncoding`).

A `:` in the password is kept verbatim. Test vectors: `test` / `123£` gives `dGVzdDoxMjPCow==`
(RFC 7617 section 2.1), `alice` / `s3cret` gives `YWxpY2U6czNjcmV0`, and a username of `e` plus
U+0301 encodes as the single code point U+00E9. The full PRECIS profiles (width mapping, disallowed
code points) are not applied.

## Image

`ghcr.io/tonylibs/dws-mid-basic` is a `busybox` image holding only `/basic.wasm` (about 283 KB; the
budget is 1 MiB) and no credential. The image is public and pinned by digest in the controller's
output. An init container copies the file into an `emptyDir` that the sidecar mounts:

```yaml
initContainers:
  - name: install-wasm
    image: ghcr.io/tonylibs/dws-mid-basic@sha256:<digest>
    command: [sh, -c, "cp /basic.wasm /mnt/dws-wasm/basic.wasm"]
    volumeMounts:
      - name: dws-wasm
        mountPath: /mnt/dws-wasm
```

with the pod annotation `dapr.io/volume-mounts: dws-wasm:/mnt/dws-wasm` so daprd reads
`file:///mnt/dws-wasm/basic.wasm`. The image is built by this package's `Dockerfile`: a
`tinygo/tinygo:0.34.0` stage named `build` that writes `/basic.wasm`, then `busybox:1.37.0` with that
one file. CI builds it and pushes on `main` and on `dws-mid-basic-v*` tags.

## Layout

```
main.go                 TinyGo entry: read config, register the handler
internal/basicauth/     Parse, Match, HeaderValue; no SDK import, plain `go test`
wasmtest/               own go.mod: runs bin/basic.wasm in http-wasm-host-go, in front of an echo server
scripts/verify-live.sh  basic-only live probe on a cluster
Makefile  Dockerfile  version.txt
```

## Build and test

Run these from `dws-mid-basic/`. Use Go 1.23: `GOTOOLCHAIN=go1.23.12` selects it when the `go` on
your path is newer (CI uses Go 1.23).

```sh
make lint && make test          # go vet + gofmt, then the guest module's unit tests; no TinyGo needed
make wasm                       # needs Docker: builds the Dockerfile's "build" stage, extracts bin/basic.wasm
make test-wasm                  # plain go, no Docker: runs wasmtest against bin/basic.wasm
```

Without Docker, build with a local TinyGo 0.34.0. `make wasm` refuses any other version, because
a module built by a newer TinyGo loads but fails every request with `module closed with
exit_code(0)`. TinyGo 0.34.0 also needs Go 1.23 or older on the `PATH` (it stops with `requires go
version 1.19 through 1.23` otherwise):

```sh
curl -sSLO https://github.com/tinygo-org/tinygo/releases/download/v0.34.0/tinygo0.34.0.linux-amd64.tar.gz
tar xzf tinygo0.34.0.linux-amd64.tar.gz      # other platforms: see the v0.34.0 release page
GOTOOLCHAIN=go1.23.12 TINYGO="$PWD/tinygo/bin/tinygo" make wasm
make test-wasm
```

Either way the command that makes the module is:

```sh
tinygo build -o bin/basic.wasm -scheduler=none --no-debug -target=wasi .
```

`bin/` is git-ignored. `make test-wasm` fails with a clear message if `bin/basic.wasm` is missing.
The `wasmtest` module pins `http-wasm-host-go` v0.7.0 and `wazero` v1.9.0; wazero v1.8.0, the
version host-go asks for, traps this module on Go 1.24 or newer.

## Live probe

`scripts/verify-live.sh` deploys the image the way the controller will and checks what an echo
target receives: `alice`/`s3cret`, a `:` in the password, a non-ASCII credential given in
decomposed form (the target must see NFC), no credential for another endpoint or an endpoint-name
prefix, a caller `Authorization` replaced, no credential in daprd logs with daprd at `debug` level (the scan fails if no debug line appears), and, in a second pod whose
config has no password, that nothing reaches the target. It pins daprd 1.18.1 and deletes its
namespace on exit.

```sh
# from the repository root
dws-mid-basic/scripts/verify-live.sh                                              # build locally (needs Docker; Docker Desktop cluster)
IMAGE=ghcr.io/tonylibs/dws-mid-basic@sha256:<digest> dws-mid-basic/scripts/verify-live.sh   # a published image
```

Prerequisites: `kubectl` pointed at a cluster with the Dapr control plane (CRDs and sidecar
injector). It uses the namespace `dws-mid-basic-probe` and pulls `python:3.13-alpine`,
`curlimages/curl` and the daprd image. It prints `PASS` and the image digest, daprd image and
`basic.wasm` size. Running it is a manual step; it needs a cluster.

From Git Bash on Windows, keep MSYS from rewriting the in-node paths that `docker exec` receives
(`/root/...`) while still converting the host paths `docker build`/`docker cp` need:

```sh
MSYS2_ARG_CONV_EXCL="/root;desktop-" bash dws-mid-basic/scripts/verify-live.sh
```

Last run (2026-10-28, Docker Desktop cluster, image built locally before the first publish, daprd at `debug`; 142 log lines scanned):

```
PASS: dws-mid-basic reached the echo target with the right Basic credential
      (plain, ':' in password, non-ASCII/NFC); other endpoints untouched; caller header replaced;
      no credential in daprd logs; a guest without a password sent nothing to the target
Guest image:  docker.io/library/dws-mid-basic@sha256:abc88cc9a8db580381a572b78e36076e1cb4b08dc9a5f3bc97daa8fd16a6dc0a (built locally: true)
Dapr sidecar: ghcr.io/dapr/daprd:1.18.1
basic.wasm:   283330 bytes
```

The negative case's daprd behavior is under [Failure behavior](#failure-behavior). Rerun with
`IMAGE=ghcr.io/tonylibs/dws-mid-basic@<digest>` once CI has published the image.

## Deviations from the proposal and ADR

- **Name**: `dws-mid-basic` (image, package, release component, tag prefix `dws-mid-basic-v*`), not
  the ADR's proposed `dws-wasm-basic`. The binary keeps the name `basic.wasm`.
- **Set, not append**: the header is set, replacing a caller value; the task said "append". With a
  caller header a true append sends two `Authorization` headers and the target may honor the
  caller's.
- **Empty password rejected**: RFC 7617's grammar allows it, but the config is composed from
  operator Secrets, where an empty value is far more likely a missing key. An intended empty
  password would need an explicit field.
- **NFC only**: not the full PRECIS profiles (`UsernameCasePreserved`, `OpaqueString`).
- **Derived probe**: `scripts/verify-live.sh` is derived from the spike's `verify.sh`, which stays
  frozen as the spike's record.
- **Go 1.23**: this module is Go 1.23, not the repository's usual 1.26, because TinyGo 0.34.0 ships
  with Go 1.23 and is the only TinyGo the guest SDK v0.4.0 works with.
- **Endpoint-wide, not path-scoped**: the spike's single `path` field is gone; the credential is
  scoped to the endpoint, as the ADR says.
