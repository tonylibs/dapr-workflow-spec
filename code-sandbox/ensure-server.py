"""Start or reuse the dedicated OpenSandbox lifecycle server."""

from contextlib import contextmanager
import errno
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time
from urllib.request import ProxyHandler, build_opener


ROOT = Path(__file__).resolve().parent
HOST = "127.0.0.1"
PORT = 8090
STATE = ROOT / ".local"
ENDPOINT = f"http://{HOST}:{PORT}/v1/sandboxes?pageSize=1"


@contextmanager
def startup_lock():
    if os.name == "nt":
        import msvcrt
    else:
        import fcntl

    with (STATE / "server.lock").open("a+b") as lock:
        deadline = time.monotonic() + 55
        while True:
            try:
                if os.name == "nt":
                    lock.seek(0)
                    msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except (BlockingIOError, PermissionError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("Another OpenSandbox startup is still in progress.") from None
                time.sleep(0.2)
        # Closing the file releases the OS lock, including after an interrupted startup.
        yield


def process_start(pid: int) -> int | str | None:
    """Identify a running process across launcher invocations, even if PIDs are reused."""
    if os.name != "nt":
        result = subprocess.run(
            ["ps", "-p", str(pid), "-o", "lstart="],
            capture_output=True,
            text=True,
            check=False,
        )
        return result.stdout.strip() or None

    import ctypes
    from ctypes import wintypes

    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
    kernel.WaitForSingleObject.argtypes = (wintypes.HANDLE, wintypes.DWORD)
    kernel.WaitForSingleObject.restype = wintypes.DWORD
    kernel.GetProcessTimes.argtypes = (wintypes.HANDLE,) + (ctypes.POINTER(wintypes.FILETIME),) * 4
    handle = kernel.OpenProcess(0x101000, False, pid)  # SYNCHRONIZE | QUERY_LIMITED_INFORMATION
    if not handle:
        return None
    try:
        times = [wintypes.FILETIME() for _ in range(4)]
        if kernel.WaitForSingleObject(handle, 0) != 258 or not kernel.GetProcessTimes(handle, *times):
            return None
        created = times[0]
        filetime = (created.dwHighDateTime << 32) | created.dwLowDateTime
        return filetime + 504911232000000000  # Convert to the previous launcher's .NET ticks.
    finally:
        kernel.CloseHandle(handle)


def listener_exists() -> bool:
    with socket.socket() as probe:
        option = socket.SO_EXCLUSIVEADDRUSE if os.name == "nt" else socket.SO_REUSEADDR
        probe.setsockopt(socket.SOL_SOCKET, option, 1)
        try:
            probe.bind((HOST, PORT))
        except OSError as error:
            if error.errno == errno.EADDRINUSE:
                return True
            raise
    return False


def wait_until_ready(pid: int, started: int | str) -> None:
    opener = build_opener(ProxyHandler({}))
    deadline = time.monotonic() + 40
    while process_start(pid) == started:
        try:
            with opener.open(ENDPOINT, timeout=2) as response:
                result = json.load(response)
            if (
                isinstance(result, dict)
                and isinstance(result.get("items"), list)
                and isinstance(result.get("pagination"), dict)
            ):
                print(f"Code execution lifecycle endpoint is ready at {HOST}:{PORT}.")
                return
        except (OSError, ValueError):
            pass
        if time.monotonic() >= deadline:
            raise RuntimeError(f"OpenSandbox startup timed out (launcher PID {pid}). See {STATE / 'server.err.log'}.")
        time.sleep(0.5)
    raise RuntimeError(f"OpenSandbox launcher exited. See {STATE / 'server.err.log'}.")


def engine_id(*docker_options: str) -> str:
    env = os.environ.copy()
    env.pop("DOCKER_CONTEXT", None)
    result = subprocess.run(
        ["docker", *docker_options, "info", "--format", "{{.ID}}"],
        capture_output=True,
        text=True,
        check=False,
        timeout=15,
        env=env,
    )
    return result.stdout.strip() if result.returncode == 0 else ""


def ensure_server(docker_host: str) -> None:
    if not docker_host:
        raise RuntimeError("Set DWS_CODE_EXEC_DOCKER_HOST to a dedicated Docker daemon for code execution.")
    if not re.fullmatch(r"tcp://(?:127\.0\.0\.1|localhost):[0-9]+", docker_host, flags=re.IGNORECASE):
        raise RuntimeError("Use a loopback TCP endpoint for the dedicated Docker daemon.")

    STATE.mkdir(exist_ok=True)
    marker = STATE / "server.json"
    with startup_lock():
        if marker.is_file():
            previous = json.loads(marker.read_text(encoding="utf-8"))
            pid = previous["pid"]
            started = process_start(pid)
            if started is not None and started == previous["started"]:
                if previous["dockerHost"] != docker_host:
                    raise RuntimeError("The code execution server is already running with a different Docker endpoint.")
                wait_until_ready(pid, started)
                return

        if listener_exists():
            raise RuntimeError(f"Port {PORT} is occupied by a listener this execution profile cannot verify.")

        dedicated = engine_id("--host", docker_host)
        if not dedicated:
            raise RuntimeError("The dedicated Docker daemon is unavailable.")
        if dedicated in {engine_id("--context", "desktop-linux"), engine_id("--context", "default")}:
            raise RuntimeError("The execution Docker endpoint reaches the shared Docker daemon. Use an independent daemon.")

        server = shutil.which("opensandbox-server")
        if server:
            command = [server]
        else:
            uvx = shutil.which("uvx")
            if not uvx:
                raise RuntimeError("Install opensandbox-server or provide the cached server package through uvx.")
            command = [uvx, "--offline", "opensandbox-server==0.2.3"]
        command.extend(["--config", str(ROOT / "opensandbox.toml")])

        env = os.environ.copy()
        env.pop("DOCKER_CONTEXT", None)
        env.pop("OPENSANDBOX_SERVER_API_KEY", None)
        env.update(DOCKER_HOST=docker_host, OPENSANDBOX_INSECURE_SERVER="YES")
        detach = (
            {"creationflags": subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP}
            if os.name == "nt"
            else {"start_new_session": True}
        )
        with (STATE / "server.log").open("wb") as output, (STATE / "server.err.log").open("wb") as errors:
            process = subprocess.Popen(
                command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                stdout=output, stderr=errors, **detach,
            )
        started = process_start(process.pid)
        if started is None:
            raise RuntimeError(f"OpenSandbox launcher exited. See {STATE / 'server.err.log'}.")
        pending = marker.with_suffix(".tmp")
        record = {"pid": process.pid, "started": started, "dockerHost": docker_host}
        pending.write_text(json.dumps(record), encoding="utf-8")
        pending.replace(marker)
        wait_until_ready(process.pid, started)


if __name__ == "__main__":
    try:
        ensure_server(os.environ.get("DWS_CODE_EXEC_DOCKER_HOST", ""))
    except (OSError, RuntimeError, ValueError, KeyError, subprocess.SubprocessError) as error:
        sys.exit(str(error))
