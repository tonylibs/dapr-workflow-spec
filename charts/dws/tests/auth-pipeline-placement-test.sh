#!/usr/bin/env bash
set -euo pipefail

# Pins the DELIBERATELY ASYMMETRIC bearer-middleware pipeline placement introduced by the
# 2026-09-13 pubsub-discovery fix (auth roadmap dws-auth.md "Decision (2026-09-13)"):
#
#   - dws-admin      -> spec.httpPipeline    (gates the sidecar's own Dapr HTTP API; leaves
#                                             daprd's internal /dapr/subscribe + pub/sub
#                                             delivery un-gated so dws.events flows)
#   - dws-controller -> spec.appHttpPipeline (reached only by Dapr service invocation over
#                                             internal gRPC, which never traverses httpPipeline;
#                                             moving it would silently delete the Phase 2 gate)
#
# This test exists so the divergence cannot be "tidied up" back to matching without a red CI.

chart_dir="${1:-charts/dws}"

auth_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
  --set auth.enabled=true
  --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin
)

admin_config="$(helm template dws "$chart_dir" "${auth_args[@]}" \
  --show-only templates/admin/auth-configuration.yaml)"
controller_config="$(helm template dws "$chart_dir" "${auth_args[@]}" \
  --show-only templates/controller/auth-configuration.yaml)"

fail() { echo "FAIL: $1" >&2; exit 1; }

# --- admin: MUST be httpPipeline, MUST NOT be appHttpPipeline --------------------------------
echo "$admin_config" | grep -qE '^  httpPipeline:$' \
  || fail "admin Configuration is not on spec.httpPipeline (pubsub-discovery fix regressed)"
if echo "$admin_config" | grep -qE 'appHttpPipeline'; then
  fail "admin Configuration is back on spec.appHttpPipeline — that re-gates /dapr/subscribe and breaks dws.events"
fi
echo "$admin_config" | grep -q 'name: dws-admin-config' \
  || fail "admin Configuration name is not dws-admin-config"
echo "$admin_config" | grep -q 'type: middleware.http.bearer' \
  || fail "admin Configuration handler is not middleware.http.bearer"

# --- controller: MUST stay appHttpPipeline, MUST NOT be httpPipeline -------------------------
echo "$controller_config" | grep -qE '^  appHttpPipeline:$' \
  || fail "controller Configuration is no longer on spec.appHttpPipeline — this silently deletes the Phase 2 gate (service invocation arrives over gRPC and never traverses httpPipeline)"
if echo "$controller_config" | grep -qE '^  httpPipeline:$'; then
  fail "controller Configuration moved to spec.httpPipeline — the Phase 2 gate is now bypassed for Dapr service invocation"
fi
echo "$controller_config" | grep -q 'name: dws-controller-config' \
  || fail "controller Configuration name is not dws-controller-config"
echo "$controller_config" | grep -q 'type: middleware.http.bearer' \
  || fail "controller Configuration handler is not middleware.http.bearer"

echo "auth-pipeline-placement-test.sh: all checks passed"
