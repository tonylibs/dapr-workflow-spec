---
name: opensandbox-sandbox
description: Create, connect to, operate, inspect, and clean up Docker-backed OpenSandbox sandboxes through the project OpenSandbox MCP server, including custom images and SSH provisioning.
---

# OpenSandbox Docker Sandbox

Use the project MCP server configured in `.codex/config.toml`. Its lifecycle service uses `localhost:8080` and `agent-sandbox/opensandbox/docker.toml`. Keep the sandbox ID returned by `sandbox_create` or `sandbox_connect`; every later MCP operation needs it.

## Start and validate the lifecycle service

Docker Desktop or Docker Engine must be running. Start the lifecycle service before calling sandbox MCP tools:

```powershell
$env:OPENSANDBOX_INSECURE_SERVER = "YES"
uvx opensandbox-server --config agent-sandbox/opensandbox/docker.toml
```

Insecure mode is acceptable only for this profile while it remains bound to `127.0.0.1`. For a durable or exposed service, configure `OPENSANDBOX_SERVER_API_KEY` instead and make the same key available to the MCP process through `OPEN_SANDBOX_API_KEY`; set it before starting Codex so the MCP process inherits it. Never print either key.

For a background local server, use `Start-Process -WindowStyle Hidden`, redirect stdout and stderr to known log files, and retain the returned launcher PID. The spawned server can have a different PID; identify the process actually listening on port 8080 before stopping it. Stop only a lifecycle-service process created for the current task, and do not stop a shared server merely because one sandbox is finished.

Validate the service with `sandbox_list` before creating a sandbox. If MCP returns HTTP 404 for both `sandbox_list` and `sandbox_create`, diagnose the lifecycle endpoint and inspect the server log; that response occurs before image provisioning and is not evidence that the requested image is missing.

## Choose lifetime and resources

Choose a timeout that matches the task rather than copying the example mechanically:

- `900` seconds is suitable for a short smoke test.
- `3600` seconds is a reasonable starting point for an interactive agent session.
- The project profile permits at most `86400` seconds.

Use `sandbox_renew` before expiration if work is still active. Pass resource values as strings accepted by the provider, for example `{"cpu": "2", "memory": "4Gi"}`. A successful create request confirms that the provider accepted the request, not necessarily that every limit is enforced. Inspect `sandbox_get_info` and `sandbox_get_metrics`; treat metrics as observations because some providers report runtime or host capacity. When strict enforcement matters, verify the underlying container limits as well.

## Create and operate a sandbox

1. Choose an image available to the Docker runtime. For the DWS Agent Sandbox, use a published image built from `agent-sandbox/Dockerfile`, normally `ghcr.io/tonylibs/dws-agent-sandbox:latest` unless the user requests a pinned tag.
2. Call `sandbox_create` with the chosen image, timeout, and appropriate resource settings.
3. For a normal HTTP workload, use the default readiness check, then call `sandbox_connect` and run commands with `command_run`.
4. Verify the resulting state with `sandbox_get_info` before reporting success.

Example normal workload:

```json
{
  "image": "python:3.11",
  "timeout_seconds": 900,
  "resource": {
    "cpu": "1",
    "memory": "2Gi"
  }
}
```

Use MCP file tools (`file_read`, `file_write`, `file_search`, and directory tools) for sandbox files and `command_run` for sandbox processes. The desktop workspace is not mounted automatically. Inspect `/workspace` after connecting and clone or transfer project content only when the user's task requires it; do not copy credentials or unrelated desktop files.

## SSH-only agent sandboxes

An image whose entrypoint is only `sshd` does not provide the HTTP readiness signal expected by the default create flow. Create it with `skip_health_check: true`:

```json
{
  "image": "ghcr.io/tonylibs/dws-agent-sandbox:latest",
  "entrypoint": ["/usr/local/bin/sshd-start"],
  "timeout_seconds": 3600,
  "skip_health_check": true
}
```

Immediately call `sandbox_connect` with `skip_health_check: true`. Then:

1. Use only the desktop key `~/.ssh/dws_sandbox.pub`. Do not scan for, select, or fall back to any other public key, even if the user did not name one.
2. If `dws_sandbox.pub` is absent but the matching private key `~/.ssh/dws_sandbox` exists, recreate the public key from that private key with `ssh-keygen -y`; do not replace the private key. If neither file exists, create a new Ed25519 key pair with the basename `~/.ssh/dws_sandbox`, then use the generated `dws_sandbox.pub`. Never overwrite either matching file or expose the private key. If the two files are otherwise inconsistent, stop and report the condition instead of using another key.
3. Create `/root/.ssh`, write the public key to `/root/.ssh/authorized_keys`, and set ownership to `root:root`.
4. Use `command_run` to enforce mode `0700` on the directory and `0600` on the file, then verify that the file is non-empty.

Do not include public-key contents in user-facing output or diagnostic logs.

If an SSH-only `sandbox_create` call times out while waiting for readiness, do not immediately create another sandbox: the Docker container may already exist. Check `sandbox_list` or the lifecycle API, reuse the running sandbox ID, and connect with the health check skipped.

## Validate the SSH endpoint

`EXPOSE 22` does not guarantee raw TCP publication. Call `sandbox_get_endpoint` for port 22 and classify the result before reporting SSH as ready:

- A plain `host:port` is a candidate raw TCP endpoint. Confirm that the TCP connection succeeds and that `ssh-keyscan -T 5 -p <port> <host>` receives an SSH host-key response.
- An endpoint containing a URL path, such as `host:port/proxy/22`, is an HTTP-routed proxy endpoint, not a raw SSH address. Standard SSH clients cannot use the path. Do not claim that SSH is available merely because the host port accepts TCP connections.

If raw SSH is unavailable, continue operating through MCP and state the limitation. A localhost-only Docker TCP proxy may be used for local diagnosis when authorized, but it is not a deployment design. Production SSH requires explicit TCP port mapping or gateway support. Keep SSH key-only and never enable password authentication.

## Inspect and clean up

- Use `sandbox_get_info`, `sandbox_list`, and `sandbox_get_metrics` for lifecycle state and resource observations.
- Use `sandbox_healthcheck` only for workloads that expose the expected readiness behavior; do not use it to judge an SSH-only image.
- Use `sandbox_renew` when active work must outlive its current timeout.
- Call `sandbox_kill` when the sandbox work is complete. This terminates the associated container but not the lifecycle service.
- After a failed or timed-out create, list sandboxes before retrying to avoid duplicates.
