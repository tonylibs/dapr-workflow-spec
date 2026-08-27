## Why

Operators can inspect deployed workflows in `dws-console`, but cannot author or resubmit a
DSL 1.0 definition from the console. Phase 3 of the authentication roadmap now supplies the
authenticated `dws-admin` write relay, so the console can safely add the first authoring surface
without waiting for the later gateway or CMS work.

## What Changes

- Add a Definition Editor route to `dws-console` where an authenticated operator can write or
  paste a raw YAML or JSON DSL 1.0 definition in a CodeMirror 6 buffer.
- Add YAML and JSON syntax highlighting using CodeMirror language packages, styled with the
  console's existing Tailwind/Radix design tokens; do not introduce Monaco.
- Add a typed, write-capable `dws-admin` client call for `POST /workflows?dryRun=false` that sends
  the raw editor text and the OIDC bearer token.
- Display a successful `ApplyResult`, treating `created: false` as the normal idempotent result of
  resubmitting identical content, or display the raw `errors[]` returned by a 400 response.
- Keep the view intentionally text-only: no dry-run/plan preview, file import, diagram, structural
  editing, or draft/active/archived workflow-state management.

## Capabilities

### New Capabilities

- `console-definition-submission`: Authenticated console users can author a raw DSL 1.0 YAML or
  JSON definition and submit it through the `dws-admin` controller relay, with clear applied,
  idempotent, validation-error, and transport-error outcomes.

### Modified Capabilities

None.

## Impact

- **Affected component:** `dws-console` only: a new route/view, typed write client, editor
  dependencies, route navigation, and focused tests.
- **Write contract:** calls `dws-admin`'s Phase 3 `POST /workflows?dryRun=false` relay with
  `Content-Type: application/yaml` and `Authorization: Bearer <OIDC access token>`; the relay
  forwards raw bytes and the header to the Dapr-gated controller.
- **Compatibility:** existing read routes and deployed DSL semantics are unchanged. Definitions
  remain controller-validated and content-addressed; a successful repeat submission returns
  `created: false`, not an error.
- **Dependencies:** the relay is documented as complete, but end-to-end browser verification
  remains contingent on a deployed relay and its reachable authenticated write path. No
  `dws-controller`, `dws-admin`, chart, or runtime-interpreter change is part of this phase.
