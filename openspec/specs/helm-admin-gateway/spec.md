# helm-admin-gateway

## Purpose

Chart-bundled nginx reverse proxy that lets `dws-console` reach `dws-admin`'s Phase 3 write
relay from a browser: it terminates the CORS preflight for the console origin locally and
forwards the actual request onto `dws-admin`'s Dapr sidecar invoke path, so the sidecar's
bearer middleware (introduced by `helm-admin-auth-middleware`) sees the same token the browser
sent. Ships inside `charts/dws`; not an assumed cluster `Ingress`.

## Requirements
