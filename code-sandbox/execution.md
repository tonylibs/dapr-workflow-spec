# DWS OpenSandbox execution

The trusted host helpers require Python 3.11+, Git, the Docker CLI, a dedicated Linux Docker daemon, the installed OpenSandbox server and MCP executable, and the `osb` CLI. The bootstrap uses the installed server on PATH or the locally cached `opensandbox-server==0.2.3` package through offline `uvx`. The execution profile contains no host bind mounts, Docker socket, Kubernetes credentials, or SSH bootstrap.

Set `DWS_CODE_EXEC_DOCKER_HOST` in the agent host environment to the dedicated daemon's loopback TCP endpoint. The daemon must also publish sandbox ports so the MCP client and the CLI can reach them at `127.0.0.1`. A separate Linux engine in a local VM with host loopback forwarding for the daemon endpoint and sandbox ports is one way to meet these requirements. OpenSandbox's Docker runtime discovers sandboxes across the engine, so the daemon must be independent of the one used for interactive sandboxes. Use Docker's standard `DOCKER_TLS_VERIFY` and `DOCKER_CERT_PATH` variables if the dedicated endpoint uses TLS.

```powershell
# Example endpoint. Use the port configured for your dedicated daemon.
$env:DWS_CODE_EXEC_DOCKER_HOST = 'tcp://127.0.0.1:2376'
py -3 code-sandbox/ensure-server.py
```

The listener uses `127.0.0.1:8090` and local loopback mode. Pass startup or configuration errors back to the user. When another service occupies port 8090, preserve it and report the conflict. The bootstrap writes startup diagnostics to the ignored `code-sandbox/.local` directory.

## Create and prepare a sandbox

Read [profiles.json](profiles.json). Use the component's profile, or select a suitable build image from the component's own manifests and CI for unmapped components. Choose an image with a shell, `tar`, `gzip`, and `sha256sum`. To prepare the local controller runtime image, run this trusted infrastructure command from the repository root. The Docker build context is limited to the code sandbox configuration directory.

```powershell
docker --host $env:DWS_CODE_EXEC_DOCKER_HOST build --target java-node -t dws-code-exec-java-node:jdk25-node24 code-sandbox
```

For example, use `opensandbox_exec.sandbox_create`:

```json
{
  "image": "dws-code-exec-java-node:jdk25-node24",
  "entrypoint": ["sleep", "infinity"],
  "timeout_seconds": 3600,
  "ready_timeout_seconds": 180,
  "resource": {"cpu": "2", "memory": "4Gi"},
  "metadata": {"purpose": "dws-code-execution", "task": "<unique-task-id>"}
}
```

Use the returned `sandbox_id` for all subsequent operations. If the MCP session lost its cached connection, use `sandbox_connect` with the same ID.

## Transfer the current worktree

Review the source scope first. Pass `--scope` once per component or shared path and `--include-untracked` once per required new file identified in `git status`. Tracked files come from the worktree, so unstaged edits and deletions are reflected. The helper excludes common credential paths and build outputs. Keep any other credentials out of the selected paths. Use narrow scopes to avoid unrelated worktree content.

```powershell
py -3 code-sandbox/upload-worktree.py --sandbox-id <sandbox-id> --scope dws-controller
```

The helper prints the remote archive path and SHA-256. With `opensandbox_exec.command_run`, set `working_directory` to `/` and use the following preparation command after substituting the returned hash and path. The placeholders are the helper's hex digest and its fixed remote archive path.

```sh
set -eu
echo '<sha256>  <remote_archive>' | sha256sum -c
mkdir -p /workspace/dws
worktree=$(mktemp -d /workspace/dws/run-XXXXXXXX)
tar -xzf '<remote_archive>' -C "$worktree" --no-same-owner
printf '%s\n' "$worktree"
```

Run the preflight and confirm the versions match the component's manifests or CI. Run the intended command with `working_directory` set to the printed path plus the component directory. For the controller, use `./mvnw test` as the command and `background: false`. Confirm the returned `exit_code`. The Linux wrapper `./mvnw` is executable in the snapshot even when uploaded from Windows. If a verification command may outlive the tool timeout, write its logs and actual exit code to files inside the sandbox and poll those using MCP.

Read text reports with `opensandbox_exec.file_read`. For a requested binary, download to an explicit local artifact path with `osb file download` targeting the same lifecycle server. Collect artifacts before calling `sandbox_kill`.
