#!/bin/sh
set -eu

repository_url=${DWS_REPOSITORY_URL:-https://github.com/tonylibs/dapr-workflow-spec.git}
repository_dir=${DWS_REPOSITORY_DIR:-/workspace}

# Docker Desktop exposes the host through host.docker.internal. The
# Kubernetes API server certificate trusts "kubernetes" (and not that Docker
# hostname), so use the host-gateway IPv4 address behind a certificate-valid
# alias, matching .devcontainer/post-start.sh.
kube_host_alias=${DWS_KUBE_HOST_ALIAS:-kubernetes}
host_gateway_ipv4=$(getent ahostsv4 host.docker.internal 2>/dev/null | awk '{print $1; exit}' || true)
if [ -z "$host_gateway_ipv4" ]; then
    host_gateway_ipv4=$(getent ahostsv4 kubernetes.docker.internal 2>/dev/null | awk '{print $1; exit}' || true)
fi

if [ -n "$host_gateway_ipv4" ]; then
    # Read the current entries fully before rewriting: piping /etc/hosts into `tee /etc/hosts`
    # races, and tee's truncation usually wins, dropping localhost. Write in place rather than
    # replacing the file, since Docker bind-mounts /etc/hosts.
    other_hosts=$(grep -v "[[:space:]]${kube_host_alias}$" /etc/hosts || true)
    printf '%s\n%s %s\n' "$other_hosts" "$host_gateway_ipv4" "$kube_host_alias" > /etc/hosts
else
    echo "WARN: could not resolve a Docker Desktop host-gateway IPv4 address; kubectl setup skipped." >&2
fi

kubeconfig_source=${DWS_KUBECONFIG_SOURCE:-/root/.kube/config}
kubeconfig_local=${DWS_KUBECONFIG_LOCAL:-/root/.kube-local/config}
if [ -f "$kubeconfig_source" ] && [ -n "$host_gateway_ipv4" ]; then
    mkdir -p "$(dirname "$kubeconfig_local")"
    sed "s/127\.0\.0\.1/${kube_host_alias}/g; s/localhost/${kube_host_alias}/g" \
        "$kubeconfig_source" > "$kubeconfig_local"
    chmod 600 "$kubeconfig_local"
    export KUBECONFIG="$kubeconfig_local"
    echo "kubectl: using $kubeconfig_local via $kube_host_alias ($host_gateway_ipv4)"
else
    echo "WARN: $kubeconfig_source is not available; kubectl will not be configured for the host cluster." >&2
fi

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
