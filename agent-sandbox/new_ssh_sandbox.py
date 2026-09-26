"""Create a local Docker-backed OpenSandbox sandbox reachable by SSH."""

from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import time
from datetime import timedelta
from pathlib import Path
from typing import Any

from opensandbox.config import ConnectionConfigSync
from opensandbox.sync.sandbox import SandboxSync


SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG = SCRIPT_DIR / "ssh-sandbox.json"

# Host environment variables forwarded to the sandbox's agent CLIs when set. Must match
# ALLOWED_NAMES in agent-auth-setup.sh and PermitUserEnvironment in the Dockerfile.
AGENT_TOKEN_NAMES = (
    "ANTHROPIC_API_KEY",
    "CLAUDE_CODE_OAUTH_TOKEN",
    "OPENAI_API_KEY",
    "COPILOT_GITHUB_TOKEN",
    "GEMINI_API_KEY",
)


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def ensure_key_pair(private_key: Path, public_key: Path) -> None:
    private_key.parent.mkdir(parents=True, exist_ok=True)

    if not private_key.exists() and not public_key.exists():
        run(
            [
                "ssh-keygen",
                "-q",
                "-t",
                "ed25519",
                "-N",
                "",
                "-f",
                str(private_key),
                "-C",
                "dws-sandbox",
            ]
        )
    elif private_key.exists() and not public_key.exists():
        derived = run(["ssh-keygen", "-y", "-f", str(private_key)]).stdout.strip()
        if not derived:
            raise RuntimeError("Could not derive dws_sandbox.pub from its private key")
        public_key.write_text(f"{derived}\n", encoding="utf-8")
    elif not private_key.exists():
        raise RuntimeError("dws_sandbox.pub exists but its private key is missing")

    private_fingerprint = run(["ssh-keygen", "-lf", str(private_key)]).stdout.split()[1]
    public_fingerprint = run(["ssh-keygen", "-lf", str(public_key)]).stdout.split()[1]
    if private_fingerprint != public_fingerprint:
        raise RuntimeError("dws_sandbox and dws_sandbox.pub are inconsistent")


def docker_ip(container: str) -> str:
    result = run(
        [
            "docker",
            "inspect",
            "--format",
            "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
            container,
        ]
    )
    address = result.stdout.strip()
    if not address:
        raise RuntimeError(f"Could not determine the Docker IP for {container}")
    return address


def allocate_ssh_port(host: str) -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind((host, 0))
        return int(listener.getsockname()[1])


def configured_ssh_port(config: dict[str, Any]) -> int | None:
    value = config.get("ssh_port")
    if value in (None, "", "random", 0, "0"):
        return None
    return int(value)


def remove_stale_bridge(ssh_port: int) -> None:
    result = run(
        [
            "docker",
            "ps",
            "--filter",
            f"publish={ssh_port}/tcp",
            "--format",
            "{{.Names}}",
        ],
        check=False,
    )
    owner = result.stdout.strip().splitlines()[0] if result.stdout.strip() else ""
    if not owner:
        return
    if not owner.startswith("dws-ssh-forward-"):
        raise RuntimeError(f"SSH port {ssh_port} is already in use by {owner}")

    old_id = owner.removeprefix("dws-ssh-forward-")
    target = run(["docker", "inspect", f"sandbox-{old_id}"], check=False)
    if target.returncode == 0:
        raise RuntimeError(f"SSH port {ssh_port} is already in use by active bridge {owner}")
    run(["docker", "rm", "--force", owner])


def wait_for_ssh(host: str, port: int, private_key: Path) -> None:
    for _ in range(60):
        result = run(
            [
                "ssh",
                "-o",
                "BatchMode=yes",
                "-o",
                "ConnectTimeout=2",
                "-o",
                "StrictHostKeyChecking=no",
                "-o",
                "UserKnownHostsFile=NUL",
                "-i",
                str(private_key),
                "-p",
                str(port),
                f"root@{host}",
                "true",
            ],
            check=False,
        )
        if result.returncode == 0:
            return
        time.sleep(0.5)
    raise RuntimeError("The SSH bridge did not accept a key-only SSH connection")


def command_failure_details(command: Any) -> str:
    output = [
        message.text.rstrip()
        for message in [*command.logs.stdout, *command.logs.stderr]
        if message.text.strip()
    ]
    return "\n".join(output) if output else "The sandbox command returned no output."


def forward_agent_tokens(container: str) -> None:
    """Stream the host's agent tokens into the sandbox's agent-auth-setup over stdin.

    Using stdin keeps the tokens off command lines, `docker inspect`, and the
    OpenSandbox store.
    """
    lines = [
        f"{name}={value.strip()}"
        for name in AGENT_TOKEN_NAMES
        if (value := os.environ.get(name, "")).strip()
    ]
    if not lines:
        print(
            f"No agent tokens set ({', '.join(AGENT_TOKEN_NAMES)}); "
            "agent CLIs will need a manual login."
        )
        return

    result = subprocess.run(
        ["docker", "exec", "-i", container, "agent-auth-setup"],
        input="\n".join(lines) + "\n",
        capture_output=True,
        text=True,
        encoding="utf-8",
        check=False,
    )
    if result.returncode != 0:
        details = "\n".join(
            output.strip() for output in (result.stdout, result.stderr) if output.strip()
        ) or "Docker returned no output."
        for line in lines:
            details = details.replace(line.split("=", 1)[1], "[REDACTED]")
        if "agent-auth-setup" in details and (
            "not found" in details or "no such file" in details.lower()
        ):
            details += (
                "\nThe cached sandbox image does not include agent-auth-setup. "
                "Rerun with --pull-image to refresh it, or rebuild the configured image."
            )
        raise RuntimeError(
            "Could not configure agent CLI credentials in the sandbox "
            f"(exit code {result.returncode}):\n{details}"
        )
    print(result.stdout.strip())


def refresh_mutable_image(image: str) -> None:
    run(["docker", "pull", image])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--pull-image",
        action="store_true",
        help="Pull the configured image before creating the sandbox.",
    )
    parser.add_argument("config", nargs="?", type=Path, default=DEFAULT_CONFIG)
    arguments = parser.parse_args()

    config_path = arguments.config.resolve()
    config: dict[str, Any] = json.loads(config_path.read_text(encoding="utf-8"))
    home = Path(os.environ.get("USERPROFILE") or os.environ["HOME"])
    private_key = (home / config["private_key"]).resolve()
    public_key = (home / config["public_key"]).resolve()
    ensure_key_pair(private_key, public_key)

    fixed_ssh_port = configured_ssh_port(config)
    if fixed_ssh_port is not None:
        remove_stale_bridge(fixed_ssh_port)
    if arguments.pull_image:
        refresh_mutable_image(config["image"])
    sandbox: SandboxSync | None = None
    bridge_name: str | None = None
    try:
        connection = ConnectionConfigSync(
            domain=config["server_domain"],
            protocol=config["server_protocol"],
        )
        timeout_seconds = config.get("timeout_seconds")
        timeout = (
            None
            if timeout_seconds is None
            else timedelta(seconds=int(timeout_seconds))
        )
        sandbox = SandboxSync.create(
            config["image"],
            connection_config=connection,
            entrypoint=list(config["entrypoint"]),
            timeout=timeout,
            resource={"cpu": config["cpu"], "memory": config["memory"]},
            extensions=config.get("extensions"),
            # File and command APIs require execd to be ready before provisioning.
            skip_health_check=False,
        )
        container = f"sandbox-{sandbox.id}"

        sandbox.commands.run("mkdir -p /root/.ssh && chmod 700 /root/.ssh")
        sandbox.files.write_file(
            "/root/.ssh/authorized_keys",
            public_key.read_text(encoding="utf-8"),
            encoding="utf-8",
            # OpenSandbox's file API expects the mode as decimal digits,
            # unlike Python's filesystem APIs which commonly use 0o600.
            mode=600,
            owner="root",
            group="root",
        )
        verification = sandbox.commands.run(
            "chmod 700 /root/.ssh && chmod 600 /root/.ssh/authorized_keys "
            "&& test -s /root/.ssh/authorized_keys "
            "&& test -f /root/.kube-local/config "
            "&& kubectl get nodes -o name --request-timeout=15s"
        )
        if verification.exit_code != 0:
            raise RuntimeError(
                "Sandbox key or kubectl verification failed "
                f"(exit code {verification.exit_code}):\n"
                f"{command_failure_details(verification)}"
            )
        forward_agent_tokens(container)

        ssh_port = fixed_ssh_port or allocate_ssh_port(config["ssh_host"])
        bridge_name = f"dws-ssh-forward-{sandbox.id}"
        run(
            [
                "docker",
                "run",
                "--detach",
                "--name",
                bridge_name,
                "--publish",
                f"{config['ssh_host']}:{ssh_port}:{config['bridge_port']}",
                "--network",
                config["docker_network"],
                config["bridge_image"],
                f"TCP-LISTEN:{config['bridge_port']},fork,reuseaddr",
                f"TCP:{docker_ip(container)}:22",
            ]
        )
        wait_for_ssh(config["ssh_host"], ssh_port, private_key)

        print(f"Sandbox created: {sandbox.id}")
        print(f"SSH: {config['ssh_host']}:{ssh_port}")
        print(f"Identity: {private_key}")
        print("Orca username: root")
        return 0
    except Exception:
        if bridge_name:
            run(["docker", "rm", "--force", bridge_name], check=False)
        if sandbox is not None:
            sandbox.kill()
        raise
    finally:
        if sandbox is not None:
            sandbox.close()


if __name__ == "__main__":
    raise SystemExit(main())
