## Context

Phase 10 (`openspec/changes/archive/2026-11-18-auth-phase-10-api-tokens`, design D4) wires the admin
Dapr API token into the bundled APISIX through two subchart values in `charts/dws/values.yaml`:

- `apisix.extraEnvVars`: `DWS_ADMIN_DAPR_API_TOKEN` from `secretKeyRef`
  `{{ .Release.Name }}-admin-dapr-api-token`, key `token`;
- `apisix.apisix.nginx.envs`: `[DWS_ADMIN_DAPR_API_TOKEN]`, the nginx `env` allow-list.

`templates/auth/api-token-secrets.yaml` creates that Secret only when `auth.enabled=true` and
`admin.enabled=true` and no `existingSecret` is set. `dws.apiGateway.validate` blocks
`apiGateway.enabled=true` with auth or admin off, but nothing blocks `apisix.enabled=true` on its
own. Two topologies therefore render a required reference to a Secret that does not exist:

| auth | admin | apisix | apiGateway | admin token Secret | APISIX today |
|---|---|---|---|---|---|
| off | any | on | off (forced) | not rendered | `CreateContainerConfigError` |
| on | off | on | off (forced) | not rendered | `CreateContainerConfigError` |
| on | on | on | on or off | rendered (or operator's) | starts |

Reproduced on this branch with Helm 4.3.2: `helm template dws charts/dws --set auth.enabled=false
--set apisix.enabled=true` renders `name: 'dws-admin-dapr-api-token'` under the APISIX container env
and no such Secret.

Constraint found while inspecting the vendored `apisix-2.16.0` subchart: `deployment.yaml` renders
`extraEnvVars` with `apisix.tplvalues.render`, which is `tpl (toYaml .value) $` in the subchart's
own context. The subchart's `.Values` is its own scope plus `global`, so the parent's
`auth.enabled`, `admin.enabled` and `apiGateway.enabled` are out of reach. Parent templates also
cannot rewrite subchart values: the engine renders subchart templates first, because they have
the deepest paths, and depending on that order would be fragile anyway.

In-force ADRs: none constrain this. ADR 0007 (chart feature layers) is still under discussion and
ADR 0008 (bring your own infra) is Proposed; neither is in force. This change is consistent with
both: the APISIX gateway belongs to the console layer and still needs L1 auth, as before.

## Goals / Non-Goals

**Goals:**

- Auth-off and admin-off releases with bundled APISIX install and start without the operator clearing
  defaults.
- No required token-Secret dependency on any workload when that Secret is not rendered.
- The auth-on shared Gateway keeps sourcing the token by `secretKeyRef`, keeps the nginx allow-list,
  and keeps every render-time drift guard.
- Regression coverage in `api-tokens-render-test.sh` for the newly supported combinations.

**Non-Goals:**

- Removing the env entry from the APISIX Deployment render when auth is off (see D1).
- New values, `global` switches, or changes to token Secrets, annotations, admin/controller/
  `dws-schedule-time` env, or the admin HTTPRoute.
- External gateway mode, which is unchanged.

## Decisions

### D1. Mark the default APISIX `secretKeyRef` `optional: true`

The default entry becomes:

```yaml
apisix:
  extraEnvVars:
    - name: DWS_ADMIN_DAPR_API_TOKEN
      valueFrom:
        secretKeyRef:
          name: "{{ .Release.Name }}-admin-dapr-api-token"
          key: token
          optional: true
```

Kubernetes semantics (`EnvVarSource.secretKeyRef.optional`): when the Secret or key is missing, the
container starts and the variable is not set. When the Secret exists at pod start, the variable
resolves exactly as it does now. `apisix.apisix.nginx.envs` keeps `DWS_ADMIN_DAPR_API_TOKEN`. The nginx
`env NAME;` directive only passes the variable through when it is set, so an unset variable has no
effect. With auth off there is no admin HTTPRoute (`apiGateway.enabled` requires auth), so nothing
reads it.

Alternatives considered (user decision, 2026-11-26):

- *Empty defaults plus explicit opt-in for the bundled gateway.* This removes the entry literally,
  but it moves the manual step to every bundled-gateway user and breaks `helm upgrade` for existing
  gateway releases until they add the values. The validator would catch it, but the issue asks that
  operators not have to edit defaults by hand. Rejected.
- *Default as a `tpl` string guarded on a new `global` value.* This removes the entry, but it adds
  a second switch that has to agree with `auth.enabled`, and a validator to enforce that. It is more
  surface for an edge topology. Rejected.
- *Parent template mutates `.Values.apisix` before the subchart renders.* This depends on Helm's
  internal render order and on value maps being shared, and the order is wrong anyway. Rejected.

This changes acceptance criterion 1 of issue #177 from "renders no token env entry" to "renders no
required token-Secret dependency, and no token variable resolves at runtime". The spec delta
records the carve-out in "Tokens are provisioned only when auth is enabled".

### D2. Drift guard unchanged, and explicitly agnostic to `optional`

`dws.apiGateway.validateAdminTokenWiring` reads `valueFrom.secretKeyRef.name` and `.key` with `dig`
and ignores other fields, so it already accepts the new default and operator overrides with or
without `optional`. No template logic changes. Its doc comment gains one line saying that `optional`
does not relax the Secret-name or key checks. Tests pin that behaviour: the existing
`guard_other_secret_*` scenarios run against the now-optional default, and a new scenario renders a
re-pointed override without `optional`.

The guard does not *require* `optional: true`. An operator who wants APISIX to fail fast on a
missing Secret can override the entry without the flag. That's a supported choice, not drift.

### D3. Test strategy: check required references, not just names

The auth-off loop in section 1 of `api-tokens-render-test.sh` greps the whole render for
`dapr-api-token`. The bundled-APISIX render legitimately contains the optional Secret name and the
nginx allow-list, so the new scenarios use targeted assertions instead of extending that loop:

- the scenario renders successfully and no token Secret is present (`token_secret_names` is empty, or
  contains only the expected component Secrets for admin-off);
- every `secretKeyRef` in the render that names a `*-dapr-api-token` or `*-app-api-token` Secret
  either contains `optional: true` or names a Secret rendered in the same output. In the auth-on,
  admin-off case the controller and `dws-schedule-time` keep their required references to their
  own rendered Secrets. A small awk over env entries does this. It is generic, so a future
  required reference to an absent Secret added anywhere fails the test;
- no `dapr.io/api-token-secret`/`app-token-secret` annotation, no `DAPR_API_TOKEN`/`APP_API_TOKEN`,
  and no `RequestHeaderModifier` in the auth-off render;
- `--set apisix.timezone=UTC` still renders `TZ` on the APISIX container, so unrelated env survives.

The existing `gw_bundled` block gains a `optional: true` assertion on the entry, and the header,
nginx-envs and guard assertions stay as they are. The scenarios use the same `--api-versions` as
`gateway_args`, so the APISIX ingress-controller subchart renders the same way in both.

### D4. Docs

The `values.yaml` comment and the README "Machine-auth tokens" section replace "for an auth-off,
apisix-only install, clear both settings" with an explanation of the optional reference: unset when the
Secret is absent, resolved when present, and APISIX needs a restart if the Secret is created after
the pod. That restart is already documented for token rotation. The archived Phase 10
`verification-report.md` keeps its warning text, because it is a historical record. A dated
"Resolved by `helm-apisix-token-auth-off` (issue #177)" line goes under it. Generated OpenWiki pages
are not hand-edited.

## Risks / Trade-offs

- [The env entry still appears in auth-off renders, so a reader may think token wiring is active] →
  The `optional: true` flag and the comment in `values.yaml` and the README explain it. The spec
  carve-out makes it an intended, tested state.
- [Auth-on with an operator `existingSecret` that does not exist: APISIX now starts with no token
  and admin requests get 401, where before APISIX failed fast] → The same missing Secret still
  blocks the admin pod, because its own container `DAPR_API_TOKEN` `secretKeyRef` (chart-rendered)
  is non-optional, so the failure stays loud at the component that owns the token. Operators who
  want APISIX itself to fail fast can override the entry without `optional` (D2).
- [Secret created after the APISIX pod, so the variable stays unset until restart] → For the
  chart-generated Secret this cannot happen at install, because Helm applies Secrets before
  Deployments within one release. For operator Secrets the README already says to create them
  first and to restart APISIX on any token change.
- [`helm upgrade` changes the APISIX pod template, so APISIX does a rolling restart] → This happens
  once, and the bundled APISIX Deployment already rolls on any config change. Call it out in the
  release notes via the conventional commit.

## Migration Plan

- No operator action. Releases that applied the README workaround (`extraEnvVars: []`,
  `nginx.envs: []`) keep rendering. They may drop it.
- Rollback: revert the chart version. The previous default comes back, and so does the auth-off
  startup failure.
- Verification before calling it done: the chart gate on Helm 4.3.0 (lint, template, every
  `charts/dws/tests/*.sh` listed in `CLAUDE.md`). Also a disposable-cluster install with
  `auth.enabled=false, apisix.enabled=true` showing the APISIX pod Ready with the variable unset,
  and a rerun of the auth-on bundled-gateway live check showing the token still resolves.

## Open Questions

- None blocking. If ADR 0007 is accepted later and makes the console layer (APISIX) strictly depend
  on L1, a future change could make `apisix.enabled=true` with auth off a render error instead of a
  supported topology. That decision belongs to ADR 0007, not here.
