#!/usr/bin/env bash
# Render matrix for the Dapr machine-auth tokens (auth roadmap Phase 10, openspec change
# auth-phase-10-api-tokens, capability helm-dapr-api-tokens). Covers every render scenario in
# that spec: auth off renders nothing; auth on renders three Secrets and wires annotations and
# secretKeyRef env; an operator-supplied Secret replaces the chart's; the controller has no app
# token; the admin route (and only it) sets the dapr-api-token header from an $env:// reference;
# the bundled APISIX receives the variable; and the three bundled-gateway wiring guards fail
# render with actionable messages.
#
# NEVER echoes Secret data. `helm template` has no `lookup`, so every render mints fresh random
# tokens; this script only ever prints descriptions and fixed strings, never a rendered file,
# a token value, or a diff of one. Rendered output lives in a private temp dir removed on exit.
#
# Not covered here (needs a live cluster): "Token survives upgrade" (`lookup` returns nothing
# under `helm template`) -- checked statically below, proven live by openspec tasks 6.x; and the
# runtime header-replacement behavior of APISIX -- openspec tasks 1.x / 6.8.
set -euo pipefail

chart_dir="${1:-charts/dws}"
release=tokrel
fullname="$release-dws"   # dws.fullname for this release; token Secret names use the bare release name
umask 077
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

base_args=(
  --api-versions dapr.io/v1alpha1
  --set dapr.enabled=false
  --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
)
auth_args=(
  "${base_args[@]}"
  --set auth.enabled=true
  --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin
)
gateway_args=(
  "${auth_args[@]}"
  --api-versions gateway.networking.k8s.io/v1
  --api-versions apisix.apache.org/v1alpha1
  --set console.enabled=true
  --set apiGateway.enabled=true
)

checks=0
die() { echo "FAIL: $1" >&2; exit 1; }
ok() { checks=$((checks + 1)); }

# --- rendering and document helpers (no yq dependency: Helm's output is deterministic) --------

# render <name> [helm args...]: render to $work/<name>.yaml and split into $work/<name>.d/doc.N.
render() {
  local name="$1"
  shift
  if ! helm template "$release" "$chart_dir" "$@" >"$work/$name.yaml" 2>"$work/$name.err"; then
    echo "FAIL: helm template unexpectedly failed for scenario '$name':" >&2
    head -c 600 "$work/$name.err" >&2
    exit 1
  fi
  mkdir -p "$work/$name.d"
  awk -v dir="$work/$name.d" '
    BEGIN { f = dir "/doc.0" }
    /^---[ \t]*$/ { n++; f = dir "/doc." n; next }
    { print > f }
  ' "$work/$name.yaml"
}

# render_fails <name> <expected-substring> [helm args...]: render must fail with the substring.
render_fails() {
  local name="$1" needle="$2"
  shift 2
  if helm template "$release" "$chart_dir" "$@" >/dev/null 2>"$work/$name.err"; then
    die "expected render to fail for scenario '$name'"
  fi
  grep -qF -- "$needle" "$work/$name.err" \
    || die "scenario '$name': error does not contain '$needle' (first 500 bytes: $(head -c 500 "$work/$name.err"))"
  ok
}

# find_doc <scenario> <kind> <metadata.name>: print the path of the matching document, or nothing.
find_doc() {
  local f
  for f in "$work/$1.d"/doc.*; do
    [ -e "$f" ] || continue
    grep -qx "kind: $2" "$f" || continue
    if [ "$(awk '/^metadata:/ { m = 1; next } m && /^  name:/ { sub(/^  name: */, ""); gsub(/"/, ""); print; exit }' "$f")" = "$3" ]; then
      echo "$f"
      return 0
    fi
  done
  return 0
}

# require_doc <scenario> <kind> <name>: like find_doc, but dies when missing.
require_doc() {
  local p
  p="$(find_doc "$1" "$2" "$3")"
  [ -n "$p" ] || die "scenario '$1': no $2 named '$3' rendered"
  echo "$p"
}

# secret_names <scenario>: names of rendered Secrets ending in -dapr-api-token or -app-api-token.
token_secret_names() {
  local f
  for f in "$work/$1.d"/doc.*; do
    [ -e "$f" ] || continue
    grep -qx 'kind: Secret' "$f" || continue
    awk '/^metadata:/ { m = 1; next } m && /^  name:/ { sub(/^  name: */, ""); gsub(/"/, ""); print; exit }' "$f"
  done | grep -E -- '-(dapr|app)-api-token$' | sort || true
}

# env_entry <file> <VAR>: the env list entry (name line plus its more-indented children).
env_entry() {
  awk -v var="$2" '
    function indent(s) { match(s, /^ */); return RLENGTH }
    !inside && $0 ~ ("^ *- name: \"?" var "\"?$") { inside = 1; base = indent($0); print; next }
    inside { if (indent($0) <= base) exit; print }
  ' "$1"
}

# token_value <secret doc>: print the 48+ char token (used only to compare, never printed to output).
token_value() { sed -n 's/^  token: "\(.*\)"$/\1/p' "$1"; }

# --- 1. Auth off: no token objects of any kind ------------------------------------------------
render auth_off "${base_args[@]}" --set console.enabled=true
render default_values
for pattern in '-dapr-api-token' '-app-api-token' 'dapr\.io/api-token-secret' 'dapr\.io/app-token-secret' \
  'DAPR_API_TOKEN' 'APP_API_TOKEN' 'dapr-api-token' 'RequestHeaderModifier'; do
  for scenario in auth_off default_values; do
    if grep -Eq -- "$pattern" "$work/$scenario.yaml"; then
      die "auth.enabled=false ($scenario) rendered something matching '$pattern'"
    fi
  done
done
ok

# --- 2. Auth on, defaults: exactly three Secrets, strong, distinct ----------------------------
render auth_on "${auth_args[@]}"
names="$(token_secret_names auth_on | tr '\n' ' ')"
expected="$release-admin-app-api-token $release-admin-dapr-api-token $release-controller-dapr-api-token "
[ "$names" = "$expected" ] || die "auth.enabled=true rendered token Secrets [$names], expected [$expected]"
ok

secret_admin_dapr="$(require_doc auth_on Secret "$release-admin-dapr-api-token")"
secret_admin_app="$(require_doc auth_on Secret "$release-admin-app-api-token")"
secret_ctrl_dapr="$(require_doc auth_on Secret "$release-controller-dapr-api-token")"
for s in "$secret_admin_dapr" "$secret_admin_app" "$secret_ctrl_dapr"; do
  grep -Eq '^  token: "[A-Za-z0-9]{48}"$' "$s" || die "a token Secret does not hold a 48-character alphanumeric value under key 'token'"
  # >= 32 characters is the spec floor; 48 is the chart's choice.
  [ "$(token_value "$s" | tr -d '\n' | wc -c)" -ge 32 ] || die "a generated token is shorter than 32 characters"
done
t_admin_dapr="$(token_value "$secret_admin_dapr")"
t_admin_app="$(token_value "$secret_admin_app")"
t_ctrl_dapr="$(token_value "$secret_ctrl_dapr")"
{ [ "$t_admin_dapr" != "$t_admin_app" ] && [ "$t_admin_dapr" != "$t_ctrl_dapr" ] && [ "$t_admin_app" != "$t_ctrl_dapr" ]; } \
  || die "two token Secrets share the same value (each component/kind MUST have its own token)"
ok
# Each token value must appear exactly once in the whole render: only in its own Secret, never
# inlined into a Deployment env, annotation or route.
for t in "$t_admin_dapr" "$t_admin_app" "$t_ctrl_dapr"; do
  [ "$(grep -cF -- "$t" "$work/auth_on.yaml")" = 1 ] || die "a token value appears outside its own Secret in the auth-on render"
done
ok
# Upgrade stability is `lookup`-based and cannot run under `helm template`; pin the mechanism.
if ! { grep -q 'lookup "v1" "Secret"' "$chart_dir/templates/_helpers.tpl" \
  && grep -q 'randAlphaNum 48' "$chart_dir/templates/_helpers.tpl"; }; then
  die "dws.apiTokens.value no longer uses lookup + randAlphaNum 48 (token would change on every upgrade)"
fi
ok

# --- 3. Annotations and env wiring (admin and controller) -------------------------------------
admin_dep="$(require_doc auth_on Deployment "$fullname-admin")"
ctrl_dep="$(require_doc auth_on Deployment "$fullname-controller")"

grep -qx "        dapr.io/api-token-secret: \"$release-admin-dapr-api-token\"" "$admin_dep" \
  || die "admin pod lacks dapr.io/api-token-secret naming $release-admin-dapr-api-token"
grep -qx "        dapr.io/app-token-secret: \"$release-admin-app-api-token\"" "$admin_dep" \
  || die "admin pod lacks dapr.io/app-token-secret naming $release-admin-app-api-token"
grep -qx "        dapr.io/api-token-secret: \"$release-controller-dapr-api-token\"" "$ctrl_dep" \
  || die "controller pod lacks dapr.io/api-token-secret naming $release-controller-dapr-api-token"
ok

# env: DAPR_API_TOKEN / APP_API_TOKEN are secretKeyRefs, with no literal value.
for var_ref in "DAPR_API_TOKEN:$release-admin-dapr-api-token" "APP_API_TOKEN:$release-admin-app-api-token"; do
  var="${var_ref%%:*}" secret="${var_ref#*:}"
  entry="$(env_entry "$admin_dep" "$var")"
  [ -n "$entry" ] || die "admin container has no $var env entry"
  grep -q 'secretKeyRef:' <<<"$entry" || die "admin $var is not a secretKeyRef"
  grep -qF "name: \"$secret\"" <<<"$entry" || die "admin $var does not reference Secret $secret"
  grep -qx '.*key: token' <<<"$entry" || die "admin $var does not read key 'token'"
  if grep -Eq '^ +value:' <<<"$entry"; then die "admin $var carries a literal value"; fi
done
ok
entry="$(env_entry "$ctrl_dep" DAPR_API_TOKEN)"
[ -n "$entry" ] || die "controller container has no DAPR_API_TOKEN env entry"
grep -q 'secretKeyRef:' <<<"$entry" || die "controller DAPR_API_TOKEN is not a secretKeyRef"
grep -qF "name: \"$release-controller-dapr-api-token\"" <<<"$entry" || die "controller DAPR_API_TOKEN does not reference its own Secret"
if grep -Eq '^ +value:' <<<"$entry"; then die "controller DAPR_API_TOKEN carries a literal value"; fi
ok
# The Secret the APP_API_TOKEN env reads is the one Dapr is told to attach (annotation).
app_ann="$(sed -n 's/^ *dapr.io\/app-token-secret: "\(.*\)"$/\1/p' "$admin_dep")"
env_entry "$admin_dep" APP_API_TOKEN | grep -qF "name: \"$app_ann\"" \
  || die "admin APP_API_TOKEN secret differs from dapr.io/app-token-secret"
ok

# helm test: the token-negative Job's sidecar requires the controller's token Secret, the Job
# reads it only through a secretKeyRef, and its script has no way to print it. The bearer
# negative Job's sidecar must NOT require a token (its 401 must come from the bearer middleware).
token_job="$(require_doc auth_on Job "$fullname-controller-token-negative-test")"
grep -qx "        dapr.io/api-token-secret: \"$release-controller-dapr-api-token\"" "$token_job" \
  || die "token-negative test Job's sidecar does not require the controller Dapr API token Secret"
entry="$(env_entry "$token_job" SENTINEL_DAPR_API_TOKEN)"
grep -q 'secretKeyRef:' <<<"$entry" || die "token-negative test Job does not read the token through a secretKeyRef"
if grep -Eq '^ +value:' <<<"$entry"; then die "token-negative test Job carries a literal token value"; fi
if grep -Eq 'set -x|curl[^|]* -v|--verbose|echo[^#]*SENTINEL_DAPR_API_TOKEN|printenv|^ *env *$' "$token_job"; then
  die "token-negative test Job could print the token (set -x / curl -v / echo of the variable)"
fi
negative_job="$(require_doc auth_on Job "$fullname-controller-auth-negative-test")"
if grep -q 'api-token-secret' "$negative_job"; then
  die "the bearer-negative test Job's sidecar requires an API token; its 401 would no longer isolate the bearer middleware"
fi
ok

# The two components use two different Secrets.
[ "$(sed -n 's/^ *dapr.io\/api-token-secret: "\(.*\)"$/\1/p' "$admin_dep")" != "$(sed -n 's/^ *dapr.io\/api-token-secret: "\(.*\)"$/\1/p' "$ctrl_dep")" ] \
  || die "admin and controller share one dapr.io/api-token-secret"
ok

# Controller: no app-token anything (follow-up change).
if grep -Eq 'app-token|APP_API_TOKEN|app-api-token' "$ctrl_dep"; then
  die "controller Deployment references an app token (deferred to the follow-up change)"
fi
render ctrl_app_reserved "${auth_args[@]}" --set auth.apiTokens.controller.appApi.existingSecret=reserved-secret
if grep -q 'reserved-secret' "$work/ctrl_app_reserved.yaml"; then
  die "auth.apiTokens.controller.appApi is reserved but affected the render"
fi
ok

# --- 4. Operator-supplied Secrets replace the chart's ------------------------------------------
render existing_one "${auth_args[@]}" --set auth.apiTokens.admin.daprApi.existingSecret=my-admin-dapr
names="$(token_secret_names existing_one | tr '\n' ' ')"
[ "$names" = "$release-admin-app-api-token $release-controller-dapr-api-token " ] \
  || die "with an admin Dapr API existingSecret the chart still renders [$names]"
admin_dep="$(require_doc existing_one Deployment "$fullname-admin")"
grep -qx '        dapr.io/api-token-secret: "my-admin-dapr"' "$admin_dep" || die "annotation does not name the supplied Secret"
env_entry "$admin_dep" DAPR_API_TOKEN | grep -qF 'name: "my-admin-dapr"' || die "DAPR_API_TOKEN does not read the supplied Secret"
ok

render existing_all "${auth_args[@]}" \
  --set auth.apiTokens.admin.daprApi.existingSecret=s-admin-dapr \
  --set auth.apiTokens.admin.appApi.existingSecret=s-admin-app \
  --set auth.apiTokens.controller.daprApi.existingSecret=s-ctrl-dapr
[ -z "$(token_secret_names existing_all)" ] || die "chart still renders a token Secret although all three were supplied"
admin_dep="$(require_doc existing_all Deployment "$fullname-admin")"
ctrl_dep="$(require_doc existing_all Deployment "$fullname-controller")"
grep -qx '        dapr.io/app-token-secret: "s-admin-app"' "$admin_dep" || die "app-token annotation does not name the supplied Secret"
env_entry "$admin_dep" APP_API_TOKEN | grep -qF 'name: "s-admin-app"' || die "APP_API_TOKEN does not read the supplied Secret"
grep -qx '        dapr.io/api-token-secret: "s-ctrl-dapr"' "$ctrl_dep" || die "controller annotation does not name the supplied Secret"
env_entry "$ctrl_dep" DAPR_API_TOKEN | grep -qF 'name: "s-ctrl-dapr"' || die "controller DAPR_API_TOKEN does not read the supplied Secret"
ok

# A disabled component gets no orphan Secret.
render_fails shared_dapr_secret "must use different Secrets" "${auth_args[@]}" \
  --set auth.apiTokens.admin.daprApi.existingSecret=shared-token \
  --set auth.apiTokens.controller.daprApi.existingSecret=shared-token

render admin_off "${auth_args[@]}" --set admin.enabled=false
[ "$(token_secret_names admin_off | tr '\n' ' ')" = "$release-controller-dapr-api-token " ] \
  || die "admin.enabled=false still renders admin token Secrets"
render controller_off "${auth_args[@]}" --set controller.enabled=false
[ "$(token_secret_names controller_off | tr '\n' ' ')" = "$release-admin-app-api-token $release-admin-dapr-api-token " ] \
  || die "controller.enabled=false still renders the controller token Secret"
ok

# --- 5. Gateway: header injected on the admin route only, from an $env:// reference -----------
render gw_bundled "${gateway_args[@]}" --set apisix.enabled=true
admin_route="$(require_doc gw_bundled HTTPRoute "$fullname-admin-route")"
console_route="$(require_doc gw_bundled HTTPRoute "$fullname-console-route")"
grep -q 'type: RequestHeaderModifier' "$admin_route" || die "admin HTTPRoute has no RequestHeaderModifier filter"
grep -q 'type: URLRewrite' "$admin_route" || die "admin HTTPRoute lost its URLRewrite filter"
awk '/requestHeaderModifier:/ { r = 1 } r && /set:/ { s = 1 } s && /- name: dapr-api-token$/ { n = 1 } n && /value: "\$env:\/\/DWS_ADMIN_DAPR_API_TOKEN"$/ { found = 1 } END { exit !found }' "$admin_route" \
  || die "admin HTTPRoute does not 'set' dapr-api-token to \$env://DWS_ADMIN_DAPR_API_TOKEN"
if grep -Eq 'add:' "$admin_route"; then die "admin HTTPRoute uses 'add' for the header (a client-supplied value would survive); must be 'set'"; fi
if grep -Eq 'dapr-api-token|RequestHeaderModifier|DWS_ADMIN_DAPR_API_TOKEN' "$console_route"; then
  die "console HTTPRoute carries the admin Dapr API token filter"
fi
ok

# The token is never a cleartext value anywhere but its Secret (route, APISIX env, annotations).
for s_name in admin-dapr-api-token admin-app-api-token controller-dapr-api-token; do
  t="$(token_value "$(require_doc gw_bundled Secret "$release-$s_name")")"
  [ "$(grep -cF -- "$t" "$work/gw_bundled.yaml")" = 1 ] || die "a token value appears outside its own Secret in the gateway render"
done
ok

# Bundled APISIX workload: env from the admin Dapr API token Secret + nginx env allow-list.
apisix_dep="$(require_doc gw_bundled Deployment "$release-apisix")"
entry="$(env_entry "$apisix_dep" DWS_ADMIN_DAPR_API_TOKEN)"
[ -n "$entry" ] || die "bundled APISIX Deployment has no DWS_ADMIN_DAPR_API_TOKEN env"
grep -q 'secretKeyRef:' <<<"$entry" || die "bundled APISIX DWS_ADMIN_DAPR_API_TOKEN is not a secretKeyRef"
grep -qE "name: '?\"?$release-admin-dapr-api-token\"?'?$" <<<"$entry" || die "bundled APISIX env does not read the admin Dapr API token Secret"
grep -qx '.*key: token' <<<"$entry" || die "bundled APISIX env does not read key 'token'"
if grep -Eq '^ +value:' <<<"$entry"; then die "bundled APISIX env carries a literal value"; fi
if ! grep -A1 -E '^ +envs:$' "$work/gw_bundled.yaml" | grep -qE '^ +- DWS_ADMIN_DAPR_API_TOKEN$'; then
  die "bundled APISIX config does not list DWS_ADMIN_DAPR_API_TOKEN in its nginx envs"
fi
ok

# Existing Secret + re-pointed APISIX renders, and APISIX reads the supplied Secret.
render gw_existing "${gateway_args[@]}" --set apisix.enabled=true \
  --set auth.apiTokens.admin.daprApi.existingSecret=my-admin-dapr \
  --set 'apisix.extraEnvVars[0].name=DWS_ADMIN_DAPR_API_TOKEN' \
  --set 'apisix.extraEnvVars[0].valueFrom.secretKeyRef.name=my-admin-dapr' \
  --set 'apisix.extraEnvVars[0].valueFrom.secretKeyRef.key=token'
apisix_dep="$(require_doc gw_existing Deployment "$release-apisix")"
env_entry "$apisix_dep" DWS_ADMIN_DAPR_API_TOKEN | grep -qF 'my-admin-dapr' || die "re-pointed APISIX env does not read the supplied Secret"
[ -z "$(find_doc gw_existing Secret "$release-admin-dapr-api-token")" ] || die "chart-generated admin Dapr API Secret rendered despite existingSecret"
ok

# External gateway: nothing rendered into a workload the chart does not own; README documents it.
render gw_external "${gateway_args[@]}" --set apisix.enabled=false --set apiGateway.external.gatewayProxyName=existing-gateway-proxy
for f in "$work/gw_external.d"/doc.*; do
  [ -e "$f" ] || continue
  if grep -qxE 'kind: (Deployment|DaemonSet|StatefulSet)' "$f" && grep -q 'DWS_ADMIN_DAPR_API_TOKEN' "$f"; then
    die "external gateway mode rendered DWS_ADMIN_DAPR_API_TOKEN into a workload"
  fi
done
grep -q 'type: RequestHeaderModifier' "$(require_doc gw_external HTTPRoute "$fullname-admin-route")" \
  || die "external gateway mode lost the admin route header filter"
grep -q 'DWS_ADMIN_DAPR_API_TOKEN' "$chart_dir/README.md" || die "README does not document the DWS_ADMIN_DAPR_API_TOKEN contract for an external APISIX"
ok

# --- 6. Bundled-gateway guards fail render with actionable messages ---------------------------
render_fails guard_missing_var "DWS_ADMIN_DAPR_API_TOKEN" "${gateway_args[@]}" --set apisix.enabled=true \
  --set 'apisix.extraEnvVars[0].name=SOMETHING_ELSE' --set 'apisix.extraEnvVars[0].value=x'
render_fails guard_missing_var_hint "apisix.extraEnvVars" "${gateway_args[@]}" --set apisix.enabled=true \
  --set 'apisix.extraEnvVars[0].name=SOMETHING_ELSE' --set 'apisix.extraEnvVars[0].value=x'
render_fails guard_nginx_envs "apisix.nginx.envs" "${gateway_args[@]}" --set apisix.enabled=true \
  --set 'apisix.apisix.nginx.envs=null'
render_fails guard_nginx_envs_name "DWS_ADMIN_DAPR_API_TOKEN" "${gateway_args[@]}" --set apisix.enabled=true \
  --set 'apisix.apisix.nginx.envs=null'
render_fails guard_other_secret_new "my-admin-dapr" "${gateway_args[@]}" --set apisix.enabled=true \
  --set auth.apiTokens.admin.daprApi.existingSecret=my-admin-dapr
render_fails guard_other_secret_old "$release-admin-dapr-api-token" "${gateway_args[@]}" --set apisix.enabled=true \
  --set auth.apiTokens.admin.daprApi.existingSecret=my-admin-dapr
render_fails guard_literal "secretKeyRef" "${gateway_args[@]}" --set apisix.enabled=true \
  --set 'apisix.extraEnvVars[0].name=DWS_ADMIN_DAPR_API_TOKEN' --set 'apisix.extraEnvVars[0].value=literal'

# --- 7. NOTES: names the Secrets and the restart command, discloses no token ------------------
# `helm template` never renders NOTES.txt, and `helm install --dry-run` needs a reachable cluster
# on Helm 3. So render it through a scratch copy of the chart in which NOTES.txt is also wrapped
# into a ConfigMap: same templates, same values, same render pass (so the tokens in the Secrets
# are the very ones NOTES would have had to leak), and it works on Helm 3 and 4 alike.
notes_chart="$work/notes-chart"
cp -R "$chart_dir" "$notes_chart"
{
  echo '{{- define "zz.notes" -}}'
  cat "$notes_chart/templates/NOTES.txt"
  echo '{{- end -}}'
  printf 'apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: zz-notes\ndata:\n  notes: |\n'
  echo '{{ include "zz.notes" . | nindent 4 }}'
} >"$notes_chart/templates/zz-notes.yaml"

# notes_of <name> [helm args...]: render the scratch chart; write the NOTES text to
# $work/<name>.notes and the Secrets' token values (one per line) to $work/<name>.tokens.
notes_of() {
  local name="$1"
  shift
  helm template "$release" "$notes_chart" "$@" >"$work/$name.all" 2>"$work/$name.err" \
    || { echo "FAIL: helm template (NOTES copy) failed for '$name':" >&2; head -c 600 "$work/$name.err" >&2; exit 1; }
  awk '/^  notes: \|$/ { on = 1; next } on && /^---/ { exit } on { sub(/^    /, ""); print }' "$work/$name.all" >"$work/$name.notes"
  sed -n 's/^  token: "\([A-Za-z0-9]\{48\}\)"$/\1/p' "$work/$name.all" >"$work/$name.tokens"
}
notes_args=(--api-versions dapr.io/v1alpha1 --set dapr.enabled=false --set postgresql.enabled=false
  --set admin.database.url=postgres://dws:dws@postgres.example.test:5432/dws
  --api-versions gateway.networking.k8s.io/v1 --api-versions apisix.apache.org/v1alpha1
  --set console.enabled=true --set auth.enabled=true --set auth.issuer=https://idp.example.test
  --set auth.audience=dws-admin --set apiGateway.enabled=true --set apisix.enabled=true)
notes_of notes_on "${notes_args[@]}"
[ -s "$work/notes_on.notes" ] || die "could not extract NOTES text from the scratch render"
[ "$(wc -l <"$work/notes_on.tokens")" -ge 3 ] || die "NOTES scenario rendered fewer than three token Secrets"
for secret in admin-dapr-api-token admin-app-api-token controller-dapr-api-token; do
  grep -qF -- "$release-$secret" "$work/notes_on.notes" || die "NOTES does not name Secret $release-$secret"
done
grep -qF 'kubectl rollout restart' "$work/notes_on.notes" || die "NOTES has no kubectl rollout restart command"
for target in "deployment/$fullname-admin" "deployment/$fullname-controller" "deployment/$release-apisix"; do
  grep -qF -- "$target" "$work/notes_on.notes" || die "NOTES restart command does not cover $target"
done
# No token value in NOTES, and no command that could print a Secret (dex is off in this
# scenario, so any such command in the NOTES would be ours).
while IFS= read -r t; do
  [ -n "$t" ] || continue
  if grep -qF -- "$t" "$work/notes_on.notes"; then die "NOTES contains a token value"; fi
done <"$work/notes_on.tokens"
if grep -Eqi 'base64|jsonpath|get secret|get secrets|-o ya?ml|-o json|--template|describe secret' "$work/notes_on.notes"; then
  die "NOTES gives a command that could print a Secret"
fi
ok
notes_of notes_off
if grep -qi 'machine-auth' "$work/notes_off.notes"; then die "auth-off NOTES mention machine-auth tokens"; fi
ok

echo "api-tokens-render-test.sh: all $checks check groups passed"
