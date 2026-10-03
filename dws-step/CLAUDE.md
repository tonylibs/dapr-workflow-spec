# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-step-specific idioms only.

## Commands

```shell
cd dws-step
./mvnw verify
```

Windows: `mvnw.cmd` instead of `./mvnw`.

## Style guide

This package is small (one config loader, one static holder, one health endpoint) — several
conventions below are "the one example that exists," not a statistically established pattern. Where
that matters, it's called out; when in doubt, follow `dws-orchestrator`'s equivalent choice since
both are Spring Boot and share the `@Bean`/constructor-injection wiring style.

### Null handling: `Optional` chain when there's more than a null check, plain guard otherwise

When a value needs a null check *plus* further checks (type, blank, shape) before use, express it as
an `Optional` chain ending in `orElseThrow` rather than a compound `if`:

```java
// SingleNodeDefinitionLoader.java pattern
String functionAppId =
    Optional.ofNullable(definition.get("functionAppId"))
        .filter(JsonNode::isTextual)
        .map(JsonNode::textValue)
        .filter(StringUtils::isNotBlank)
        .orElseThrow(() -> new DefinitionLoadException("functionAppId is required ..."));
```

A bare null check with nothing else stays a plain `if (x == null)` guard — wrapping a single
check in `Optional` adds nothing. Use `commons-lang3` (`StringUtils`, `BooleanUtils`) for the
predicates in these chains; it's a declared dependency.

### Loops: StreamEx for a filter-to-list, matching dws-orchestrator's library choice

```java
// SingleNodeDefinition.java:15
List<String> presentTasks = StreamEx.of(KNOWN_TASK_KINDS).filter(task::has).toList();
```

### DI: constructor injection, `@Bean` factory methods — same as dws-orchestrator

```java
// StepRuntimeConfig.java:11-20 pattern
@Configuration
public class StepRuntimeConfig {
    @Bean
    public SingleNodeDefinition definition() {
        return SingleNodeDefinitionLoader.load(System.getenv("DWS_STEP_DEFINITION_PATH"));
    }
}
```

### Exceptions: `DefinitionLoadException` is a startup-time failure, not a request-time one

`DefinitionLoadException extends RuntimeException` (two constructors: message-only, message+cause)
is thrown from inside a `@Bean` method so an invalid definition file fails application startup —
there is deliberately no `@ResponseStatus`, no `ExceptionMapper`, no global handler, because the
package's only HTTP endpoint (`/healthz`) can't fail. Don't add error-handling machinery here for
a request path that doesn't exist; if this package grows a real API surface, follow
`dws-orchestrator`'s `@ResponseStatus`-on-exception pattern instead of inventing a third approach.

```java
// StepRuntimeConfig.java:18-20 pattern — let a bad definition kill the app at boot
@Bean
public SingleNodeDefinition definition() {
    try {
        return SingleNodeDefinitionLoader.load(path);
    } catch (DefinitionLoadException e) {
        throw e; // fails context startup — intentional, not a bug
    }
}
```

### Tests: AssertJ, `@TempDir` fixtures, small inline helper methods over a builder class

```java
// SingleNodeDefinitionLoaderTest.java pattern
@TempDir
Path tempDir;

private Path write(String name, String content) throws IOException {
    Path file = tempDir.resolve(name);
    Files.writeString(file, content);
    return file;
}

@Test
void rejectsMissingKind() {
    assertThatThrownBy(() -> SingleNodeDefinitionLoader.load(write("def.json", "{}")))
            .isInstanceOf(DefinitionLoadException.class);
}
```

No builder/fixture class exists for this — for a package this small, a couple of one-line `write(...)`
helpers beat introducing a `DefinitionFixtures` class. Reconsider only if the test file grows large
enough that the inline helpers stop pulling their weight.

### Formatting

Same Spotless + `googleJavaFormat` 1.32.0 config as the other two Java packages, auto-applied on
`process-sources`.
