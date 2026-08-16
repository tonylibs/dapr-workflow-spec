#!/usr/bin/env bash
set -euo pipefail

# Re-point kubectl at the local Kubernetes cluster on every container start (not just at build
# time via post-create.sh), because both the cluster's exposed port and the host-gateway IP can
# change across Docker Desktop restarts / cluster recreations.
#
# Why this is needed (context for future debugging):
#   - ~/.kube/config is bind-mounted read/write from the host's real kubeconfig (see the
#     "mounts" entry in devcontainer.json), so its content is always in sync -- but its server
#     address is https://127.0.0.1:<port>, and 127.0.0.1 inside this container is the
#     container's OWN loopback, not the host's. Connecting to it here always fails with
#     "connection refused".
#   - The cluster (kind, judging by its default cert) issues its TLS cert only for:
#     desktop-control-plane, kubernetes, kubernetes.default(.svc(.cluster.local)), localhost --
#     NOT kubernetes.docker.internal -- so connecting via that hostname fails cert verification
#     ("x509: certificate is valid for ... not kubernetes.docker.internal").
#   - kubernetes.docker.internal (added via --add-host=...:host-gateway in devcontainer.json)
#     can resolve to an IPv6 host-gateway address in this environment, which isn't routable
#     from inside the container ("network is unreachable").
#
# Fix: resolve the host-gateway IPv4 address ourselves, alias it to the hostname "kubernetes"
# (a name the cluster's cert actually trusts) in /etc/hosts, and write a container-local
# kubeconfig copy (~/.kube-local/config, NOT the bind-mounted ~/.kube/config -- editing that in
# place would also corrupt kubectl on the host) that points at "kubernetes" instead of
# 127.0.0.1. devcontainer.json's containerEnv.KUBECONFIG points at ~/.kube-local/config.

KUBE_HOST_ALIAS="kubernetes"

HOST_GATEWAY_IPV4="$(getent ahostsv4 kubernetes.docker.internal 2>/dev/null | awk '{print $1; exit}' || true)"

if [ -z "${HOST_GATEWAY_IPV4}" ]; then
  echo "WARN: couldn't resolve kubernetes.docker.internal (host-gateway IPv4) -- skipping kubectl host alias setup" >&2
  exit 0
fi

# Replace any previous entry for this alias (its IP can change across restarts) then add the
# current one. NOTE: must NOT use an in-place editor here -- /etc/hosts is bind-mounted into
# the container as its own mount point, and tools that write-temp-then-rename (like `sed -i`)
# fail across a bind-mount boundary ("Device or resource busy"). `tee` truncates-and-writes
# the existing file in place instead, which bind mounts do support.
{ grep -v "[[:space:]]${KUBE_HOST_ALIAS}\$" /etc/hosts || true; echo "${HOST_GATEWAY_IPV4} ${KUBE_HOST_ALIAS}"; } | sudo tee /etc/hosts >/dev/null

if [ -f "${HOME}/.kube/config" ]; then
  mkdir -p "${HOME}/.kube-local"
  sed "s/127\.0\.0\.1/${KUBE_HOST_ALIAS}/" "${HOME}/.kube/config" > "${HOME}/.kube-local/config"
  echo "kubectl: ~/.kube-local/config now points at https://${KUBE_HOST_ALIAS}:<port> (host-gateway ${HOST_GATEWAY_IPV4})"
else
  echo "WARN: ${HOME}/.kube/config not found (host .kube mount empty?) -- skipping kubectl host alias setup" >&2
fi
