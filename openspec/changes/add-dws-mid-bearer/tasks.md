# Tasks

All commands run from `dws-mid-bearer/` unless stated. Package gate: `make lint && make test`.

## 1. Scaffold and config validation (`dws-mid-bearer`)

- [x] 1.1 Create `go.mod` (`module github.com/dws/dws-mid-bearer` like the other Go packages, but `go 1.23` (TinyGo 0.34.0's ceiling) rather than their 1.26.9, `http-wasm-guest-tinygo v0.4.0`), `go.sum`, `Makefile` (`build`, `test`, `vet`, `fmt-check`, `lint`, `docker`, `clean`, same shape as `dws-call-grpc`), `version.txt` (`0.0.0`, see 4.2), `.gitignore`; verify `go mod verify && make lint` pass on the empty package.
- [x] 1.2 Write failing tests in `internal/bearer/config_test.go` for `Parse`: valid config; missing/empty/whitespace-only token; token with `\n`; missing/empty endpoint; endpoint with `/ ? #`; unknown field (`scheme`, `username`); empty input; non-object JSON; and for every failure that the error text names the field and never contains the supplied token. Verify they fail for the right reason (`go test ./internal/bearer`).
- [x] 1.3 Implement `Config` and `Parse` with static per-field error messages (no `json` error text, no config bytes) until 1.2 passes under `go test -race ./internal/bearer`.

## 2. Request handling (`dws-mid-bearer`)

- [x] 2.1 Write failing tests in `internal/bearer/handler_test.go` using a fake `api.Request` (embed the interface, override `GetURI` and `Headers`): header added with and without a query string; bare `/method` and `/method/`; caller-supplied `Authorization` replaced so exactly one value remains; other endpoint; `pets-api-2` / `pets-apix` look-alikes; `/v1.0/state/x`, `/v1.0/invoke/pets-api/methods/x`, `/`; foreign `Authorization` on another endpoint left intact; handler always returns `next=true`.
- [x] 2.2 Implement `Matches` (prefix plus `/`, `?` or end boundary) and `Handle` (`Headers().Set("Authorization", "Bearer "+token)`) until 2.1 passes; verify `go test -race ./...` green and that mutating the boundary check or switching `Set` to `Add` makes a test fail.

## 3. Guest entrypoint and image (`dws-mid-bearer`)

- [x] 3.1 Add `main.go`: read `handler.Host.GetConfig()`, `Parse`, `panic` with the static message on error, set `handler.HandleRequestFn`; no logging anywhere. Verify `go vet ./...` and `go build ./...` pass on the host toolchain.
- [x] 3.2 Add the `Dockerfile` (`tinygo/tinygo:0.34.0` build stage with the spike's flags, `busybox:1.37.0` runtime, `/bearer.wasm` mode 0644, `USER 65532:65532`, no entrypoint) and `.dockerignore`. Verify `make docker` succeeds, `docker run --rm --entrypoint head <image> -c 4 /bearer.wasm | od -An -c` shows `\0 a s m`, and record the `.wasm` size (the spike's combined guest was 218,133 bytes).
- [x] 3.3 Against the built module (not only plain-Go tests), check that an unknown field and an empty token abort at load. If TinyGo's `encoding/json` ignores `DisallowUnknownFields`, switch `Parse` to the `map[string]json.RawMessage` check from design D3 and keep 1.2 green. Verify by instantiating the module once under a small throwaway wazero harness kept out of the repo, or note why it was not possible.

## 4. CI and release wiring

- [x] 4.1 Add `.github/workflows/dws-mid-bearer.yml` modelled on `dws-call-grpc.yml` (triggers on `dws-mid-bearer/**`, `dws-mid-bearer-v*` tags and the workflow file; `test` job: `make lint && make test`; `build-image` job: build with `load: true`, smoke-check magic bytes and size, then push `:latest`, `:<sha>`, `:<version>` off PRs to `ghcr.io/${{ github.repository_owner }}/dws-mid-bearer`). Verify with `actionlint` if available and by the workflow's first run on the PR.
- [x] 4.2 Add the `dws-mid-bearer` package to `release-please-config.json` (`simple`, `version.txt`, `CHANGELOG.md`, `skip-github-release: true`), `"dws-mid-bearer": "0.0.0"` to `.release-please-manifest.json`, and `dws-mid-bearer` to the `component-release.yml` choices; start the manifest at `0.0.0` so the first `feat` yields an ordinary `0.1.0` release PR (no seed-and-force; document this in `docs/release-process.md` and add the component to its table). Verify `python3 -m json.tool` on both JSON files and a diff review of the workflow.

## 5. Docs

- [x] 5.1 Write `README.md` (what it is, guest config, matching rule, fail-closed behavior, the 4.1d contract from design D4, TinyGo pin and why, how to build and probe, release steps) and `CLAUDE.md` (stack idioms only, linking the root file). Verify every command in the README runs as written.
- [x] 5.2 Add the package to the root `CLAUDE.md` package map and gate table; update ADR 0010 (name `dws-mid-bearer` replaces the proposed `dws-wasm-bearer`, measured size, empty-token decision) and the roadmap §4h rows (4.1c-bearer status, name). Verify with `grep -rn "dws-wasm-bearer" docs/ CLAUDE.md` that only the intentionally historical mentions remain.

## 6. Live verification and report

- [x] 6.1 Add `probe/verify.sh` and `probe/resources.yaml`: bearer-only, derived from the spike's probe, image from `IMAGE` (or built locally), init-container copy of `/bearer.wasm`, assertions for header at the target, caller-supplied overwrite, second-endpoint isolation, non-invocation passthrough, no token in daprd logs, plus an empty-token Deployment that must not forward a call. Verify `bash -n probe/verify.sh` and `shellcheck` if available.
- [ ] 6.2 Run the probe against a Dapr cluster. In this session's container that is only possible if a cluster is reachable; otherwise leave it for Tony's local cluster and say so. Verify by the probe's `PASS` output and record what daprd did with the empty-token component (design D3). **Not done in the authoring container:** `kind` cannot start there. Substituted a real daprd 1.18.1 in self-hosted mode against the built module (see design D3); the Kubernetes run is left for Tony's cluster.
- [x] 6.3 Final integration check and report: `make lint && make test`, `helm`/other packages untouched (`git diff --stat`), CI run green on the PR, then report the image name, digest (or "not yet pushed"), the manual follow-ups (seed first release, make GHCR package public) and every deviation: name change, no `path` field and the changed probe assertion, `Set` rather than `Add`, any probe not run.

## Workflow follow-up

- Archive the change after the PR merges and the probe has passed on a real cluster.
- Seed the first release (`component-release`, `force=true`) and make the GHCR package public, by hand.
