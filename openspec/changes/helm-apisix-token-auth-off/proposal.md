## Why

Rendering `charts/dws` with `auth.enabled=false` and `apisix.enabled=true` gives the bundled APISIX
container a required `DWS_ADMIN_DAPR_API_TOKEN` env entry sourced from
`<release>-admin-dapr-api-token`, but the chart only creates that Secret when `auth.enabled=true`
and `admin.enabled=true`. A fresh install of that topology leaves APISIX in
`CreateContainerConfigError`, and it contradicts the Phase 10 guarantee that an auth-off release
carries no machine-auth token wiring (issue #177; the remaining warning in the archived
`auth-phase-10-api-tokens` verification report). The same orphan reference appears with
`auth.enabled=true, admin.enabled=false, apisix.enabled=true`, because the admin Secrets are not
rendered when admin is off.

The default cannot simply be made conditional. The APISIX subchart renders `apisix.extraEnvVars` in
its own values scope (`apisix.tplvalues.render`, `tpl` against the subchart's `.Values`), so it
cannot read the parent's `auth.enabled`, `admin.enabled` or `apiGateway.enabled`. Helm gives a
subchart only its own values and `global`. Making the Secret reference optional removes the hard
dependency without any new value or operator action.

## What Changes

- `charts/dws/values.yaml`: the default `apisix.extraEnvVars` entry for `DWS_ADMIN_DAPR_API_TOKEN`
  gains `optional: true` on its `secretKeyRef`. When the admin Dapr API token Secret is absent
  (auth off, or admin disabled), kubelet starts APISIX and leaves the variable unset. When the
  Secret exists (the auth-on shared Gateway), the variable resolves exactly as it does today.
  `apisix.apisix.nginx.envs` keeps listing the variable. An nginx `env` directive for an unset
  variable has no effect.
- `dws.apiGateway.validateAdminTokenWiring` keeps rejecting a missing entry, the wrong Secret, the
  wrong key and a missing nginx allow-list entry when `apiGateway.enabled=true` and
  `apisix.enabled=true`. It accepts the reference with or without `optional`, so the new default and
  operator overrides that leave out `optional` both render. No new check.
- `charts/dws/tests/api-tokens-render-test.sh`: regression scenarios for auth off with APISIX on,
  and for auth on with admin off, APISIX on and the Gateway off. The tests check that no token
  Secret renders, that no required (non-optional) reference to a token Secret is left on any
  container, and that unrelated APISIX env entries stay intact. Bundled-gateway assertions
  are extended to require the optional flag. The existing guard failures stay in place.
- Docs: the chart README "Machine-auth tokens" section and the `values.yaml` comment drop the
  "clear both settings for auth-off" workaround and describe the optional reference. The archived
  Phase 10 `verification-report.md` warning gets a short resolution note that points to this change.

Not **BREAKING**: no value is renamed or removed. Existing overrides of `apisix.extraEnvVars` keep
working unchanged. On `helm upgrade` the APISIX pod template changes (one added field), so APISIX
rolls once.

### Non-goals

- No new value or `global` switch, and no change to the token Secrets, annotations, admin/controller/
  `dws-schedule-time` env wiring, or the admin HTTPRoute header filter.
- No removal of the env entry from the rendered APISIX Deployment when auth is off. The entry stays
  as an optional, unresolved reference (decision recorded in design D1).
- No change to external-gateway mode (`apisix.enabled=false`): the chart still renders nothing into
  workloads it does not own.
- No change to `dws-admin`, `dws-controller` or any other code package.

### Compatibility

- Workflow definitions, DSL behavior and runtime interpretation do not change. Only the chart's
  deployed APISIX Deployment spec changes.
- Default install (`auth.enabled=false`, `apisix.enabled=false`) renders byte-identical output.
- An operator who applied the README workaround (`extraEnvVars: []`, `nginx.envs: []`) keeps a
  valid render. They may drop the override, but they don't have to.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `helm-dapr-api-tokens`: the auth-off guarantee now covers the bundled APISIX topology explicitly,
  and so does the admin-disabled topology. Neither leaves a required reference to an absent token
  Secret. The bundled gateway's reference to the admin Dapr API token Secret is defined as
  optional. Render-time drift guards stay as they are and are pinned to hold with the optional flag
  set.

## Impact

- Affected component: `charts/dws` only (values, one helper comment, render test, README). The
  chart's own gate covers it: `helm lint`, `helm template`, and the chart render tests listed in
  `CLAUDE.md`, on Helm 4.3.0. No other package builds or changes.
- Deployed resources: the APISIX Deployment's `DWS_ADMIN_DAPR_API_TOKEN` `secretKeyRef` gains
  `optional: true`. Nothing else in any rendered manifest changes.
- Runtime: auth-off and admin-off installs with bundled APISIX start instead of failing with
  `CreateContainerConfigError`. On the auth-on shared Gateway, a missing or late Secret no longer
  blocks APISIX startup. See design for how that failure still surfaces.
