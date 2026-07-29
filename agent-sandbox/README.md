# agent-sandbox

Templates for running Claude/agent dev sessions against this monorepo inside a
[kubernetes-sigs/agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) `Sandbox`,
on top of the already-provisioned OpenSandbox control plane + CRDs.

This is separate from `scripts/start-kind-cluster.sh` (ephemeral local kind cluster for
`CLAUDE_CODE_REMOTE` test runs) — this directory is for longer-lived, cluster-hosted agent
sessions with persistent caches.

## Files

| File | Purpose | Status |
|---|---|---|
| `Dockerfile` | Shared dev image: JDK 25, Go 1.26, Node 24/pnpm, git/gh/jq | pinned, with a build-time smoke test — see `.github/workflows/agent-sandbox.yml` |
| `sandbox.yaml` | `Sandbox` CRD manifest for one agent session | skeleton — confirm installed CRD apiVersion first |
| `cache-pvcs.yaml` | PVCs for `~/.m2`, Go module cache, pnpm store | skeleton — confirm storageClass |

## Confirm before use

- Installed CRD version: `kubectl get crd sandboxes.agents.x-k8s.io -o jsonpath='{.spec.versions[*].name}'`
  (v0.4.x only serves `v1alpha1`, no conversion webhook — manifest must match exactly)
- RuntimeClass available on nodes (gVisor/Kata) for the `podTemplate`
- Registry the built image gets pushed to, referenced in `sandbox.yaml`

## Not scaffolded yet

- RBAC/namespace scoping for the sandbox service account
- Image build tooling inside the sandbox itself (buildah/kaniko), if Dockerfile validation is needed in-session
- `kubectl`/`dapr` CLI in the image (deferred — add only once the agent needs to validate against a live cluster)

`.github/workflows/agent-sandbox.yml` builds the image on every push/PR touching this directory
(the Dockerfile's smoke-test `RUN` step fails the build if a toolchain is missing or the wrong
version, and the workflow then runs each component's real CI-gate command inside the built image:
`./mvnw verify` for `dws-controller`/`dws-orchestrator`, `make vet && make test` for
`dws-call-http`/`dws-run`, `pnpm lint && pnpm test && pnpm build` for `dws-call-openapi`) and pushes
to `ghcr.io/tonylibs/dws-agent-sandbox` only on merge to `main`.
