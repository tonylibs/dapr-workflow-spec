# Tasks

## 1. Package scaffold

- [x] 1.1 Create `dws-mid-basic/` with `go.mod` (`go 1.23`, `github.com/http-wasm/http-wasm-guest-tinygo` v0.4.0, a `golang.org/x/text` version that supports Go 1.23), committed `go.sum`, `version.txt` (`0.0.0`) and `.gitignore` (`bin/`); verify `go mod verify` passes in `dws-mid-basic/`
- [x] 1.2 Add the `Makefile` targets `lint`, `test`, `wasm`, `test-wasm` per design Decision 8; verify `make lint` runs clean on the empty scaffold

## 2. Guest logic (`internal/basicauth`)

- [x] 2.1 Write failing table tests for config parsing: valid config; unknown fields (`scheme`, `token`); absent, null and empty `username`/`password`; `:` in username; control characters in either; empty or `/`/`?`/`#` in `endpoint`; non-object and malformed JSON; all errors reported together. Verify `go test ./internal/...` fails for the missing code
- [x] 2.2 Implement `Parse` to make 2.1 pass; add a test that no error message contains any test username, password or JSON fragment; verify `go test ./internal/...` passes
- [x] 2.3 Write table tests for path matching (spec "Only the endpoint's invocation path is matched": method path, bare `/method`, query string, `pets` vs `pets-v2`, other endpoint, raw and `%2e` dot segments, non-invocation paths) and implement `Match`; verify they pass
- [x] 2.4 Write tests for the header value (`alice`/`s3cret` → `YWxpY2U6czNjcmV0`, `pa:ss:word` password, RFC 7617 `test`/`123£` → `dGVzdDoxMjPCow==`, decomposed `é` normalized to U+00E9) and implement `HeaderValue` with NFC + UTF-8 + `StdEncoding`; verify they pass

## 3. Wasm entry point and binary

- [x] 3.1 Write `main.go`: read `handler.Host.GetConfig()`, `basicauth.Parse`, panic with the field/rule-only message on error, otherwise register a handler that calls `Headers().Set("Authorization", …)` on a match and always returns `next=true`; no logging. Verify `go vet ./...` and `make lint` pass
- [ ] 3.2 Write the `Dockerfile` (`tinygo/tinygo:0.34.0` build with the spike's flags, `busybox:1.37.0` with only `/basic.wasm`) and the `make wasm` extraction; verify `make wasm` produces `bin/basic.wasm`, record its size, and confirm it is under 1 MiB (else apply design Decision 5's fallback and note it in the README)
- [ ] 3.3 Create the `wasmtest/` module (`http-wasm-host-go` nethttp middleware around an `httptest` echo server, loading `../bin/basic.wasm` with a test guest config) covering: header on a matching path, caller header replaced (one `Authorization` only), pass-through for other path and other endpoint with headers/URI/body unchanged, `:` and non-ASCII credentials, and a config missing `password` failing middleware creation or every request without reaching the echo server. Verify `make test-wasm` passes

## 4. CI and release

- [ ] 4.1 Add `.github/workflows/dws-mid-basic.yml` (jobs `test`, `wasm`, `build-image` per design Decision 8; path filters on `dws-mid-basic/**` and the workflow file; push on `main`, `dws-mid-basic-v*` tags and `workflow_dispatch`; digest in the job summary); verify with `actionlint` if available, and confirm the PR run is green with the image built but not pushed
- [ ] 4.2 Add the `dws-mid-basic` package to `release-please-config.json` (`simple`, `version.txt`, `CHANGELOG.md`, `skip-github-release: true`), `0.0.0` to `.release-please-manifest.json`, and the choice to `component-release.yml`; verify both JSON files parse with `jq`
- [ ] 4.3 Dry-run release-please against the branch (`npx release-please release-pr --dry-run` with the repo's config, or the local `Manifest.buildPullRequests()` method in `docs/release-process.md`) and verify it proposes `dws-mid-basic` `0.1.0` listing only this change's commits and no other component; if it does not, switch to the documented bootstrap (manifest `0.1.0` + `force=true`) and record why in design.md

## 5. Live probe

- [ ] 5.1 Write `dws-mid-basic/scripts/verify-live.sh` per design Decision 9 (local build or `IMAGE=<ref@digest>`, basic-only assertions, negative missing-password case, log-leak scan, EXIT cleanup); verify `bash -n` and `shellcheck` (if available) pass
- [ ] 5.2 Document the probe's prerequisites and the observed daprd behavior for a guest that refuses to start in the package README (filled in after 7.3)

## 6. Docs

- [ ] 6.1 Write `dws-mid-basic/README.md` (what it does, guest-config contract, failure behavior, encoding rules, image contents and init-container usage, build/test commands, deviations from the task and ADR) and `dws-mid-basic/CLAUDE.md` (Go 1.23 / TinyGo 0.34.0 pin and why, no logging rule, gate commands); verify every command in them runs as written
- [ ] 6.2 Update root `CLAUDE.md` (package map row, gate table row `make lint && make test && make test-wasm`); verify the table renders and the commands match the Makefile
- [ ] 6.3 Add a dated amendment to ADR 0010 recording the component names `dws-mid-basic` / `dws-mid-bearer` and the set-not-append header reading, and update roadmap §4h row 4.1c-basic (name, status, image); verify `grep -n "dws-wasm-basic"` leaves only the historical mentions the amendment explains

## 7. Integration checks

- [ ] 7.1 Run the package gate from `dws-mid-basic/`: `make lint && make test && make wasm && make test-wasm`; all pass
- [ ] 7.2 After merge to `main`, confirm the CI push of `ghcr.io/tonylibs/dws-mid-basic`, that it pulls anonymously, and record the image name and digest
- [ ] 7.3 Tony runs `IMAGE=ghcr.io/tonylibs/dws-mid-basic@<digest> dws-mid-basic/scripts/verify-live.sh` on the local Docker Desktop cluster; it prints `PASS`, and the output (digest, `basic.wasm` size, negative-case behavior) is recorded in the README and the change report

## Workflow follow-up

- Report the image name and digest and every deviation (name, set-vs-append, empty-password rejection, NFC-only encoding, derived probe, Go 1.23, release bootstrap) on the PR.
- Archive the change after review and the live probe; sync `basic-auth-middleware` into `openspec/specs/`.
- 4.1c-bearer copies this package's layout, CI and release shape.
