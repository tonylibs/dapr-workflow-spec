# Refactoring practices

Language-agnostic working rules for changing existing code without changing what it does. These
apply to every package in this repo — Java, Go, TypeScript, .NET alike — and are written to be
usable outside it.

For the stack-specific idioms that say *how* to write the code once you've decided what to change,
see the root [`CLAUDE.md`](../CLAUDE.md) package map and each package's own `CLAUDE.md`.

## Before touching code

**Size the request, and say the size out loud.** Bounded (a change to a flow that already exists
here) → a short design in the conversation, then stop for approval. Architectural (new subsystem,
changed layering, changed interface others depend on) → options with tradeoffs, a recommendation,
then approval. When in doubt take the heavier path; complexity discovered mid-task upgrades it,
never downgrades.

**"Refactor X" is underspecified — ask what it means.** The default reading is usually "split the
file up," which moves lines without fixing anything. Ask which pain is being addressed: forgettable
invariants, bad call shapes, untestable rules, layering, naming. Different answers produce
genuinely different work.

**Approval is a gate regardless of size.** Two sentences of design still need a yes before edits
start.

## The moves

| Move | Trigger |
|---|---|
| **Extract Class** | A cluster of helpers sharing a hidden concept — a cursor, a registry, a projection |
| **Move Method** | A method that references another class's data or rules more than its own (feature envy) |
| **Introduce Parameter Object** | Call sites padding a positional list with `null`/empty defaults |
| **Extract Method / dedupe** | Several bodies identical modulo a constant |
| **Replace conditional ladder** | A chain of `x != null && x.getY() != null` dispatch branches |
| **Move Class** | Regrouping by responsibility — always via the VCS rename so history follows |
| **Anemic → rich model** | A type that owns data but can't answer questions about itself |

## Design principles

### Parse, don't validate

Fuse reading with checking so an unvalidated value cannot exist. A check the caller *can* forget
will eventually be forgotten — at a call site added six months later.

```text
# Fragile: two calls, and the second is optional in practice
raw = read(source, field)
requireConsistent(typed, raw)

# Sound: one call, and no unchecked instance can be constructed
raw = Reader.read(source, field, typed)   # reads AND checks
```

### Invariants belong in constructors, not at point of use

Then a violation fails while the code that caused it is still on the stack, instead of surfacing
three layers away during serialization or I/O.

### Never store what you derive

If field A is a projection of field B, storing both invites them to disagree — and the disagreement
is usually silent. The classic trap: an object holds a rendered or cached representation *plus* the
collection it was rendered from, then grows a mutation method. Everything looks right; the rendered
half is stale. Derive it, or don't offer the mutation.

### One translation boundary per layer crossing

When a lower layer's exception must become a higher layer's error, translate it at the single entry
point, not at each construction site. Per-site wrapping is exactly what the next site forgets.

### Untestability is a design signal

A rule you can only reach through a full end-to-end run is misplaced. Extracting it isn't "refactor
and then test" — the difficulty of testing it was the evidence.

### Preserve user-facing strings verbatim when moving code

Error messages are contract. Reword them in a separate change, never as a side effect of a move.

### Price the visibility you widen

Judge a package split by what it forces public, not by how tidy the tree looks. That number should
be able to change your recommendation between two layouts.

### Follow the house style over your own

Match the surrounding idiom, comment density, and naming. Don't convert one convention to another
wholesale because you prefer it.

### Scope discipline

Real problems found adjacent to the work get *reported*, not fixed. Say what you found, why it's out
of scope, and let the owner decide.

## Verification

**Get a characterization net first.** Snapshot/golden tests over exact output are ideal behind a
refactor: they fail on any behavioral drift, including drift you didn't think to check. This repo
has one in `dws-controller`'s `V2GoldenTest`.

**Run a differential baseline before attributing failures.** When tests fail, don't reason about
whether the failure "looks like yours" — stash your changes, re-run, compare. Then root-cause the
pre-existing failures rather than stopping at "probably pre-existing"; the root cause is often a
real bug worth reporting.

**Compare failure *identity*, not just count.** "Same number of failures" is not the same claim as
"the same failures."

**Report the gate honestly.** Each package's gate is listed in the root `CLAUDE.md`. If it can't
run, say so and say why. Never present a partial run as a clean one.

**Put new API on a real path.** Unused-but-tested API is dead code with an alibi. Wiring it into one
genuine call site puts it under the existing suite — and is how you find the integration bugs its
unit tests can't see.

## Failure modes to expect

- **Bulk regex/`sed` edits silently drop modifiers.** Read the resulting diff before building; the
  compiler catches some of it, not all of it (access changes, mangled comments).
- **Extracting a rule can move it outside an existing safety wrapper.** Ask what boundary the old
  code sat inside.
- **A test you write can be unreachable by construction.** When one fails oddly, check the *test's*
  premise before the code's. If a case turns out impossible, delete it and record why, so nobody
  re-adds it.
- **Stale build artifacts after moving or renaming classes** produce confusing "cannot convert A to
  A" errors. Clean, or delete the stale outputs.
- **The urge to fix everything you touch.** Note it; don't do it.

## Checklist

- [ ] Scope classified and stated; design approved before edits
- [ ] Characterization tests exist and pass before starting
- [ ] Each extracted unit has direct tests it couldn't have had before
- [ ] Invariants enforced at construction
- [ ] Nothing stored that is derived
- [ ] User-facing strings unchanged
- [ ] Visibility widening counted and justified
- [ ] Failure set identical before and after, by identity
- [ ] Adjacent problems reported, not fixed
- [ ] The package's own gate run and reported honestly, including what couldn't run
