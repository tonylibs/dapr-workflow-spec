## Context

`dws-console` currently owns the definition text and selected source format in `DefinitionEditor` React state. As a result, drafts disappear on refresh and there is no way to load a workflow definition from disk. This change is confined to the console: the existing submission path continues to pass the unmodified buffer through `dws-admin` to `dws-controller`, and no deployed resources or runtime interpretation behavior changes.

## Goals / Non-Goals

**Goals:**
- Let an operator select a local `.yaml`, `.yml`, or `.json` definition file and immediately edit its text in the existing CodeMirror editor.
- Persist definition text and selected YAML/JSON highlighting format across browser refreshes, including hand-authored drafts.
- Keep format selection user-overridable after importing a file.

**Non-Goals:**
- Parsing, validating, or transforming imported source.
- Writing changes back to the selected local file or using the File System Access API.
- Persisting drafts outside browser-local storage or syncing them between users, browsers, or devices.
- Changing definition submission, DSL semantics, controller deployment behavior, or orchestrator runtime interpretation.

## Decisions

### Use a standard file input and `File.text()`

The editor will expose an `<input type="file">` restricted to the supported source extensions. Its change handler will read the selected `File` with `File.text()` and place the resulting text in the shared draft store. This browser-standard API works across supported browsers and meets the import-only requirement.

The File System Access API was rejected because its write-back handle is unnecessary and its browser support excludes Firefox and Safari.

### Centralize draft state in a persisted Zustand store

`dws-console` will add Zustand and create a focused `definition-draft-store` in `src/lib`. The store will contain `definition`, `format`, and typed setters, wrapped with Zustand's `persist` middleware using a stable `dws:draft` storage key. `DefinitionEditor` will read and update this store instead of local `useState`.

This keeps imported and typed content on exactly one editor path, avoids hand-written storage effects, and provides an extensible home for later definition-editor features. Browser storage hydration will retain the existing empty-string/YAML defaults when no saved draft is available.

### Infer format only from filename extension

The import handler will set `json` when the selected filename ends in `.json` (case-insensitively), otherwise it will set `yaml` for accepted YAML extensions. It will not inspect file contents. The format selector remains available, so extension inference cannot prevent an operator from correcting the editor mode.

Content sniffing was rejected because it introduces parsing behavior that this phase neither needs nor promises.

## Risks / Trade-offs

- [Browser-local storage can be cleared or unavailable] -> Draft persistence is best-effort browser behavior; the editor remains usable with the initial empty YAML draft.
- [An extension can misrepresent source content] -> The format selector remains immediately user-overridable, and submission preserves raw source rather than depending on the inferred mode.
- [Loading a large file is asynchronous] -> The handler updates the store only after `File.text()` resolves, keeping the editor's existing draft intact if the read fails.

## Migration Plan

1. Add Zustand to the console dependency manifest and lockfile.
2. Introduce the persisted draft store and switch the definition editor to it.
3. Add file import UI and focused tests for importing, extension-based format selection, and draft persistence behavior.
4. Ship with no data migration: a missing `dws:draft` entry uses the existing empty YAML defaults. Rollback consists of removing the feature; existing local storage is harmless and ignored by prior builds.

## Open Questions

None. The file API, format inference, and persistence approach are decided.
