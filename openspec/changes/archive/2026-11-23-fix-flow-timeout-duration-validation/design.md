## Context

`SequencerRunner.DispatchTask` calls `UseResolver.Timeout` before constructing a `TimeoutCaller`.
`UseResolverLoader.Validate` calls the same `TaskTimeout.Parse` for every referenced timeout
entry before `Program.cs` registers the workflow. The parser currently skips unknown properties
and gives a missing component a zero contribution. The `after` branch returns early and bypasses
any sibling properties. See the [proposal](proposal.md) and the
[task timeout delta](specs/dws-flow-sequencer/spec.md).

## Goals / Non-Goals

- Fail malformed inline and named timeout objects at their existing validation points.
- Keep ISO-8601 parsing, valid numeric component conversion, timeout formatting, and the child/timer race compatible.
- Keep this fix scoped to task timeout interpretation in `dws-flow`. Controller submission validation, workflow-level timeout enforcement and the v1 runtime are outside the scope.

## Decisions

- Validate object shape in `TaskTimeout.ParseObject` so both callers enforce the same rule. Require at
  least one recognized component in direct objects. Check every property, even when a valid unit
  has already been found. For an `after` wrapper, require a non-null value and reject siblings before
  delegating to the existing duration parser. This retains wrapped ISO-8601 strings and applies the
  same object checks at each recursive step. Checking only for an empty object would miss typos in
  mixed objects. Validating separately in the loader would duplicate rules across the two paths.
- Treat supplied null component values as invalid numbers. Preserve the duration calculation for
  supported numbers and keep explicit numeric zero valid. Inferring validity from the calculated
  total would also reject an intentional zero.
- Use the existing `config failure:` message style and identify the bad property or the missing
  components. Keep the existing startup exception wrapping in `UseResolverLoader`, which adds the
  entry's kind and name, in line with ADR 0010 Decisions 2 and 3.

## Risks / Trade-offs

- Previously accepted malformed definitions → report the failing property and retain an explicit zero form.
- A run replaying a task with historically malformed inline configuration can encounter the new
  validation at a point where the old host scheduled work → account for affected in-flight runs
  when rolling out the updated host.
- Preserve the existing task-to-resource mapping, step-service HTTP contract, and immutable,
  content-addressed definition storage by keeping the implementation in the host's parser.

## Migration Plan

Ship through the ordinary `dws-flow` release. Correct invalid definitions and submit them as new
versions. Use an explicit zero unit for definitions intended to produce a zero-duration timeout.

## Open Questions

None.
