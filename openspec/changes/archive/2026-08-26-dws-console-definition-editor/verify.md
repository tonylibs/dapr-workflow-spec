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

## End-to-end boundary

No deployed browser-to-relay environment was available locally. The console call targets the
documented Phase 3 `dws-admin` relay contract (`POST /workflows`), which has already been
unit-verified in that component. A deployment acceptance run must configure
`VITE_DWS_ADMIN_URL`, sign in through the target OIDC provider, and submit a valid definition
through the reachable relay.

## Assessment

No critical or warning issues found. The implementation meets the change requirements and is ready
to archive.
