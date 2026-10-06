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

Omnigent is installed through uv with both `copilot` and `antigravity` extras
(`omnigent[copilot,antigravity]`), using the version pinned in the Dockerfile.
The image's build-time smoke test checks that both SDK packages are installed in
Omnigent's tool environment.

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
Use `root` as the SSH username and `/workspace` as Orca's remote project directory. The
repository is cloned directly into `/workspace`, not `/home/workspace/dapr-workflow-spec`.
If a restored terminal reports `chdir(2) failed`, check that its saved remote project path
exists in the current sandbox and reopen the project from `/workspace`.
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

SSH does not inherit the image's Docker `ENV` values. The image sets Java, Go, pnpm, and uv
paths for direct SSH commands and restores them in Bash shells. After changing these settings,
rebuild or pull the updated image and create a new sandbox; existing containers keep their
original SSH configuration.

The equivalent Python command is:

```powershell
py -3 .\agent-sandbox\new_ssh_sandbox.py
```

Its settings are in `agent-sandbox/ssh-sandbox.json`. It uses the OpenSandbox Python SDK for
lifecycle and sandbox file operations, and Docker only for the localhost-only SSH bridge and for
forwarding agent tokens (below).

If credential setup reports that `agent-auth-setup` is missing, the cached image predates
token forwarding. Rerun with `--pull-image` (or `-PullImage` for PowerShell) to refresh it;
locally built images need to be rebuilt from the current Dockerfile.

### Agent CLI login via tokens

Both helpers forward these host environment variables into the sandbox when they are set, so the
agent CLIs start already signed in. Unset variables are skipped; those CLIs keep their manual login.
They also copy the host's global `git config user.name` and `user.email` into the sandbox's global
Git config. For GitHub, set `GH_TOKEN` on the host or sign in to the host's `gh` CLI; the helpers
fall back to `gh auth token` when `GH_TOKEN` is unset. The sandbox configures `gh` and Git's HTTPS
credential helper from that token. `COPILOT_GITHUB_TOKEN` is kept separate because its permission
scope is for Copilot Requests, not repository access.

| Variable | CLI | Notes |
|---|---|---|
| `CLAUDE_CODE_OAUTH_TOKEN` | Claude Code | Subscription token from `claude setup-token` on the host. |
| `ANTHROPIC_API_KEY` | Claude Code | API billing. Pre-approved, so interactive `claude` does not prompt for it. Takes precedence over the OAuth token when both are set. |
| `OPENAI_API_KEY` | Codex | Codex ignores the env var, so the sandbox runs `codex login --with-api-key` once (stored in `~/.codex/auth.json`). |
| `COPILOT_GITHUB_TOKEN` | Copilot CLI | Fine-grained PAT with only the **Copilot Requests** account permission; classic PATs are not supported. It is not used for `gh`/Git. |
| `GEMINI_API_KEY` | Antigravity (`agy`) | Also sets `"modelProvider": "gemini"` in `~/.gemini/antigravity-cli/settings.json`, so `agy` uses Gemini API billing instead of your Google account sign-in. |
| `GH_TOKEN` | GitHub CLI and Git HTTPS | A GitHub token with the repository permissions needed for your intended `gh` and Git operations. Falls back to the host's `gh auth token`. |

```powershell
$env:CLAUDE_CODE_OAUTH_TOKEN = "..."   # plus any of the others
.\agent-sandbox\new-ssh-sandbox.ps1
```

The tokens are streamed over `docker exec -i` stdin into `agent-auth-setup`, so they never appear
on a command line, in `docker inspect`, or in the OpenSandbox store. Inside the sandbox they are
written to `/root/.config/agent-sandbox/credentials.env` (directory mode 700, file mode 600).
The image's Bash startup hooks load this file into login and interactive shells, including
Orca remote terminals. Values are parsed as data, never evaluated as shell code.
`/root/.ssh/environment` is also maintained for direct SSH commands.

Rebuild and publish the updated image, then create a new sandbox with `-PullImage` or
`--pull-image` from a shell where the desired token variables are set. Connect Orca to the
SSH endpoint printed by the launcher; no Orca setup script is needed.

To add or rotate tokens on a running sandbox, pipe the full set again. For example, using
the existing host environment variable rather than typing a token into command history:

```powershell
"COPILOT_GITHUB_TOKEN=$env:COPILOT_GITHUB_TOKEN" | docker exec -i sandbox-<id> agent-auth-setup
```

Names left out are removed from the credential files, except Codex's stored login, which stays until
`codex logout`; previously configured global Git name/email stay until changed with `git config`.
Open a fresh Orca terminal to load the updated credentials. Existing processes
retain their old environment; restarting the terminal is required to remove omitted tokens.
To reload added or changed tokens in an existing Bash terminal, run:

```sh
. /etc/profile.d/agent-env.sh
test -n "${COPILOT_GITHUB_TOKEN:-}" && echo 'Copilot token loaded'
```

The check prints only token presence. A loaded token still needs to be valid and have the
Copilot Requests permission.

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
and pushes to `ghcr.io/tonylibs/dws-agent-sandbox` only on merge to `main`.
The Dockerfile's smoke-test `RUN` step fails the build if a toolchain is missing or
the wrong version. DWS component builds and tests run in their own CI workflows.

`.github/workflows/agent-sandbox-components.yml` provides a separate, manually
triggered check of `dws-controller`, `dws-orchestrator`, `dws-call-http`, `dws-run`,
and `dws-call-openapi` inside the sandbox image. Run **agent-sandbox-components**
from GitHub Actions using **Run workflow**. It builds the sandbox image from the
selected ref and mounts the CI checkout for these checks; it does not publish
an image or run automatically on pushes or pull requests.
