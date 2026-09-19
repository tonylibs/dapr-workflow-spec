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

## OpenTelemetry observability (Phase 1)

`observability.enabled=false` by default. With it off the chart renders exactly what it rendered
before the feature existed — no `Instrumentation`, no OTLP Secret, no Dapr tracing, no injection
annotations. `tests/observability-render-test.sh` pins that against a recorded baseline.

### External prerequisite: the OpenTelemetry Operator

The Operator is **not** a `Chart.yaml` dependency and this chart will not install it. It requires
cert-manager, which is a cluster singleton — bundling it risks colliding with an existing install.
Install it yourself, pinned to the combination this phase was verified against:

| Component | Pinned version |
|---|---|
| `open-telemetry/opentelemetry-operator` Helm chart | `0.123.0` |
| operator image | `0.159.0` |
| cert-manager | whatever your cluster already standardises on (the Operator's own prerequisite) |

```bash
helm repo add jetstack https://charts.jetstack.io --force-update
helm upgrade --install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --set crds.enabled=true

helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts --force-update
helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator \
  --namespace opentelemetry-operator-system --create-namespace \
  --version 0.123.0 \
  --set 'manager.collectorImage.repository=otel/opentelemetry-collector-k8s'
```

`helm install`/`helm upgrade` of this chart fails fast with explicit guidance when
`observability.enabled=true` and `opentelemetry.io/v1alpha1` is absent. Set
`observability.operator.required=false` to bypass that check in an environment that installs the
Operator out of band after the release.

You also need a reachable OTLP receiver. This chart does not bundle a collector in Phase 1 —
`observability.otlp.endpoint` defaults to `http://dws-otel-collector:4318`, which names a
collector you install yourself.

### What Phase 1 covers

| Pod | Agent | Dapr sidecar |
|---|---|---|
| `dws-controller` | Java, injected into container `controller` only | exports spans via its shared Dapr `Configuration` |
| `dws-admin` | Node.js, injected into container `admin` only | exports spans via its shared Dapr `Configuration` |

`dws-orchestrator`, `dws-flow`, `dws-step`, and the compiled Knative step Services are later
phases (see [`docs/roadmaps/observability.md`](../../docs/roadmaps/observability.md)).

Two things worth knowing before enabling it:

- **`dapr.io/config` is single-valued.** Tracing is merged into the same per-component Dapr
  `Configuration` that may already carry the bearer auth pipeline
  (`templates/controller/configuration.yaml`, `templates/admin/configuration.yaml`). There is no
  separate tracing `Configuration`, and there is never more than one `dapr.io/config` annotation.
- **The application agent is the only sampling root.** `observability.traces.samplingRate` sets
  the agent's `parentbased_traceidratio` argument; every Dapr `Configuration` is pinned to
  `samplingRate: "1"` so daprd honours the parent decision instead of sampling again (ADR 0005
  Decision 2). Two independent samplers would compose multiplicatively and produce partial traces
  that look like exporter failures.

### OTLP headers

Header credentials (a SaaS backend API key, for example) always travel through a Secret; they are
never inlined into the `Instrumentation` resource. The contract is one key named `headers` holding
the comma-separated `key=value` list `OTEL_EXPORTER_OTLP_HEADERS` expects.

```yaml
observability:
  enabled: true
  otlp:
    # A BASE endpoint, with a scheme and no signal path: the agents append /v1/traces,
    # /v1/metrics and /v1/logs themselves. The chart strips the scheme on the way into Dapr,
    # whose tracing.otel.endpointAddress wants a bare host:port and carries its own isSecure.
    endpoint: https://otlp.vendor.example
    protocol: http/protobuf
    # (a) let the chart create the Secret
    headers:
      api-key: REDACTED
    # (b) or point at one you manage; this wins over headers above and the chart creates none
    existingSecret: ""
```

An operator-managed Secret must expose that same `headers` key:

```bash
kubectl create secret generic platform-otlp \
  --namespace dws-system \
  --from-literal=headers='api-key=REDACTED,x-tenant=acme'
```

Dapr's own exporter does not receive these headers. Dapr's `otel.headers[]` structure needs a
header-name-to-secret-key mapping that the single-string contract above cannot express, so a
deployment that needs authenticated Dapr export should point `observability.otlp.endpoint` at an
in-cluster collector and let that collector authenticate upstream.

## Validating changes to this chart

```bash
cd charts/dws
helm lint .
helm template dws .
bash tests/values-schema-test.sh .
bash tests/api-gateway-render-test.sh .
bash tests/auth-pipeline-placement-test.sh .
bash tests/observability-render-test.sh .
```

`scripts/verify-console-ingress-migration.sh` (repo root) additionally rehearses the pre-Gateway
migration end to end against a real cluster in a disposable namespace; see its header comment for
prerequisites.
