# openshell

Runs Omnigent sessions in [NVIDIA OpenShell](https://github.com/NVIDIA/OpenShell) sandboxes
booted from the DWS agent-sandbox image. Status: **example, not yet built or run end to end.**

Reference: Omnigent's `deploy/openshell/README.md` (the provider guide this follows).

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | Layer on top of `ghcr.io/tonylibs/dws-agent-sandbox` that meets OpenShell's image contract. |
| `policy.yaml` | Egress allow-list baked in at `/etc/openshell/policy.yaml` (OpenShell denies all egress by default). |
| `omnigent-sandbox.yaml` | `sandbox:` block for the Omnigent **server** config. |
| `run-orchestrator.sh` | Starts the `.omnigent/` orchestrator in a managed OpenShell sandbox. |

## Run the DWS orchestrator in OpenShell

An agent spec cannot choose its sandbox: placement is per session. `run-orchestrator.sh`
packs the tracked files under `.omnigent/` into a bundle and sends it with the multipart
`POST /v1/sessions` API, using `host_type: "managed"` and `sandbox_provider: "openshell"`.
The server provisions the sandbox and clones the workspace repository into it. The bundle
and request shape were checked against Omnigent 0.14.0's own validators: the orchestrator
and all 12 sub-agents pass upload validation.

With the gateway, image and server from the steps below in place:

```sh
export OMNIGENT_SERVER=http://localhost:6767   # you talk to the server locally; only the sandbox uses the tunnel
export DWS_WORKSPACE='https://github.com/tonylibs/dapr-workflow-spec#main'   # optional; this is the default
# export OMNIGENT_TOKEN=...   # only when the server runs with OMNIGENT_AUTH_ENABLED=1

# like `omnigent run .omnigent -p "..."`: waits for the sandbox, sends the prompt, opens the TUI
agent-sandbox/openshell/run-orchestrator.sh -p "List the DWS packages and their gates"

# like `omnigent run .omnigent`: opens the TUI; type once the sandbox is ready
agent-sandbox/openshell/run-orchestrator.sh

# create only, print the session id (scripts); open the TUI later with `omnigent attach`
agent-sandbox/openshell/run-orchestrator.sh -p "..." --no-attach
```

With `-p`, the script prints each sandbox stage (`provisioning`, `cloning`, `starting`,
`connecting`, `ready`) and exits with the server's error on `failed`. It waits up to
`DWS_READY_TIMEOUT_S` (default 900) seconds, since the first pull of the large image is slow.
`omnigent attach` uses the credentials from `omnigent login <server>` when auth is enabled.

Credentials needed in the **server** environment, one per harness or MCP server in
`.omnigent/`:

| Variable | Used by |
|---|---|
| `ANTHROPIC_API_KEY` (or `CLAUDE_CODE_OAUTH_TOKEN`) | `claude-sdk`: orchestrator and most sub-agents |
| `OPENAI_API_KEY` | `codex`: go-developer |
| `COPILOT_GITHUB_TOKEN` | `copilot`: nodejs-developer, dotnet-developer |
| `GEMINI_API_KEY` | `antigravity`: frontend-developer |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | `@modelcontextprotocol/server-github` MCP tool |
| `GIT_TOKEN` | cloning and pushing the workspace repository |

Drop a name from `openshell.env` if you don't use that harness, because a listed name
that is unset fails the launch. `COPILOT_GITHUB_TOKEN` and `GITHUB_PERSONAL_ACCESS_TOKEN`
are outside the host's default forwarding set, so they must also appear in
`OMNIGENT_RUNNER_ENV_PASSTHROUGH`. The comments in `omnigent-sandbox.yaml` give the full value.

## Why the base image needs a layer

| OpenShell requirement | Base image | This layer |
|---|---|---|
| Non-root `sandbox` user/group | missing | `useradd sandbox` |
| `iproute2` + `nftables` | missing | installs both |
| Execs run as `sandbox`, `HOME=/home/sandbox` | tools and agent config under `/root` (mode 700) | `chmod a+rX /root`, copies `.claude`/`.codex`/`.config` into `/home/sandbox` |
| Writable Go cache | `GOPATH=/root/go` | `GOPATH=/home/sandbox/go` |

The base image's `CMD ["sshd-start"]` does not apply here: OpenShell injects its own
supervisor as the entrypoint.

## Requirements

- **amd64 Linux** for the gateway host. OpenShell's supervisor does not run reliably under
  emulation, so Apple Silicon and Windows need a remote amd64 Linux box (WSL2 is untested).
- Docker on the gateway host.
- Omnigent `0.14.0` on the server, matching `OMNIGENT_VERSION` in `agent-sandbox/Dockerfile`.
  Server-managed sandboxes run the image's own Omnigent; CLI-launched ones overlay wheels
  from your local checkout instead.

## Steps

1. Install the native OpenShell gateway. On Debian/Ubuntu the installer adds a `.deb`
   (CLI, `openshell-gateway`, prover), starts the `openshell-gateway` systemd **user**
   service on `https://127.0.0.1:17670` with mTLS, and registers it as gateway `openshell`:

   ```sh
   curl -fsSL https://raw.githubusercontent.com/NVIDIA/OpenShell/main/install.sh -o install.sh
   sh install.sh
   sudo loginctl enable-linger "$USER"   # keep the gateway running after logout
   openshell status                      # expect: Connected, Authenticated
   openshell gateway info                # expect the docker driver to be initialized
   ```

   Logs: `journalctl --user -u openshell-gateway -f`. Config: `~/.config/openshell/gateway.toml`.

2. Install Omnigent with the OpenShell extra, as the same user that owns the gateway
   registration, and select the gateway:

   ```sh
   uv tool install --python 3.12 'omnigent[openshell]==0.14.0'
   openshell gateway select openshell
   ```

   For a quick plaintext test gateway instead, Omnigent's
   `deploy/openshell/start-local-docker-gateway.sh` also works; don't expose it on a network.

3. Back in this repository's root, build the image and check the contract:

   ```sh
   docker build -f agent-sandbox/openshell/Dockerfile --platform linux/amd64 \
     -t ghcr.io/tonylibs/dws-agent-sandbox:openshell agent-sandbox/openshell
   docker run --rm --entrypoint sh ghcr.io/tonylibs/dws-agent-sandbox:openshell \
     -lc 'id sandbox && command -v ip && command -v nft'
   ```

   The gateway's Docker daemon must be able to pull or already hold this image.

4. Make the Omnigent server reachable from the sandbox. Locally, the simplest route is a
   quick tunnel:

   ```sh
   cloudflared tunnel --url http://localhost:<omnigent-server-port>
   ```

   Put the printed `https://….trycloudflare.com` URL in `server_url`, and its host in
   `policy.yaml`'s `omnigent-server` entry. Rebuild the image after editing `policy.yaml`.

5. **CLI-launched sandbox** (quickest test):

   ```sh
   export OMNIGENT_OPENSHELL_HOST_IMAGE=ghcr.io/tonylibs/dws-agent-sandbox:openshell
   export OMNIGENT_OPENSHELL_SANDBOX_ENV=ANTHROPIC_API_KEY,GIT_TOKEN
   omnigent sandbox create --provider openshell --server https://<your-server-url>
   omnigent sandbox connect --provider openshell --sandbox-id <id> --server https://<your-server-url>
   ```

   **Server-managed sandbox**: merge `omnigent-sandbox.yaml` into the server config, export
   `OMNIGENT_RUNNER_ENV_PASSTHROUGH=https_proxy,http_proxy,HTTPS_PROXY,HTTP_PROXY,NO_PROXY,no_proxy`
   plus the credentials in the server environment, restart the server, then create a session
   with `"host_type": "managed"`.

6. Verify inside the sandbox that a DWS gate runs as the `sandbox` user, for example
   `cd dws-call-http && make test`.

## Known risks

- **Nested bubblewrap.** Omnigent's Linux terminal wrappers use `bwrap`, which needs user
  namespaces. OpenShell's Landlock/seccomp profile may block that inside the sandbox.
- **Policy schema.** `policy.yaml` copies the minimal shape from Omnigent's guide. Check the
  OpenShell policy docs before relying on wildcards or `tls: skip`.
- **Registry gaps.** Any host missing from `policy.yaml` fails with a proxy `403`. Maven
  plugins, Go modules and pnpm may pull from more hosts than listed.
- **Credential isolation.** OpenShell recommends its inference routing over allow-listing
  `api.anthropic.com` directly. The allow-list is the simple path, not the hardened one.
