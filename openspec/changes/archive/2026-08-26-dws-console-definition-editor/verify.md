# Verification — dws-console-definition-editor

## Summary

| Dimension | Status |
| --- | --- |
| Completeness | 12/12 tasks complete; 4 specification requirements implemented |
| Correctness | Submission transport, success/idempotency, validation, and request-failure paths covered |
| Coherence | Follows existing console client, route, layout, and OIDC patterns |

## Implementation evidence

- `dws-console/src/routes/workflows/new.tsx` provides the `/workflows/new` authoring route, a
  controlled CodeMirror buffer, YAML/JSON extensions, and an `EditorView.theme()` that uses
  existing console CSS variables.
- `dws-console/src/routes/workflows/index.tsx` exposes the route through **New definition**.
- `dws-console/src/lib/admin-client.ts` centralizes `POST /workflows?dryRun=false`, preserves the
  raw definition body, attaches the bearer token, parses `ApplyResult`, returns raw 400
  `errors[]`, and rejects other response failures as `ApiError`.
- The editor derives sign-in state from `useOidc()` and gets the in-memory bearer token through
  the typed `getOidc({ assert: "user logged in" }).getAccessToken()` API. This is the actual
  `oidc-spa` v10 API; the hook state intentionally does not expose token material.
- `created: false` is rendered as an idempotent success, and validation errors are displayed as
  the controller's flat list without source-location claims.

## Local gates (green)

Run from `dws-console` on 2026-08-26:

```powershell
pnpm lint       # Checked 42 files, no fixes
pnpm typecheck  # passed
pnpm test       # 5 files, 61 tests passed
pnpm build      # client and SSR production builds passed
```

Focused transport tests in `src/lib/admin-client.test.ts` cover configured relay URL, raw body,
`dryRun=false`, bearer header, newly created result, idempotent result, raw 400 errors, and a
non-400 relay failure.

```powershell
openspec validate dws-console-definition-editor --strict  # valid
```

## Live acceptance (2026-08-31)

**Result: passed for the shipped direct console -> `dws-admin` Phase 3 relay path.**

Docker Desktop context `docker-desktop` ran Helm release `dws` in namespace `dws-phase5` with
`auth.enabled=true`, `dex.enabled=true`, `auth.dex.enabled=true`, and
`adminGateway.enabled=false`. The cluster's existing Dapr control plane was reused
(`dapr.enabled=false`); an isolated password-protected Redis fixture backed the chart Components.
The console was built locally with `VITE_DWS_ADMIN_URL=http://localhost:3001`,
`VITE_OIDC_ISSUER_URI=http://host.docker.internal:5556`, `VITE_OIDC_CLIENT_ID=dws-console`, and
`VITE_OIDC_SCOPES="profile email"`. Dex, console, and direct admin Service were port-forwarded at
ports 5556, 3000, and 3001 respectively.

| Scenario | Result |
| --- | --- |
| Bootstrap-admin sign-in | Passed: Dex local-password login and consent completed; `/workflows/new` displayed **Sign out** and enabled submission. |
| Valid DSL 1.0 YAML | Passed: authenticated browser `POST /workflows?dryRun=false` returned `201`; the editor rendered `Applied vf080e0d8.` and controller created ConfigMap `dws-def-phase5-live-editor-vf080e0d8` plus Deployment `phase5-live-editor-vf080e0d8`. |
| Identical re-submit | Passed: returned `200` with `created: false`; the editor rendered `vf080e0d8 is already applied.` as success. |
| Invalid definition | Passed: returned `400`; the controller `errors[]` entries were rendered individually as validation feedback, including the YAML parse-location details. |
| Invalidated bearer token | Passed: temporarily changed the controller bearer middleware audience, which made the still-authenticated browser token invalid. The live request returned `401` and the editor rendered `POST /workflows failed: 401 Unauthorized`; the failure was not swallowed. The middleware audience was restored immediately afterward. |

The browser capture also confirmed the direct cross-origin preflight: `OPTIONS` returned `204`
with `Access-Control-Allow-Origin: *`, `Access-Control-Allow-Headers: authorization,content-type`,
and `Access-Control-Allow-Methods: GET,HEAD,OPTIONS`; the real request carried the bearer token
and `Content-Type: application/yaml`.

The run uncovered and corrected three deployment-path defects:

- The console Docker image did not expose `VITE_OIDC_*` build arguments, so a locally deployed
  console could not be configured for Dex.
- In externally managed-Dapr mode, the chart did not inject `dws-admin` even when
  `auth.enabled=true`, and it did not set `DAPR_CONTROLLER_APP_ID` from the chart controller
  fullname.
- Nest's built-in raw-body capture does not parse `application/yaml`; the relay therefore
  forwarded an empty definition. It now uses an explicit YAML raw parser and forwards that buffer
  verbatim.

**Routing decision:** retain the shipped direct `dws-console -> dws-admin POST /workflows` path
permanently for Phase 5. The controller's Dapr sidecar remains the bearer enforcement point for
this relay. Phase 4's optional gateway is not a prerequisite for definition editing; it remains
reserved for the later user-management write surface (Phase 7) unless a future requirements change
explicitly needs its stricter browser-origin gateway.

## Assessment

The implementation and deployed acceptance meet the change requirements for the direct relay path.
