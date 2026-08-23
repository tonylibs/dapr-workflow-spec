#!/usr/bin/env bash
# Verifies the Dapr-native OAuth path used by CALL_HTTP and CALL_OPENAPI steps.
#
# Prerequisites: a writable Kubernetes cluster, kubectl, Helm 3, and network access to the
# Dapr Helm repository. CI creates a disposable kind cluster before running this script. The
# script owns only the two namespaces named below and removes them on exit.
set -euo pipefail

DAPR_VERSION="${DAPR_VERSION:-1.18.1}"
TEST_NAMESPACE="${OAUTH_E2E_NAMESPACE:-dws-oauth-e2e}"
DAPR_NAMESPACE="${OAUTH_E2E_DAPR_NAMESPACE:-dws-oauth-e2e-dapr-system}"
DAPR_RELEASE="${OAUTH_E2E_DAPR_RELEASE:-dws-oauth-e2e-dapr}"
ENDPOINT_NAME="oauth-e2e-endpoint-v1"
CLIENT_APP_ID="oauth-e2e-client"

delete_namespace_and_wait() {
  local namespace="$1"
  kubectl delete namespace "$namespace" --ignore-not-found --wait=false >/dev/null 2>&1 || true
  kubectl wait --for=delete namespace/"$namespace" --timeout=3m >/dev/null 2>&1 || true
}

cleanup() {
  helm uninstall "$DAPR_RELEASE" --namespace "$DAPR_NAMESPACE" --wait --timeout 2m >/dev/null 2>&1 || true
  delete_namespace_and_wait "$TEST_NAMESPACE"
  delete_namespace_and_wait "$DAPR_NAMESPACE"
}

command -v helm >/dev/null
command -v kubectl >/dev/null

# Make a retry safe even if a preceding process was interrupted while its namespaces were
# terminating. This is intentionally limited to the fixed disposable-cluster names above.
cleanup
trap cleanup EXIT

echo "Installing Dapr Helm chart version ${DAPR_VERSION}"
helm repo add dapr https://dapr.github.io/helm-charts/ --force-update
helm repo update dapr
helm upgrade --install "$DAPR_RELEASE" dapr/dapr \
  --namespace "$DAPR_NAMESPACE" --create-namespace \
  --version "$DAPR_VERSION" --wait --timeout 5m

kubectl create namespace "$TEST_NAMESPACE"

# This is the version-scoped resource shape emitted by StackSynthesizer for one OAuth policy:
# same HTTPEndpoint/Component/Configuration name, Component scopes, secretKeyRefs, and a narrow
# appHttpPipeline pathFilter. The fixed v1 suffix stands in for the definition version hash.
kubectl apply --namespace "$TEST_NAMESPACE" -f - <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: oauth-e2e-client-id
type: Opaque
stringData:
  value: oauth-e2e-client
---
apiVersion: v1
kind: Secret
metadata:
  name: oauth-e2e-client-secret
type: Opaque
stringData:
  value: oauth-e2e-secret
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: oauth-e2e-mock-idp
data:
  server.py: |
    import json
    from http.server import BaseHTTPRequestHandler, HTTPServer

    class Handler(BaseHTTPRequestHandler):
        def _write(self, code, value, content_type="text/plain"):
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.end_headers()
            self.wfile.write(value.encode())

        def do_POST(self):
            if self.path != "/token":
                return self._write(404, "not found")
            # The test intentionally does not log credentials. Dapr's middleware must obtain this
            # token before it can call /intended, proving secret-backed client credentials work.
            return self._write(200, json.dumps({
                "access_token": "issued-oauth-token",
                "token_type": "Bearer",
                "expires_in": 300
            }), "application/json")

        def do_GET(self):
            header = self.headers.get("Authorization", "<none>")
            if self.path in ("/intended", "/unrelated"):
                return self._write(200, header)
            return self._write(404, "not found")

        def log_message(self, format, *args):
            pass

    HTTPServer(("", 8080), Handler).serve_forever()
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: oauth-e2e-mock-idp
spec:
  replicas: 1
  selector:
    matchLabels:
      app: oauth-e2e-mock-idp
  template:
    metadata:
      labels:
        app: oauth-e2e-mock-idp
    spec:
      containers:
        - name: mock-idp
          image: python:3.13-alpine
          command: ["python", "/app/server.py"]
          ports:
            - containerPort: 8080
          volumeMounts:
            - name: source
              mountPath: /app
      volumes:
        - name: source
          configMap:
            name: oauth-e2e-mock-idp
---
apiVersion: v1
kind: Service
metadata:
  name: oauth-e2e-mock-idp
spec:
  selector:
    app: oauth-e2e-mock-idp
  ports:
    - port: 8080
      targetPort: 8080
---
apiVersion: dapr.io/v1alpha1
kind: HTTPEndpoint
metadata:
  name: ${ENDPOINT_NAME}
  labels:
    dws.io/workflow: oauth-e2e
    dws.io/version: v1
    dws.io/managed-by: dws-controller
spec:
  baseUrl: http://oauth-e2e-mock-idp.${TEST_NAMESPACE}.svc.cluster.local:8080
scopes:
  - ${CLIENT_APP_ID}
---
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: ${ENDPOINT_NAME}
  labels:
    dws.io/workflow: oauth-e2e
    dws.io/version: v1
    dws.io/managed-by: dws-controller
spec:
  type: middleware.http.oauth2clientcredentials
  version: v1
  metadata:
    - name: clientId
      secretKeyRef:
        name: oauth-e2e-client-id
        key: value
    - name: clientSecret
      secretKeyRef:
        name: oauth-e2e-client-secret
        key: value
    - name: scopes
      value: api.read
    - name: tokenURL
      value: http://oauth-e2e-mock-idp.${TEST_NAMESPACE}.svc.cluster.local:8080/token
    - name: headerName
      value: authorization
    - name: authStyle
      value: "2"
    - name: pathFilter
      value: ^/v1\\.0/invoke/${ENDPOINT_NAME}/method(?:/intended)$
scopes:
  - ${CLIENT_APP_ID}
---
apiVersion: dapr.io/v1alpha1
kind: Configuration
metadata:
  name: ${ENDPOINT_NAME}
  labels:
    dws.io/workflow: oauth-e2e
    dws.io/version: v1
    dws.io/managed-by: dws-controller
spec:
  httpPipeline:
    handlers:
      - name: ${ENDPOINT_NAME}
        type: middleware.http.oauth2clientcredentials
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${CLIENT_APP_ID}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ${CLIENT_APP_ID}
  template:
    metadata:
      labels:
        app: ${CLIENT_APP_ID}
      annotations:
        dapr.io/enabled: "true"
        dapr.io/app-id: ${CLIENT_APP_ID}
        dapr.io/config: ${ENDPOINT_NAME}
    spec:
      containers:
        - name: client
          image: curlimages/curl:8.11.1
          command: ["sh", "-ec", "while true; do sleep 3600; done"]
EOF

kubectl rollout status deployment/oauth-e2e-mock-idp --namespace "$TEST_NAMESPACE" --timeout=2m
kubectl rollout status deployment/"$CLIENT_APP_ID" --namespace "$TEST_NAMESPACE" --timeout=3m

invoke() {
  kubectl exec --namespace "$TEST_NAMESPACE" deployment/"$CLIENT_APP_ID" -c client -- \
    curl --fail --silent --show-error --connect-timeout 3 --max-time 15 \
    "http://127.0.0.1:3500/v1.0/invoke/${ENDPOINT_NAME}/method/$1"
}

# Component discovery is asynchronous. The first intended call must eventually return the token
# injected by middleware; a successful raw endpoint response without that header is not enough.
for attempt in $(seq 1 30); do
  intended="$(invoke intended 2>/dev/null || true)"
  if [ "$intended" = "Bearer issued-oauth-token" ]; then
    break
  fi
  if [ "$attempt" = 30 ]; then
    echo "expected OAuth token on the intended endpoint, got: ${intended:-<request failed>}" >&2
    kubectl logs --namespace "$TEST_NAMESPACE" deployment/"$CLIENT_APP_ID" -c daprd --tail=300 || true
    exit 1
  fi
  sleep 2
done

unrelated="$(invoke unrelated)"
if [ "$unrelated" != "<none>" ]; then
  echo "OAuth token leaked past pathFilter to unrelated endpoint: $unrelated" >&2
  exit 1
fi

echo "OAuth endpoint isolation passed: intended path received the issued token; unrelated path did not."
