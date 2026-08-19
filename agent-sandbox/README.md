# agent-sandbox

Templates for running Codex, Claude, or other agent dev sessions against this monorepo inside a
[kubernetes-sigs/agent-sandbox](https://github.com/kubernetes-sigs/agent-sandbox) `Sandbox`,
on top of the already-provisioned OpenSandbox control plane + CRDs.

This is separate from `scripts/start-kind-cluster.sh` (ephemeral local kind cluster for
`CLAUDE_CODE_REMOTE` test runs) — this directory is for longer-lived, cluster-hosted agent
sessions with persistent caches.

For local development, OpenSandbox can instead use Docker directly. This is a separate
runtime option: it creates containers through the local Docker daemon and does not create
`Sandbox` CRDs.

## Files

| File | Purpose | Status |
|---|---|---|
| `Dockerfile` | Shared dev image: JDK 25, Go 1.26, Node 24/pnpm, git/gh/jq, Claude Code, [Codex](https://github.com/openai/codex), [OpenSpec](https://github.com/Fission-AI/OpenSpec), [OpenWiki](https://github.com/langchain-ai/openwiki), and [ClawTeam](https://github.com/HKUDS/ClawTeam) CLIs, with Superpowers installed for Claude and Codex | pinned, with a build-time smoke test — see `.github/workflows/agent-sandbox.yml` |
| `sandbox.yaml` | `Sandbox` CRD manifest for one agent session | skeleton — confirm installed CRD apiVersion first |
| `cache-pvcs.yaml` | PVCs for `~/.m2`, Go module cache, pnpm store | skeleton — confirm storageClass |
| `opensandbox/docker.toml` | OpenSandbox lifecycle-server profile for local Docker-backed sandboxes | local profile — Docker Desktop/Engine required |
| `sshd-start.sh` | Key-only SSH daemon entrypoint for Docker-backed remote-development sandboxes | generates unique host keys at each container start |

## Local Docker runtime

With Docker Desktop or Docker Engine running, start a local OpenSandbox server with the
Docker profile:

```powershell
$env:OPENSANDBOX_SERVER_API_KEY = "replace-with-a-local-secret"
uvx opensandbox-server --config agent-sandbox/opensandbox/docker.toml
```

The server listens only on `127.0.0.1:8080`. Each `POST /v1/sandboxes` creates one Docker
container. The profile uses bridge networking, drops dangerous Linux capabilities, prevents
privilege escalation, and limits each container to 4096 processes. Do not set a public bind
address without an API key and an explicit exposure design.

For a one-off localhost experiment without an API key, set
`OPENSANDBOX_INSECURE_SERVER=YES` instead; this must not be used outside a local test.

### SSH remote development

The image includes OpenSSH server support for using a sandbox as a Codex Desktop SSH remote
project. It is key-only: passwords and root-password login are disabled. The default image
command runs `sshd-start`, which generates fresh host keys per container and starts `sshd` in
the foreground. Supply an authorized public key at runtime in `/root/.ssh/authorized_keys`,
then map the image's declared port 22 through the Docker/OpenSandbox deployment. Do not expose
the SSH port publicly; use a localhost mapping, VPN, or mesh network.

The published GHCR image will contain this capability after the updated Dockerfile passes the
agent-sandbox CI workflow and is published from `main`.

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
