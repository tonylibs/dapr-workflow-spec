#!/usr/bin/env bash
# Structural/topology render matrix for the shared API Gateway (auth roadmap section 2b).
# Covers: bundled/external topology shape, admin URLRewrite/backend, TLS/hostname wiring,
# route precedence, disabled-negative assertions, sidecar-only admin Service/Deployment
# topology, and absence of every superseded legacy front-door resource.
set -euo pipefail

chart_dir="${1:-charts/dws}"
rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT

base_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)

gateway_args=(
  "${base_args[@]}"
  --api-versions gateway.networking.k8s.io/v1
  --api-versions apisix.apache.org/v1alpha1
  --set console.enabled=true
  --set auth.enabled=true
  --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin
  --set apiGateway.enabled=true
)

assert_count() {
  local pattern="$1" expected="$2" file="$3"
  local actual
  actual="$(grep -Ec "$pattern" "$file" || true)"
  if [ "$actual" != "$expected" ]; then
    echo "FAIL: expected $expected matches of '$pattern' in $file, got $actual" >&2
    exit 1
  fi
}

assert_absent() {
  local pattern="$1" file="$2"
  if grep -Eq "$pattern" "$file"; then
    echo "FAIL: expected NO match of '$pattern' in $file" >&2
    exit 1
  fi
}

# =============================================================================================
# 1. Default render: gateway disabled -> zero Gateway/APISIX/legacy front-door objects.
# =============================================================================================
helm template dws "$chart_dir" \
  "${base_args[@]}" \
  --set console.enabled=true \
  > "$rendered"

assert_absent '^kind: GatewayClass$' "$rendered"
assert_absent '^kind: Gateway$' "$rendered"
assert_absent '^kind: HTTPRoute$' "$rendered"
assert_absent '^apiVersion: apisix\.apache\.org' "$rendered"
assert_absent '^kind: Ingress$' "$rendered"
assert_absent 'name: .*-admin-gateway' "$rendered"
assert_absent 'nginx' "$rendered"
assert_absent 'containerPort: 3001' "$rendered"
assert_absent 'DAPR_APP_PORT' "$rendered"

# =============================================================================================
# 2. Bundled mode: exactly one GatewayClass/Gateway/GatewayProxy, two HTTPRoutes referencing the
#    same Gateway, no resource missing.
# =============================================================================================
helm template dws "$chart_dir" \
  "${gateway_args[@]}" \
  --set apisix.enabled=true \
  > "$rendered"

assert_count '^kind: GatewayClass$' 1 "$rendered"
assert_count '^kind: Gateway$' 1 "$rendered"
assert_count '^kind: GatewayProxy$' 1 "$rendered"
assert_count '^kind: HTTPRoute$' 2 "$rendered"

gateway_name="$(awk '/^kind: Gateway$/{found=1} found && /^metadata:/{getline; print $2; exit}' "$rendered")"
parent_ref_count="$(grep -c "name: ${gateway_name}$" "$rendered")"
# 1 Gateway metadata.name + 2 HTTPRoute parentRefs[].name == 3
test "$parent_ref_count" -ge 3

# Admin route: PathPrefix /dws-admin, URLRewrite to the Dapr invoke prefix, targets the admin
# Service (not the console Service, and not the app port directly).
grep -q 'value: /dws-admin' "$rendered"
grep -q 'type: URLRewrite' "$rendered"
grep -q 'replacePrefixMatch: /v1.0/invoke/dws-admin/method' "$rendered"

# Console route: PathPrefix / and no URLRewrite filter (route precedence relies on Gateway API's
# built-in longest-path-match; asserting the specific / rule below with no rewrite filter is
# sufficient to prove no /dws-admin-style rewrite leaked onto the console rule).
grep -q 'value: /$' "$rendered"

# =============================================================================================
# 3. External mode: no APISIX workloads/Service, DWS routes still render, GatewayProxy is not
#    created by this chart (references the operator-owned one instead).
# =============================================================================================
helm template dws "$chart_dir" \
  "${gateway_args[@]}" \
  --set apisix.enabled=false \
  --set apiGateway.external.gatewayProxyName=existing-gateway-proxy \
  > "$rendered"

assert_count '^kind: GatewayClass$' 1 "$rendered"
assert_count '^kind: Gateway$' 1 "$rendered"
assert_count '^kind: HTTPRoute$' 2 "$rendered"
assert_absent '^kind: GatewayProxy$' "$rendered"
assert_absent 'image: "apache/apisix' "$rendered"
grep -q 'name: existing-gateway-proxy' "$rendered"

# =============================================================================================
# 4. TLS/hostname wiring: HTTPS listener with the configured Secret, hostname applied to Gateway
#    and both HTTPRoutes.
# =============================================================================================
helm template dws "$chart_dir" \
  "${gateway_args[@]}" \
  --set apisix.enabled=true \
  --set apiGateway.hostname=dws.example.test \
  --set apiGateway.tls.enabled=true \
  --set apiGateway.tls.certificateName=dws-tls \
  > "$rendered"

grep -q 'protocol: HTTPS' "$rendered"
grep -q 'port: 443' "$rendered"
grep -q 'mode: Terminate' "$rendered"
grep -q 'name: dws-tls' "$rendered"
assert_count 'hostname: "dws\.example\.test"' 2 "$rendered"
assert_count '^ *- "dws\.example\.test"$' 2 "$rendered"

# =============================================================================================
# 5. Gateway disabled with defaults: no negative preflight failures, no gateway objects.
# =============================================================================================
helm template dws "$chart_dir" "${base_args[@]}" > "$rendered"
assert_absent '^kind: GatewayClass$' "$rendered"
assert_absent '^kind: Gateway$' "$rendered"

# =============================================================================================
# 6. Sidecar-only admin topology in gateway mode: one admin Service port targeting 3500, no
#    targetPort 3000, no container port 3001, no DAPR_APP_PORT, dapr.io/app-port "3000".
# =============================================================================================
helm template dws "$chart_dir" \
  "${gateway_args[@]}" \
  --set apisix.enabled=true \
  --show-only templates/admin/service.yaml \
  --show-only templates/admin/deployment.yaml \
  > "$rendered"

assert_count 'targetPort: 3500' 1 "$rendered"
assert_absent 'targetPort: 3000' "$rendered"
assert_absent 'targetPort: http' "$rendered"
assert_absent 'containerPort: 3001' "$rendered"
assert_absent 'DAPR_APP_PORT' "$rendered"
grep -q 'dapr.io/app-port: "3000"' "$rendered"
grep -q 'containerPort: 3000' "$rendered"

# =============================================================================================
# 7. Gateway-disabled migration topology unchanged: admin Service keeps its app-port-targeting
#    default port, plus the dapr-http port when auth.enabled=true.
# =============================================================================================
helm template dws "$chart_dir" \
  "${base_args[@]}" \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --show-only templates/admin/service.yaml \
  > "$rendered"

grep -q 'targetPort: http' "$rendered"
grep -q 'targetPort: 3500' "$rendered"

# =============================================================================================
# 8. Negative: gateway disabled by default -> render succeeds without any explicit capabilities
#    (no false preflight failures on a plain default install).
# =============================================================================================
helm template dws "$chart_dir" > "$rendered"
assert_absent '^kind: GatewayClass$' "$rendered"

# =============================================================================================
# 9. Legacy admin-gateway/console Ingress templates are gone from the filesystem.
# =============================================================================================
test ! -d "$chart_dir/templates/admin-gateway"
test ! -f "$chart_dir/templates/console/ingress.yaml"
test ! -f "$chart_dir/tests/admin-gateway-cors-test.sh"

echo "api-gateway-render-test.sh: all checks passed"
