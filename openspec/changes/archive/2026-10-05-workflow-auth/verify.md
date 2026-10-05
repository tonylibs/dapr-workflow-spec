# Verification Report

**Change**: `workflow-auth`
**Verified at**: `2026-10-05 Asia/Ho_Chi_Minh` (re-verification; supersedes the 2026-08-23 FAIL report)
**Verifier**: Claude Code, live-cluster closure of tasks 6.2/6.3

---

## 1. Structural Validation (`openspec validate --all --json`)

`workflow-auth` itself is valid. Repository-wide: 57 items, 4 invalid, none touched by this change.

The five items flagged in the 2026-08-23 report no longer apply: `helm-postgres-deployment`,
`helm-pubsub-integration-test`, `helm-redis-dependency`, and `run-step-execution` now validate, and
`ows-phase3-errors-timeouts` has been archived. The current unrelated invalid items are:

| Item | Type | Issue |
|---|---|---|
| `dws-step-failure-contract` | spec | Requirement must contain SHALL or MUST keyword |
| `helm-admin-gateway` | spec | Spec must have at least one requirement |
| `single-node-definition-contract` | spec | Requirement must contain SHALL or MUST keyword |
| `workflow-timeouts` | spec | Requirement must contain SHALL or MUST keyword (×2) |

---

## 2. Task Completion (`tasks.md`)

- [x] All tasks are checked — 21/21.

---

## 3. Delta Spec Sync State

| Capability | Sync state | Note |
|---|---|---|
| `workflow-authentication` | Synced on archive | Delta spec ready. |
| `workflow-secrets` | Synced on archive | Delta spec ready. |

---

## 4. Design / Specs Coherence Spot Check

| Sample | Design decision | Specs / implementation evidence | Drift |
|---|---|---|---|
| Secrets | Names only compile; values use `secretKeyRef` | `EnvValue.SecretKeyRef`, DNS-1123 `use.secrets` validation, compiler/synthesizer tests | None |
| OAuth | Dapr-native `client_credentials`, narrow `pathFilter` | `StackSynthesizer` HTTPEndpoint/Component/Configuration synthesis; live probe (§5) | None |
| jq | Declared secrets available only to `set`/`switch` | Bootstrap and activity tests; README leakage warning | None |
| OpenAPI | OAuth target is the effective operation server | Compiler and runner relative-server regression tests | None |

### Probe vs. controller output

`scripts/verify-dapr-oauth-path-filter.sh` hand-writes the resource triple. Compared against
`StackSynthesizer` and `StackSynthesizerTest` at `9d8f2b30`:

| Aspect | Probe | Controller | Match |
|---|---|---|---|
| Resource names | HTTPEndpoint, Component, Configuration share one name | All three use `endpoint.name()` | Yes |
| Labels | `dws.io/workflow`, `dws.io/version`, `dws.io/managed-by` | `Labels.forPlan` | Yes |
| Scopes | HTTPEndpoint + Component scoped to the caller app-id; Configuration unscoped | Same (`endpoint.appIds()`) | Yes |
| Credentials | `clientId`/`clientSecret` via `secretKeyRef`, key `value` | Same; secret names now must be DNS-1123 (probe names comply) | Yes |
| Metadata | comma `scopes`, `tokenURL`, `headerName: authorization`, `authStyle: "2"` | Same (`client_secret_basic` → `2`) | Yes |
| `pathFilter` | `^/v1\.0/invoke/<name>/method(?:/intended)$` | `^/v1\.0/invoke/<escaped name>/method(?:<sorted escaped paths>)$` | Yes |
| Pipeline | `spec.httpPipeline` | `spec.httpPipeline` | Yes |
| Binding | `dapr.io/config: <name>` on a plain Deployment | Same annotation on the step's Knative Service | Workload kind differs; immaterial to the middleware |

Non-material: the probe's comment says "appHttpPipeline" while both the probe manifest and the
controller use `httpPipeline` (the correct pipeline for app→sidecar invocation). Comment only.

---

## 5. Live Dapr Integration Result (task 6.2)

| Item | Value |
|---|---|
| Cluster | Local kind-based Docker Desktop cluster (`docker-desktop` context), 3 nodes, Kubernetes v1.34.3 |
| Dapr | **1.18.2** (pre-installed control plane, release `dapr` in `dapr-system`; injector `ghcr.io/dapr/injector:1.18.2`) |
| Probe | `scripts/verify-dapr-oauth-path-filter.sh`, run as a copy with only its Dapr Helm install/uninstall lines removed |
| Result | **PASS** — `OAuth endpoint isolation passed: intended path received the issued token; unrelated path did not.` |
| Observed | `/intended` → `Bearer issued-oauth-token`; `/unrelated` → `<none>` (no `Authorization` header) |
| Cleanup | Probe namespace `dws-oauth-e2e` removed on exit; shared Dapr untouched |

Why the Helm lines were removed: the cluster is shared and already runs Dapr 1.18.2. A second Dapr
chart release would collide on cluster-scoped objects (`dapr-sidecar-injector` webhook,
ClusterRoles, CRDs), and the probe's exit trap (`helm uninstall`) could delete objects owned by the
existing release. The diff against the committed script is limited to those lines; resource
manifests and assertions are byte-identical.

---

## 6. Component Gates (task 6.3)

Run on 2026-10-05 against `9d8f2b30`.

| Component | Command | Result |
|---|---|---|
| `dws-controller` | `mvn -B -Dexec.skip=true verify` (Windows, JDK 25.0.4, Maven 3.9.14) | 224 tests, **6 failures** — all `V2GoldenTest`, Windows-only (see W2). All OAuth/secret tests pass (`StackSynthesizerTest` 26/26). |
| `dws-controller` (CI reference) | `./mvnw -B -ntp test` on ubuntu-latest, run on `main` @ `f953bb64` (controller tree identical to `9d8f2b30`) | 224 tests, 0 failures |
| `dws-orchestrator` | `mvnw.cmd -B verify` (JDK 25.0.4) | 162 tests, 0 failures/errors; BUILD SUCCESS |
| `dws-call-http` | `make lint && make test` in `golang:1.26` container (Go 1.26.5) | Pass — vet + gofmt clean (golangci-lint not installed); `go test -race`: 3 packages ok, 9 top-level tests |
| `dws-call-openapi` | `pnpm lint && pnpm test && pnpm build` | Lint pass; 95/95 tests (8 files); build pass |

### Deployment / execution prerequisites discovered

- **Live probe:** a writable cluster with kubectl and network access to the Dapr Helm repo. On a
  cluster that already runs Dapr, the probe's own Helm install must be skipped (see §5); a
  disposable cluster (kind) is needed to run it unmodified at the 1.18.1 default. The probe states
  "Helm 3"; Helm 4 is untested.
- **`dws-controller` on Windows:** the `cdk8s-import-*` exec steps fail (`Error: unsupported
  protocol c:`) because `cdk8s-cli import` treats the absolute `C:\…\k8s\*.yaml` path as a URL.
  Workaround used: copy `target/generated-sources/cdk8s` from a checkout with identical
  `k8s/` CRD inputs and cdk8s config, then build with `-Dexec.skip=true`.
- **`dws-call-http` on Windows:** `make` and a cgo toolchain (needed for `go test -race`) are not
  installed locally; the gate was run in a Linux Go container.
- **Maven wrapper in slim Linux images:** without `unzip`, `mvnw` falls back to the `.tar.gz`
  distribution, which fails the `.zip` `distributionSha256Sum` check.
- **RBAC:** none beyond what the probe already creates in its own namespace.

---

## 7. Front-Door Routing Leak Detector

- [x] No `docs/superpowers/specs/*.md` files were found.

---

## 8. Deferred Manual Dogfood vs Automated Test Equivalence

| Manual / environment check | Automated coverage | Assessment | Real gap? |
|---|---|---|---|
| Live Dapr OAuth isolation probe | Synthesizer path-filter tests + live probe (§5) | Live middleware injection and isolation proven on 1.18.2 | No (see W1) |

---

## Warnings

- **W1 — Integration version.** The spec scenario "OAuth path filtering is verified at the pinned
  version" names 1.18.1; the live run used **1.18.2** (one patch above the pin, the cluster's
  installed control plane). Accepted by the project owner as satisfying the scenario.
  `charts/dws` still pins Dapr 1.18.1.
- **W2 — Controller gate red on Windows.** `V2GoldenTest` (6 cases) fails locally because
  `SingleNodeDefinition.java:72` pretty-prints with Jackson's default printer, which emits the OS
  line separator (`\r\n` on Windows), while the golden fixtures are LF. Unrelated to this change
  (v2 compiler); green on Linux CI for the identical controller tree. Not fixed here.
- **W3 — Probe ran with Helm install removed** (§5). Assertions and manifests unchanged.

---

## Overall Decision

- [ ] PASS
- [x] PASS WITH WARNINGS — W1–W3 above.
- [ ] FAIL

**Next step**: archive `workflow-auth` and sync the `workflow-authentication` and
`workflow-secrets` delta specs.
