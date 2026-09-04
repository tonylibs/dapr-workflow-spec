## Why

Operators currently have to paste workflow definitions into the console editor, and any imported or hand-authored draft is lost on refresh. Loading local YAML or JSON definitions and retaining all unfinished drafts makes the submission workflow practical without changing the deployed workflow contract.

## What Changes

- Add a local-file import control to the definition editor that accepts `.yaml`, `.yml`, and `.json` files and loads their text into the existing editable definition buffer.
- Infer the editor format as JSON only for `.json` files and YAML for all other accepted extensions, while preserving the user's ability to change the format.
- Persist the definition text and selected format in browser storage so imported and hand-authored drafts survive page refreshes.
- Replace the editor's in-memory definition and format state with a persisted Zustand draft store.

## Capabilities

### New Capabilities
- `console-definition-draft-management`: Import local workflow definition files into the console editor and preserve editable definition drafts across page refreshes.

### Modified Capabilities
- `console-definition-submission`: The definition editor's draft source changes from page-local state to the persisted draft management capability.

## Impact

- Affects the `dws-console` React application, particularly the definition editor and its tests.
- Adds Zustand with its persistence middleware as a console dependency.
- Does not alter the DSL text sent to `dws-admin`, controller APIs, deployed resources, or orchestrator interpretation behavior.
- Existing workflow definitions remain compatible because imported and persisted content is stored and submitted verbatim; browser-local drafts are initialized to the current empty YAML default when none exists.
