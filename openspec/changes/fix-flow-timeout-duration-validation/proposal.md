## Why

Empty or misspelled timeout objects can make `dws-flow` report an immediate `PT0S` timeout. Referenced `use.timeouts` entries can pass startup validation with the same mistake. [GitHub issue #164](https://github.com/tonylibs/dapr-workflow-spec/issues/164)

## What Changes

- Reject timeout duration objects that are empty or contain unrecognized properties, including objects with a mix of recognized and unrecognized properties.
- Report an invalid inline timeout as a configuration failure when the task is reached, before dispatching its child or scheduling a timer.
- Fail startup for a malformed referenced `use.timeouts` entry and identify the entry in the error.
- Preserve ISO-8601 duration strings and valid object durations. An explicit numeric zero continues to mean a zero-duration timeout.

## Capabilities

### New Capabilities

### Modified Capabilities

- `dws-flow-sequencer` — define valid task timeout objects and the inline configuration failure.
- `dws-flow-use-resolver` — specify startup rejection of malformed referenced timeout objects.

## Impact

Implementation and tests belong to the independently built `dws-flow` component. Existing definitions that relied on ignored timeout properties or an empty object must correct those properties or specify `{"seconds":0}` for an intentional immediate timeout.

### Non-Goals

Controller validation, deployed resource generation, workflow definition versioning, the v1 orchestrator, workflow-level timeouts, and the step-service HTTP contract are outside this change's scope.
