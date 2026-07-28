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
| `Dockerfile` | Shared dev image: JDK 25, Go 1.26, Node 24/pnpm, git/gh/jq | skeleton — TODO fill in versions/pins |
| `sandbox.yaml` | `Sandbox` CRD manifest for one agent session | skeleton — confirm installed CRD apiVersion first |
| `cache-pvcs.yaml` | PVCs for `~/.m2`, Go module cache, pnpm store | skeleton — confirm storageClass |

## Confirm before use

- Installed CRD version: `kubectl get crd sandboxes.agents.x-k8s.io -o jsonpath='{.spec.versions[*].name}'`
  (v0.4.x only serves `v1alpha1`, no conversion webhook — manifest must match exactly)
- RuntimeClass available on nodes (gVisor/Kata) for the `podTemplate`
- Registry the built image gets pushed to, referenced in `sandbox.yaml`

## Not scaffolded yet

- CI job to build/push this image
- RBAC/namespace scoping for the sandbox service account
- Image build tooling inside the sandbox itself (buildah/kaniko), if Dockerfile validation is needed in-session
