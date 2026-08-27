## Context

`dws-console` is a TanStack Start/React application whose read routes use a centralized
`admin-client.ts` and configurable `VITE_DWS_ADMIN_URL` base. The app already bootstraps
`oidc-spa`; its `useOidc()` hook exposes the in-memory access token. `dws-admin` Phase 3 now
accepts `POST /workflows`, preserving the request's `dryRun` query, raw body, and
`Authorization` header while invoking the Dapr-gated controller.

This phase adds the first console authoring surface. It must retain raw YAML/JSON bytes until
submission so controller canonicalization and content-addressed versioning remain authoritative.
The controller's current 400 response is `{ message, errors: string[] }`; it has no structured
path or line position, so the UI cannot correctly mark source lines.

## Goals / Non-Goals

**Goals:**

- Provide a dedicated authenticated workflow-definition editor route with a CodeMirror 6 text
  buffer, YAML/JSON language support, and console-consistent theme tokens.
- Submit raw editor text to the documented `dws-admin` relay endpoint with `dryRun=false` and the
  current OIDC bearer token.
- Distinguish a new deployment, an idempotent repeated submission, controller validation errors,
  and non-validation request failures in the UI.
- Keep write transport types and response parsing centralized and testable outside the route.

**Non-Goals:**

- Dry-run/deployment-plan preview, local schema validation, parser-backed structural editing,
  inline diagnostic highlighting, file import, graph rendering, and content-state/CMS features.
- Changes to `dws-admin`, `dws-controller`, Helm routing, OIDC configuration, or DSL/runtime
  behavior.
- Retrying or persisting submissions automatically; operators explicitly submit the current
  buffer.

## Decisions

### D1 — Add a top-level definition-editor route

Use the existing filesystem-routing convention to introduce a dedicated `/workflows/new` (or
equivalent named) route and expose it from the workflow navigation surface. It keeps authoring
separate from the deployed-workflow detail route, whose Definition tab is a historical/read-only
view.

An inline editor on the workflow list was considered, but it would overload the read route's
loading, empty, and pagination states and make the authoring URL non-addressable.

### D2 — Use CodeMirror 6 through `@uiw/react-codemirror`

Install `@uiw/react-codemirror`, `@codemirror/lang-yaml`, and `@codemirror/lang-json`. The React
wrapper owns editor lifecycle and controlled-buffer integration while the language extensions
provide the required syntax highlighting. Build an `EditorView.theme()` extension from the
existing CSS/Tailwind color variables so editor chrome remains coherent in the console theme.

Monaco is rejected by the roadmap's recorded bundle-size/complexity decision. Wiring
`@codemirror/state` and `@codemirror/view` directly is also rejected for this phase because it
duplicates React lifecycle work without a required lower-level behavior.

### D3 — Choose YAML or JSON highlighting by explicit operator selection

The editor maintains one string buffer and a format selector that swaps only the language
extension. It does not parse, convert, or mutate buffer content; the operator selects JSON when
editing JSON and YAML otherwise. This preserves paste fidelity and avoids a speculative
content-sniffing parser that could misclassify partial/in-progress text.

### D4 — Centralize the relay call and represent expected failures explicitly

Add a write call beside the existing `admin-client.ts` helpers. It accepts `definition: string` and
an access token, posts to `/workflows?dryRun=false` with `Content-Type: application/yaml`,
`Accept: application/json`, and `Authorization: Bearer <token>`, then parses the documented
`ApplyResult`. A 400 with a valid `{ errors: string[] }` payload becomes a typed validation result;
all other non-2xx responses are surfaced as an `ApiError` with status and a meaningful request
message.

The route calls `useOidc()` directly for the access token as required; it does not read token
storage or add a token prop to app-wide state. The relay, rather than the browser, remains the
only controller-facing network destination.

### D5 — Model `created: false` as successful idempotency

The submission state has separate pending, success, validation-error, and request-error outcomes.
Both `created: true` and `created: false` are success states; the latter explains that identical
content is already applied. The UI shows the full `ApplyResult` fields needed by operators, without
inventing deployment-plan data.

## Risks / Trade-offs

- **[Risk] The API relay or browser-accessible gateway is unavailable in an environment** →
  preserve the editor buffer and show an explicit request failure; local component/client tests
  cover contract handling, while end-to-end confirmation requires the deployed write path.
- **[Risk] The OIDC hook's access-token property differs from an assumed library shape** →
  inspect and type-check against the installed `oidc-spa` API during implementation; no casts or
  storage fallback.
- **[Trade-off] No line-specific validation feedback** → render the controller's raw flat
  `errors[]` list until its RFC 7807 path-aware response model exists.
- **[Trade-off] A selectable language mode does not infer format** → it remains predictable for
  incomplete documents and avoids modifying operators' raw source.

## Migration Plan

1. Ship the console route and dependency lockfile updates with the write control gated by the
   existing authenticated console session.
2. Configure the console's existing `VITE_DWS_ADMIN_URL` to reach the deployed `dws-admin`
   write route (or its gateway once that route is live in the target environment).
3. Roll back by removing the console release/version; no persisted console state, controller
   schema, or DSL data is migrated by this change.

## Open Questions

- The current relay is `POST /workflows` and accepted by the roadmap. During apply, confirm its
  final response JSON field names and the exact `oidc-spa` access-token property from source/types
  before finalizing the client type and tests.
