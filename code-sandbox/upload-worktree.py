"""Upload selected working-tree files to the dedicated local OpenSandbox service."""

import argparse
from contextlib import contextmanager
import fnmatch
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import stat
import subprocess
import tarfile
import uuid


EXCLUDED_DIRS = {
    ".git", ".aws", ".ssh", ".kube", ".gnupg", ".codex", ".local", ".venv", ".next",
    "__pycache__", "node_modules", "target", "dist", "coverage", "obj",
}
EXCLUDED_NAMES = {
    ".env", ".envrc", ".netrc", ".npmrc", ".pypirc", "kubeconfig",
    "id_rsa", "id_ed25519", "auth.json", "credentials.json",
}
EXCLUDED_PATTERNS = ("*.key", "*.p12", "*.pfx", "*.jks", "*.kubeconfig")
OSB = (
    "osb", "--domain", "127.0.0.1:8090", "--protocol", "http",
    "--no-use-server-proxy", "--request-timeout", "120",
)


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def relative_path(value):
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "\\" in value or ":" in value:
        raise ValueError(f"Expected a repository-relative path: {value!r}")
    return path


def excluded(path):
    parts = tuple(part.lower() for part in path.parts)
    name = parts[-1]
    return (
        any(part in EXCLUDED_DIRS for part in parts)
        or name in EXCLUDED_NAMES
        or (name.startswith(".env.") and name not in {".env.example", ".env.sample"})
        or any(fnmatch.fnmatchcase(name, pattern) for pattern in EXCLUDED_PATTERNS)
    )


def selected_files(root, scopes, untracked):
    selected = {}
    for record in git(root, "ls-files", "--cached", "--stage", "-z").split(b"\0"):
        if not record:
            continue
        header, raw_path = record.split(b"\t", 1)
        mode, _, stage = header.split()
        path = relative_path(os.fsdecode(raw_path))
        if excluded(path) or not any(path.is_relative_to(scope) for scope in scopes):
            continue
        if stage != b"0" or mode not in (b"100644", b"100755"):
            raise ValueError(f"Cannot snapshot a conflicted file, link, or submodule: {path}")
        selected[path] = mode == b"100755"

    if untracked:
        candidates = {
            relative_path(os.fsdecode(name))
            for name in git(root, "ls-files", "--others", "--exclude-standard", "-z").split(b"\0")
            if name
        }
        for path in untracked:
            if path not in candidates or excluded(path) or not any(path.is_relative_to(s) for s in scopes):
                raise ValueError(f"Untracked path is ignored, excluded, or outside the requested scopes: {path}")
            selected[path] = None
    return selected


def read_file(root, path):
    current = root
    for part in path.parts:
        current /= part
        try:
            info = current.lstat()
        except FileNotFoundError:
            return None  # A tracked file deleted from the current worktree.
        attributes = getattr(info, "st_file_attributes", 0)
        if stat.S_ISLNK(info.st_mode) or attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise ValueError(f"Cannot snapshot a link or reparse point: {path}")
    if not stat.S_ISREG(info.st_mode):
        raise ValueError(f"Cannot snapshot a non-regular file: {path}")
    return current.read_bytes(), info.st_mode


def snapshot(root, scopes, untracked, destination):
    files = selected_files(root, scopes, untracked)
    count = 0
    with (
        destination.open("wb") as raw,
        gzip.GzipFile(filename="", fileobj=raw, mode="wb", mtime=0) as compressed,
        tarfile.open(fileobj=compressed, mode="w|") as archive,
    ):
        for path, executable in sorted(files.items()):
            file = read_file(root, path)
            if file is None:
                continue
            data, file_mode = file
            shell_script = path.name == "mvnw" or path.suffix == ".sh"
            if shell_script:
                data = data.replace(b"\r\n", b"\n")
            if executable is None:
                executable = bool(file_mode & stat.S_IXUSR) or data.startswith(b"#!")
            entry = tarfile.TarInfo(path.as_posix())
            entry.size = len(data)
            entry.mode = 0o755 if executable else 0o644
            archive.addfile(entry, io.BytesIO(data))
            count += 1
    if not count:
        raise ValueError("The requested scopes contain no transferable files.")
    with destination.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest(), count


@contextmanager
def transfer_files():
    state_dir = Path(__file__).resolve().parent / ".local"
    state_dir.mkdir(exist_ok=True)
    work_dir = state_dir / f"snapshot-{uuid.uuid4().hex}"
    # On Windows inherit the workspace ACL so the osb child process can read the archive.
    work_dir.mkdir(mode=0o777 if os.name == "nt" else 0o700)
    archive = work_dir / "source.tar.gz"
    config = work_dir / "osb.toml"
    try:
        yield archive, config
    finally:
        archive.unlink(missing_ok=True)
        config.unlink(missing_ok=True)
        work_dir.rmdir()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sandbox-id", required=True)
    parser.add_argument("--scope", action="append", required=True)
    parser.add_argument("--include-untracked", action="append", default=[])
    args = parser.parse_args()

    try:
        root = Path(os.fsdecode(git(Path.cwd(), "rev-parse", "--show-toplevel")).rstrip("\r\n"))
        scopes = [relative_path(value) for value in args.scope]
        untracked = [relative_path(value) for value in args.include_untracked]
        with transfer_files() as (archive, transfer_config):
            transfer_config.write_text("", encoding="utf-8")
            digest, count = snapshot(root, scopes, untracked, archive)
            remote = f"/tmp/dws-source-{digest}.tar.gz"
            env = os.environ.copy()
            env.pop("OPEN_SANDBOX_API_KEY", None)
            subprocess.run(
                [*OSB, "--config", str(transfer_config), "file", "upload", "--",
                 args.sandbox_id, str(archive), remote],
                env=env, check=True, stdout=subprocess.DEVNULL,
            )
        print(json.dumps({"sha256": digest, "remote_archive": remote, "files": count}))
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Workspace upload failed: {error}\n")


if __name__ == "__main__":
    main()
