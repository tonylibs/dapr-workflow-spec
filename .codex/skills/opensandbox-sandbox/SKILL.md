---
name: opensandbox-sandbox
description: Create, connect to, operate, and clean up Docker-backed OpenSandbox sandboxes through the project OpenSandbox MCP server. Use when a task needs sandbox_create, sandbox lifecycle management, command or file operations in a sandbox, custom sandbox images, or SSH provisioning.
---

# OpenSandbox Docker Sandbox

Use the project MCP server configured in `.codex/config.toml`. Its OpenSandbox service must be reachable at `localhost:8080`; the Docker runtime profile is `agent-sandbox/opensandbox/docker.toml`.

## Create a sandbox

1. Choose an image that is already available to the Docker runtime. For the Agent Sandbox image, use a published image built from `agent-sandbox/Dockerfile`.
2. Call `sandbox_create` with the image and the required lifetime and resource settings.
3. For a normal HTTP workload, allow the default readiness check, then use `sandbox_connect` and run commands with `command_run`.
4. Keep the returned sandbox ID. Use it for every follow-up MCP action and finish with `sandbox_kill`.

Example shape for a normal sandbox:

```json
{
  "image": "ghcr.io/<owner>/<image>:<tag>",
  "timeout_seconds": 900
}
```

Use the MCP file tools (`file_read`, `file_write`, `file_search`, and directory tools) for files and `command_run` for processes. Do not assume the desktop filesystem is mounted in the sandbox.

## SSH-only sandbox images

An image whose entrypoint is only `sshd` does not provide the HTTP-style readiness signal expected by the default MCP create flow. Create it with `skip_health_check: true`:

```json
{
  "image": "<image-built-from-agent-sandbox-Dockerfile>",
  "entrypoint": ["/usr/local/bin/sshd-start"],
  "timeout_seconds": 900,
  "skip_health_check": true
}
```

Immediately follow with `sandbox_connect`, also using `skip_health_check: true`. Then create `/root/.ssh`, write the intended public key to `/root/.ssh/authorized_keys`, and use `command_run` to set directory mode `0700` and key-file mode `0600`.

If an SSH-only `sandbox_create` call times out while waiting for readiness, do **not** immediately issue another create request: the Docker container may already exist. Check `sandbox_list` or the OpenSandbox lifecycle API, reuse the running sandbox ID, and connect with the health check skipped. This avoids unintentionally creating a duplicate sandbox.

## SSH endpoint caveat

`EXPOSE 22` in an image does not by itself guarantee that the OpenSandbox Docker provider publishes port 22. Call `sandbox_get_endpoint` for port 22 and verify that it returns a reachable TCP endpoint before attempting SSH from the desktop.

For local diagnosis, a localhost-only Docker TCP proxy can bridge to the container's port 22. Treat that as a test workaround, not a deployment design. Production remote SSH requires explicit TCP port-mapping or gateway support from the OpenSandbox provider. Keep SSH key-only, do not enable password authentication, and remove the sandbox when finished.

## Inspect and clean up

- Use `sandbox_get_info`, `sandbox_list`, and `sandbox_get_metrics` to inspect lifecycle state and resources.
- Use `sandbox_healthcheck` only for workloads that expose the expected readiness behavior.
- Use `sandbox_renew` when the sandbox must outlive its current timeout.
- Call `sandbox_kill` when the work is complete; this stops the associated Docker container.
