## REMOVED Requirements

### Requirement: Admin gateway Deployment/Service/ConfigMap render when enabled

**Reason**: The chart-bundled nginx Deployment, Service, and ConfigMap are superseded by the
shared Kubernetes Gateway API front door implemented by APISIX.

**Migration**: Remove `adminGateway.enabled` and related image/service/scheduling values. Enable
`apiGateway.enabled` and choose bundled APISIX with `apisix.enabled=true` or configure compatible
external APISIX prerequisites.

### Requirement: nginx answers CORS preflight for the console origin without proxying

**Reason**: Console and admin now share one Gateway origin, so browser admin calls are same-origin
and no dedicated CORS preflight proxy is required.

**Migration**: Remove `adminGateway.corsOrigins`; configure the shared Gateway hostname/TLS and
the console OIDC redirect URI to the same public origin.

### Requirement: nginx proxies real requests to dws-admin's sidecar invoke path

**Reason**: The admin HTTPRoute now owns the `/dws-admin` to Dapr invoke URL rewrite and forwards
all admin methods, not only the write relay.

**Migration**: Use the `helm-api-gateway` route contract. Do not retain a separate nginx Service or
direct app-port backend, because either would create a parallel bypass.

### Requirement: Gateway requires explicit CORS allow-list; render fails on empty

**Reason**: Same-origin routing removes the CORS allow-list contract entirely.

**Migration**: Delete `adminGateway.corsOrigins`; use the Gateway listener hostname as the single
browser origin.

