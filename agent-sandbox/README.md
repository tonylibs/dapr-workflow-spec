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
| `Dockerfile` | Shared dev image: JDK 25, Go 1.26, Node 24/pnpm, git/gh/jq, uv, bubblewrap, [Omnigent](https://github.com/omnigent-ai/omnigent), Claude Code, [Codex](https://github.com/openai/codex), [GitHub Copilot CLI](https://github.com/github/copilot-cli), [Antigravity CLI (`agy`)](https://github.com/google-antigravity/antigravity-cli), [OpenSpec](https://github.com/Fission-AI/OpenSpec), [OpenWiki](https://github.com/langchain-ai/openwiki), and [ClawTeam](https://github.com/HKUDS/ClawTeam) CLIs, with Superpowers installed for Claude and Codex | pinned (except `agy`, whose installer only serves the current release), with a build-time smoke test — see `.github/workflows/agent-sandbox.yml` |
| `sandbox.yaml` | `Sandbox` CRD manifest for one agent session | skeleton — confirm installed CRD apiVersion first |
| `cache-pvcs.yaml` | PVCs for `~/.m2`, Go module cache, pnpm store | skeleton — confirm storageClass |
| `opensandbox/docker.toml` | OpenSandbox lifecycle-server profile for local Docker-backed sandboxes | local profile — Docker Desktop/Engine required |
| `start-opensandbox.ps1` | Windows launcher that injects the host kubeconfig path into the local Docker profile | use this instead of starting the server with the TOML directly |
| `sshd-start.sh` | Key-only SSH daemon entrypoint for Docker-backed remote-development sandboxes | clones the DWS repository into an empty `/workspace`, reuses an existing matching checkout unchanged, and generates unique host keys at each container start |

## Local Docker runtime

With Docker Desktop or Docker Engine running, start a local OpenSandbox server with the
Docker profile:

```powershell
$env:OPENSANDBOX_SERVER_API_KEY = "replace-with-a-local-secret"
./agent-sandbox/start-opensandbox.ps1
```

The launcher resolves the host's `~/.kube/config`, generates a temporary server
configuration, and mounts that single file read-only at `/root/.kube/config` in every
sandbox. Starting `uvx opensandbox-server` directly with the checked-in TOML leaves its
placeholder bind path unresolved.

The server listens only on `127.0.0.1:8080`. Each `POST /v1/sandboxes` creates one Docker
container. The profile uses bridge networking, drops dangerous Linux capabilities, prevents
privilege escalation, and limits each container to 4096 processes. `SYS_ADMIN` is intentionally
left out of the global drop list so the helper can request OpenSandbox's
`bootstrap.execd.isolation=enable` extension for Omnigent sandboxes; OpenSandbox then grants the
bubblewrap-specific Docker settings only for that sandbox. Do not set a public bind address
without an API key and an explicit exposure design.

For a one-off localhost experiment without an API key, set
`OPENSANDBOX_INSECURE_SERVER=YES` instead; this must not be used outside a local test.

### SSH remote development

The image includes OpenSSH server support for using a sandbox as a Codex Desktop SSH remote
project. It is key-only: passwords and root-password login are disabled. The default image
command runs `sshd-start`, which clones `https://github.com/tonylibs/dapr-workflow-spec.git`
into an empty `/workspace`, leaves an existing checkout with the same origin unchanged, generates
fresh host keys per container, and starts `sshd` in the foreground. It refuses to overwrite a
non-empty workspace or a checkout with a different origin. `DWS_REPOSITORY_URL` and
`DWS_REPOSITORY_DIR` can override the clone source and destination.

Supply an authorized public key at runtime in `/root/.ssh/authorized_keys`, then map the image's
declared port 22 through the Docker/OpenSandbox deployment. Do not expose the SSH port publicly;
use a localhost mapping, VPN, or mesh network.

The sandbox image includes `kubectl`. On startup, `sshd-start` copies the mounted kubeconfig
to `/root/.kube-local/config`, rewrites Docker Desktop's `127.0.0.1`/`localhost` API endpoint
to the host-gateway IPv4 address behind the certificate-valid hostname `kubernetes`, and sets
`KUBECONFIG` to that copy. The host kubeconfig remains read-only and untouched.

To create a fully configured local SSH sandbox with a fresh localhost SSH port for Orca, run:

```powershell
.\agent-sandbox\new-ssh-sandbox.ps1
```

The helper calls `osb sandbox create`, provisions only `~/.ssh/dws_sandbox.pub`, creates a
localhost-only TCP bridge on an available random port, and prints the exact `host:port` for Orca.
Edit `agent-sandbox/ssh-sandbox.psd1` or `agent-sandbox/ssh-sandbox.json` to change the image,
resource limits, lifetime, or set a fixed local SSH port. Leaving `SshPort`/`ssh_port` null avoids
Orca host-key cache conflicts between freshly provisioned sandboxes. The default profile uses
manual cleanup (`none`/`null` timeout), so remember to kill the sandbox when finished.
By default, both helpers reuse the local image. Pass `-PullImage` to the PowerShell helper or
`--pull-image` to the Python helper to refresh the configured image before creating a sandbox:

```powershell
.\agent-sandbox\new-ssh-sandbox.ps1 -PullImage
py -3 .\agent-sandbox\new_ssh_sandbox.py --pull-image
```

The equivalent Python command is:

```powershell
py -3 .\agent-sandbox\new_ssh_sandbox.py
```

Its settings are in `agent-sandbox/ssh-sandbox.json`. It uses the OpenSandbox Python SDK for
lifecycle and sandbox file operations, and Docker only for the localhost-only SSH bridge and for
forwarding agent tokens (below).

### Agent CLI login via tokens

Both helpers forward these host environment variables into the sandbox when they are set, so the
agent CLIs start already signed in. Unset variables are skipped; those CLIs keep their manual login.

| Variable | CLI | Notes |
|---|---|---|
| `CLAUDE_CODE_OAUTH_TOKEN` | Claude Code | Subscription token from `claude setup-token` on the host. |
| `ANTHROPIC_API_KEY` | Claude Code | API billing. Pre-approved, so interactive `claude` does not prompt for it. Takes precedence over the OAuth token when both are set. |
| `OPENAI_API_KEY` | Codex | Codex ignores the env var, so the sandbox runs `codex login --with-api-key` once (stored in `~/.codex/auth.json`). |
| `COPILOT_GITHUB_TOKEN` | Copilot CLI | Fine-grained PAT with only the **Copilot Requests** account permission; classic PATs are not supported. Deliberately not `GH_TOKEN`, which would also sign in `gh`/git. |
| `GEMINI_API_KEY` | Antigravity (`agy`) | Also sets `"modelProvider": "gemini"` in `~/.gemini/antigravity-cli/settings.json`, so `agy` uses Gemini API billing instead of your Google account sign-in. |

```powershell
$env:CLAUDE_CODE_OAUTH_TOKEN = "..."   # plus any of the others
.\agent-sandbox\new-ssh-sandbox.ps1
```

The tokens are streamed over `docker exec -i` stdin into `agent-auth-setup`, so they never appear
on a command line, in `docker inspect`, or in the OpenSandbox store. Inside the sandbox they are
written to `/root/.ssh/environment` (mode 600), which `sshd` loads into every SSH session for the
names allowed by `PermitUserEnvironment`. Plain container env would not reach SSH sessions. To add
or rotate tokens on a running sandbox, pipe the full set again:
`"COPILOT_GITHUB_TOKEN=..." | docker exec -i sandbox-<id> agent-auth-setup` (names left out are
removed, except Codex's stored login, which stays until `codex logout`); then open a new SSH
session.

Any agent in the sandbox runs as root and can read every forwarded token, so a prompt-injected
agent could leak them. Use dedicated, narrowly scoped keys with spend limits, and revoke them when
the sandbox is done.

## Confirm before use

- Installed CRD version: `kubectl get crd sandboxes.agents.x-k8s.io -o jsonpath='{.spec.versions[*].name}'`
  (v0.4.x only serves `v1alpha1`, no conversion webhook — manifest must match exactly)
- RuntimeClass available on nodes (gVisor/Kata) for the `podTemplate`
- Registry the built image gets pushed to, referenced in `sandbox.yaml`

## Not scaffolded yet

- RBAC/namespace scoping for the sandbox service account
- Image build tooling inside the sandbox itself (buildah/kaniko), if Dockerfile validation is needed in-session
- `dapr` CLI in the image (not needed for kubectl access to the host cluster)

`.github/workflows/agent-sandbox.yml` builds the image on every push/PR touching this directory
(the Dockerfile's smoke-test `RUN` step fails the build if a toolchain is missing or the wrong
version, and the workflow then runs each component's real CI-gate command inside the built image:
`./mvnw verify` for `dws-controller`/`dws-orchestrator`, `make vet && make test` for
`dws-call-http`/`dws-run`, `pnpm lint && pnpm test && pnpm build` for `dws-call-openapi`) and pushes
to `ghcr.io/tonylibs/dws-agent-sandbox` only on merge to `main`.
