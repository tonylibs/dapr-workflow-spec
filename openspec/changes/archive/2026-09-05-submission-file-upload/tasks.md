## 1. Draft State

- [x] 1.1 Add Zustand to `dws-console` and implement a typed, persisted definition-draft store with empty YAML defaults.
- [x] 1.2 Replace the definition editor's page-local definition and format state with the persisted draft store.

## 2. File Import

- [x] 2.1 Add an accessible `.yaml`, `.yml`, and `.json` file-selection control to the definition editor.
- [x] 2.2 Load selected file text into the draft store and infer JSON only from a `.json` filename, otherwise YAML.

## 3. Verification

- [x] 3.1 Add focused tests for loading imported text, inferring the format, editing imported content, and retaining drafts after persistence hydration.
- [x] 3.2 Run the `dws-console` lint, test, and build commands.
