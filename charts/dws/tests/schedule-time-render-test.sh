#!/usr/bin/env bash
# Render matrix for dws-schedule-time in the Core layer (scheduling Phase 6a, openspec change
# scheduling-phase-6a-time-triggers, capability helm-schedule-time-deployment). Covers every render
# scenario in that spec except the auth-on token wiring, which api-tokens-render-test.sh pins:
#   - the default render has exactly one ServiceAccount, Role, RoleBinding and Deployment for the
#     scheduler, and scheduleTime.enabled=false renders none of them;
#   - the Role grants only get/list/watch on configmaps and apps/deployments;
#   - the Deployment is Dapr-enabled with app-id dws-schedule-time and an app port matching the
#     container's HTTP port, and its image comes from scheduleTime.image.*;
#   - the container env: DWS_SCHEDULE_TIME_STATE_STORE names the chart's state store Component,
#     DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL/CLAIM_TTL follow values, POD_NAMESPACE comes from the
#     downward API, and DWS_LOG_FORMAT follows observability values through the shared helper;
#   - values.schema.json rejects unknown and ill-typed scheduleTime values.
#
# Run from the repo root: bash charts/dws/tests/schedule-time-render-test.sh charts/dws
set -euo pipefail

chart_dir="${1:-charts/dws}"
release=dws
name="dws-schedule-time"    # dws.scheduleTime.fullname for release "dws" (= its Dapr app-id)
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

base_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)

checks=0
die() { echo "FAIL: $1" >&2; exit 1; }
ok() { checks=$((checks + 1)); }

# render <scenario> [helm args...]: render to $work/<scenario>.yaml, split into $work/<scenario>.d/doc.N.
render() {
  local scenario="$1"
  shift
  if ! helm template "$release" "$chart_dir" "$@" >"$work/$scenario.yaml" 2>"$work/$scenario.err"; then
    echo "FAIL: helm template unexpectedly failed for scenario '$scenario':" >&2
    head -c 600 "$work/$scenario.err" >&2
    exit 1
  fi
  mkdir -p "$work/$scenario.d"
  awk -v dir="$work/$scenario.d" '
    BEGIN { f = dir "/doc.0" }
    /^---[ \t]*$/ { n++; f = dir "/doc." n; next }
    { print > f }
  ' "$work/$scenario.yaml"
}

# render_fails <scenario> <expected-substring> [helm args...]: render must fail with the substring.
render_fails() {
  local scenario="$1" needle="$2"
  shift 2
  if helm template "$release" "$chart_dir" "$@" >/dev/null 2>"$work/$scenario.err"; then
    die "expected render to fail for scenario '$scenario'"
  fi
  grep -qF -- "$needle" "$work/$scenario.err" \
    || die "scenario '$scenario': error does not contain '$needle' (first 400 bytes: $(head -c 400 "$work/$scenario.err"))"
  ok
}

doc_name() { awk '/^metadata:/ { m = 1; next } m && /^  name:/ { sub(/^  name: */, ""); gsub(/"/, ""); print; exit }' "$1"; }

# docs_of <scenario> <kind> <name>: paths of every document of that kind and metadata.name.
docs_of() {
  local f
  for f in "$work/$1.d"/doc.*; do
    [ -e "$f" ] || continue
    grep -qx "kind: $2" "$f" || continue
    [ "$(doc_name "$f")" = "$3" ] && echo "$f"
  done
  return 0
}

# require_one <scenario> <kind> <name>: path of the single matching document; dies on 0 or >1.
require_one() {
  local found
  found="$(docs_of "$1" "$2" "$3")"
  [ -n "$found" ] || die "scenario '$1': no $2 named '$3' rendered"
  [ "$(wc -l <<<"$found")" = 1 ] || die "scenario '$1': more than one $2 named '$3' rendered"
  echo "$found"
}

# env_entry <file> <VAR>: the env list entry (name line plus its more-indented children).
env_entry() {
  awk -v var="$2" '
    function indent(s) { match(s, /^ */); return RLENGTH }
    !inside && $0 ~ ("^ *- name: \"?" var "\"?$") { inside = 1; base = indent($0); print; next }
    inside { if (indent($0) <= base) exit; print }
  ' "$1"
}

# env_value <file> <VAR>: the literal value of a `value:` env entry.
env_value() { env_entry "$1" "$2" | sed -n 's/^ *value: "\{0,1\}\([^"]*\)"\{0,1\}$/\1/p'; }

# --- 1. Default render: exactly one of each scheduler resource --------------------------------
render defaults "${base_args[@]}"
for kind in ServiceAccount Role RoleBinding Deployment; do
  require_one defaults "$kind" "$name" >/dev/null
done
[ "$(grep -c "^kind: Deployment$" "$work/defaults.yaml")" -ge 1 ] || die "default render has no Deployment at all"
ok
# The full default render (with the Dapr and Postgres subcharts) includes it too.
render full_defaults --api-versions dapr.io/v1alpha1
for kind in ServiceAccount Role RoleBinding Deployment; do
  require_one full_defaults "$kind" "$name" >/dev/null
done
ok

# --- 2. scheduleTime.enabled=false renders no scheduler resource ------------------------------
render disabled "${base_args[@]}" --set scheduleTime.enabled=false
if grep -Eq 'schedule-time|DWS_SCHEDULE_TIME_' "$work/disabled.yaml"; then
  die "scheduleTime.enabled=false still renders something for dws-schedule-time"
fi
render disabled_auth "${base_args[@]}" --set scheduleTime.enabled=false \
  --set auth.enabled=true --set auth.issuer=https://idp.example.test --set auth.audience=dws-admin
if grep -q 'schedule-time' "$work/disabled_auth.yaml"; then
  die "scheduleTime.enabled=false with auth on still renders a dws-schedule-time token Secret or reference"
fi
ok

# --- 3. Role: get/list/watch on configmaps and apps/deployments, nothing else -----------------
role="$(require_one defaults Role "$name")"
rules="$(awk '/^rules:/ { r = 1; next } r && /^[^ #-]/ { exit } r && !/^ *#/ { print }' "$role")"
expected_rules='  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get", "list", "watch"]
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "list", "watch"]'
[ "$rules" = "$expected_rules" ] || die "scheduler Role rules are not exactly get/list/watch on configmaps and apps/deployments:
$rules"
binding="$(require_one defaults RoleBinding "$name")"
grep -qx "  name: $name" <(sed -n '/^roleRef:/,$p' "$binding") || die "RoleBinding does not reference the scheduler Role"
awk '/^subjects:/ { s = 1 } s && /name: / { print; exit }' "$binding" | grep -q "name: $name" \
  || die "RoleBinding subject is not the scheduler ServiceAccount"
ok

# --- 4. Deployment: Dapr annotations, app port, ServiceAccount, probes ------------------------
dep="$(require_one defaults Deployment "$name")"
grep -qx '        dapr.io/enabled: "true"' "$dep" || die "scheduler pod lacks dapr.io/enabled: \"true\""
grep -qx "        dapr.io/app-id: \"$name\"" "$dep" || die "scheduler pod lacks dapr.io/app-id: $name"
app_port="$(sed -n 's/^        dapr.io\/app-port: "\([0-9]*\)"$/\1/p' "$dep")"
container_port="$(awk '/- name: http$/ { h = 1; next } h && /containerPort:/ { print $2; exit }' "$dep")"
[ -n "$app_port" ] && [ "$app_port" = "$container_port" ] \
  || die "dapr.io/app-port ($app_port) does not match the container http port ($container_port)"
grep -qx "      serviceAccountName: $name" "$dep" || die "scheduler pod does not run as its own ServiceAccount"
[ "$(grep -c 'path: /healthz' "$dep")" = 2 ] || die "scheduler liveness/readiness probes do not both hit /healthz"
if grep -q 'dapr.io/api-token-secret' "$dep"; then die "auth off, but the scheduler pod requires a Dapr API token"; fi
if grep -q 'instrumentation.opentelemetry.io' "$dep"; then die "the scheduler pod carries OpenTelemetry injection annotations"; fi
ok

# --- 5. Image from scheduleTime.image.* -------------------------------------------------------
grep -qx '          image: "ghcr.io/tonylibs/dws-schedule-time:[0-9][^"]*"' "$dep" || die "default scheduler image is not a pinned ghcr.io/tonylibs/dws-schedule-time tag"
render image "${base_args[@]}" --set scheduleTime.image.repository=registry.example.test/sched \
  --set scheduleTime.image.tag=9.9.9 --set scheduleTime.image.pullPolicy=Always --set scheduleTime.replicaCount=2
dep_image="$(require_one image Deployment "$name")"
grep -qx '          image: "registry.example.test/sched:9.9.9"' "$dep_image" || die "scheduleTime.image.repository/tag not rendered"
grep -qx '          imagePullPolicy: Always' "$dep_image" || die "scheduleTime.image.pullPolicy not rendered"
grep -qx '  replicas: 2' "$dep_image" || die "scheduleTime.replicaCount not rendered"
ok

# --- 6. Env: state store, intervals, namespace ------------------------------------------------
store="$(env_value "$dep" DWS_SCHEDULE_TIME_STATE_STORE)"
[ -n "$store" ] || die "scheduler container has no DWS_SCHEDULE_TIME_STATE_STORE"
component="$(require_one defaults Component "$store")"
grep -qx '  type: state.redis' "$component" || die "DWS_SCHEDULE_TIME_STATE_STORE ($store) is not the chart's state.redis Component"
[ "$(env_value "$dep" DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL)" = PT15S ] || die "DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL is not PT15S by default"
[ "$(env_value "$dep" DWS_SCHEDULE_TIME_CLAIM_TTL)" = PT24H ] || die "DWS_SCHEDULE_TIME_CLAIM_TTL is not PT24H by default"
env_entry "$dep" POD_NAMESPACE | grep -q 'fieldPath: metadata.namespace' || die "POD_NAMESPACE does not come from metadata.namespace"
render intervals "${base_args[@]}" --set scheduleTime.afterPollInterval=PT1M --set scheduleTime.claimTtl=PT6H
dep_intervals="$(require_one intervals Deployment "$name")"
[ "$(env_value "$dep_intervals" DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL)" = PT1M ] || die "scheduleTime.afterPollInterval is not rendered"
[ "$(env_value "$dep_intervals" DWS_SCHEDULE_TIME_CLAIM_TTL)" = PT6H ] || die "scheduleTime.claimTtl is not rendered"
render fullname "${base_args[@]}" --set fullnameOverride=acme
dep_fullname="$(require_one fullname Deployment acme-schedule-time)"
require_one fullname Component "$(env_value "$dep_fullname" DWS_SCHEDULE_TIME_STATE_STORE)" >/dev/null
ok

# --- 7. DWS_LOG_FORMAT follows observability, through the controller's helper -----------------
[ -z "$(env_entry "$dep" DWS_LOG_FORMAT)" ] || die "DWS_LOG_FORMAT rendered with observability disabled"
render logs_json "${base_args[@]}" --api-versions opentelemetry.io/v1alpha1 \
  --set observability.enabled=true --set observability.logs.format=json
dep_json="$(require_one logs_json Deployment "$name")"
ctrl_json="$(require_one logs_json Deployment "$release-controller")"   # dws.controller.fullname for release "dws"
[ "$(grep -c '^            - name: DWS_LOG_FORMAT$' "$dep_json")" = 1 ] || die "scheduler container must carry exactly one DWS_LOG_FORMAT"
[ "$(env_value "$dep_json" DWS_LOG_FORMAT)" = json ] || die "scheduler DWS_LOG_FORMAT is not json with observability.logs.format=json"
[ "$(env_entry "$dep_json" DWS_LOG_FORMAT)" = "$(env_entry "$ctrl_json" DWS_LOG_FORMAT)" ] \
  || die "scheduler and controller DWS_LOG_FORMAT entries differ (must come from the same helper)"
if grep -q 'instrumentation.opentelemetry.io' "$dep_json"; then die "observability on annotated the scheduler pod for OpenTelemetry injection"; fi
render logs_off "${base_args[@]}" --api-versions opentelemetry.io/v1alpha1 \
  --set observability.enabled=true --set observability.logs.enabled=false
[ -z "$(env_entry "$(require_one logs_off Deployment "$name")" DWS_LOG_FORMAT)" ] || die "DWS_LOG_FORMAT rendered with observability.logs.enabled=false"
ok

# --- 8. values.schema.json closes scheduleTime ------------------------------------------------
render_fails unknown_key "additional properties 'bogus' not allowed" "${base_args[@]}" --set scheduleTime.bogus=true
render_fails bad_duration "afterPollInterval" "${base_args[@]}" --set scheduleTime.afterPollInterval=15s
render_fails bad_ttl "claimTtl" "${base_args[@]}" --set scheduleTime.claimTtl=24h
render_fails bad_pull_policy "pullPolicy" "${base_args[@]}" --set scheduleTime.image.pullPolicy=Sometimes
render_fails bad_enabled "enabled" "${base_args[@]}" --set-string scheduleTime.enabled=yes
render_fails bad_replicas "replicaCount" "${base_args[@]}" --set scheduleTime.replicaCount=-1
# The schema constrains scheduleTime only: an unrelated top-level key still renders.
render unrelated "${base_args[@]}" --set someOtherBlock.anything=true
ok

echo "schedule-time-render-test.sh: all $checks check groups passed"
