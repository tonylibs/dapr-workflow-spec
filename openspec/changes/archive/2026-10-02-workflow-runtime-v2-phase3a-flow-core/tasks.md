## 1. Sequencer core

- [x] 1.1 Introduce scope dispatch and workflow input envelope; route `for`/`try-catch`/`fork` to a not-implemented configuration failure
- [x] 1.2 Implement source-order execution and `then` resolution (name, end, exit, continue, unknown target, step cap)
- [x] 1.3 Implement child classification and Step/Flow child dispatch with instance ID and envelope
- [x] 1.4 Implement failure propagation with unchanged messages
- [x] 1.5 Implement task `timeout` race with v1 wording

## 2. Tests

- [x] 2.1 Stand-in children tests for order, `then`, dispatch, instance ID, envelope, failure, timeout, controller routing
- [x] 2.2 Replay determinism test

## 3. Docs and gate

- [x] 3.1 Update `dws-flow/README.md` and `dws-flow/CLAUDE.md` (stale `Load` example)
- [x] 3.2 Run `cd dws-flow && dotnet test`
