---
name: opensandbox-exec
description: Use when an agent needs to run a build, test, linter, package install, script, or application from a local worktree through the dedicated OpenSandbox code execution service.
---

# Execute code through OpenSandbox

Read the repository and component instructions to identify the command and all its source directories. Run project code through the **code execution** MCP server, `opensandbox_exec`. Host commands are for inspection, editing, trusted lifecycle setup, image preparation, and file transfer.

In DWS, use the profiles and helpers in [code-sandbox](../../../code-sandbox). Keep these sandboxes separate from interactive agent environments. For tool arguments and workspace transfer, read [the execution reference](../../../code-sandbox/execution.md).

1. Run `py -3 code-sandbox/ensure-server.py` on the host to verify or start the dedicated server, then call `opensandbox_exec.sandbox_list`. If the tool is missing, register it from [mcp.toml](../../../code-sandbox/mcp.toml) in the agent's MCP configuration and reload the session. If readiness or configuration remains blocked, report the blocker and leave project execution pending.
2. Select the profile matching the component and its CI toolchain. Prepare its image if it has a local Dockerfile target. Create a sandbox with the profile defaults and task metadata. Save its ID. After a create timeout, look up that task's metadata before retrying.
3. Transfer the relevant directories from the current worktree, including any untracked files the command needs. Extract into a fresh sandbox directory for each new snapshot, verify the archive hash, and run the profile preflight from the component directory.
4. Call `opensandbox_exec.command_run` with the sandbox ID, component working directory, and the command. Inspect the logs and exit code. Missing completion evidence means the result is unverified. Keep the sandbox for related calls and refresh the snapshot after host edits.
5. Collect any requested artifacts and call `opensandbox_exec.sandbox_kill` for task-owned sandboxes when finished. Keep the shared lifecycle server available.

For commands that need a cluster, a container daemon, or a different toolchain, check for an explicitly configured execution environment. If it is unavailable, report the missing dependency and run any independent checks the existing profile supports.
