---
name: streamex
description: Guide for using StreamEx (one.util:streamex) in Java code — StreamEx/EntryStream/IntStreamEx operators, map-friendly pipelines, and the plain-Stream fallbacks to prefer. Use when writing or reviewing Java stream pipelines, collapsing verbose Collectors chains, iterating Maps, zipping/pairing elements, or when the user mentions StreamEx, EntryStream, or MoreCollectors.
---

# StreamEx

StreamEx is a zero-dependency enhancement of the JDK Stream API. Every `StreamEx<T>`
**is** a `java.util.stream.Stream<T>`, so it drops into existing code with no adapters
and no behavioral surprises. It adds shortcut operators, `Map`-aware streams, and
collectors the JDK never shipped.

## When to use

Reach for StreamEx when a plain-Stream pipeline is getting noisy:

| Smell in plain Stream | StreamEx answer |
|---|---|
| `.filter(Objects::nonNull)` | `.nonNull()` |
| `.filter(x -> !p.test(x))` | `.remove(p)` |
| `.collect(Collectors.toList())` | `.toList()` (already JDK 16+) / `.toImmutableList()` |
| `.collect(Collectors.toMap(k, v))` | `.toMap(k, v)` |
| `map.entrySet().stream()` + `Map.Entry::getKey` noise | `EntryStream.of(map)` |
| Manual index counter in a loop | `EntryStream.of(list)` (index → value) |
| Nested `flatMap` over a `Map<K, List<V>>` | `EntryStream.of(map).flatMapValues(List::stream)` |
| Comparing adjacent elements | `.pairMap(BiFunction)` |
| `Collectors.joining` on non-strings | `.joining(", ")` directly |

Do **not** reach for it when a plain `Stream`, an enhanced `for` loop, or a `switch`
is already clearer. StreamEx removes ceremony; it does not license 12-operator
pipelines. The project rule still holds: keep pipelines to ~3–4 operations, and
prefer a loop when the logic is genuinely imperative.

## Dependency

`dws-orchestrator` already declares it:

```xml
<properties>
  <streamex.version>0.8.3</streamex.version>
</properties>

<dependency>
  <groupId>one.util</groupId>
  <artifactId>streamex</artifactId>
  <version>${streamex.version}</version>
</dependency>
```

Adding it to another Java module means adding the same two blocks. Do not pull it
into `dws-controller` unless a pipeline there actually needs it — it is not
currently a controller dependency.

## The four entry types

```java
import one.util.streamex.StreamEx;      // Stream<T>       + extras
import one.util.streamex.EntryStream;   // Stream<Entry<K,V>> with key/value ops
import one.util.streamex.IntStreamEx;   // IntStream       + extras
import one.util.streamex.DoubleStreamEx; // (also LongStreamEx)
```

Creation:

```java
StreamEx.of(collection);
StreamEx.of(array);
StreamEx.of(a, b, c);              // varargs
StreamEx.of(optional);             // 0 or 1 element — no .stream().flatMap dance
StreamEx.of(iterator);
StreamEx.ofNullable(maybeNull);    // 0 or 1 element
StreamEx.ofLines(reader);
StreamEx.split("a,b,c", ",");      // no Pattern.compile boilerplate

EntryStream.of(map);
EntryStream.of(list);              // Integer index -> element
EntryStream.of(k1, v1, k2, v2);
EntryStream.zip(keys, values);     // two parallel lists -> pairs

IntStreamEx.range(0, n);
IntStreamEx.of(intArray);
```

## Core operators worth knowing

### Null and negative filtering

```java
// plain
list.stream().filter(Objects::nonNull).filter(s -> !s.isBlank())

// StreamEx
StreamEx.of(list).nonNull().remove(String::isBlank)
```

`nonNull()` is the single most-used operator in this repo — see
`InterpreterWorkflow.dispatchBody`, which streams nine mutually-exclusive
`getXxxTask()` accessors and keeps the one that is set:

```java
return StreamEx.of(
        task.getSwitchTask(),
        task.getCallTask(),
        task.getRunTask(),
        /* ... */
        task.getTryTask())
    .nonNull()
    .map(concreteTask -> dispatchConcreteTask(ctx, concreteTask, name, data, mapper))
    .findFirst()
    .orElseThrow(() -> new IllegalStateException("task '" + name + "' has an unsupported type"));
```

That is the idiom to copy for "exactly one of N optional fields is populated".

### Type-selecting

```java
// plain — filter then cast, two steps and an unchecked feel
stream.filter(CallTask.class::isInstance).map(CallTask.class::cast)

// StreamEx — one step, correctly typed
StreamEx.of(tasks).select(CallTask.class)
```

### Map building

```java
// plain
list.stream().collect(Collectors.toMap(Task::name, Function.identity()))

// StreamEx
StreamEx.of(list).toMap(Task::name, Function.identity());
StreamEx.of(list).mapToEntry(Task::name, Function.identity()).toMap();
```

`toMap` throws on duplicate keys, same as `Collectors.toMap`. Pass a merge
function when duplicates are legal — silence here is a real bug source.

### EntryStream — the main reason to adopt StreamEx

```java
// plain: rebuild a Map with transformed values
map.entrySet().stream()
   .filter(e -> e.getValue() != null)
   .collect(Collectors.toMap(Map.Entry::getKey, e -> render(e.getValue())));

// StreamEx
EntryStream.of(map)
    .nonNullValues()
    .mapValues(this::render)
    .toMap();
```

Key EntryStream operators:

| Operator | Effect |
|---|---|
| `mapKeys` / `mapValues` | transform one side, keep the other |
| `filterKeys` / `filterValues` | filter by one side |
| `removeKeys` / `removeValues` | inverse filter |
| `nonNullKeys` / `nonNullValues` | drop null halves |
| `flatMapValues` | `Map<K, List<V>>` → `EntryStream<K, V>` |
| `invert` | swap key and value |
| `keys()` / `values()` | project down to a `StreamEx` |
| `grouping()` | collect to `Map<K, List<V>>` |
| `join(" = ")` | render each entry to a String |
| `toMap()` / `toImmutableMap()` | terminal |

Indexed iteration without a counter variable:

```java
EntryStream.of(taskNames)               // index -> name
    .mapKeyValue((index, name) -> index + ": " + name)
    .joining("\n");
```

### pairMap — adjacent elements

```java
// deltas between consecutive timestamps
StreamEx.of(timestamps).pairMap((prev, next) -> Duration.between(prev, next));

// verify a list is strictly ascending
boolean sorted = StreamEx.of(values).pairMap((a, b) -> a.compareTo(b) < 0).allMatch(Boolean::booleanValue);
```

`pairMap` yields N-1 elements and is the intended replacement for index-based
`for (int i = 1; i < list.size(); i++)` comparison loops.

### Joining without a collector

```java
StreamEx.of(steps).map(Step::name).joining(", ", "[", "]");
EntryStream.of(headers).join(": ").joining("\n");
```

`joining` on `StreamEx<T>` calls `String.valueOf` per element — no `.map(Object::toString)` first.

### Useful terminals

```java
.toList() / .toSet() / .toImmutableList() / .toImmutableSet()
.toArray(String[]::new)
.groupingBy(Task::kind)
.foldLeft(identity, accumulator)   // reduce that does not require associativity
.headTail((head, tailStream) -> ...) // recursive/lazy decomposition
.distinct(Task::name)              // distinct BY a key — no wrapper record needed
.sortedBy(Task::name)              // shorthand for Comparator.comparing
.maxBy(Task::priority) / .minBy(...)
.collapse(predicate)               // merge adjacent elements that match
```

`distinct(keyExtractor)` and `sortedBy` alone remove a lot of `Comparator.comparing`
and `Collectors.toMap(..., (a, b) -> a)` boilerplate.

### MoreCollectors

```java
import static one.util.streamex.MoreCollectors.*;

stream.collect(first());              // Optional<T>, short-circuits
stream.collect(last());
stream.collect(head(10));             // first 10 as List
stream.collect(countingInt());        // int, not long
stream.collect(toEnumSet(Kind.class));
stream.collect(minMax(cmp, Pair::of));
stream.collect(onlyOne());            // Optional — empty if 0 or 2+ elements
```

`onlyOne()` is a good assertion collector: it makes "exactly one match expected"
explicit instead of `findFirst()` quietly hiding a second match.

## Gotchas

**Short-circuiting collectors need `StreamEx.collect`.** `first()`, `head(n)`, and
`onlyOne()` only short-circuit when applied to a StreamEx-family stream. Handing them
to a plain `Stream.collect` still gives the right answer but consumes the whole source.

**`headTail` is single-shot.** The tail stream passed to the lambda may be consumed
exactly once, and the lambda runs lazily on first terminal traversal. Do not capture
and reuse it.

**Parallel + `pairMap`/`collapse` is order-sensitive but supported.** These operators
are correctly parallelizable, but they force encounter order. Do not add `.parallel()`
expecting a speedup on small collections — the fork/join overhead dominates. In this
repo, workflow definitions are small; keep pipelines sequential unless profiling says
otherwise.

**Never `.parallel()` inside a Dapr workflow method.** Orchestrator code under
`io.dws.orchestrator.workflow` must stay deterministic and single-threaded for replay
to work. StreamEx pipelines there are sequential, always.

**Do not mutate the source during traversal.** Same rule as plain streams; StreamEx
does not add protection. Follow the project immutability rule — build a new collection,
do not mutate in a `forEach`.

**`toMap()` throws on duplicate keys.** Supply a merge function whenever the key is not
provably unique.

**Import discipline.** `one.util.streamex.StreamEx` collides in the reader's head with
`java.util.stream.Stream`. Do not alias, do not static-import the factories — write
`StreamEx.of(...)` in full so the entry point is obvious at the call site.

## Anti-patterns

| Anti-pattern | Why it is wrong | Do instead |
|---|---|---|
| Converting a plain `Stream` to `StreamEx` mid-pipeline just to use one shortcut | Extra wrapping for cosmetics | Start with `StreamEx.of(...)` or leave the pipeline alone |
| `StreamEx.of(map.entrySet())` | Loses every key/value operator | `EntryStream.of(map)` |
| 10-operator StreamEx chain replacing a readable loop | Density is not clarity | Split into named methods or use a loop |
| Adding the dependency to a module for a single `.nonNull()` | New dependency for one call site | `.filter(Objects::nonNull)` |
| `.parallel()` in orchestrator workflow code | Breaks Dapr replay determinism | Keep sequential |
| Side effects inside `.map` / `.peek` | Same hazard as plain streams | Pure transformations only |

## Checklist before committing a StreamEx pipeline

- [ ] Pipeline is ≤ ~4 operations, or split into named helper methods
- [ ] Entry type matches the shape: `EntryStream` for maps/indexed, `IntStreamEx` for primitives
- [ ] `toMap` has a merge function or keys are provably unique
- [ ] No `.parallel()` in Dapr workflow code
- [ ] No mutation of the source collection or captured state
- [ ] `StreamEx.` / `EntryStream.` written in full at the entry point
- [ ] `./mvnw verify` passes (google-java-format via Spotless will reformat the chain)

## Related

- `design-patterns` — when a pipeline is really a Strategy or Template Method in disguise
- Project rule `java/coding-style.md` — the ≤4-operation pipeline guidance this skill inherits
