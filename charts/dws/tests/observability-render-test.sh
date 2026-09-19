#!/usr/bin/env bash
# Render matrix for the OpenTelemetry observability surface (observability roadmap Phase 1).
#
# Two things this pins that nothing else can:
#
#   1. The four-way auth x observability matrix. Both features render into ONE Dapr
#      Configuration per component, because dapr.io/config is single-valued. A change that
#      splits them apart, drops the auth pipeline while adding tracing, or emits a second
#      dapr.io/config annotation produces no render error and a green helm lint — only a
#      no-token request in a live cluster would ever reveal it.
#   2. The disabled-state no-op. observability.enabled=false must leave the rendered manifest
#      byte-identical to tests/fixtures/default-render-baseline.yaml, captured from the chart
#      immediately before this feature existed.
#
# Run: bash charts/dws/tests/observability-render-test.sh [chart_dir] [--update-baseline]
set -euo pipefail

chart_dir="charts/dws"
update_baseline=0
for arg in "$@"; do
  case "$arg" in
    --update-baseline) update_baseline=1 ;;
    *) chart_dir="$arg" ;;
  esac
done

baseline="$chart_dir/tests/fixtures/default-render-baseline.yaml"

fail() { echo "FAIL: $1" >&2; exit 1; }

# Bitnami's postgresql subchart generates both passwords randomly on every render, so pin them
# to make the default render byte-comparable. Nothing else is overridden — this IS the default.
baseline_args=(
  --api-versions dapr.io/v1alpha1
  --set postgresql.auth.password=dws-baseline
  --set postgresql.auth.postgresPassword=dws-baseline-super
)

# Feature-matrix renders use the lightweight topology the other chart tests use (no Dapr
# subchart, external Postgres) so the assertions below are about observability, not about
# whichever dependency happens to be installed.
base_args=(
  --api-versions dapr.io/v1alpha1
  --api-versions opentelemetry.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)
auth_args=(
  --set auth.enabled=true
  --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin
)
obs_args=(
  --set observability.enabled=true
)

render() { helm template dws "$chart_dir" "$@"; }

# Count whole-line matches of an extended regex.
count() { grep -Ec "$1" <<<"$2" || true; }

assert_count() {
  local pattern="$1" haystack="$2" expected="$3" message="$4"
  local actual
  actual="$(count "$pattern" "$haystack")"
  [ "$actual" -eq "$expected" ] \
    || fail "$message (expected $expected match(es) of /$pattern/, got $actual)"
}

assert_absent() {
  local pattern="$1" haystack="$2" message="$3"
  ! grep -Eq "$pattern" <<<"$haystack" || fail "$message"
}

# =============================================================================================
# 1. Disabled state is a byte-identical no-op
# =============================================================================================
default_render="$(render "${baseline_args[@]}")"

if [ "$update_baseline" -eq 1 ]; then
  printf '%s\n' "$default_render" > "$baseline"
  echo "observability-render-test.sh: baseline regenerated at $baseline"
  exit 0
fi

[ -f "$baseline" ] || fail "missing baseline fixture $baseline (regenerate with --update-baseline)"

if ! diff -u "$baseline" <(printf '%s\n' "$default_render"); then
  fail "the DEFAULT render changed. observability.enabled=false must stay byte-identical to the pre-observability chart. If the diff above is a deliberate chart change (including a Chart.yaml version/appVersion bump, which lands in every resource's labels), review it and re-record the baseline with: bash $chart_dir/tests/observability-render-test.sh $chart_dir --update-baseline"
fi

assert_absent '^kind: Instrumentation$' "$default_render" \
  "default render contains an Instrumentation resource"
assert_absent 'instrumentation\.opentelemetry\.io/' "$default_render" \
  "default render contains an OpenTelemetry injection annotation"
assert_absent '^[[:space:]]*tracing:$' "$default_render" \
  "default render contains a Dapr tracing block"
# Assert on the source template rather than on `kind: Configuration` or the resource name:
# the Dapr subchart renders its own control-plane Configuration ("daprsystem"), and
# controller/config-component.yaml renders a Dapr *Component* that happens to share the name
# dws-controller-config. Neither is what this check is about.
assert_absent '^# Source: dws/templates/(controller|admin)/configuration\.yaml$' "$default_render" \
  "default render contains a component Dapr Configuration (auth and observability are both off)"

# =============================================================================================
# 2. Four-way auth x observability matrix: exactly one Configuration per enabled component,
#    exact pipeline placement under auth only, tracing under observability only
# =============================================================================================
config_only=(-s templates/controller/configuration.yaml -s templates/admin/configuration.yaml)

# --- auth off, observability off -------------------------------------------------------------
if render "${base_args[@]}" "${config_only[@]}" > /dev/null 2>&1; then
  fail "a Dapr Configuration rendered with both auth and observability disabled"
fi

# --- auth on, observability off ---------------------------------------------------------------
auth_only="$(render "${base_args[@]}" "${auth_args[@]}" "${config_only[@]}")"
assert_count '^kind: Configuration$' "$auth_only" 2 "auth-only should render one Configuration per component"
assert_count '^  appHttpPipeline:$' "$auth_only" 1 "auth-only: controller must keep spec.appHttpPipeline"
assert_count '^  httpPipeline:$' "$auth_only" 1 "auth-only: admin must keep spec.httpPipeline"
assert_absent '^[[:space:]]+tracing:$' "$auth_only" \
  "auth-only render contains a tracing block — tracing must follow observability.enabled alone"

# --- auth off, observability on -----------------------------------------------------------------
obs_only="$(render "${base_args[@]}" "${obs_args[@]}" "${config_only[@]}")"
assert_count '^kind: Configuration$' "$obs_only" 2 "observability-only should render one Configuration per component"
assert_count '^  tracing:$' "$obs_only" 2 "observability-only: both components need spec.tracing"
assert_absent '^[[:space:]]*(app)?[hH]ttpPipeline:$' "$obs_only" \
  "observability-only render contains an auth pipeline — the pipeline must follow auth.enabled alone"
assert_absent 'middleware\.http\.bearer' "$obs_only" \
  "observability-only render contains a bearer handler"

# --- auth on, observability on --------------------------------------------------------------
both="$(render "${base_args[@]}" "${auth_args[@]}" "${obs_args[@]}" "${config_only[@]}")"
assert_count '^kind: Configuration$' "$both" 2 \
  "auth+observability must still render exactly one Configuration per component — a second one cannot be referenced, dapr.io/config is single-valued"
# The admin Configuration must never grow an appHttpPipeline: that would re-gate daprd's own
# /dapr/subscribe discovery and break dws.events delivery (auth roadmap 2026-09-13 decision).
assert_count '^  appHttpPipeline:$' "$both" 1 "auth+observability: exactly the controller must use spec.appHttpPipeline"
assert_count '^  httpPipeline:$' "$both" 1 "auth+observability: exactly the admin must use spec.httpPipeline"
assert_count '^  tracing:$' "$both" 2 "auth+observability: both components need spec.tracing"

# =============================================================================================
# 3. Dapr sampling is pinned to "1" regardless of the agent's tunable rate (ADR 0005 Decision 2)
# =============================================================================================
assert_count '^    samplingRate: "1"$' "$obs_only" 2 'Dapr samplingRate must be the literal string "1"'

custom_rate="$(render "${base_args[@]}" "${obs_args[@]}" --set observability.traces.samplingRate=0.25 \
  -s templates/controller/configuration.yaml -s templates/admin/configuration.yaml \
  -s templates/observability/instrumentation.yaml)"
assert_count '^    samplingRate: "1"$' "$custom_rate" 2 \
  "observability.traces.samplingRate leaked into the Dapr sampling value — the application agent is the only sampling root"
assert_count '^    argument: "0.25"$' "$custom_rate" 1 \
  "the Instrumentation sampler argument does not follow observability.traces.samplingRate"

# =============================================================================================
# 4. OTLP transport mapping: public protocol -> Dapr protocol, endpoint scheme -> isSecure
# =============================================================================================
assert_count '^      protocol: "http"$' "$obs_only" 2 "http/protobuf must map to Dapr protocol http"
assert_count '^      isSecure: false$' "$obs_only" 2 "a plain http:// endpoint must render isSecure false"

secure_grpc="$(render "${base_args[@]}" "${obs_args[@]}" \
  --set observability.otlp.endpoint=https://otlp.example.test:4317 \
  --set observability.otlp.protocol=grpc "${config_only[@]}")"
assert_count '^      protocol: "grpc"$' "$secure_grpc" 2 "grpc must stay grpc in the Dapr tracing block"
assert_count '^      isSecure: true$' "$secure_grpc" 2 "an https:// endpoint must render isSecure true"
assert_count '^      endpointAddress: "https://otlp.example.test:4317"$' "$secure_grpc" 2 \
  "the configured OTLP endpoint did not reach the Dapr tracing block"

# =============================================================================================
# 5. Pod annotations: one dapr.io/config matching the rendered Configuration, targeted injection
# =============================================================================================
deployments_only=(-s templates/controller/deployment.yaml -s templates/admin/deployment.yaml)

pods_default="$(render "${base_args[@]}" "${deployments_only[@]}")"
assert_absent 'dapr\.io/config' "$pods_default" "dapr.io/config rendered with auth and observability both off"
assert_absent 'instrumentation\.opentelemetry\.io/' "$pods_default" \
  "an OpenTelemetry injection annotation rendered with observability disabled"

pods_auth="$(render "${base_args[@]}" "${auth_args[@]}" "${deployments_only[@]}")"
assert_count '^        dapr\.io/config: "dws-controller-config"$' "$pods_auth" 1 "auth-only controller dapr.io/config"
assert_count '^        dapr\.io/config: "dws-admin-config"$' "$pods_auth" 1 "auth-only admin dapr.io/config"
assert_absent 'instrumentation\.opentelemetry\.io/' "$pods_auth" \
  "auth-only render carries an OpenTelemetry injection annotation"

for pods in "$(render "${base_args[@]}" "${obs_args[@]}" "${deployments_only[@]}")" \
            "$(render "${base_args[@]}" "${auth_args[@]}" "${obs_args[@]}" "${deployments_only[@]}")"; do
  assert_count '^        dapr\.io/config: "dws-controller-config"$' "$pods" 1 \
    "controller pod must carry exactly one dapr.io/config naming its own Configuration"
  assert_count '^        dapr\.io/config: "dws-admin-config"$' "$pods" 1 \
    "admin pod must carry exactly one dapr.io/config naming its own Configuration"
  assert_count '^        instrumentation\.opentelemetry\.io/inject-java: "dws-instrumentation"$' "$pods" 1 \
    "controller pod must request Java injection from the release Instrumentation"
  assert_count '^        instrumentation\.opentelemetry\.io/inject-nodejs: "dws-instrumentation"$' "$pods" 1 \
    "admin pod must request Node.js injection from the release Instrumentation"
  # Targeting is mandatory: without it the Operator's webhook tries to instrument the daprd
  # sidecar too, which is a Go binary and cannot take a Java or Node.js agent.
  assert_count '^        instrumentation\.opentelemetry\.io/container-names: "controller"$' "$pods" 1 \
    "controller injection is not targeted at the controller container"
  assert_count '^        instrumentation\.opentelemetry\.io/container-names: "admin"$' "$pods" 1 \
    "admin injection is not targeted at the admin container"
  assert_absent 'inject-dotnet' "$pods" "Phase 1 must not annotate any pod for .NET injection"
done

# =============================================================================================
# 6. Instrumentation resource shape and the OTLP header Secret contract
# =============================================================================================
instrumentation="$(render "${base_args[@]}" "${obs_args[@]}" -s templates/observability/instrumentation.yaml)"
assert_count '^kind: Instrumentation$' "$instrumentation" 1 "exactly one Instrumentation resource"
assert_count '^    type: parentbased_traceidratio$' "$instrumentation" 1 "sampler type"
assert_count '^    argument: "0.1"$' "$instrumentation" 1 "default sampler argument"
assert_count '^      service\.namespace: "dws"$' "$instrumentation" 1 "service.namespace resource attribute"
assert_count '^    endpoint: "http://dws-otel-collector:4318"$' "$instrumentation" 1 "exporter endpoint"
for section in java nodejs dotnet; do
  assert_count "^  $section: \{\}$" "$instrumentation" 1 "the $section instrumentation section is missing"
done
for propagator in tracecontext baggage; do
  assert_count "^    - $propagator$" "$instrumentation" 1 "the $propagator propagator is missing"
done
assert_absent 'OTEL_EXPORTER_OTLP_HEADERS' "$instrumentation" \
  "a header environment variable rendered with no header source configured"

# Signal toggles select otlp/none per signal.
signals="$(render "${base_args[@]}" "${obs_args[@]}" --set observability.metrics.enabled=false \
  -s templates/observability/instrumentation.yaml)"
assert_count '^      value: "none"$' "$signals" 1 "metrics.enabled=false must select exactly one 'none' exporter"
assert_count '^      value: "otlp"$' "$signals" 2 "traces and logs must still export via otlp"

# service.namespace=dws survives an operator attempt to override it.
attrs_values="$(mktemp)"
trap 'rm -f "$attrs_values"' EXIT
cat > "$attrs_values" <<'YAML'
observability:
  enabled: true
  resourceAttributes:
    deployment.environment: production
    service.namespace: operator-override
YAML
attrs="$(render "${base_args[@]}" -f "$attrs_values" -s templates/observability/instrumentation.yaml)"
assert_count '^      service\.namespace: "dws"$' "$attrs" 1 "service.namespace must not be overridable"
assert_count '^      deployment\.environment: "production"$' "$attrs" 1 "extra resource attributes must render"

# No Secret without headers; inline headers produce one; existingSecret wins and creates none.
if render "${base_args[@]}" "${obs_args[@]}" -s templates/observability/otlp-secret.yaml > /dev/null 2>&1; then
  fail "an OTLP Secret rendered with no headers configured"
fi

inline_secret="$(render "${base_args[@]}" "${obs_args[@]}" \
  --set observability.otlp.headers.api-key=secret-value -s templates/observability/otlp-secret.yaml)"
assert_count '^kind: Secret$' "$inline_secret" 1 "inline headers must render exactly one Secret"
assert_count '^  headers: "api-key=secret-value"$' "$inline_secret" 1 "the headers key holds the OTLP key=value list"

inline_instrumentation="$(render "${base_args[@]}" "${obs_args[@]}" \
  --set observability.otlp.headers.api-key=secret-value -s templates/observability/instrumentation.yaml)"
assert_count '^          name: "dws-otlp"$' "$inline_instrumentation" 1 "the Instrumentation must reference the chart's OTLP Secret"
assert_count '^          key: headers$' "$inline_instrumentation" 1 "the Instrumentation must read the headers key"
assert_absent 'secret-value' "$inline_instrumentation" \
  "a header credential was inlined into the Instrumentation resource"

if render "${base_args[@]}" "${obs_args[@]}" \
  --set observability.otlp.existingSecret=platform-otlp \
  --set observability.otlp.headers.api-key=secret-value \
  -s templates/observability/otlp-secret.yaml > /dev/null 2>&1; then
  fail "existingSecret must take precedence over inline headers and suppress the chart-owned Secret"
fi

existing_instrumentation="$(render "${base_args[@]}" "${obs_args[@]}" \
  --set observability.otlp.existingSecret=platform-otlp \
  --set observability.otlp.headers.api-key=secret-value \
  -s templates/observability/instrumentation.yaml)"
assert_count '^          name: "platform-otlp"$' "$existing_instrumentation" 1 \
  "the Instrumentation must reference the operator-supplied Secret"

# =============================================================================================
# 7. Operator preflight: required by default, explicitly bypassable
# =============================================================================================
# No --api-versions opentelemetry.io/v1alpha1 here: that is the "Operator not installed" case.
preflight_out="$(helm template dws "$chart_dir" \
  --api-versions dapr.io/v1alpha1 \
  --set dapr.enabled=false --set postgresql.enabled=false \
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws \
  --set observability.enabled=true 2>&1 || true)"
grep -q 'opentelemetry.io/v1alpha1' <<<"$preflight_out" \
  || fail "missing Operator CRDs did not fail render with the documented guidance"
grep -q 'cert-manager' <<<"$preflight_out" \
  || fail "the preflight failure does not mention the cert-manager prerequisite"
grep -q 'observability.operator.required=false' <<<"$preflight_out" \
  || fail "the preflight failure does not name the explicit opt-out"

helm template dws "$chart_dir" \
  --api-versions dapr.io/v1alpha1 \
  --set dapr.enabled=false --set postgresql.enabled=false \
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws \
  --set observability.enabled=true --set observability.operator.required=false > /dev/null \
  || fail "observability.operator.required=false did not bypass the preflight"

echo "observability-render-test.sh: all checks passed"
