# charts/dws

Helm chart for DWS (dapr-workflow-spec): `dws-controller`, `dws-admin`, `dws-console`, and their
supporting Dapr/PostgreSQL/Redis/Dex/APISIX dependencies. See the repository root
[`AGENTS.md`](../../AGENTS.md) for the overall project and [`docs/roadmaps/dws-auth.md`](../../docs/roadmaps/dws-auth.md)
for the auth/Gateway design history.

## Upgrading from a pre-Gateway release

If an existing release still has `console.ingress.enabled=true` set (the plain
`networking.k8s.io/v1 Ingress` that fronted `dws-console` before the shared Gateway API front
door), any `helm upgrade` to this chart version fails at render time with an explicit migration
message (`dws.console.legacyIngress.validate` in `templates/_helpers.tpl`, called unconditionally
from `templates/preflight.yaml`). This is intentional: the old `Ingress` and the bundled nginx
`admin-gateway` templates were removed, and the chart refuses to silently drop your route instead
of migrating it.

This section is the operator-facing version of that message, expanded with what a real
`helm upgrade` rehearsal against a live cluster found (see
[`scripts/verify-console-ingress-migration.sh`](../../scripts/verify-console-ingress-migration.sh),
which runs and asserts every step below end to end in a disposable namespace).

### 1. Choose bundled or external APISIX — and read this before choosing bundled

The shared Gateway (`apiGateway.enabled=true`) needs an APISIX / Gateway API controller behind
it, selected by `apisix.enabled`:

- **(a) Bundled (`apisix.enabled=true`)**: this chart installs its own APISIX data plane,
  ingress controller, and (by default) a Bitnami etcd StatefulSet as chart dependencies.
- **(b) External (`apisix.enabled=false` + `apiGateway.external.gatewayProxyName`)**: you point
  the chart at an APISIX / Gateway API controller and `GatewayProxy` you manage separately.

**For an existing release, use (b), external mode.** Bundled mode (a) can only be turned on via a
brand-new `helm install`. Enabling `apisix.enabled=true` via `helm upgrade` on a release that
never had it before **deadlocks**: the bundled Bitnami-etcd sub-chart ships a `pre-upgrade` hook
Job that requires a JWT-token Secret which only the chart's own main manifest sync would create —
but that sync never runs, because the pre-upgrade hook blocks (and eventually times out) first.
Helm also only ever applies a chart's `crds/` directory on `helm install`, never on `helm
upgrade`, so even the Gateway API/APISIX CRDs themselves may not get installed. Neither of these
is specific to `charts/dws` — they're both general Helm/vendored-chart limitations that this
chart happens to expose the first time an operator flips `apisix.enabled` on an existing release.
This was verified reproducibly on a live cluster; it does **not** show up in `helm
template`/`helm lint`, since rendering doesn't execute hooks.

To migrate an existing release with bundled APISIX anyway, install APISIX as its **own**,
separate Helm release first (a fresh `helm install`, so the hook/CRD issue above doesn't apply),
create a `GatewayProxy` pointed at its admin API (mirroring
`templates/api-gateway/gatewayproxy.yaml`), and then use external mode
(`apiGateway.external.gatewayProxyName=<that GatewayProxy>`) for this chart's own migration
upgrade — exactly what `scripts/verify-console-ingress-migration.sh` does.

### 2. Set the required values

```yaml
auth:
  enabled: true
  issuer: https://your-idp.example.com    # or auth.dex.enabled: true for the in-chart IdP
  audience: dws-admin

apiGateway:
  enabled: true
  hostname: dws.example.com               # was console.ingress.host
  tls:
    enabled: true                         # was console.ingress.tls
    certificateName: dws-tls              # the same Secret name, moved from the Ingress spec
  external:
    gatewayProxyName: my-platform-apisix-proxy   # only when apisix.enabled=false

apisix:
  enabled: false   # or true, ONLY on a fresh install — see above

console:
  ingress:
    enabled: false   # drop the legacy value entirely once migrated
```

`console.ingress.className` and `console.ingress.annotations` have no Gateway API equivalent —
drop them; ingress-class-equivalent routing is expressed by `apiGateway.controllerName` /
`apiGateway.gatewayClassName` instead.

Also update your OIDC client's registered redirect URI (`dex.consoleRedirectURI` for the in-chart
Dex, or your external IdP client configuration) to the new shared Gateway origin — console and
admin now share one origin, so the redirect URI changes even if the hostname doesn't.

### 3. Run the upgrade

```bash
helm upgrade <release> charts/dws --namespace <namespace> \
  --set auth.enabled=true --set auth.issuer=... --set auth.audience=... \
  --set apiGateway.enabled=true --set apiGateway.hostname=... \
  --set apisix.enabled=false --set apiGateway.external.gatewayProxyName=... \
  --set console.ingress.enabled=false
```

On success, the legacy `Ingress` and `admin-gateway` `Deployment`/`Service`/`ConfigMap` are gone,
and a `GatewayClass`/`Gateway`/two `HTTPRoute`s (console + `/dws-admin`) exist in their place.

### 4. Rollback

**Verified working**: `helm rollback <release> <pre-migration-revision>` restores the exact
pre-migration state — the legacy `Ingress` and `admin-gateway` objects come back, and the
`Gateway`/`HTTPRoute`/`GatewayClass` objects are removed. This works even though the current
chart's templates no longer contain the legacy resources, because Helm rollback re-applies the
**stored manifest** from that revision's release history (a Secret), not a fresh render from the
chart on disk — so it does not depend on those templates still existing.

If a coordinated application-level change also shipped in the same migration (for example, an
admin image built for a different listener contract than the one the old `admin-gateway` expects
— see `docs/roadmaps/dws-auth.md` §2b), rolling back the chart's Kubernetes objects alone does not
undo that; restore the matching application image alongside the chart-level rollback.

## Validating changes to this chart

```bash
cd charts/dws
helm lint .
helm template dws .
bash tests/values-schema-test.sh .
bash tests/api-gateway-render-test.sh .
```

`scripts/verify-console-ingress-migration.sh` (repo root) additionally rehearses the pre-Gateway
migration end to end against a real cluster in a disposable namespace; see its header comment for
prerequisites.
