# console-definition-draft-management Specification

## Purpose

Defines local definition-file imports and persisted editor drafts for the `dws-console`.

## Requirements

### Requirement: Definition editor imports local source files
The `dws-console` SHALL provide its definition editor with a local file-selection control that accepts `.yaml`, `.yml`, and `.json` files. When an operator selects an accepted file, the console SHALL read its text with the browser `File.text()` API and replace the editor's definition draft with that text, leaving it writable.

#### Scenario: Operator imports a YAML definition
- **WHEN** an operator selects a `.yaml` or `.yml` file in the definition editor
- **THEN** the console loads the file text into the writable definition buffer and selects YAML highlighting

#### Scenario: Operator imports a JSON definition
- **WHEN** an operator selects a `.json` file in the definition editor
- **THEN** the console loads the file text into the writable definition buffer and selects JSON highlighting

#### Scenario: Operator edits an imported definition
- **WHEN** a definition has been loaded from a local file
- **THEN** the operator can modify its text through the existing definition editor before submitting it

### Requirement: Definition drafts persist in browser storage
The `dws-console` SHALL persist the current definition text and selected YAML or JSON editor format in browser-local storage using a Zustand persisted draft store. The console SHALL restore those values when the definition editor is opened after a page refresh.

#### Scenario: Operator refreshes a hand-authored draft
- **WHEN** an operator enters definition text, selects a format, and refreshes the page
- **THEN** the definition editor restores the entered text and selected format

#### Scenario: Operator refreshes an imported draft
- **WHEN** an operator imports a supported definition file and refreshes the page
- **THEN** the definition editor restores the imported text and its extension-inferred format

#### Scenario: No draft has been stored
- **WHEN** an operator opens the definition editor without a persisted draft
- **THEN** the console provides the existing empty definition text and YAML format defaults
