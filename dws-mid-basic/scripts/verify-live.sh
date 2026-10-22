#!/usr/bin/env bash
# Live probe for the dws-mid-basic Wasm middleware on a Kubernetes cluster with Dapr
# (ADR 0010 Decision 4a, OpenSpec change add-dws-mid-basic, design Decision 9).
#
# Derived from spikes/wasm-auth-middleware/verify.sh, basic scheme only. It deploys the image the
# way the controller will (init container copies /basic.wasm into an emptyDir that daprd mounts at
# /mnt/dws-wasm, Component url file:///mnt/dws-wasm/basic.wasm, guestConfig from a Secret) and
# asserts what the echo target receives.
#
# Usage:
#   dws-mid-basic/scripts/verify-live.sh                       # build the image from this package
#   IMAGE=ghcr.io/tonylibs/dws-mid-basic@sha256:<digest> dws-mid-basic/scripts/verify-live.sh
#
# Requires kubectl, a cluster with the Dapr control plane (Components, HTTPEndpoints and the
# sidecar injector). Building locally also requires Docker and imports the image into the nodes,
# which only works on Docker Desktop style clusters whose nodes are Docker containers. Uses only
# the namespace below and deletes it on exit.
set -euo pipefail

namespace=dws-mid-basic-probe
daprd_image=ghcr.io/dapr/daprd:1.18.1
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
local_image=docker.io/library/dws-mid-basic
archive=""
tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/dws-mid-basic-probe.XXXXXX")"

cleanup() {
  kubectl delete namespace "$namespace" --ignore-not-found --wait=false >/dev/null 2>&1 || true
  [[ -n "$archive" ]] && rm -f "$archive"
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_eq() { [[ "$1" == "$2" ]] || fail "expected '$2', got '$1' ($3)"; }
b64() { printf '%s' "$1" | base64 | tr -d '\n'; }

command -v kubectl >/dev/null || fail "kubectl is required"
kubectl cluster-info >/dev/null || fail "no reachable cluster"
kubectl get crd components.dapr.io httpendpoints.dapr.io >/dev/null 2>&1 ||
  fail "Dapr CRDs (components, httpendpoints) not found: install the Dapr control plane first"

# --- The image under test --------------------------------------------------------------------
if [[ -n "${IMAGE:-}" ]]; then
  [[ "$IMAGE" =~ @sha256:[0-9a-f]{64}$ ]] || fail "IMAGE must be a reference pinned by digest (name@sha256:...), got '$IMAGE'"
  guest_image="$IMAGE"
  pull_policy=IfNotPresent
  built_locally=false
else
  command -v docker >/dev/null || fail "docker is required to build the image (or set IMAGE=<ref@digest>)"
  docker info >/dev/null 2>&1 || fail "docker is not reachable"
  archive="$(mktemp "${TMPDIR:-/tmp}/dws-mid-basic-image.XXXXXX.tar")"
  docker build -q -t "$local_image:probe" -f "$root/Dockerfile" "$root" >/dev/null
  digest="$(docker image inspect "$local_image:probe" --format '{{.Id}}')"
  [[ "$digest" == sha256:* ]] || fail "could not resolve the local image digest"
  docker save -o "$archive" "$local_image:probe"
  for node in $(kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'); do
    docker cp "$archive" "$node:/root/dws-mid-basic.tar"
    docker exec "$node" ctr -n k8s.io images import /root/dws-mid-basic.tar >/dev/null
    docker exec "$node" ctr -n k8s.io images tag "$local_image:probe" "$local_image@$digest" >/dev/null
    docker exec "$node" rm /root/dws-mid-basic.tar
  done
  guest_image="$local_image@$digest"
  pull_policy=Never
  built_locally=true
fi

# --- Credentials under test ------------------------------------------------------------------
# Built from bytes so the script does not depend on how an editor normalizes Unicode.
alice_user=alice
alice_pass=s3cret
alice_expected='Basic YWxpY2U6czNjcmV0'                       # RFC 7617 style, computed independently
colon_user=bob
colon_pass='pa:ss:word'                                        # a ':' in the password is kept
utf8_user=$'zoe\xcc\x81'                                       # decomposed e: guest must normalize to NFC
utf8_pass=$'123\xc2\xa3'                                       # 123 + pound sign (RFC 7617 section 2.1)
utf8_nfc_userpass=$'zo\xc3\xab:123\xc2\xa3'                    # what the target must see, in NFC
caller_header='Bearer caller-supplied'

secret() { # secret <name> <guestConfig JSON>
  kubectl create secret generic "$1" -n "$namespace" --from-literal="guestConfig=$2" >/dev/null
}

kubectl delete namespace "$namespace" --ignore-not-found --wait=true >/dev/null
kubectl create namespace "$namespace" >/dev/null
secret basic-alice "{\"endpoint\":\"basic-alice\",\"username\":\"$alice_user\",\"password\":\"$alice_pass\"}"
secret basic-colon "{\"endpoint\":\"basic-colon\",\"username\":\"$colon_user\",\"password\":\"$colon_pass\"}"
secret basic-utf8 "{\"endpoint\":\"basic-utf8\",\"username\":\"$utf8_user\",\"password\":\"$utf8_pass\"}"
# Negative case: no password. The username is deliberately not secret.
secret basic-broken '{"endpoint":"broken-target","username":"neg-user"}'

# --- Manifests (positive pod; the negative pod is applied later) -----------------------------
install_init() { # the init containers shared by both pods (copy the guest, report its size)
  cat <<YAML
      initContainers:
        - name: install-wasm
          image: $guest_image
          imagePullPolicy: $pull_policy
          command: [sh, -c, "cp /basic.wasm /mnt/dws-wasm/basic.wasm"]
          volumeMounts:
            - name: dws-wasm
              mountPath: /mnt/dws-wasm
        - name: wasm-size
          image: $guest_image
          imagePullPolicy: $pull_policy
          command: [sh, -c, "wc -c < /basic.wasm"]
YAML
}

endpoint_yaml() { # endpoint_yaml <name> <scope>
  cat <<YAML
---
apiVersion: dapr.io/v1alpha1
kind: HTTPEndpoint
metadata:
  name: $1
spec:
  baseUrl: http://probe-echo.$namespace.svc.cluster.local:8080
scopes: [$2]
YAML
}

component_yaml() { # component_yaml <name> <scope>
  cat <<YAML
---
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: $1
spec:
  type: middleware.http.wasm
  version: v1
  metadata:
    - name: url
      value: file:///mnt/dws-wasm/basic.wasm
    - name: guestConfig
      secretKeyRef:
        name: $1
        key: guestConfig
auth:
  secretStore: kubernetes
scopes: [$2]
YAML
}

caller_yaml() { # caller_yaml <app-id> <dapr config name>
  cat <<YAML
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $1
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $1
  template:
    metadata:
      labels:
        app: $1
      annotations:
        dapr.io/enabled: "true"
        dapr.io/app-id: $1
        dapr.io/config: $2
        dapr.io/sidecar-image: $daprd_image
        dapr.io/volume-mounts: dws-wasm:/mnt/dws-wasm
        dapr.io/enable-native-sidecar: "false"
    spec:
      serviceAccountName: probe-caller
$(install_init)
      containers:
        - name: caller
          image: curlimages/curl:8.11.1
          command: [sh, -ec, "while true; do sleep 3600; done"]
      volumes:
        - name: dws-wasm
          emptyDir: {}
YAML
}

{
  cat <<YAML
apiVersion: v1
kind: ServiceAccount
metadata:
  name: probe-caller
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: probe-secret-reader
rules:
  - apiGroups: [""]
    resources: [secrets]
    resourceNames: [basic-alice, basic-colon, basic-utf8, basic-broken]
    verbs: [get]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: probe-secret-reader
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: probe-secret-reader
subjects:
  - kind: ServiceAccount
    name: probe-caller
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: probe-echo
data:
  server.py: |
    from http.server import BaseHTTPRequestHandler, HTTPServer

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            values = self.headers.get_all("Authorization") or []
            authorization = " | ".join(values) if values else "<none>"
            print(f"path={self.path} count={len(values)} authorization={authorization}", flush=True)
            self.send_response(200)
            self.end_headers()
            self.wfile.write(authorization.encode())

    HTTPServer(("", 8080), Handler).serve_forever()
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: probe-echo
spec:
  replicas: 1
  selector:
    matchLabels:
      app: probe-echo
  template:
    metadata:
      labels:
        app: probe-echo
    spec:
      containers:
        - name: echo
          image: python:3.13-alpine
          command: [python, /app/server.py]
          ports:
            - containerPort: 8080
          volumeMounts:
            - name: source
              mountPath: /app
      volumes:
        - name: source
          configMap:
            name: probe-echo
---
apiVersion: v1
kind: Service
metadata:
  name: probe-echo
spec:
  selector:
    app: probe-echo
  ports:
    - port: 8080
      targetPort: 8080
---
apiVersion: dapr.io/v1alpha1
kind: Configuration
metadata:
  name: probe-positive
spec:
  httpPipeline:
    handlers:
      - name: basic-alice
        type: middleware.http.wasm
      - name: basic-colon
        type: middleware.http.wasm
      - name: basic-utf8
        type: middleware.http.wasm
---
apiVersion: dapr.io/v1alpha1
kind: Configuration
metadata:
  name: probe-negative
spec:
  httpPipeline:
    handlers:
      - name: basic-broken
        type: middleware.http.wasm
YAML
  for ep in basic-alice basic-colon basic-utf8 basic-alice-v2 unrelated-target; do endpoint_yaml "$ep" probe-positive-caller; done
  endpoint_yaml broken-target probe-negative-caller
  for c in basic-alice basic-colon basic-utf8; do component_yaml "$c" probe-positive-caller; done
  component_yaml basic-broken probe-negative-caller
  caller_yaml probe-positive-caller probe-positive
} >"$tmpdir/positive.yaml"
caller_yaml probe-negative-caller probe-negative >"$tmpdir/negative.yaml"

kubectl apply -n "$namespace" -f "$tmpdir/positive.yaml" >/dev/null
kubectl rollout status -n "$namespace" deployment/probe-echo --timeout=3m >/dev/null
kubectl rollout status -n "$namespace" deployment/probe-positive-caller --timeout=3m >/dev/null
pod="$(kubectl get pods -n "$namespace" -l app=probe-positive-caller \
  --sort-by=.metadata.creationTimestamp -o name | tail -1)"
actual_daprd="$(kubectl get "$pod" -n "$namespace" -o jsonpath='{.spec.containers[?(@.name=="daprd")].image}')"
assert_eq "$actual_daprd" "$daprd_image" "daprd image of the sidecar"

invoke() { # invoke <endpoint> <path> [curl args...]
  local endpoint=$1 path=$2
  shift 2
  kubectl exec -n "$namespace" "$pod" -c caller -- \
    curl --fail --silent --show-error --max-time 15 "$@" \
    "http://127.0.0.1:3500/v1.0/invoke/$endpoint/method/$path"
}

# Dapr discovers endpoints and components asynchronously: wait for the first protected call.
observed=""
for _ in $(seq 1 30); do
  observed="$(invoke basic-alice probe 2>/dev/null || true)"
  [[ "$observed" == "$alice_expected" ]] && break
  sleep 2
done

echo "--- assertions"
assert_eq "$observed" "$alice_expected" "alice/s3cret after discovery"
assert_eq "$(invoke basic-alice 'v1/pets/42?q=a')" "$alice_expected" "alice, nested path with a query string"
assert_eq "$(invoke basic-colon probe)" "Basic $(b64 "$colon_user:$colon_pass")" "password containing ':'"
assert_eq "$(invoke basic-utf8 probe)" "Basic $(b64 "$utf8_nfc_userpass")" "non-ASCII credential, decomposed input normalized to NFC"
assert_eq "$(invoke unrelated-target probe)" '<none>' "another endpoint gets no credential"
assert_eq "$(invoke basic-alice-v2 probe)" '<none>' "endpoint name prefix gets no credential"
assert_eq "$(invoke basic-alice probe -H "Authorization: $caller_header")" "$alice_expected" \
  "caller-supplied Authorization is replaced, exactly one value reaches the target"
assert_eq "$(invoke unrelated-target probe -H "Authorization: $caller_header")" "$caller_header" \
  "another endpoint keeps the caller's own Authorization"

echo "--- log scan"
logs="$(kubectl logs -n "$namespace" "$pod" -c daprd 2>&1)"
for secret_value in "$alice_pass" "$colon_pass" "$utf8_pass" "$alice_expected" "${alice_expected#Basic }" \
  "$(b64 "$colon_user:$colon_pass")" "$(b64 "$utf8_nfc_userpass")" "$(b64 "$alice_user:$alice_pass")"; do
  if grep -Fq -- "$secret_value" <<<"$logs"; then
    fail "a credential value appeared in default-level daprd logs"
  fi
done
echo "no credential in daprd logs"

wasm_size="$(kubectl logs -n "$namespace" "$pod" -c wasm-size | tr -d '[:space:]')"

# --- Negative case: a guest that refuses to start must not let a call through -----------------
echo "--- negative case (config without password)"
kubectl apply -n "$namespace" -f "$tmpdir/negative.yaml" >/dev/null
neg=""
for _ in $(seq 1 30); do
  neg="$(kubectl get pods -n "$namespace" -l app=probe-negative-caller -o name 2>/dev/null | tail -1)"
  [[ -n "$neg" ]] && break
  sleep 2
done
[[ -n "$neg" ]] || fail "the negative pod was not created"
# Give daprd time to load components and either exit or keep running.
sleep 45

neg_status="$(kubectl get "$neg" -n "$namespace" \
  -o jsonpath='phase={.status.phase} daprd.ready={.status.containerStatuses[?(@.name=="daprd")].ready} daprd.restarts={.status.containerStatuses[?(@.name=="daprd")].restartCount} daprd.state={.status.containerStatuses[?(@.name=="daprd")].state}' 2>&1 || true)"
neg_call="not attempted"
if kubectl exec -n "$namespace" "$neg" -c caller -- \
  curl --fail --silent --max-time 15 \
  "http://127.0.0.1:3500/v1.0/invoke/broken-target/method/negative-probe" >/dev/null 2>&1; then
  neg_call="succeeded"
else
  neg_call="failed"
fi
echo_log="$(kubectl logs -n "$namespace" deployment/probe-echo 2>&1)"
if grep -Fq 'path=/negative-probe' <<<"$echo_log"; then
  fail "the echo target received a request from the pod whose guest refused to start"
fi
[[ "$neg_call" == failed ]] || fail "a call through the misconfigured middleware succeeded"
echo "echo target received no request from the negative pod; the call through the sidecar failed"

echo "Observed daprd behavior when the guest refuses to start (record this in the README):"
echo "  pod: $neg_status"
{ kubectl logs -n "$namespace" "$neg" -c daprd 2>&1; kubectl logs -n "$namespace" "$neg" -c daprd --previous 2>&1; } |
  grep -iE 'wasm|middleware|password|username|panic|fatal|unreachable|dws-mid-basic' | head -n 10 | sed 's/^/  daprd: /' || true

echo
echo "PASS: dws-mid-basic reached the echo target with the right Basic credential"
echo "      (plain, ':' in password, non-ASCII/NFC); other endpoints untouched; caller header replaced;"
echo "      no credential in daprd logs; a guest without a password sent nothing to the target"
echo "Guest image:  $guest_image (built locally: $built_locally)"
echo "Dapr sidecar: $actual_daprd"
echo "basic.wasm:   $wasm_size bytes"
