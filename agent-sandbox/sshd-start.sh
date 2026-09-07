#!/bin/sh
set -eu

repository_url=${DWS_REPOSITORY_URL:-https://github.com/tonylibs/dapr-workflow-spec.git}
repository_dir=${DWS_REPOSITORY_DIR:-/workspace}

if [ -e "$repository_dir/.git" ]; then
    existing_origin=$(git -C "$repository_dir" remote get-url origin 2>/dev/null || true)
    if [ "$existing_origin" != "$repository_url" ]; then
        echo "Refusing to reuse $repository_dir: origin is '$existing_origin', expected '$repository_url'." >&2
        exit 1
    fi
    echo "Repository already exists at $repository_dir; leaving it unchanged."
else
    if [ -e "$repository_dir" ] && [ ! -d "$repository_dir" ]; then
        echo "Refusing to clone: $repository_dir exists and is not a directory." >&2
        exit 1
    fi

    mkdir -p "$repository_dir"
    if [ -n "$(find "$repository_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
        echo "Refusing to clone: $repository_dir is not empty and is not the expected repository." >&2
        exit 1
    fi

    git clone "$repository_url" "$repository_dir"
fi

# Host keys must be created per container; they are intentionally absent from the image.
mkdir -p /run/sshd
ssh-keygen -A

# Public keys must be supplied at runtime in /root/.ssh/authorized_keys.
exec /usr/sbin/sshd -D -e
