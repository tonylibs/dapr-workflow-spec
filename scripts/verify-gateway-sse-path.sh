#!/usr/bin/env bash
# Live verification of the deferred item in openspec/changes/api-gateway/verify.md section 5:
# an SSE stream traversing APISIX (Gateway API data plane) -> Dapr sidecar (service invocation)
# -> Nest on port 3000, with Dapr's bearer middleware as the ONLY JWT verifier, on a real
# cluster. See docs/roadmaps/dws-auth.md section 2c for the design this proves.
#
# What this script does, end to end, against the CURRENT working tree (not a published image):
#   1. Installs the two cluster-scoped CRD groups the bundled API Gateway needs (Kubernetes
#      Gateway API v1 + APISIX apisix.apache.org/v1alpha1), extracted from the exact vendored
#      charts/dws/charts/apisix-2.16.0.tgz this chart already pins -- no network fetch of a
#      differently-versioned CRD bundle. These are cluster-scoped and are NEVER deleted by this
#      script's cleanup.
#   2. Builds local dws-admin/dws-console images from THIS worktree (imagePullPolicy: Never) so
#      the run actually exercises this branch's single-app-port Nest consolidation and bearer-
#      authenticated SSE client -- not whatever tag happens to be published to ghcr.io.
#   3. Deploys a disposable Redis (for the three Dapr Redis Components) and a disposable mock
#      OIDC JWKS server (for Dapr's bearer middleware) inside one throwaway namespace.
#   4. `helm install`s charts/dws in bundled gateway mode (apiGateway.enabled=true,
#      apisix.enabled=true, auth/admin/console enabled, dapr.enabled=false to reuse the
#      cluster's already-installed Dapr control plane rather than installing a second one).
#   5. Proves the live matrix through a port-forwarded APISIX gateway Service:
#        (a) valid bearer -> Gateway -> APISIX -> Dapr invoke -> Nest -> real JSON
#        (b) missing/tampered bearer -> 401, and the SAME 401 reproduces when the sidecar is hit
#            directly (bypassing APISIX entirely) -- proving Dapr, not APISIX, is the verifier
#        (c) an SSE subscription on the admin instance-events endpoint delivers a named event
#            frame WHILE the connection is still open (not merely at close) -- the actual
#            buffering question this change deferred
#        (d) console root routes to the console Service, and /dws-admin/* still wins
#
# Prerequisites: kubectl, helm, docker, node, curl, jq, on a cluster with Dapr's control plane
# already installed (this script never installs a second one -- see dapr.enabled=false below).
# All Helm chart dependencies used here are vendored as committed .tgz archives under
# charts/dws/charts/, so no network access to any chart repository is required for the install
# itself (network IS used to pull the small helper images: redis, nginx, and, on a fresh
# machine, the apisix/etcd images the bundled dependency pulls).
#
# This script owns ONLY namespaces prefixed dws-gw-e2e (enforced below) and the Helm release
# inside them. It NEVER touches dws, dws-phase4, dapr-system, kafka, or any other pre-existing
# namespace/release, and it NEVER changes the kubectl context.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_DIR="${GW_E2E_CHART_DIR:-$REPO_ROOT/charts/dws}"

NAMESPACE="${GW_E2E_NAMESPACE:-dws-gw-e2e}"
RELEASE="${GW_E2E_RELEASE:-dws}"

# Hard safety rail (see file header): refuse to run against anything but a disposable,
# clearly-prefixed namespace, regardless of env overrides.
case "$NAMESPACE" in
  dws-gw-e2e*) ;;
  *)
    echo "FAIL: GW_E2E_NAMESPACE ('$NAMESPACE') must start with 'dws-gw-e2e' -- refusing to run" >&2
    exit 1
    ;;
esac

ADMIN_IMAGE_TAG="gw-e2e"
CONSOLE_IMAGE_TAG="gw-e2e"
REDIS_SVC="gw-e2e-redis"
REDIS_SECRET="gw-e2e-redis"
MOCK_IDP_SVC="mock-idp"
ISSUER="http://${MOCK_IDP_SVC}.${NAMESPACE}.svc.cluster.local"
AUDIENCE="dws-admin"

GATEWAY_PF_PORT="${GW_E2E_GATEWAY_PORT:-18080}"
ADMIN_PF_PORT="${GW_E2E_ADMIN_PORT:-18081}"
PF_PIDS=()

PASS_COUNT=0
pass() { PASS_COUNT=$((PASS_COUNT + 1)); echo "PASS: $1"; }
fail_and_exit() { echo "FAIL: $1" >&2; exit 1; }

dump_debug_state() {
  echo "--- debug: pods in $NAMESPACE ---" >&2
  kubectl get pods -n "$NAMESPACE" -o wide >&2 || true
  echo "--- debug: events in $NAMESPACE (last 5m) ---" >&2
  kubectl get events -n "$NAMESPACE" --sort-by=.lastTimestamp >&2 || true
  echo "--- debug: admin pod logs (admin container) ---" >&2
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/component=admin -c admin --tail=200 >&2 || true
  echo "--- debug: admin pod logs (daprd container) ---" >&2
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/component=admin -c daprd --tail=200 >&2 || true
  echo "--- debug: apisix pods ---" >&2
  kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=apisix -o wide >&2 || true
  echo "--- debug: apisix data-plane logs ---" >&2
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/name=apisix,app.kubernetes.io/service!=apisix-ingress-controller --tail=200 >&2 || true
  echo "--- debug: apisix-ingress-controller logs ---" >&2
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/instance="${RELEASE}" -c apisix-ingress-controller --tail=200 >&2 || true
  kubectl logs -n "$NAMESPACE" -l app.kubernetes.io/name=apisix-ingress-controller --all-containers --tail=200 >&2 || true
  echo "--- debug: Gateway/HTTPRoute/GatewayProxy status ---" >&2
  kubectl get gatewayclass,gateway,httproute,gatewayproxy -n "$NAMESPACE" -o wide >&2 || true
  kubectl describe gateway -n "$NAMESPACE" >&2 || true
  kubectl describe httproute -n "$NAMESPACE" >&2 || true
}

strip_finalizers() {
  # Best-effort: remove metadata.finalizers from a resource so deletion can complete even if the
  # controller that would normally clear them is already gone. Silent no-op if the resource or
  # the finalizers field doesn't exist.
  kubectl patch "$1" ${2:+-n "$2"} --type=json -p='[{"op":"remove","path":"/metadata/finalizers"}]' >/dev/null 2>&1 || true
}

cleanup() {
  echo
  echo "=== cleanup ==="
  for pid in "${PF_PIDS[@]:-}"; do
    [ -n "${pid:-}" ] && kill "$pid" >/dev/null 2>&1 || true
  done

  # The chart-owned GatewayClass (cluster-scoped, dws.apiGateway.className) carries an
  # apisix.apache.org/gc-protection finalizer that only a LIVE apisix-ingress-controller can
  # clear. `helm uninstall` gives no ordering guarantee that the controller processes this
  # deletion before its own pod is torn down as part of the same uninstall -- once the
  # controller is gone, that finalizer blocks the GatewayClass (and `kubectl delete namespace`
  # transitively) forever. Strip it unconditionally, up front, so cleanup always terminates.
  strip_finalizers "gatewayclass/${NAMESPACE}-${RELEASE}-apisix"

  helm uninstall "$RELEASE" --namespace "$NAMESPACE" --wait --timeout 2m >/dev/null 2>&1 || true
  kubectl delete gatewayclass "${NAMESPACE}-${RELEASE}-apisix" --ignore-not-found --timeout=30s >/dev/null 2>&1 || true

  kubectl delete namespace "$NAMESPACE" --ignore-not-found --wait=false >/dev/null 2>&1 || true
  if ! kubectl wait --for=delete "namespace/${NAMESPACE}" --timeout=90s >/dev/null 2>&1; then
    # Same finalizer class can strand namespace-scoped apisix.apache.org resources too (e.g. the
    # GatewayProxy). Strip anything left, then give the namespace one more window to finish.
    for res in gatewayproxies.apisix.apache.org apisixroutes.apisix.apache.org; do
      for name in $(kubectl get "$res" -n "$NAMESPACE" -o name 2>/dev/null); do
        strip_finalizers "$name" "$NAMESPACE"
      done
    done
    kubectl wait --for=delete "namespace/${NAMESPACE}" --timeout=90s >/dev/null 2>&1 || true
  fi
  if [ -n "${WORKDIR:-}" ]; then rm -rf "$WORKDIR"; fi
  # Deliberately NOT deleting: the Gateway API v1 / apisix.apache.org CRDs installed below.
  # They are cluster-scoped and, per the task constraints, are left in place.
}

command -v kubectl >/dev/null
command -v helm >/dev/null
command -v docker >/dev/null
command -v node >/dev/null
command -v curl >/dev/null
command -v jq >/dev/null

# Idempotent: clean up anything left by a previous interrupted run before starting. WORKDIR is
# deliberately created AFTER this pre-clean (cleanup() removes it too) so this run's own scratch
# directory is never deleted out from under it.
cleanup
trap cleanup EXIT
WORKDIR="$(mktemp -d)"

context="$(kubectl config current-context)"
echo "kubectl context: $context"
case "$context" in
  docker-desktop) ;;
  kind-*) ;;
  *) echo "WARNING: kubectl context '$context' is neither docker-desktop nor a kind-* cluster -- proceeding anyway, but this script only ever touches namespaces prefixed dws-gw-e2e." >&2 ;;
esac

# ================================================================================================
# 1. Cluster-scoped CRDs: Kubernetes Gateway API v1 + APISIX apisix.apache.org/v1alpha1.
#    Extracted from the exact vendored charts/dws/charts/apisix-2.16.0.tgz (same file
#    tests/api-gateway-render-test.sh and Chart.lock already pin), not a separately-versioned
#    upstream URL, so the CRD schema always matches the ingress-controller version this chart
#    actually deploys. This is a CLUSTER-WIDE, one-time install; kubectl apply is idempotent, and
#    these are NEVER removed by cleanup() above.
# ================================================================================================
echo
echo "=== 1. Installing cluster-scoped Gateway API v1 + APISIX CRDs (idempotent, not removed on cleanup) ==="
APISIX_CHART_TGZ="$CHART_DIR/charts/apisix-2.16.0.tgz"
test -f "$APISIX_CHART_TGZ" || fail_and_exit "vendored $APISIX_CHART_TGZ not found -- run 'helm dependency build $CHART_DIR' first"

tar xzf "$APISIX_CHART_TGZ" -C "$WORKDIR" \
  apisix/charts/apisix-ingress-controller/crds/gwapi-crds.yaml \
  apisix/charts/apisix-ingress-controller/crds/apisixic-crds.yaml
GWAPI_CRDS="$WORKDIR/apisix/charts/apisix-ingress-controller/crds/gwapi-crds.yaml"
APISIX_CRDS="$WORKDIR/apisix/charts/apisix-ingress-controller/crds/apisixic-crds.yaml"

kubectl apply --server-side -f "$GWAPI_CRDS"
kubectl apply --server-side -f "$APISIX_CRDS"
kubectl wait --for=condition=Established --timeout=60s -f "$GWAPI_CRDS"
kubectl wait --for=condition=Established --timeout=60s -f "$APISIX_CRDS"
pass "Gateway API v1 CRDs and APISIX apisix.apache.org/v1alpha1 CRDs are Established cluster-wide"

# ================================================================================================
# 2. Local images from THIS worktree (not ghcr.io:latest -- this branch's dws-admin/dws-console
#    application changes are exactly what's under test).
# ================================================================================================
echo
echo "=== 2. Building local images from the current worktree ==="
docker build -q -t "dws-admin:${ADMIN_IMAGE_TAG}" "$REPO_ROOT/dws-admin" >"$WORKDIR/docker-build-admin.log" 2>&1 \
  || { cat "$WORKDIR/docker-build-admin.log" >&2; fail_and_exit "docker build dws-admin failed"; }
docker build -q -t "dws-console:${CONSOLE_IMAGE_TAG}" "$REPO_ROOT/dws-console" >"$WORKDIR/docker-build-console.log" 2>&1 \
  || { cat "$WORKDIR/docker-build-console.log" >&2; fail_and_exit "docker build dws-console failed"; }
pass "dws-admin:${ADMIN_IMAGE_TAG} and dws-console:${CONSOLE_IMAGE_TAG} built from $REPO_ROOT"

# Docker Desktop's "docker-desktop" context (this cluster) is, under the hood, a kind cluster
# (each node -- desktop-control-plane, desktop-worker, desktop-worker2 -- is its own
# kindest/node Docker container with its OWN containerd content store, confirmed via
# `docker ps`). A plain `docker build` only populates the HOST docker engine's store, which is
# NOT automatically visible to any node's containerd/kubelet -- imagePullPolicy: Never then
# fails with ErrImageNeverPull on whichever node the pod happens to land on. This loads each
# image directly into every node's containerd content store via `ctr images import`, piped over
# stdin so no in-container file path is ever passed as a CLI argument (git-bash/MSYS on Windows
# aggressively rewrites bare "/..." arguments into Windows paths, which silently breaks a
# path-argument version of this same operation -- piping avoids that class of bug entirely).
# The same technique works unmodified against a real `kind` cluster in CI (kind always names its
# node containers identically to the Kubernetes node names).
load_image_into_cluster_nodes() {
  local image="$1"
  local node
  for node in $(kubectl get nodes -o jsonpath='{.items[*].metadata.name}'); do
    if docker inspect "$node" >/dev/null 2>&1; then
      echo "Loading ${image} into node container '${node}' (containerd import via stdin)..."
      docker save "$image" | docker exec -i "$node" ctr --namespace=k8s.io images import - >/dev/null
    fi
  done
}
load_image_into_cluster_nodes "dws-admin:${ADMIN_IMAGE_TAG}"
load_image_into_cluster_nodes "dws-console:${CONSOLE_IMAGE_TAG}"
pass "dws-admin:${ADMIN_IMAGE_TAG} and dws-console:${CONSOLE_IMAGE_TAG} loaded into every cluster node's containerd content store"

# ================================================================================================
# 3. Disposable namespace, Redis, and mock JWKS IdP.
# ================================================================================================
echo
echo "=== 3. Creating namespace $NAMESPACE and disposable Redis + mock IdP ==="
kubectl create namespace "$NAMESPACE"

kubectl -n "$NAMESPACE" apply -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${REDIS_SECRET}
type: Opaque
stringData:
  redis-password: gw-e2e-redis-pw
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${REDIS_SVC}
spec:
  replicas: 1
  selector:
    matchLabels: {app: ${REDIS_SVC}}
  template:
    metadata:
      labels: {app: ${REDIS_SVC}}
    spec:
      containers:
        - name: redis
          image: redis:7-alpine
          args: ["--requirepass", "gw-e2e-redis-pw"]
          ports: [{containerPort: 6379}]
          readinessProbe:
            tcpSocket: {port: 6379}
            initialDelaySeconds: 2
            periodSeconds: 2
---
apiVersion: v1
kind: Service
metadata:
  name: ${REDIS_SVC}
spec:
  selector: {app: ${REDIS_SVC}}
  ports: [{port: 6379, targetPort: 6379}]
EOF

# --- mint an RSA keypair, a JWKS document, and a set of test tokens (host-side, via node) -----
# Dapr's built-in middleware.http.bearer verifies against auth.jwksURL directly when set (see
# https://docs.dapr.io -> supported-middleware/middleware-bearer), so the mock IdP only ever
# needs to SERVE a static JWKS document -- no live signing service required in-cluster.
mkdir -p "$WORKDIR/idp"
cat > "$WORKDIR/mint-tokens.js" <<'NODE_EOF'
const crypto = require('crypto');
const fs = require('fs');

const [, , issuer, audience, outDir] = process.argv;

const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = publicKey.export({ format: 'jwk' });
const kid = crypto.createHash('sha256').update(jwk.n).digest('hex').slice(0, 16);
jwk.kid = kid;
jwk.alg = 'RS256';
jwk.use = 'sig';

function b64url(buf) {
  return Buffer.from(buf).toString('base64url');
}

function sign(payload) {
  const header = { alg: 'RS256', typ: 'JWT', kid };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = crypto.sign('RSA-SHA256', Buffer.from(signingInput), privateKey);
  return `${signingInput}.${b64url(sig)}`;
}

const now = Math.floor(Date.now() / 1000);
const basePayload = { iss: issuer, aud: audience, sub: 'gw-e2e-user', iat: now, exp: now + 3600 };

const valid = sign(basePayload);
// Flip a character in the signature segment -- header/payload/claims stay valid-looking, only
// the signature verification must fail. This isolates "Dapr checks the signature" from "Dapr
// checks the claims".
const parts = valid.split('.');
const sigChars = parts[2].split('');
const flipIndex = 4;
sigChars[flipIndex] = sigChars[flipIndex] === 'A' ? 'B' : 'A';
const tampered = `${parts[0]}.${parts[1]}.${sigChars.join('')}`;

fs.writeFileSync(`${outDir}/jwks.json`, JSON.stringify({ keys: [jwk] }));
fs.writeFileSync(
  `${outDir}/discovery.json`,
  JSON.stringify({ issuer, jwks_uri: `${issuer}/keys.json` }),
);
fs.writeFileSync(`${outDir}/valid.token`, valid);
fs.writeFileSync(`${outDir}/tampered.token`, tampered);
console.log('minted RSA keypair, JWKS document, and test tokens');
NODE_EOF
node "$WORKDIR/mint-tokens.js" "$ISSUER" "$AUDIENCE" "$WORKDIR/idp"
VALID_TOKEN="$(cat "$WORKDIR/idp/valid.token")"
TAMPERED_TOKEN="$(cat "$WORKDIR/idp/tampered.token")"
pass "minted RSA keypair, JWKS document, and valid/tampered test tokens (issuer=$ISSUER audience=$AUDIENCE)"

kubectl -n "$NAMESPACE" create configmap "${MOCK_IDP_SVC}-content" \
  --from-file=keys.json="$WORKDIR/idp/jwks.json" \
  --from-file=openid-configuration="$WORKDIR/idp/discovery.json"

kubectl -n "$NAMESPACE" apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${MOCK_IDP_SVC}
spec:
  replicas: 1
  selector:
    matchLabels: {app: ${MOCK_IDP_SVC}}
  template:
    metadata:
      labels: {app: ${MOCK_IDP_SVC}}
    spec:
      containers:
        - name: nginx
          image: nginx:1.27-alpine
          ports: [{containerPort: 80}]
          volumeMounts:
            - name: content
              mountPath: /usr/share/nginx/html/keys.json
              subPath: keys.json
            - name: content
              mountPath: /usr/share/nginx/html/.well-known/openid-configuration
              subPath: openid-configuration
          readinessProbe:
            httpGet: {path: /keys.json, port: 80}
            initialDelaySeconds: 1
            periodSeconds: 2
      volumes:
        - name: content
          configMap:
            name: ${MOCK_IDP_SVC}-content
---
apiVersion: v1
kind: Service
metadata:
  name: ${MOCK_IDP_SVC}
spec:
  selector: {app: ${MOCK_IDP_SVC}}
  ports: [{port: 80, targetPort: 80}]
EOF

kubectl -n "$NAMESPACE" rollout status deployment/"$REDIS_SVC" --timeout=2m \
  || { dump_debug_state; fail_and_exit "disposable Redis never became ready"; }
kubectl -n "$NAMESPACE" rollout status deployment/"$MOCK_IDP_SVC" --timeout=2m \
  || { dump_debug_state; fail_and_exit "mock IdP never became ready"; }
pass "disposable Redis and mock JWKS IdP are Ready in $NAMESPACE"

# ================================================================================================
# 4. helm install charts/dws in bundled gateway mode.
#    dapr.enabled=false: reuse the cluster's already-installed Dapr control plane (dapr-system)
#    rather than installing a second one (installing a second Dapr chart would create a SECOND
#    cluster-scoped sidecar-injector MutatingWebhookConfiguration -- exactly the kind of
#    cluster-wide interference with the existing dapr-system release this task forbids).
# ================================================================================================
echo
echo "=== 4. helm install $RELEASE $CHART_DIR --namespace $NAMESPACE (bundled gateway mode) ==="
helm install "$RELEASE" "$CHART_DIR" \
  --namespace "$NAMESPACE" \
  --timeout 10m \
  --set dapr.enabled=false \
  --set controller.enabled=false \
  --set postgresql.enabled=true \
  --set admin.enabled=true \
  --set admin.image.repository=dws-admin \
  --set admin.image.tag="$ADMIN_IMAGE_TAG" \
  --set admin.image.pullPolicy=Never \
  --set console.enabled=true \
  --set console.image.repository=dws-console \
  --set console.image.tag="$CONSOLE_IMAGE_TAG" \
  --set console.image.pullPolicy=Never \
  --set auth.enabled=true \
  --set auth.issuer="$ISSUER" \
  --set auth.audience="$AUDIENCE" \
  --set auth.jwksURL="${ISSUER}/keys.json" \
  --set redis.external.host="${REDIS_SVC}.${NAMESPACE}.svc.cluster.local:6379" \
  --set redis.external.existingSecret="$REDIS_SECRET" \
  --set redis.external.existingSecretKey=redis-password \
  --set apiGateway.enabled=true \
  --set apisix.enabled=true \
  || { dump_debug_state; fail_and_exit "helm install failed"; }
pass "helm install succeeded (bundled APISIX + Gateway API, auth/admin/console enabled)"

# Sanity-check the fix this live run drove into charts/dws/templates/admin/deployment.yaml:
# daprd's app-facing HTTP API binds to loopback only by default (independently documented in
# .github/workflows/helm.yml's pubsub e2e job: "daprd's HTTP API (port 3500) binds to
# 127.0.0.1 / [::1] only for security"), which the D5 sidecar-only admin Service topology
# (templates/admin/service.yaml, targetPort: 3500) depends on being Service-reachable. The first
# live run of this script caught that gap (APISIX got ECONNREFUSED/502 at the sidecar hop); the
# chart now sets dapr.io/sidecar-listen-addresses whenever auth.enabled=true. Fail fast with a
# clear message if a future edit ever drops that annotation, rather than failing confusingly at
# the route-wait step below.
kubectl -n "$NAMESPACE" get deployment "${RELEASE}-admin" \
  -o jsonpath='{.spec.template.metadata.annotations.dapr\.io/sidecar-listen-addresses}' \
  | grep -q '0.0.0.0' \
  || fail_and_exit "admin Deployment is missing dapr.io/sidecar-listen-addresses -- daprd's app-facing API will only bind to loopback and no Service (including the Gateway's route) will ever reach it. See templates/admin/deployment.yaml."
pass "admin Deployment carries dapr.io/sidecar-listen-addresses so daprd's sidecar port is actually Service-reachable"

echo
echo "=== 4b. Waiting for every Deployment/StatefulSet in $NAMESPACE to roll out ==="
for d in $(kubectl -n "$NAMESPACE" get deploy -o jsonpath='{.items[*].metadata.name}'); do
  kubectl -n "$NAMESPACE" rollout status "deployment/$d" --timeout=6m \
    || { dump_debug_state; fail_and_exit "deployment/$d never became ready"; }
done
for s in $(kubectl -n "$NAMESPACE" get statefulset -o jsonpath='{.items[*].metadata.name}'); do
  kubectl -n "$NAMESPACE" rollout status "statefulset/$s" --timeout=6m \
    || { dump_debug_state; fail_and_exit "statefulset/$s never became ready"; }
done
pass "all Deployments/StatefulSets in $NAMESPACE are Ready (admin, console, apisix, apisix-ingress-controller, etcd, postgres, redis, mock-idp)"

kubectl -n "$NAMESPACE" get gatewayclass "${NAMESPACE}-${RELEASE}-apisix" -o name \
  || { dump_debug_state; fail_and_exit "GatewayClass not found"; }
kubectl -n "$NAMESPACE" get gateway -o name
kubectl -n "$NAMESPACE" get httproute -o name
kubectl -n "$NAMESPACE" get gatewayproxy -o name
pass "GatewayClass/Gateway/GatewayProxy/HTTPRoutes exist in $NAMESPACE"

admin_pod="$(kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/component=admin -o jsonpath='{.items[0].metadata.name}')"
[ -n "$admin_pod" ] || fail_and_exit "no admin pod found"
echo "admin pod: $admin_pod"

# ================================================================================================
# Finding (not one of the four required assertions, but directly relevant): does Dapr's own
# internal subscription discovery survive the bearer gate, as spec `helm-admin-auth-middleware`
# requires ("Dapr's internal programmatic-subscription discovery and pub/sub callback delivery
# SHALL continue to reach the app without requiring a browser bearer token")? Dapr's
# `appHttpPipeline` applies to EVERY inbound sidecar->app call, including daprd's own internal
# `GET /dapr/subscribe` discovery call -- which carries no Authorization header. Check the
# daprd log for the specific failure signature and report it plainly either way.
# ================================================================================================
if kubectl -n "$NAMESPACE" logs "$admin_pod" -c daprd --tail=500 2>/dev/null \
  | grep -q 'app returned http status code 401 from subscription endpoint'; then
  echo
  echo "FINDING: Dapr's own internal 'GET /dapr/subscribe' discovery call was rejected with 401" >&2
  echo "by the admin sidecar's own bearer middleware (see daprd log line above/below). This" >&2
  echo "means the bearer Configuration, as currently wired into spec.appHttpPipeline, gates" >&2
  echo "Dapr's internal subscription discovery too -- contradicting the requirement in spec" >&2
  echo "'helm-admin-auth-middleware' that pubsub discovery/delivery stay reachable without a" >&2
  echo "bearer token. Dapr's built-in middleware.http.bearer has no path-exemption option (only" >&2
  echo "audience/issuer/jwksURL), so there is no values-only workaround. This is reported as a" >&2
  echo "finding, not fixed here -- fixing it needs a design change (e.g. splitting the pubsub" >&2
  echo "callback onto an unauthenticated path Dapr's ACL can scope separately, or moving" >&2
  echo "discovery/delivery off the gated pipeline some other way)." >&2
  kubectl -n "$NAMESPACE" logs "$admin_pod" -c daprd --tail=500 2>/dev/null | grep 'subscription endpoint' >&2
fi

# ================================================================================================
# 5. Port-forward the APISIX gateway data-plane Service and, separately, the admin Service
#    itself (which in gateway mode fronts ONLY the Dapr sidecar's 3500 -- see
#    templates/admin/service.yaml) so we can distinguish "APISIX rejected this" from
#    "Dapr rejected this" by bypassing APISIX entirely on the second port-forward.
# ================================================================================================
echo
echo "=== 5. Port-forwarding the APISIX gateway Service and the admin (sidecar) Service ==="
APISIX_GATEWAY_SVC="${RELEASE}-apisix-gateway"
ADMIN_SVC="${RELEASE}-admin"

kubectl -n "$NAMESPACE" get svc "$APISIX_GATEWAY_SVC" -o name || { dump_debug_state; fail_and_exit "APISIX gateway Service $APISIX_GATEWAY_SVC not found"; }
kubectl -n "$NAMESPACE" get svc "$ADMIN_SVC" -o name || { dump_debug_state; fail_and_exit "admin Service $ADMIN_SVC not found"; }

wait_for_port_forward() {
  local log="$1"
  for _ in $(seq 1 30); do
    grep -q "Forwarding from 127.0.0.1" "$log" 2>/dev/null && return 0
    sleep 0.5
  done
  echo "port-forward never became ready ($log):" >&2
  cat "$log" >&2 || true
  return 1
}

kubectl -n "$NAMESPACE" port-forward "svc/${APISIX_GATEWAY_SVC}" "${GATEWAY_PF_PORT}:80" \
  >"$WORKDIR/pf-gateway.log" 2>&1 &
PF_PIDS+=("$!")
wait_for_port_forward "$WORKDIR/pf-gateway.log" || { dump_debug_state; fail_and_exit "gateway port-forward failed"; }

kubectl -n "$NAMESPACE" port-forward "svc/${ADMIN_SVC}" "${ADMIN_PF_PORT}:3000" \
  >"$WORKDIR/pf-admin.log" 2>&1 &
PF_PIDS+=("$!")
wait_for_port_forward "$WORKDIR/pf-admin.log" || { dump_debug_state; fail_and_exit "admin port-forward failed"; }

GW="http://127.0.0.1:${GATEWAY_PF_PORT}"
ADMIN_DIRECT="http://127.0.0.1:${ADMIN_PF_PORT}"
pass "port-forwards ready: Gateway ($GW) and admin Service direct ($ADMIN_DIRECT, bypasses APISIX)"

# APISIX's controller reconciles the Gateway/HTTPRoute/GatewayProxy objects asynchronously after
# they're created, so the very first requests may 404/502 before the route is programmed. Retry
# with a generous budget instead of asserting on the first attempt.
wait_for_gateway_route() {
  local path="$1" expect_min="$2" expect_max="$3"
  local code="000" body=""
  for attempt in $(seq 1 120); do
    body="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $VALID_TOKEN" "${GW}${path}" 2>&1)"
    code="$body"
    if [[ "$code" =~ ^[0-9]+$ ]] && [ "$code" -ge "$expect_min" ] 2>/dev/null && [ "$code" -le "$expect_max" ] 2>/dev/null; then
      echo "  attempt $attempt: HTTP $code"
      return 0
    fi
    if [ $((attempt % 10)) -eq 0 ]; then
      echo "  attempt $attempt: HTTP ${code} (still waiting)"
    fi
    sleep 1
  done
  echo "last observed response code: $code" >&2
  return 1
}
echo "Waiting for APISIX to program the /dws-admin route..."
wait_for_gateway_route "/dws-admin/instances" 200 200 \
  || { dump_debug_state; fail_and_exit "APISIX never started routing /dws-admin/instances to a 200 (route not programmed?)"; }
pass "APISIX has programmed the /dws-admin route (first successful proxied request observed)"

# ================================================================================================
# 6. Assertion 1: valid bearer -> Gateway -> APISIX -> Dapr invoke -> Nest -> real JSON.
# ================================================================================================
echo
echo "=== 6. Assertion 1: valid bearer reaches Nest through APISIX -> Dapr invoke ==="
resp_headers="$WORKDIR/a1-headers.txt"
resp_body="$WORKDIR/a1-body.json"
code="$(curl -sS -D "$resp_headers" -o "$resp_body" -w '%{http_code}' \
  -H "Authorization: Bearer $VALID_TOKEN" "${GW}/dws-admin/instances")"
echo "GET /dws-admin/instances (valid bearer) -> HTTP $code"
cat "$resp_headers"
echo "--- body ---"
cat "$resp_body"; echo
[ "$code" = "200" ] || fail_and_exit "expected 200 for a valid bearer through the Gateway, got $code"
jq -e 'has("items") and has("nextCursor")' "$resp_body" >/dev/null \
  || fail_and_exit "response body is not the expected PaginatedInstanceSummaryDto shape (items/nextCursor)"
pass "GET /dws-admin/instances with a valid bearer returns HTTP 200 with the expected {items,nextCursor} JSON from Nest, via Gateway -> APISIX -> Dapr invoke -> Nest:3000"

# ================================================================================================
# 7. Assertion 2: invalid/absent bearer is rejected by DAPR, not APISIX.
# ================================================================================================
echo
echo "=== 7. Assertion 2: invalid/absent bearer rejected by the Dapr sidecar, not APISIX ==="

code_missing_gw="$(curl -sS -o "$WORKDIR/a2-missing-gw.txt" -w '%{http_code}' "${GW}/dws-admin/instances")"
echo "GET /dws-admin/instances (no Authorization header, via Gateway) -> HTTP $code_missing_gw"
cat "$WORKDIR/a2-missing-gw.txt"; echo
[ "$code_missing_gw" = "401" ] || fail_and_exit "expected 401 for a missing bearer through the Gateway, got $code_missing_gw"

code_tampered_gw="$(curl -sS -o "$WORKDIR/a2-tampered-gw.txt" -w '%{http_code}' \
  -H "Authorization: Bearer $TAMPERED_TOKEN" "${GW}/dws-admin/instances")"
echo "GET /dws-admin/instances (tampered-signature bearer, via Gateway) -> HTTP $code_tampered_gw"
[ "$code_tampered_gw" = "401" ] || fail_and_exit "expected 401 for a tampered-signature bearer through the Gateway, got $code_tampered_gw"
pass "missing and tampered-signature bearers both get HTTP 401 through the Gateway"

# The decisive check: hit the admin Service's Dapr-invoke path DIRECTLY, entirely bypassing
# APISIX (this port-forward goes straight to the admin Service, whose only port targets the
# sidecar's 3500 in gateway mode -- see templates/admin/service.yaml). If the SAME 401/200
# behavior reproduces here, APISIX cannot be the component doing the enforcement, because
# APISIX is not in this request's path at all.
code_missing_direct="$(curl -sS -o "$WORKDIR/a2-missing-direct.txt" -w '%{http_code}' \
  "${ADMIN_DIRECT}/v1.0/invoke/${RELEASE}-admin/method/instances")"
echo "GET (direct-to-sidecar, no Authorization, APISIX bypassed) -> HTTP $code_missing_direct"
cat "$WORKDIR/a2-missing-direct.txt"; echo
[ "$code_missing_direct" = "401" ] || fail_and_exit "expected 401 direct-to-sidecar with no bearer (APISIX bypassed), got $code_missing_direct"

code_valid_direct="$(curl -sS -o "$WORKDIR/a2-valid-direct.txt" -w '%{http_code}' \
  -H "Authorization: Bearer $VALID_TOKEN" \
  "${ADMIN_DIRECT}/v1.0/invoke/${RELEASE}-admin/method/instances")"
echo "GET (direct-to-sidecar, valid bearer, APISIX bypassed) -> HTTP $code_valid_direct"
[ "$code_valid_direct" = "200" ] || fail_and_exit "expected 200 direct-to-sidecar with a valid bearer (APISIX bypassed), got $code_valid_direct"

pass "direct-to-sidecar requests (APISIX entirely bypassed via a second port-forward straight to the admin Service) reproduce the identical 401/200 behavior -- Dapr's bearer middleware is the enforcement point, APISIX adds none of its own"

# ================================================================================================
# 8. Assertion 3: SSE delivers a named event frame while the connection is still open (i.e. it
#    is not buffered/batched until close) through Gateway -> APISIX -> Dapr invoke -> Nest.
# ================================================================================================
echo
echo "=== 8. Assertion 3: SSE streams incrementally through the Gateway (not buffered) ==="
cat > "$WORKDIR/sse-probe.js" <<'NODE_EOF'
const http = require('http');
const fs = require('fs');

const [, , urlStr, token, resultPath, lingerMsArg] = process.argv;
const lingerMs = parseInt(lingerMsArg || '5000', 10);
const url = new URL(urlStr);
const result = { connectedAt: null, firstEventAt: null, closedAt: null, firstEventRaw: null, error: null, status: null };

function finish(exitCode) {
  fs.writeFileSync(resultPath, JSON.stringify(result, null, 2));
  process.exit(exitCode);
}

const req = http.request(
  {
    hostname: url.hostname,
    port: url.port,
    path: url.pathname + url.search,
    method: 'GET',
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
  },
  (res) => {
    result.connectedAt = Date.now();
    result.status = res.statusCode;
    fs.writeFileSync(`${resultPath}.connected`, '1');
    if (res.statusCode !== 200) {
      let body = '';
      res.on('data', (c) => (body += c));
      res.on('end', () => {
        result.error = `unexpected status ${res.statusCode}`;
        result.body = body;
        finish(1);
      });
      return;
    }
    let buf = '';
    res.on('data', (chunk) => {
      buf += chunk.toString('utf8');
      if (!result.firstEventAt && buf.includes('\n\n')) {
        result.firstEventAt = Date.now();
        result.firstEventRaw = buf.slice(0, buf.indexOf('\n\n') + 2);
        // Deliberately keep the connection open for lingerMs AFTER the event arrives, then
        // close it ourselves. If firstEventAt is well before closedAt, the server delivered
        // data mid-stream -- the opposite of "batched at close".
        setTimeout(() => {
          result.closedAt = Date.now();
          req.destroy();
          finish(0);
        }, lingerMs);
      }
    });
    res.on('end', () => {
      if (!result.firstEventAt) {
        result.error = 'stream ended before any event frame arrived';
        finish(1);
      }
    });
    res.on('error', (err) => {
      if (!result.firstEventAt) {
        result.error = `stream error before any event frame arrived: ${err}`;
        finish(1);
      }
    });
  },
);
req.on('error', (err) => {
  result.error = String(err);
  finish(1);
});
req.end();

setTimeout(() => {
  if (!result.firstEventAt) {
    result.error = 'timed out waiting for the first SSE event frame';
    req.destroy();
    finish(1);
  }
}, 45000);
NODE_EOF

SSE_RESULT="$WORKDIR/sse-result.json"
rm -f "${SSE_RESULT}.connected"
node "$WORKDIR/sse-probe.js" "${GW}/dws-admin/instances/events" "$VALID_TOKEN" "$SSE_RESULT" 5000 &
SSE_PID=$!

for _ in $(seq 1 30); do
  [ -f "${SSE_RESULT}.connected" ] && break
  sleep 0.5
done
[ -f "${SSE_RESULT}.connected" ] || { kill "$SSE_PID" >/dev/null 2>&1 || true; dump_debug_state; fail_and_exit "SSE probe never connected"; }

# Trigger the event with a directly-authenticated POST to the Dapr subscription DELIVERY route,
# through the SAME Gateway -> APISIX -> Dapr-invoke path already proven above, rather than
# through Dapr's own automatic pubsub delivery. This is deliberate, not a shortcut: the "Finding"
# block above already showed daprd's own internal (unauthenticated) subscription-discovery call
# gets 401'd by this same bearer gate, so Dapr's automatic delivery to POST /dapr/events/dws
# would be 401'd too and never reach Nest -- that is a separate, already-reported defect, not
# the SSE-transport/buffering question this assertion exists to answer. Posting the identical
# transport-CloudEvent shape Dapr would have sent (`{"data": <our envelope>}`), but WITH a valid
# bearer attached, isolates that transport/buffering question from the pubsub-registration defect.
echo "SSE probe connected; POSTing an io.dws.instance.started transport event through the Gateway (valid bearer, bypassing the broken automatic pubsub delivery -- see the Finding above)..."

INSTANCE_ID="gw-e2e-inst-$(date +%s)"
NOW="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
INNER_ENVELOPE=$(printf '{"id":"%s","source":"gw-e2e","type":"io.dws.instance.started","time":"%s","datacontenttype":"application/json","data":{"instanceId":"%s","workflow":"gw-e2e","version":"v1","appId":"gw-e2e","startedAt":"%s"}}' \
  "$INSTANCE_ID" "$NOW" "$INSTANCE_ID" "$NOW")
TRANSPORT_BODY=$(printf '{"data":%s}' "$INNER_ENVELOPE")
deliver_code="$(curl -sS -o "$WORKDIR/a3-deliver-body.json" -w '%{http_code}' \
  -X POST -H "Authorization: Bearer $VALID_TOKEN" -H 'Content-Type: application/json' \
  -d "$TRANSPORT_BODY" "${GW}/dws-admin/dapr/events/dws")"
echo "POST /dws-admin/dapr/events/dws (valid bearer, via Gateway) -> HTTP $deliver_code"
cat "$WORKDIR/a3-deliver-body.json"; echo
# Nest's default POST status is 201 (no @HttpCode override on DaprSubscriptionController.deliver);
# Dapr's own subscription-delivery contract only cares that the response is 2xx (SUCCESS).
[[ "$deliver_code" =~ ^20[0-9]$ ]] || { dump_debug_state; fail_and_exit "delivering io.dws.instance.started via the Gateway-routed dapr/events/dws endpoint failed (HTTP $deliver_code)"; }

wait "$SSE_PID" || true
echo "--- SSE probe result ---"
cat "$SSE_RESULT"
echo

sse_status="$(jq -r '.status' "$SSE_RESULT")"
sse_error="$(jq -r '.error' "$SSE_RESULT")"
sse_connected_at="$(jq -r '.connectedAt' "$SSE_RESULT")"
sse_first_event_at="$(jq -r '.firstEventAt' "$SSE_RESULT")"
sse_closed_at="$(jq -r '.closedAt' "$SSE_RESULT")"
sse_first_event_raw="$(jq -r '.firstEventRaw' "$SSE_RESULT")"

[ "$sse_status" = "200" ] || { dump_debug_state; fail_and_exit "SSE request did not get HTTP 200 (got $sse_status, error: $sse_error)"; }
[ "$sse_error" = "null" ] || { dump_debug_state; fail_and_exit "SSE probe reported an error: $sse_error"; }
[ "$sse_first_event_at" != "null" ] || { dump_debug_state; fail_and_exit "no SSE event frame ever arrived (stream may be fully buffered until close, or never delivered)"; }
echo "$sse_first_event_raw" | grep -q '^event: instance' \
  || fail_and_exit "first SSE frame is not a named 'instance' event: $sse_first_event_raw"

# The decisive non-buffering check: the event must have arrived BEFORE we closed the connection
# ourselves (closedAt is only set lingerMs after firstEventAt, by our own client code) --
# i.e. the server pushed data mid-stream rather than withholding it until the response ended.
[ "$sse_first_event_at" -lt "$sse_closed_at" ] \
  || fail_and_exit "SSE event only appeared at/after our own deliberate close -- looks buffered until close"
delivery_ms=$((sse_first_event_at - sse_connected_at))
linger_actual_ms=$((sse_closed_at - sse_first_event_at))
echo "SSE event arrived ${delivery_ms}ms after connect, while the connection stayed open ${linger_actual_ms}ms afterward before we closed it ourselves."
pass "SSE on /dws-admin/instances/events delivers a named 'event: instance' frame ${delivery_ms}ms after connect, observed while the connection was still open (closed ${linger_actual_ms}ms later by the client, not the server) -- NOT buffered/batched at close, through Gateway -> APISIX -> Dapr invoke -> Nest"

# ================================================================================================
# 9. Assertion 4: console root routes to the console Service; /dws-admin/* still wins.
# ================================================================================================
echo
echo "=== 9. Assertion 4: console root routing and admin-prefix precedence ==="
root_headers="$WORKDIR/a4-root-headers.txt"
root_body="$WORKDIR/a4-root-body.html"
root_code="$(curl -sS -D "$root_headers" -o "$root_body" -w '%{http_code}' "${GW}/")"
echo "GET / -> HTTP $root_code"
cat "$root_headers"
# The console SPA issues its own root redirect (/ -> /workflows, a same-app default view) --
# that is expected app behavior, not a gateway routing failure. Confirm it's console-owned
# (Location has no /dws-admin prefix) and follow it once to confirm the final page is really
# served by the console Service, not misrouted elsewhere.
if [ "$root_code" = "307" ] || [ "$root_code" = "302" ]; then
  redirect_location="$(grep -i '^location:' "$root_headers" | tr -d '\r' | awk '{print $2}')"
  echo "console root redirected to: $redirect_location"
  case "$redirect_location" in
    /dws-admin*) fail_and_exit "console root redirected into the admin prefix ($redirect_location) -- unexpected" ;;
  esac
  root_code="$(curl -sS -D "$root_headers" -o "$root_body" -w '%{http_code}' "${GW}${redirect_location}")"
  echo "GET ${redirect_location} (following console's own redirect) -> HTTP $root_code"
  cat "$root_headers"
fi
[ "$root_code" = "200" ] || fail_and_exit "expected 200 from the console (after following its own same-app redirect, if any), got $root_code"
grep -qi 'text/html' "$root_headers" || fail_and_exit "console root did not return text/html (got: $(cat "$root_headers"))"
grep -qi '<html' "$root_body" || fail_and_exit "console root body does not look like the console's HTML shell"
pass "GET / routes to the console Service (redirects same-app to its default view exactly as the console SPA does outside the gateway too) and ultimately returns HTTP 200 text/html"

admin_prefix_code="$(curl -sS -o "$WORKDIR/a4-admin-body.json" -w '%{http_code}' \
  -H "Authorization: Bearer $VALID_TOKEN" "${GW}/dws-admin/instances")"
[ "$admin_prefix_code" = "200" ] || fail_and_exit "expected 200 for /dws-admin/instances (precedence check), got $admin_prefix_code"
jq -e 'has("items")' "$WORKDIR/a4-admin-body.json" >/dev/null \
  || fail_and_exit "/dws-admin/instances did not return the admin JSON shape -- console may have shadowed the admin route"
pass "/dws-admin/instances still returns the admin JSON payload (not the console's HTML) -- the admin PathPrefix rule wins over the console's catch-all /"

echo
echo "=== ${PASS_COUNT} assertions passed. verify-gateway-sse-path.sh: all checks passed ==="
