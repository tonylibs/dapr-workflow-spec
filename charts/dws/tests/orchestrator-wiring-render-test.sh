#!/usr/bin/env bash
# Pins the chart-side wiring the controller-compiled orchestrators depend on. Each assertion
# below was a live failure on a kind cluster (docs/roadmaps/observability-phase2a-evidence.md,
# "Other defects found while getting the workflow to run"), invisible to `helm lint` and
# `helm template` because every manifest rendered cleanly:
#
#   1. The controller Role must cover every Kubernetes kind the controller manages, including
#      dapr.io WorkflowAccessPolicy/HTTPEndpoint/Configuration, and `deletecollection` (fabric8's
#      withLabels(...).delete() issues a collection DELETE — workflow delete and version GC 500
#      without it). Missing grant -> POST /workflows returns 500.
#   2. Orchestrator pods run as a dedicated ServiceAccount that can read the definition
#      ConfigMap (the configuration.kubernetes Component reads it as the pod's service
#      account). Without it daprd crash-loops on "failed to sync informer cache for ConfigMap".
#      The controller is told that ServiceAccount's name via DWS_ORCHESTRATOR_SERVICE_ACCOUNT,
#      so the env var and the rendered ServiceAccount must agree under any release name.
#   3. dws-actor-statestore must not be scoped to a single app id: compiled orchestrators run
#      under the workflow name and step apps are workflow workers too, so a scope of
#      `dws-orchestrator` matches none of them ("the state store is not configured to use the
#      actor runtime").
#
# Run: bash charts/dws/tests/orchestrator-wiring-render-test.sh [chart_dir]
set -euo pipefail

chart_dir="${1:-charts/dws}"

fail() { echo "FAIL: $1" >&2; exit 1; }

base_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)

render() { local release="$1"; shift; helm template "$release" "$chart_dir" "${base_args[@]}" "$@"; }

# Print the `verbs:` line of the Role rule whose `resources:` line names $2 exactly, from the
# multi-document manifest on stdin ($1 is unused documentation of the Role under inspection).
rule_verbs() {
  local resource="$1"
  awk -v want="\"$resource\"" '
    /^  - apiGroups:/ { block = ""; inrule = 1 }
    inrule { block = block $0 "\n" }
    inrule && /^    verbs:/ {
      if (block ~ ("resources: \\[[^]]*" want "[^]]*\\]")) { print $0; exit }
      inrule = 0
    }
  '
}

assert_verbs() {
  local doc="$1" resource="$2"; shift 2
  local line verb
  line="$(rule_verbs "$resource" <<<"$doc")"
  [ -n "$line" ] || fail "controller Role has no rule for resource \"$resource\""
  for verb in "$@"; do
    grep -Eq "\"$verb\"" <<<"$line" \
      || fail "controller Role rule for \"$resource\" lacks verb \"$verb\" (got: $line)"
  done
}

# =============================================================================================
# 1. Controller Role covers every managed kind, including deletecollection
# =============================================================================================
controller_rbac="$(render dws --show-only templates/controller/rbac.yaml)"
role_doc="$(awk '/^kind: Role$/ {p=1} /^---$/ {p=0} p' <<<"$controller_rbac")"
[ -n "$role_doc" ] || fail "templates/controller/rbac.yaml renders no Role"

full=(get list create delete deletecollection)
mutable=(get list create delete deletecollection update patch)
assert_verbs "$role_doc" configmaps "${full[@]}"
assert_verbs "$role_doc" deployments "${mutable[@]}"
assert_verbs "$role_doc" services "${mutable[@]}"
assert_verbs "$role_doc" components "${mutable[@]}"
assert_verbs "$role_doc" httpendpoints "${mutable[@]}"
assert_verbs "$role_doc" configurations "${mutable[@]}"
assert_verbs "$role_doc" workflowaccesspolicies "${mutable[@]}"

# =============================================================================================
# 2. Dedicated orchestrator ServiceAccount, readable definition ConfigMap, name handed to the
#    controller — under the default release name and a custom one
# =============================================================================================
for release in dws myrel; do
  orchestrator_rbac="$(render "$release" --show-only templates/controller/orchestrator-rbac.yaml)" \
    || fail "templates/controller/orchestrator-rbac.yaml does not render (release $release)"

  sa_name="$(awk '/^kind: ServiceAccount$/ {p=1} p && /^  name:/ {print $2; exit}' <<<"$orchestrator_rbac")"
  [ -n "$sa_name" ] || fail "no orchestrator ServiceAccount rendered (release $release)"
  [ "$sa_name" != "default" ] || fail "orchestrator ServiceAccount must be dedicated, not default"

  grep -Eq '^kind: Role$' <<<"$orchestrator_rbac" || fail "no orchestrator Role rendered (release $release)"
  grep -Eq '^kind: RoleBinding$' <<<"$orchestrator_rbac" || fail "no orchestrator RoleBinding rendered (release $release)"

  role_rules="$(awk '/^kind: Role$/ {p=1} /^---$/ {p=0} p' <<<"$orchestrator_rbac")"
  line="$(rule_verbs configmaps <<<"$role_rules")"
  [ -n "$line" ] || fail "orchestrator Role has no configmaps rule (release $release)"
  for verb in get list watch; do
    grep -Eq "\"$verb\"" <<<"$line" || fail "orchestrator Role lacks configmaps verb \"$verb\" (got: $line)"
  done
  # Least privilege: the definition store is read-only, and nothing else belongs here.
  grep -Eq '"(create|update|patch|delete|deletecollection)"' <<<"$line" \
    && fail "orchestrator Role must be read-only on configmaps (got: $line)"
  [ "$(grep -c 'resources:' <<<"$role_rules")" -eq 1 ] \
    || fail "orchestrator Role must grant exactly one resource (configmaps)"

  binding="$(awk '/^kind: RoleBinding$/ {p=1} p' <<<"$orchestrator_rbac")"
  bound_sa="$(awk '/^subjects:/ {s=1} s && /^    name:/ {print $2; exit}' <<<"$binding")"
  [ "$bound_sa" = "$sa_name" ] \
    || fail "orchestrator RoleBinding subject ($bound_sa) is not the ServiceAccount ($sa_name)"

  deployment="$(render "$release" --show-only templates/controller/deployment.yaml)"
  env_sa="$(awk '/name: DWS_ORCHESTRATOR_SERVICE_ACCOUNT$/ {getline; gsub(/"/, "", $2); print $2; exit}' <<<"$deployment")"
  [ "$env_sa" = "$sa_name" ] \
    || fail "controller DWS_ORCHESTRATOR_SERVICE_ACCOUNT ($env_sa) does not match the rendered ServiceAccount ($sa_name) for release $release"
done

# The whole stack is gated by controller.enabled — nothing orchestrator-related renders without it.
if render dws --set controller.enabled=false --show-only templates/controller/orchestrator-rbac.yaml >/dev/null 2>&1; then
  fail "orchestrator RBAC rendered with controller.enabled=false"
fi

# =============================================================================================
# 3. The actor state store is not pinned to one app id
# =============================================================================================
actor="$(render dws --show-only templates/actor-statestore-component.yaml)"
grep -Eq '^kind: Component$' <<<"$actor" || fail "actor state store Component not rendered"
grep -Eq 'name: actorStateStore' <<<"$actor" || fail "actor state store lost actorStateStore metadata"
if grep -Eq '^scopes:' <<<"$actor"; then
  fail "dws-actor-statestore must not declare scopes: compiled orchestrators and step workers run under their own app ids, so any fixed scope excludes them"
fi

echo "orchestrator-wiring-render-test.sh: all checks passed"
