#!/usr/bin/env bash
set -euo pipefail

chart_dir="${1:-charts/dws}"
rendered="$(mktemp)"
trap 'rm -f "$rendered"' EXIT

# --- Scheduling defaults deep-merge across every chart-owned Deployment -----------------------
# (controller, admin, console — admin-gateway was removed by the API Gateway change).
helm template dws "$chart_dir" \
  --api-versions dapr.io/v1alpha1 \
  --set dapr.enabled=false \
  --set postgresql.enabled=false \
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws \
  --set console.enabled=true \
  --set defaults.resources.requests.cpu=125m \
  --set defaults.resources.requests.memory=192Mi \
  --set defaults.resources.limits.memory=384Mi \
  --set defaults.nodeSelector.workload=dws \
  --set defaults.tolerations[0].key=reserved \
  --set defaults.tolerations[0].operator=Exists \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].key=workload \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].operator=In \
  --set defaults.affinity.nodeAffinity.requiredDuringSchedulingIgnoredDuringExecution.nodeSelectorTerms[0].matchExpressions[0].values[0]=dws \
  > "$rendered"

deployments="$(grep -c '^kind: Deployment$' "$rendered")"
test "$deployments" -eq 3

for value in 'cpu: 125m' 'memory: 192Mi' 'memory: 384Mi' 'workload: dws' '- key: reserved' 'operator: In'; do
  test "$(grep -Ec "^[[:space:]]*$value$" "$rendered")" -eq 3
done

# --- APISIX is a pinned, explicitly versioned dependency ------------------------------------
grep -A3 -- '- name: apisix' "$chart_dir/Chart.yaml" | grep -q 'version: 2.16.0'
grep -A3 -- '- name: apisix' "$chart_dir/Chart.yaml" | grep -q 'repository: https://apache.github.io/apisix-helm-chart'
grep -A3 -- '- name: apisix' "$chart_dir/Chart.yaml" | grep -q 'condition: apisix.enabled'
grep -q -- '- name: apisix' "$chart_dir/Chart.lock"
test -f "$chart_dir/charts/apisix-2.16.0.tgz"

base_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)

gateway_valid_args=(
  "${base_args[@]}"
  --api-versions gateway.networking.k8s.io/v1
  --api-versions apisix.apache.org/v1alpha1
  --set console.enabled=true
  --set auth.enabled=true
  --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin
  --set apiGateway.enabled=true
)

# --- Positive: bundled APISIX mode renders ---------------------------------------------------
helm template dws "$chart_dir" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=true \
  > "$rendered"
grep -q 'kind: GatewayClass' "$rendered"
grep -q 'kind: Gateway$' "$rendered"

# --- Positive: external APISIX mode renders with an explicit GatewayProxy name --------------
helm template dws "$chart_dir" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=false \
  --set apiGateway.external.gatewayProxyName=existing-gateway-proxy \
  > "$rendered"
grep -q 'kind: GatewayClass' "$rendered"

fail_render() {
  local description="$1"
  shift
  if helm template dws "$chart_dir" "$@" > /dev/null 2>/tmp/dws-values-schema-test-err; then
    echo "FAIL: expected render to fail: $description" >&2
    exit 1
  fi
}

assert_error_contains() {
  local needle="$1"
  if ! grep -qF "$needle" /tmp/dws-values-schema-test-err; then
    echo "FAIL: expected error output to contain: $needle" >&2
    cat /tmp/dws-values-schema-test-err >&2
    exit 1
  fi
}

# --- Negative: gateway mode without auth is rejected -----------------------------------------
fail_render "apiGateway.enabled without auth.enabled" \
  "${base_args[@]}" \
  --api-versions gateway.networking.k8s.io/v1 --api-versions apisix.apache.org/v1alpha1 \
  --set console.enabled=true \
  --set apiGateway.enabled=true \
  --set apisix.enabled=true
assert_error_contains "auth.enabled=true"

# --- Negative: gateway mode without admin is rejected ----------------------------------------
fail_render "apiGateway.enabled without admin.enabled" \
  "${base_args[@]}" \
  --api-versions gateway.networking.k8s.io/v1 --api-versions apisix.apache.org/v1alpha1 \
  --set console.enabled=true \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --set admin.enabled=false \
  --set apiGateway.enabled=true \
  --set apisix.enabled=true
assert_error_contains "admin.enabled=true"

# --- Negative: gateway mode without console is rejected --------------------------------------
fail_render "apiGateway.enabled without console.enabled" \
  "${base_args[@]}" \
  --api-versions gateway.networking.k8s.io/v1 --api-versions apisix.apache.org/v1alpha1 \
  --set console.enabled=false \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --set apiGateway.enabled=true \
  --set apisix.enabled=true
assert_error_contains "console.enabled=true"

# --- Negative: external mode without an existing GatewayProxy name is rejected ---------------
fail_render "external apisix mode without apiGateway.external.gatewayProxyName" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=false
assert_error_contains "apiGateway.external.gatewayProxyName"

# --- Negative: external mode without required cluster CRDs is rejected ----------------------
fail_render "external apisix mode without Gateway API CRDs" \
  "${base_args[@]}" \
  --set console.enabled=true \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --set apiGateway.enabled=true \
  --set apisix.enabled=false \
  --set apiGateway.external.gatewayProxyName=existing-gateway-proxy
assert_error_contains "Gateway API"

fail_render "external apisix mode without APISIX CRDs" \
  "${base_args[@]}" \
  --api-versions gateway.networking.k8s.io/v1 \
  --set console.enabled=true \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --set apiGateway.enabled=true \
  --set apisix.enabled=false \
  --set apiGateway.external.gatewayProxyName=existing-gateway-proxy
assert_error_contains "apisix.apache.org"

# --- Positive: bundled mode does not false-fail preflight even without cluster CRDs ----------
helm template dws "$chart_dir" \
  "${base_args[@]}" \
  --set console.enabled=true \
  --set auth.enabled=true \
  --set auth.issuer=https://idp.example.test \
  --set auth.audience=dws-admin \
  --set apiGateway.enabled=true \
  --set apisix.enabled=true \
  > "$rendered"
grep -q 'kind: GatewayClass' "$rendered"

# --- Negative: createGatewayClass=false without an explicit class name is rejected ----------
fail_render "createGatewayClass=false without gatewayClassName" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=true \
  --set apiGateway.createGatewayClass=false
assert_error_contains "apiGateway.gatewayClassName"

# --- Negative: tls.enabled without a certificateName is rejected ----------------------------
fail_render "apiGateway.tls.enabled without certificateName" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=true \
  --set apiGateway.tls.enabled=true
assert_error_contains "apiGateway.tls.certificateName"

# --- Negative: legacy console.ingress.enabled=true fails with migration guidance ------------
fail_render "console.ingress.enabled=true legacy value" \
  "${base_args[@]}" \
  --set console.enabled=true \
  --set console.ingress.enabled=true
assert_error_contains "apiGateway.enabled"
assert_error_contains "hostname"
assert_error_contains "TLS"
assert_error_contains "OIDC redirect"

# --- Positive: migrated values (legacy ingress disabled) render no Ingress -------------------
helm template dws "$chart_dir" \
  "${gateway_valid_args[@]}" \
  --set apisix.enabled=true \
  --set console.ingress.enabled=false \
  > "$rendered"
! grep -q '^kind: Ingress$' "$rendered"

rm -f /tmp/dws-values-schema-test-err

echo "values-schema-test.sh: all checks passed"
