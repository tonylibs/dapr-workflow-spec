## Why

`dws-console` now ships a real image (`ghcr.io/tonylibs/dws-console`, built and smoke-tested by
`.github/workflows/dws-console.yml`), which unblocks Helm packaging Phase 6: `charts/dws` can now
deploy the console instead of leaving `templates/console/` empty. Without this, an operator running
`helm install` gets the control plane (controller/admin/postgres/dapr) but has to hand-roll their
own console Deployment/Service/Ingress to get a UI.

## What Changes

- Add `charts/dws/templates/console/{deployment,service,ingress}.yaml`, gated by
  `.Values.console.enabled` (default `false`), following the existing
  `dws.<component>.fullname`/`dws.<component>.selectorLabels` helper pattern.
- Add `dws.console.fullname`/`dws.console.selectorLabels` helpers to `_helpers.tpl`.
- Add a `console:` block to `values.yaml`: `enabled`, `replicaCount`, `image.*`, `service.port`,
  and an `ingress:` sub-block (`enabled`, `className`, `host`, `annotations`, `tls`, path config).
- Default Ingress topology serves the console at `/` and reverse-proxies `/dws-admin/*` to the
  admin Service on the same host, stripping the `/dws-admin` prefix before forwarding — `dws-admin`
  sets no global route prefix (confirmed in `dws-admin/src/main.ts`), so it must receive
  unprefixed paths (`/workflows`, `/instances`, ...). This matches the console image's default
  build-time `VITE_DWS_ADMIN_URL=/dws-admin` (same-origin).
- Extend `.github/workflows/helm.yml`'s lint/template matrix with `console.enabled=true`/`false`
  legs, and add an integration leg that installs an Ingress controller on `kind` and verifies
  reachability through the rendered Ingress (console `/` and proxied admin `/dws-admin/health`).
- Update `docs/roadmaps/helm-packaging.md` (Phase 6 → done, chart-layout tree, Phase 7 unblocked
  note) and the Helm row in `docs/roadmaps/README.md` once this change is archived.

**BREAKING**: none — `console.enabled` defaults to `false`, so existing installs are unaffected.

## Capabilities

### New Capabilities
- `helm-console-deployment`: Renders the `dws-console` Deployment, Service, and Ingress from
  chart values, including the default same-host `/dws-admin` proxy topology to the admin Service.

### Modified Capabilities
(none — `helm-admin-deployment`'s own requirements are unchanged; the admin Service is only
referenced, not modified, by the new Ingress)

## Impact

- **Code**: `charts/dws/templates/console/*.yaml` (new), `charts/dws/templates/_helpers.tpl`
  (new helpers), `charts/dws/values.yaml` (new `console:` block).
- **CI**: `.github/workflows/helm.yml` — new template-matrix legs and (if feasible) a new
  integration job exercising the Ingress on `kind`.
- **Docs**: `docs/roadmaps/helm-packaging.md`, `docs/roadmaps/README.md` (post-archive follow-up,
  not part of the merged diff itself — see tasks.md).
- **Out of scope**: `dws-console` Phase 4 (definition submission) and Phase 5 (auth) — the console
  image is read-only today, so this Ingress has no write path to secure yet. No changes to
  `dws-admin` or `dws-console` application code.
