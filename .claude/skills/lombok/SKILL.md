---
name: lombok
description: Guide for using Project Lombok in this repo's Java modules — which annotations are allowed, which are banned, records-first rules, Spring constructor injection with @RequiredArgsConstructor, @Slf4j logging, and the delomboking/annotation-processor gotchas. Use when writing or reviewing Java in dws-orchestrator, when tempted to add @Data/@Builder/@Getter, or when the user mentions Lombok, boilerplate reduction, or a Lombok compile error.
---

# Lombok

Lombok is an annotation processor that generates boilerplate (constructors, accessors,
builders, loggers) into the `.class` file at compile time. Nothing ships at runtime —
the dependency is `<optional>true</optional>` and never lands in the fat jar.

Java 25 records, `var`, and pattern matching already delete most of the boilerplate
Lombok was invented for. **In this repo Lombok is a narrow tool, not a default style.**

## Where it is available

Only `dws-orchestrator`:

```xml
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <optional>true</optional>
</dependency>
```

Version comes from the `spring-boot-starter-parent` BOM — do **not** pin a version.
The Spring Boot parent also pre-wires `maven-compiler-plugin` annotation processing, so
no `<annotationProcessorPaths>` block is needed.

`dws-controller` is Quarkus and has **no** Lombok. Do not add it there — Quarkus
build-time augmentation, `@RegisterForReflection`, and Lombok-generated members interact
badly, and the controller's compiler/CDI model is already working. Write the constructor
by hand.

## Current usage

One annotation, one file — `WorkflowSupport`:

```java
import lombok.experimental.UtilityClass;

@UtilityClass
public class WorkflowSupport { ... }
```

`@UtilityClass` makes the class `final`, adds a private throwing constructor, and makes
every member `static`. That is exactly right for the static bridge between Spring beans
and the reflectively-instantiated Dapr workflow classes.

**Note the trap:** inside `@UtilityClass` you write `private volatile Workflow definition;`
and Lombok silently makes it `static`. The source therefore does not read like the
bytecode. In this file the fields are written with an explicit `static` anyway — keep
that habit so a reader is never misled.

## Allowed annotations

| Annotation | Use it for |
|---|---|
| `@UtilityClass` | Static-only holder/helper classes |
| `@RequiredArgsConstructor` | Spring `@Component`/`@Service` constructor injection over `final` fields |
| `@Slf4j` | Logger field, instead of `LoggerFactory.getLogger(X.class)` |
| `@NonNull` (on params) | Null-check at a public boundary; generates `NullPointerException` with the parameter name |
| `@Cleanup` | Rare; prefer try-with-resources — only for a resource whose type is not `AutoCloseable` |

### Constructor injection

```java
@Service
@RequiredArgsConstructor
public class DefinitionLoader {
  private final DaprClient daprClient;
  private final ObjectMapper mapper;
}
```

Rules: fields `private final`, no `@Autowired` anywhere, no field injection. If a
constructor needs any logic (validation, defaulting, defensive copy), delete
`@RequiredArgsConstructor` and write the constructor — a hidden generated constructor
plus a hand-written one is a merge hazard.

### Logging

```java
@Slf4j
public class CallTaskActivity implements WorkflowActivity {
  public Object run(WorkflowActivityContext ctx) {
    log.debug("invoking app-id={}", appId);
  }
}
```

Generates `private static final org.slf4j.Logger log`. Use `{}` placeholders, never
string concatenation. Never log workflow data payloads at `info` — they can carry
caller-supplied content.

## Banned annotations

| Banned | Why | Do instead |
|---|---|---|
| `@Data` | Bundles `@Setter` + mutable `equals`/`hashCode` + `toString`; violates the project immutability rule and is a live bug source in hash-based collections | `record`, or explicit `final` fields |
| `@Setter` | Mutation. The project rule is copy-on-write | New instance, or a `with...` method |
| `@Getter` on a new type | Java 25 has records | `record Foo(String bar) {}` |
| `@Builder` | Adds a mutable builder and skips constructor validation; records + static factories cover this repo's needs | Static factory `Foo.of(...)`, or a hand-written builder when parameters are genuinely many and optional |
| `@Value` | Poor-man's record from Java 8 | `record` |
| `@SneakyThrows` | Throws a checked exception the signature does not declare — callers cannot catch it, and in Dapr activity code it produces exceptions the retry policy cannot classify | Declare it, or wrap in a domain `RuntimeException` with context |
| `@EqualsAndHashCode` / `@ToString` on entities | Generated over all fields, including lazily-loaded or huge payloads | Write it, over the identity fields only |
| `@FieldDefaults` / `@ExtensionMethod` / `@var` | Non-obvious source semantics; the reader cannot see what compiles | Explicit modifiers |

## Records first

Before any Lombok annotation on a data-carrying type, ask whether a record works:

```java
// Prefer
public record StepResult(String taskName, JsonNode output, Duration elapsed) {}

// Not
@Data
public class StepResult { private String taskName; ... }
```

Records get accessors, `equals`, `hashCode`, `toString`, and immutability from the
language, with no processor involved. Compact constructors give validation and defensive
copies:

```java
public record StepResult(String taskName, JsonNode output) {
  public StepResult {
    Objects.requireNonNull(taskName, "taskName");
  }
}
```

Lombok on a record is legal only for `@Slf4j` and `@Builder`; since `@Builder` is banned
here, that leaves `@Slf4j`. Everything else is a signal the type should not be a record.

## Gotchas

**Annotation processing must be on.** A "cannot find symbol: method getX()" or
"variable log not found" error is almost always a disabled processor or a stale
`target/` — run `./mvnw clean compile` in `dws-orchestrator` before debugging further.
Do not "fix" it by hand-writing the member next to the annotation.

**IDE needs the plugin.** IntelliJ/Eclipse show phantom errors without Lombok support
enabled. Maven is the source of truth: if `./mvnw verify` passes, the code is fine.

**Spotless does not see generated code.** google-java-format formats the source you
wrote; generated members are invisible to it and to code review. That asymmetry is the
core reason the banned list above is long — anything a reviewer cannot read in the diff
is a liability.

**Do not mix Lombok with Jackson on the same type.** Jackson binds via the constructor or
accessors it can see reflectively. A record with an explicit canonical constructor is
predictable; a Lombok-generated all-args constructor plus `@JsonCreator` expectations is
not. All DSL/workflow-data types here go through `ObjectMapper` — keep them plain.

**Nothing generated inside Dapr workflow methods.** Orchestrator code under
`io.dws.orchestrator.workflow` must be deterministic for replay. Lombok itself is
replay-safe (compile-time only), but `@SneakyThrows` and generated `toString` on payload
objects hide behavior at exactly the place where behavior must be obvious.

**Delombok is the escape hatch.** If Lombok ever has to be removed, `mvn lombok:delombok`
writes the expanded source. Being able to run it is not a reason to lean on the banned
annotations.

## Checklist before committing Lombok usage

- [ ] Module is `dws-orchestrator` — not `dws-controller`
- [ ] A `record` was considered first and rejected for a stated reason
- [ ] Annotation is on the allowed list
- [ ] No `@Data`, `@Setter`, `@Builder`, `@Value`, `@SneakyThrows`
- [ ] Injected fields are `private final`, no `@Autowired`
- [ ] No hand-written member duplicating a generated one
- [ ] `log` used with `{}` placeholders, no payload logging at `info`
- [ ] `./mvnw verify` passes from a clean `target/`

## Related

- `design-patterns` — builder/strategy guidance when `@Builder` is the wrong answer
- `streamex` — the other "reduce ceremony" library in this module, same discipline applies
- Project rule `java/coding-style.md` — records, immutability, constructor injection
