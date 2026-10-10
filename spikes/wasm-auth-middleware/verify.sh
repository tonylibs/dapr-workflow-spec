#!/usr/bin/env bash
# Live probe for ADR 0010 Decision 4a on Docker Desktop Kubernetes.
# Requires Docker, kubectl, and a Dapr injector/control plane. Uses only the namespace below.
set -euo pipefail

namespace=dws-wasm-auth-spike
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
image=docker.io/library/dws-wasm-auth-spike
archive="$(mktemp "${TMPDIR:-/tmp}/dws-wasm-auth-image.XXXXXX.tar")"

cleanup() {
  kubectl delete namespace "$namespace" --ignore-not-found --wait=false >/dev/null 2>&1 || true
  rm -f "$archive"
}
trap cleanup EXIT

for tool in docker kubectl; do command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }; done
kubectl cluster-info >/dev/null
docker info >/dev/null

kubectl delete namespace "$namespace" --ignore-not-found --wait=true >/dev/null
docker build -q -t "$image:spike" -f "$root/Dockerfile" "$root" >/dev/null
digest="$(docker image inspect "$image:spike" --format '{{.Id}}')"
[[ "$digest" == sha256:* ]] || { echo "could not resolve image digest" >&2; exit 1; }
docker save -o "$archive" "$image:spike"
for node in $(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'); do
  docker cp "$archive" "$node:/root/dws-wasm-auth-spike.tar"
  docker exec "$node" ctr -n k8s.io images import /root/dws-wasm-auth-spike.tar >/dev/null
  docker exec "$node" ctr -n k8s.io images tag "$image:spike" "$image@$digest" >/dev/null
  docker exec "$node" rm /root/dws-wasm-auth-spike.tar
done

kubectl create namespace "$namespace"
bearer="spike-bearer-$(date +%s)"
username=spike-user
password=spike-password
basic="Basic $(printf '%s' "$username:$password" | base64 | tr -d '\n')"
kubectl create secret generic bearer-auth -n "$namespace" \
  --from-literal="guestConfig={\"scheme\":\"bearer\",\"endpoint\":\"bearer-target\",\"path\":\"/intended\",\"token\":\"$bearer\"}" >/dev/null
kubectl create secret generic basic-auth -n "$namespace" \
  --from-literal="guestConfig={\"scheme\":\"basic\",\"endpoint\":\"basic-target\",\"path\":\"/intended\",\"username\":\"$username\",\"password\":\"$password\"}" >/dev/null

sed -E "s|${image}@sha256:[0-9a-f]{64}|${image}@${digest}|g" "$root/resources.yaml" |
  kubectl apply -n "$namespace" -f - >/dev/null
kubectl rollout status -n "$namespace" deployment/wasm-auth-echo --timeout=3m >/dev/null
kubectl rollout status -n "$namespace" deployment/wasm-auth-caller --timeout=3m >/dev/null
pod="$(kubectl get pods -n "$namespace" -l app=wasm-auth-caller \
  --sort-by=.metadata.creationTimestamp -o name | tail -1)"
actual_image="$(kubectl get "$pod" -n "$namespace" -o jsonpath='{.spec.containers[?(@.name=="daprd")].image}')"
[[ "$actual_image" == ghcr.io/dapr/daprd:1.18.1 ]] || {
  echo "expected daprd 1.18.1, got $actual_image" >&2; exit 1;
}

invoke() {
  kubectl exec -n "$namespace" "$pod" -c caller -- \
    curl --fail --silent --show-error --max-time 15 "http://127.0.0.1:3500/v1.0/invoke/$1/method/$2"
}
assert_eq() {
  [[ "$1" == "$2" ]] || { echo "expected '$2', got '$1'" >&2; exit 1; }
}

# Dapr discovers endpoint resources asynchronously; require the protected request to succeed.
for attempt in $(seq 1 30); do
  observed="$(invoke bearer-target intended 2>/dev/null || true)"
  [[ "$observed" == "Bearer $bearer" ]] && break
  sleep 2
done
assert_eq "$observed" "Bearer $bearer"
assert_eq "$(invoke basic-target intended)" "$basic"
assert_eq "$(invoke bearer-target unrelated)" '<none>'
assert_eq "$(invoke unrelated-target intended)" '<none>'
assert_eq "$(kubectl exec -n "$namespace" "$pod" -c caller -- curl --fail --silent --show-error \
  -H 'Authorization: Bearer caller-supplied' \
  http://127.0.0.1:3500/v1.0/invoke/bearer-target/method/intended)" "Bearer $bearer"

if kubectl logs -n "$namespace" "$pod" -c daprd | grep -Fq "$bearer"; then
  echo "secret token appeared in default-level daprd logs" >&2
  exit 1
fi

echo "PASS: Bearer and Basic reached the echo target; endpoint/path isolation and overwrite passed"
echo "Dapr image: $actual_image; guest image: $image@$digest"
echo "Guest size: $(docker run --rm --entrypoint wc "$image:spike" -c /auth.wasm) bytes"
