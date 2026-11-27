## 1. charts/dws: regression tests first (RED)

- [x] 1.1 In `charts/dws/tests/api-tokens-render-test.sh`, add a helper that lists every `secretKeyRef`
      naming a `*-dapr-api-token` or `*-app-api-token` Secret and fails if one has no `optional: true`
      and names a Secret that is not rendered in the same output (design D3). Do not print rendered
      files or token values.
- [x] 1.2 Add scenario `auth_off_apisix` (`base_args` plus the gateway `--api-versions`,
      `--set apisix.enabled=true --set apisix.timezone=UTC`). Assert: render succeeds; no token
      Secret; no `dapr.io/api-token-secret`/`app-token-secret` annotation; no `DAPR_API_TOKEN`/
      `APP_API_TOKEN`; no `RequestHeaderModifier`; APISIX `DWS_ADMIN_DAPR_API_TOKEN` entry is a
      `secretKeyRef` with `optional: true`; the helper from 1.1 passes; `TZ` is still on the APISIX
      container.
- [x] 1.3 Add scenario `admin_off_apisix` (`auth_args`, `--set admin.enabled=false --set
      apisix.enabled=true`, Gateway off). Assert: no `<release>-admin-*-token` Secret; the APISIX
      reference to `<release>-admin-dapr-api-token` has `optional: true`; the helper from 1.1 passes
      (controller and `dws-schedule-time` refs resolve to rendered Secrets).
- [x] 1.4 Extend the `gw_bundled` APISIX block: the `DWS_ADMIN_DAPR_API_TOKEN` entry contains
      `optional: true`. Keep the existing Secret-name, key, no-literal and nginx-envs assertions.
- [x] 1.5 Cover an operator override that re-points the entry to an existing Secret without
      `optional`: it must render, and APISIX must read the supplied Secret. The existing
      `gw_existing` scenario already does this, because `--set apisix.extraEnvVars[0]...` replaces the
      list. An assertion that the entry has no `optional` flag was added there, with no new render.
      Confirm the existing `guard_*` failures (missing var, nginx envs, `guard_other_secret_*`
      against the now-optional default) still fail with the same messages.
- [x] 1.6 Update the script header comment to list the new scenarios. Run
      `bash charts/dws/tests/api-tokens-render-test.sh charts/dws` and confirm 1.2–1.4 fail on the
      current default (RED).

## 2. charts/dws: make the default reference optional (GREEN)

- [x] 2.1 In `charts/dws/values.yaml`, add `optional: true` to the default `apisix.extraEnvVars`
      `DWS_ADMIN_DAPR_API_TOKEN` `secretKeyRef`. Rewrite the comment block above it: the reference is
      optional so auth-off and admin-off installs start; it resolves when the Secret exists; restart
      APISIX if the Secret is created after the pod. Drop the "set `apisix.extraEnvVars=null` and
      clear the nginx envs" workaround. Leave `apisix.apisix.nginx.envs` unchanged.
- [x] 2.2 In `charts/dws/templates/_helpers.tpl`, add one line to the
      `dws.apiGateway.validateAdminTokenWiring` doc comment: `optional` is accepted and does not relax
      the Secret-name or key checks (design D2). No logic change.
- [x] 2.3 Rerun `bash charts/dws/tests/api-tokens-render-test.sh charts/dws` and confirm it passes
      (GREEN).

## 3. charts/dws: docs

- [x] 3.1 In the `charts/dws/README.md` "Machine-auth tokens" section, update the bundled APISIX values
      snippet to show `optional: true`. Replace "for an auth-off install that only wants APISIX, clear
      both settings" with what the optional reference does, and note that operators who applied the
      old workaround may drop it.
- [x] 3.2 Under the Warning in
      `openspec/changes/archive/2026-11-18-auth-phase-10-api-tokens/verification-report.md`, append a
      dated note: "Resolved by `helm-apisix-token-auth-off` (issue #177)". Leave the original
      warning text as it is.
- [x] 3.3 Check `CLAUDE.md` "Machine-auth token env contract" and `AGENTS.md` for a statement that
      the optional reference makes false. Edit only if something is wrong. Do not hand-edit generated
      `openwiki/` pages.

## 4. charts/dws: chart gate (Helm 4.3.0, from repo root)

- [x] 4.1 `helm lint charts/dws` and `helm template dws charts/dws`
- [x] 4.2 `bash charts/dws/tests/values-schema-test.sh charts/dws`
- [x] 4.3 `bash charts/dws/tests/api-gateway-render-test.sh charts/dws`
- [x] 4.4 `bash charts/dws/tests/auth-pipeline-placement-test.sh charts/dws`
- [x] 4.5 `bash charts/dws/tests/observability-render-test.sh charts/dws` (byte-compares a fixture;
      needs Helm 4.3.0 exactly)
- [x] 4.6 `bash charts/dws/tests/orchestrator-wiring-render-test.sh charts/dws`
- [x] 4.7 `bash charts/dws/tests/api-tokens-render-test.sh charts/dws`
- [x] 4.8 `bash charts/dws/tests/schedule-time-render-test.sh charts/dws`
- [x] 4.9 Confirm the default-values render is byte-identical to `main`
      (`helm template dws charts/dws` diff against a render of the base commit).

## 5. Live verification (disposable cluster)

- [x] 5.1 On a disposable cluster (`scripts/start-kind-cluster.sh` or docker-desktop), install
      fresh with `auth.enabled=false, apisix.enabled=true`. Record that the APISIX pod reaches
      Ready, is never in `CreateContainerConfigError`, and has no `DWS_ADMIN_DAPR_API_TOKEN` in its
      environment (`kubectl exec … printenv`, which prints only presence and never a value).
      Record the evidence in this change's `verify.md`.
- [x] 5.2 On the auth-on bundled shared Gateway, rerun the Phase 10 gateway live check
      `scripts/verify-gateway-sse-path.sh`. Its section (e) covers the token matrix and the
      forged-header replacement; `verify-console-ingress-migration.sh` only exercises external
      mode. Confirm admin requests through the Gateway still succeed with the Secret's token.
      Record it in `verify.md`.

## 6. Close out

- [x] 6.1 `openspec validate helm-apisix-token-auth-off --type change --strict`
- [x] 6.2 Commit as `fix(helm): make bundled APISIX admin token reference optional` and reference
      issue #177.
