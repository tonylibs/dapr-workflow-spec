#!/usr/bin/env bash
# Live probe for the dws-mid-bearer image (OWS 4.1c-bearer), derived from
# spikes/wasm-auth-middleware/verify.sh. Run against the current kubectl context;
# the cluster needs Dapr 1.18 (injector, operator, sentry). Uses one namespace.
#
#   IMAGE=ghcr.io/tonylibs/dws-mid-bearer@sha256:...  ./verify.sh   # a pushed image
#   ./verify.sh                                                      # build ../ locally first
#
# Other knobs: NAMESPACE, DAPRD_IMAGE (default ghcr.io/dapr/daprd:1.18.1).
set -euo pipefail

namespace="${NAMESPACE:-dws-mid-bearer-probe}"
daprd_image="${DAPRD_IMAGE:-ghcr.io/dapr/daprd:1.18.1}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/dws-mid-bearer-probe.XXXXXX")"

cleanup() {
  kubectl delete namespace "$namespace" --ignore-not-found --wait=false >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT

for tool in kubectl; do command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }; done
kubectl cluster-info >/dev/null

if [[ -n "${IMAGE:-}" ]]; then
  guest_image="$IMAGE"
  pull_policy=IfNotPresent
else
  command -v docker >/dev/null || { echo "docker is required to build the image (or set IMAGE)" >&2; exit 1; }
  local_tag=dws-mid-bearer:probe
  docker build -q -t "$local_tag" "$root/.." >/dev/null
  guest_image="$local_tag"
  pull_policy=Never
  context="$(kubectl config current-context)"
  if [[ "$context" == kind-* ]]; then
    kind load docker-image "$local_tag" --name "${context#kind-}" >/dev/null
  else
    docker save -o "$tmp/image.tar" "$local_tag"
    for node in $(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'); do
      docker cp "$tmp/image.tar" "$node:/root/dws-mid-bearer.tar"
      docker exec "$node" ctr -n k8s.io images import /root/dws-mid-bearer.tar >/dev/null
      docker exec "$node" rm /root/dws-mid-bearer.tar
    done
  fi
fi

kubectl delete namespace "$namespace" --ignore-not-found --wait=true >/dev/null
kubectl create namespace "$namespace" >/dev/null

token="probe-token-$(date +%s)-$RANDOM"
# printf, not echo: a trailing newline in the token is rejected by the guest.
kubectl create secret generic bearer-guest-config -n "$namespace" \
  --from-literal="guestConfig=$(printf '{"endpoint":"bearer-target","token":"%s"}' "$token")" >/dev/null
kubectl create secret generic empty-guest-config -n "$namespace" \
  --from-literal='guestConfig={"endpoint":"bearer-target","token":""}' >/dev/null

sed -e "s|@NAMESPACE@|$namespace|g" -e "s|@DAPRD_IMAGE@|$daprd_image|g" \
    -e "s|@GUEST_IMAGE@|$guest_image|g" -e "s|@PULL_POLICY@|$pull_policy|g" "$root/resources.yaml" |
  kubectl apply -n "$namespace" -f - >/dev/null

kubectl rollout status -n "$namespace" deployment/echo --timeout=3m >/dev/null
kubectl rollout status -n "$namespace" deployment/caller --timeout=3m >/dev/null
pod="$(kubectl get pods -n "$namespace" -l app=caller --sort-by=.metadata.creationTimestamp -o name | tail -1)"
actual_daprd="$(kubectl get "$pod" -n "$namespace" -o jsonpath='{.spec.containers[?(@.name=="daprd")].image}')"
[[ "$actual_daprd" == "$daprd_image" ]] || { echo "expected daprd $daprd_image, got $actual_daprd" >&2; exit 1; }

invoke() { # endpoint path [extra curl args...]
  local endpoint="$1" path="$2"; shift 2
  kubectl exec -n "$namespace" "$pod" -c caller -- \
    curl --fail --silent --show-error --max-time 15 "$@" \
    "http://127.0.0.1:3500/v1.0/invoke/$endpoint/method/$path"
}
assert_eq() {
  [[ "$1" == "$2" ]] || { echo "FAIL: $3: expected '$2', got '$1'" >&2; exit 1; }
}

# Dapr discovers endpoint resources asynchronously; wait for the protected call.
observed=
for _ in $(seq 1 30); do
  observed="$(invoke bearer-target intended 2>/dev/null || true)"
  [[ "$observed" == "Bearer $token" ]] && break
  sleep 2
done
assert_eq "$observed" "Bearer $token" "bearer header at the target"
assert_eq "$(invoke bearer-target other/path?x=1)" "Bearer $token" "any path under the configured endpoint, with a query"
assert_eq "$(invoke bearer-target intended -H 'Authorization: Bearer caller-supplied')" "Bearer $token" "caller-supplied value replaced by exactly one header"
assert_eq "$(invoke other-target intended)" '<none>' "second endpoint from the same pod gets no credential"
assert_eq "$(invoke other-target intended -H 'Authorization: Basic eDp5')" 'Basic eDp5' "foreign Authorization passes through untouched"

# A request the guest must ignore entirely (not an invocation path) still works.
assert_eq "$(kubectl exec -n "$namespace" "$pod" -c caller -- curl --silent --max-time 10 -o /dev/null \
  -w '%{http_code}' http://127.0.0.1:3500/v1.0/metadata)" 200 "non-invocation request passes through the pipeline"

if kubectl logs -n "$namespace" "$pod" -c daprd | grep -Fq "$token"; then
  echo "FAIL: the token appeared in daprd logs" >&2; exit 1
fi

# Empty token: the guest must abort at load, so this pod must never get a call
# through to the target. Record what daprd actually did (design D3).
set +e
kubectl rollout status -n "$namespace" deployment/caller-empty --timeout=60s >/dev/null 2>&1
empty_pod="$(kubectl get pods -n "$namespace" -l app=caller-empty --sort-by=.metadata.creationTimestamp -o name | tail -1)"
for _ in $(seq 1 20); do
  kubectl exec -n "$namespace" "$empty_pod" -c caller -- \
    curl --silent --max-time 5 -o /dev/null http://127.0.0.1:3500/v1.0/invoke/bearer-target/method/empty-case
  sleep 1
done
set -e
echo "--- empty-token pod: state and daprd log (token was empty; nothing secret here) ---"
kubectl get "$empty_pod" -n "$namespace" -o jsonpath='{range .status.containerStatuses[*]}{.name}{" ready="}{.ready}{" restarts="}{.restartCount}{"\n"}{end}'
# Match on the log level: every line carries app_id=caller-empty, so a keyword
# filter like "empty" selects everything. --previous is the crashed container.
{ kubectl logs -n "$namespace" "$empty_pod" -c daprd --previous 2>/dev/null ||
  kubectl logs -n "$namespace" "$empty_pod" -c daprd 2>&1; } |
  grep -E 'level=(error|fatal)' | cut -c1-400 | head -5 || true
if kubectl logs -n "$namespace" -l app=echo | grep -Fq 'path=/empty-case'; then
  echo "FAIL: a call from the empty-token pod reached the target (daprd skipped the broken Wasm middleware); check the Component for ignoreErrors: true (design D3/D4)" >&2
  exit 1
fi

echo "PASS: Bearer added at the target; endpoint isolation, overwrite and pass-through held; empty token never reached the target"
echo "daprd: $actual_daprd; guest image: $guest_image"
