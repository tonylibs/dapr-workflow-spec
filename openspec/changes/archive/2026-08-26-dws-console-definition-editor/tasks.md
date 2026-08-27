## 1. Console dependency and client contract

- [x] 1.1 Add the CodeMirror React integration and YAML/JSON language dependencies to
  `dws-console/package.json` and refresh the pnpm lockfile without adding Monaco.
- [x] 1.2 Confirm the installed `oidc-spa` `useOidc()` access-token API and the final
  `dws-admin` relay response shape from source/types.
- [x] 1.3 Extend `dws-console/src/lib/admin-client.ts` with typed `ApplyResult` and validation
  error contracts plus a raw-body `POST /workflows?dryRun=false` helper that attaches an OIDC
  bearer token and distinguishes 400 validation errors from request failures.
- [x] 1.4 Add focused client tests for configured-base URL use, raw body/header/query shape,
  successful created and idempotent responses, validation errors, and non-400 failures.

## 2. Definition-editor interface

- [x] 2.1 Add the dedicated workflow-definition editor route and workflow-navigation entry using
  the existing TanStack filesystem-route and console layout patterns.
- [x] 2.2 Implement a controlled CodeMirror text buffer with explicit YAML/JSON mode selection,
  `@codemirror/lang-yaml` and `@codemirror/lang-json` extensions, and an
  `EditorView.theme()` derived from existing console tokens.
- [x] 2.3 Submit the unchanged buffer with the current `useOidc()` bearer token, disable duplicate
  submission while pending, and retain draft text through all outcomes.
- [x] 2.4 Render `ApplyResult` success, including a normal idempotent `created: false` outcome;
  render every 400 `errors[]` entry as validation feedback; and render transport/non-400 errors
  explicitly without claiming unavailable source positions.
- [x] 2.5 Add route/component tests covering language-mode buffer preservation, successful and
  idempotent submission, validation errors, and request failures.

## 3. Documentation and verification

- [x] 3.1 Update `docs/roadmaps/dws-console-submission.md` Phase 1 status and its stale direct,
  unauthenticated transport assumptions to reference the authenticated `dws-admin` relay; do not
  edit generated OpenWiki pages.
- [x] 3.2 Run from `dws-console`: `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build`.
- [x] 3.3 Run `openspec validate dws-console-definition-editor --strict`, record local-gate
  results and the unavailable/deployed write-path end-to-end evidence boundary in `verify.md`.
