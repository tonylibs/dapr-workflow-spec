#!/usr/bin/env bash
# Render contract for the chart-wide imagePullSecrets value, the dedicated identities used by
# controller-generated Knative steps (<fullname>-task and the pull-secret-free
# <fullname>-task-override), the opt-in imagePullServiceAccount sharing of <fullname>-task with
# admin and console, and per-step-image imagePullSecrets overrides. The list contains references only: operators create the
# named Secrets in the effective workload namespace before installing the chart.
#
# Run: bash charts/dws/tests/image-pull-secrets-render-test.sh [chart_dir]
set -euo pipefail

chart_dir="${1:-charts/dws}"
umask 077
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail() { echo "FAIL: $1" >&2; exit 1; }

base_args=(
  --api-versions dapr.io/v1alpha1
  --api-versions opentelemetry.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
  --set console.enabled=true
  --set observability.enabled=true
)

render() {
  local scenario="$1" release="$2"
  shift 2
  if ! helm template "$release" "$chart_dir" "${base_args[@]}" "$@" >"$work/$scenario.yaml" 2>"$work/$scenario.err"; then
    echo "FAIL: helm template unexpectedly failed for scenario '$scenario':" >&2
    head -c 1000 "$work/$scenario.err" >&2
    exit 1
  fi

  mkdir -p "$work/$scenario.d"
  awk -v dir="$work/$scenario.d" '
    BEGIN { f = dir "/doc.0" }
    /^---[ \t]*$/ { n++; f = dir "/doc." n; next }
    { print > f }
  ' "$work/$scenario.yaml"
}

render_fails() {
  local scenario="$1"
  shift
  render_fails_with "$scenario" 'imagePullSecrets' "$@"
}

# Expect a schema failure whose diagnostic names the given values path fragment.
render_fails_with() {
  local scenario="$1" diagnostic="$2"
  shift 2
  if helm template dws "$chart_dir" "${base_args[@]}" "$@" >"$work/$scenario.out" 2>"$work/$scenario.err"; then
    fail "expected malformed scenario '$scenario' to fail Helm validation"
  fi
  grep -qF "$diagnostic" "$work/$scenario.err" \
    || fail "scenario '$scenario' failed without a '$diagnostic' schema diagnostic"
}

# Unquoted value of a container env var, or empty when it is not rendered.
env_value() {
  local file="$1" name="$2"
  awk -v n="$name" '$0 ~ ("name: " n "$") {
    getline
    sub(/^[[:space:]]*value: */, "")
    sub(/^"/, ""); sub(/"$/, "")
    gsub(/\\"/, "\"")
    print
    exit
  }' "$file"
}

assert_service_account_name() {
  local file="$1" expected="$2"
  grep -qx "      serviceAccountName: $expected" "$file" \
    || fail "$(basename "$file"): pod template does not run as '$expected'"
}

assert_no_service_account_name() {
  local file="$1"
  if grep -q 'serviceAccountName:' "$file"; then
    fail "$(basename "$file"): pod template must not set serviceAccountName"
  fi
}

find_doc() {
  local scenario="$1" kind="$2" name="$3" file
  for file in "$work/$scenario.d"/doc.*; do
    [ -e "$file" ] || continue
    grep -qx "kind: $kind" "$file" || continue
    if [ "$(awk '/^metadata:/ { in_metadata = 1; next } in_metadata && /^  name:/ { sub(/^  name: */, ""); gsub(/"/, ""); print; exit }' "$file")" = "$name" ]; then
      echo "$file"
      return 0
    fi
  done
  return 0
}

require_doc() {
  local path
  path="$(find_doc "$1" "$2" "$3")"
  [ -n "$path" ] || fail "scenario '$1': no $2 named '$3' rendered"
  echo "$path"
}

assert_namespace() {
  local file="$1" expected="$2" actual
  actual="$(awk '/^metadata:/ { in_metadata = 1; next } in_metadata && /^  namespace:/ { sub(/^  namespace: */, ""); gsub(/"/, ""); print; exit }' "$file")"
  [ "$actual" = "$expected" ] \
    || fail "$(basename "$file"): namespace '$actual' does not match '$expected'"
}

assert_service_account_secrets() {
  local file="$1"
  grep -qx 'imagePullSecrets:' "$file" || fail "$(basename "$file"): ServiceAccount has no imagePullSecrets"
  grep -qx '  - name: regcred' "$file" || fail "$(basename "$file"): ServiceAccount lacks regcred"
  grep -qx '  - name: mirror-creds' "$file" || fail "$(basename "$file"): ServiceAccount lacks mirror-creds"
}

assert_deployment_secrets() {
  local file="$1"
  grep -qx '      imagePullSecrets:' "$file" || fail "$(basename "$file"): Deployment pod template has no imagePullSecrets"
  grep -qx '        - name: regcred' "$file" || fail "$(basename "$file"): Deployment pod template lacks regcred"
  grep -qx '        - name: mirror-creds' "$file" || fail "$(basename "$file"): Deployment pod template lacks mirror-creds"
}

assert_no_pull_secrets() {
  local file="$1"
  if grep -q 'imagePullSecrets:' "$file"; then
    fail "$(basename "$file"): imagePullSecrets must be omitted for an empty list"
  fi
}

assert_no_step_rbac() {
  local scenario="$1" step_name="$2" file kind
  for file in "$work/$scenario.d"/doc.*; do
    [ -e "$file" ] || continue
    kind="$(sed -n 's/^kind: //p' "$file" | head -n 1)"
    case "$kind" in
      RoleBinding|ClusterRoleBinding)
        if grep -Eq "^[[:space:]]+name: \"?${step_name}\"?$" "$file"; then
          fail "scenario '$scenario': $kind grants RBAC to dedicated step account '$step_name'"
        fi
        ;;
    esac
  done
}

configured_args=(
  --set imagePullSecrets[0].name=regcred
  --set imagePullSecrets[1].name=mirror-creds
)

# All enabled first-party ServiceAccounts and Deployments receive both references. The default
# release namespace is the effective workload namespace when namespaceOverride is empty.
render configured dws --namespace release-space "${configured_args[@]}"
for account in dws-controller dws-schedule-time dws-orchestrator dws-task dws-dapr-ready; do
  account_doc="$(require_doc configured ServiceAccount "$account")"
  assert_namespace "$account_doc" release-space
  assert_service_account_secrets "$account_doc"
done
for deployment in dws-controller dws-schedule-time dws-admin dws-console; do
  deployment_doc="$(require_doc configured Deployment "$deployment")"
  assert_namespace "$deployment_doc" release-space
  assert_deployment_secrets "$deployment_doc"
done
hook_job="$(require_doc configured Job dws-dapr-ready)"
grep -qx '      serviceAccountName: dws-dapr-ready' "$hook_job" \
  || fail "Dapr readiness hook Job does not use its pull-secret-bearing ServiceAccount"
assert_no_step_rbac configured dws-task
[ -z "$(find_doc configured ServiceAccount dws-step)" ] \
  || fail "chart still renders the pre-rename dws-step ServiceAccount"

controller_doc="$(require_doc configured Deployment dws-controller)"
configured_step_env="$(awk '/name: DWS_TASK_SERVICE_ACCOUNT$/ { getline; gsub(/"/, "", $2); print $2; exit }' "$controller_doc")"
[ "$configured_step_env" = dws-task ] \
  || fail "controller DWS_TASK_SERVICE_ACCOUNT '$configured_step_env' does not match dws-task"
[ "$(env_value "$controller_doc" DWS_TASK_OVERRIDE_SERVICE_ACCOUNT)" = dws-task-override ] \
  || fail "controller DWS_TASK_OVERRIDE_SERVICE_ACCOUNT does not match dws-task-override"
override_doc="$(require_doc configured ServiceAccount dws-task-override)"
assert_namespace "$override_doc" release-space
assert_no_pull_secrets "$override_doc"
assert_no_step_rbac configured dws-task-override
if grep -q 'DWS_IMAGES_PULL_SECRETS_' "$controller_doc"; then
  fail "controller renders DWS_IMAGES_PULL_SECRETS_* without per-image overrides"
fi
# Default mode: admin and console keep the namespace default account.
assert_no_service_account_name "$(require_doc configured Deployment dws-admin)"
assert_no_service_account_name "$(require_doc configured Deployment dws-console)"

# One shared name rule must survive a custom release name and namespaceOverride.
render custom myrel --namespace release-space --set namespaceOverride=workloads "${configured_args[@]}"
custom_step="$(require_doc custom ServiceAccount myrel-dws-task)"
assert_namespace "$custom_step" workloads
assert_service_account_secrets "$custom_step"
custom_controller="$(require_doc custom Deployment myrel-dws-controller)"
assert_namespace "$custom_controller" workloads
custom_step_env="$(awk '/name: DWS_TASK_SERVICE_ACCOUNT$/ { getline; gsub(/"/, "", $2); print $2; exit }' "$custom_controller")"
[ "$custom_step_env" = myrel-dws-task ] \
  || fail "custom release DWS_TASK_SERVICE_ACCOUNT '$custom_step_env' does not match myrel-dws-task"
assert_no_step_rbac custom myrel-dws-task
custom_override="$(require_doc custom ServiceAccount myrel-dws-task-override)"
assert_namespace "$custom_override" workloads
assert_no_pull_secrets "$custom_override"
assert_no_step_rbac custom myrel-dws-task-override
[ "$(env_value "$custom_controller" DWS_TASK_OVERRIDE_SERVICE_ACCOUNT)" = myrel-dws-task-override ] \
  || fail "custom release DWS_TASK_OVERRIDE_SERVICE_ACCOUNT does not match myrel-dws-task-override"

# Both the omitted default and an explicit [] omit the field everywhere while retaining the
# dedicated step account.
render omitted dws
render empty dws --set-json 'imagePullSecrets=[]'
for scenario in omitted empty; do
  for account in dws-controller dws-schedule-time dws-orchestrator dws-task dws-task-override dws-dapr-ready; do
    assert_no_pull_secrets "$(require_doc "$scenario" ServiceAccount "$account")"
  done
  for deployment in dws-controller dws-schedule-time dws-admin dws-console; do
    assert_no_pull_secrets "$(require_doc "$scenario" Deployment "$deployment")"
  done
done

# The step identity belongs to the controller lifecycle.
render controller_disabled dws --set controller.enabled=false "${configured_args[@]}"
[ -z "$(find_doc controller_disabled ServiceAccount dws-task)" ] \
  || fail "step ServiceAccount rendered with controller.enabled=false"
if grep -q 'DWS_TASK_SERVICE_ACCOUNT' "$work/controller_disabled.yaml"; then
  fail "DWS_TASK_SERVICE_ACCOUNT rendered with controller.enabled=false"
fi
[ -z "$(find_doc controller_disabled ServiceAccount dws-task-override)" ] \
  || fail "override step ServiceAccount rendered with controller.enabled=false"
if grep -q 'DWS_TASK_OVERRIDE_SERVICE_ACCOUNT' "$work/controller_disabled.yaml"; then
  fail "DWS_TASK_OVERRIDE_SERVICE_ACCOUNT rendered with controller.enabled=false"
fi

# Shared mode attaches admin and console to the same stable task account. RBAC-bearing accounts
# stay separate and keep the global references; the controller wiring is unchanged.
render shared dws --set imagePullServiceAccount.enabled=true "${configured_args[@]}"
assert_service_account_secrets "$(require_doc shared ServiceAccount dws-task)"
assert_no_step_rbac shared dws-task
assert_no_pull_secrets "$(require_doc shared ServiceAccount dws-task-override)"
for deployment in dws-admin dws-console; do
  deployment_doc="$(require_doc shared Deployment "$deployment")"
  assert_service_account_name "$deployment_doc" dws-task
  assert_deployment_secrets "$deployment_doc"
done
for account in dws-controller dws-schedule-time dws-orchestrator dws-dapr-ready; do
  assert_service_account_secrets "$(require_doc shared ServiceAccount "$account")"
done
assert_service_account_name "$(require_doc shared Deployment dws-controller)" dws-controller
assert_service_account_name "$(require_doc shared Deployment dws-schedule-time)" dws-schedule-time
shared_controller="$(require_doc shared Deployment dws-controller)"
[ "$(env_value "$shared_controller" DWS_TASK_SERVICE_ACCOUNT)" = dws-task ] \
  || fail "shared mode changed DWS_TASK_SERVICE_ACCOUNT"
[ "$(env_value "$shared_controller" DWS_ORCHESTRATOR_SERVICE_ACCOUNT)" = dws-orchestrator ] \
  || fail "shared mode changed DWS_ORCHESTRATOR_SERVICE_ACCOUNT"
[ -n "$(find_doc shared RoleBinding dws-orchestrator)" ] \
  || fail "shared mode dropped the orchestrator RoleBinding"

# Shared mode keeps the task account for admin alone, without the controller-only override account.
render shared_no_controller dws --set imagePullServiceAccount.enabled=true \
  --set controller.enabled=false --set console.enabled=false "${configured_args[@]}"
assert_service_account_secrets "$(require_doc shared_no_controller ServiceAccount dws-task)"
assert_no_step_rbac shared_no_controller dws-task
assert_service_account_name "$(require_doc shared_no_controller Deployment dws-admin)" dws-task
[ -z "$(find_doc shared_no_controller ServiceAccount dws-task-override)" ] \
  || fail "override step ServiceAccount rendered with controller.enabled=false in shared mode"

# With no non-RBAC workload enabled, shared mode renders no task account.
render shared_none dws --set imagePullServiceAccount.enabled=true --set controller.enabled=false \
  --set admin.enabled=false --set console.enabled=false "${configured_args[@]}"
[ -z "$(find_doc shared_none ServiceAccount dws-task)" ] \
  || fail "task ServiceAccount rendered with controller, admin and console disabled"

# Per-step-image overrides reach the controller as JSON arrays, only for overridden images, and
# never change the default task account.
render overrides dws "${configured_args[@]}" \
  --set-json 'controller.images.callHttp.imagePullSecrets=[{"name":"http-cred"}]' \
  --set-json 'controller.images.run.shell.imagePullSecrets=[]' \
  --set-json 'controller.images.callA2a.imagePullSecrets=null'
assert_service_account_secrets "$(require_doc overrides ServiceAccount dws-task)"
assert_no_pull_secrets "$(require_doc overrides ServiceAccount dws-task-override)"
overrides_controller="$(require_doc overrides Deployment dws-controller)"
[ "$(env_value "$overrides_controller" DWS_IMAGES_PULL_SECRETS_CALL_HTTP)" = '["http-cred"]' ] \
  || fail "DWS_IMAGES_PULL_SECRETS_CALL_HTTP is not [\"http-cred\"]"
[ "$(env_value "$overrides_controller" DWS_IMAGES_PULL_SECRETS_RUN_SHELL)" = '[]' ] \
  || fail "DWS_IMAGES_PULL_SECRETS_RUN_SHELL is not []"
[ "$(grep -c 'name: DWS_IMAGES_PULL_SECRETS_' "$overrides_controller")" = 2 ] \
  || fail "controller renders DWS_IMAGES_PULL_SECRETS_* for un-overridden step images"

# Each of the eight step images maps to its own controller key.
render all_overrides dws \
  --set-json 'controller.images.callHttp.imagePullSecrets=[{"name":"a"}]' \
  --set-json 'controller.images.callOpenapi.imagePullSecrets=[{"name":"b"}]' \
  --set-json 'controller.images.callGrpc.imagePullSecrets=[{"name":"c"}]' \
  --set-json 'controller.images.callAsyncapi.imagePullSecrets=[{"name":"d"}]' \
  --set-json 'controller.images.callA2a.imagePullSecrets=[{"name":"e"}]' \
  --set-json 'controller.images.run.shell.imagePullSecrets=[{"name":"f"}]' \
  --set-json 'controller.images.run.scriptJs.imagePullSecrets=[{"name":"g"}]' \
  --set-json 'controller.images.run.scriptPython.imagePullSecrets=[{"name":"h"},{"name":"i"}]'
all_controller="$(require_doc all_overrides Deployment dws-controller)"
for pair in CALL_HTTP=a CALL_OPENAPI=b CALL_GRPC=c CALL_ASYNCAPI=d CALL_A2A=e RUN_SHELL=f RUN_SCRIPT_JS=g RUN_SCRIPT_PYTHON=h\",\"i; do
  kind="${pair%%=*}"
  expected="[\"${pair#*=}\"]"
  [ "$(env_value "$all_controller" "DWS_IMAGES_PULL_SECRETS_$kind")" = "$expected" ] \
    || fail "DWS_IMAGES_PULL_SECRETS_$kind is not $expected"
done

# Schema rejects values that Kubernetes could not interpret as LocalObjectReferences.
render_fails scalar --set-string imagePullSecrets=regcred
render_fails missing_name --set-json 'imagePullSecrets=[{}]'
render_fails empty_name --set-json 'imagePullSecrets=[{"name":""}]'
render_fails non_string_name --set-json 'imagePullSecrets=[{"name":3}]'
render_fails_with shared_non_boolean imagePullServiceAccount --set-string imagePullServiceAccount.enabled=yes
render_fails_with shared_unknown_key imagePullServiceAccount --set imagePullServiceAccount.name=x
render_fails step_scalar --set-string controller.images.callHttp.imagePullSecrets=http-cred
render_fails step_missing_name --set-json 'controller.images.run.scriptJs.imagePullSecrets=[{}]'
render_fails step_empty_name --set-json 'controller.images.callGrpc.imagePullSecrets=[{"name":""}]'
render_fails step_extra_field --set-json 'controller.images.run.shell.imagePullSecrets=[{"name":"a","x":1}]'

echo "image-pull-secrets-render-test.sh: all checks passed"
