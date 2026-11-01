# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this package.

Root-level cross-cutting rules (commits, contract-change etiquette, per-package gate commands) are in
the repo root [`CLAUDE.md`](../CLAUDE.md). This file is dws-orchestrator-specific idioms only,
inferred from the actual source — not generic Spring Boot advice.

## Commands

```shell
cd dws-orchestrator
./mvnw verify                      # compile + test
./mvnw test -Dtest=JqEvaluatorTest # single test class
```

Windows: `mvnw.cmd` instead of `./mvnw`.

## Style guide

### Null handling: `Optional.ofNullable(...).map(...).orElse(...)` chains are the idiom, use them liberally

This package leans much more heavily on chained `Optional` than `dws-controller` does — not just as
a finder return type, but as the default way to thread a possibly-absent value through
transformations. This is the repo-wide standard going forward; keep using it here.

```java
// DataFlowPipeline.java:48-67 pattern — chain, don't nest ifs
Object resolved = Optional.ofNullable(task.getInput())
        .map(input -> jq.evaluate(input, data))
        .orElse(data);
```

Reserve explicit `if (x == null)` for early-exit guard clauses and interpreter-loop internals where
a chain would obscure control flow, not for general value threading:

```java
// WorkflowSupport.java:156 — guard clause, correctly left as an explicit check
if (after == null) {
    return Duration.ZERO;
}
```

### Loops: index-based `for` for interpreter/program-counter logic, StreamEx for transforms

The interpreter's replay semantics depend on an explicit program counter, so those loops stay
classic `for` — don't refactor them into a stream:

```java
// InterpreterWorkflow.java:155 — pc must be an explicit mutable index, streams can't replay this
for (int steps = 0; pc >= 0 && pc < items.size(); steps++) {
    // ...
}
```

For actual transforms, this package uses **StreamEx** (`one.util.streamex.StreamEx`), not plain
`java.util.stream.Stream` — match that, don't introduce plain streams alongside it:

```java
// CallServiceActivity.java:57-61
int status = StreamEx.iterate(failure, Objects::nonNull, CallServiceActivity::nextCause)
        .select(DaprException.class)
        .mapToInt(DaprException::getHttpStatusCode)
        .findFirst(s -> s > 0)
        .orElse(0);
```

### DI: constructor injection, beans wired via `@Bean` factory methods in `@Configuration`

Zero `@Autowired`/`@Inject` usages in this package. Beans that hand off to the reflectively-constructed
Dapr workflow runtime are built with `@Bean` methods, not `@Component` scanning:

```java
// WorkflowRuntimeConfig.java:27-70 pattern
@Configuration
public class WorkflowRuntimeConfig {
    @Bean
    public InterpreterWorkflow interpreterWorkflow(@Qualifier("stepDefinition") StepDefinition def) {
        return new InterpreterWorkflow(def);
    }
}
```

### DTO/model: records for request/response I/O; `OrchestratorProperties` is the one deliberate exception

Everything that crosses an activity or HTTP boundary is a `record` (`CallRequest`, `EmitRequest`,
`InstanceStatusResponse`). `OrchestratorProperties` (`@ConfigurationProperties`) is a plain mutable
class with hand-written getters/setters — that's required because Spring binds config props via
setters, not a style regression; don't "fix" it into a record.

`lombok` is a declared dependency but used exactly once, `@lombok.experimental.UtilityClass` on
`WorkflowSupport` — don't reach for `@Data`/`@Getter`/`@Builder` here, it's not this package's
convention.

### Exceptions: `@ResponseStatus` on the exception class, no `@ControllerAdvice`

No global exception handler exists. Each HTTP-facing exception is annotated directly and Spring
MVC's default handling takes care of the response:

```java
// NotFoundException.java:7
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

Errors crossing the Dapr activity boundary are a different, deliberate pattern — only the exception's
**message** survives that boundary, so `StepInvocationException`/`RaisedErrorException` bake structured
info (app-id, HTTP status, error JSON) into `getMessage()` as marker-prefixed strings, decoded back by
`WorkflowErrors.classify`/`WorkflowErrors.of` (`WorkflowErrors.java:79-105,140-167`). If you add a new
activity-boundary failure mode, follow this string-marker convention rather than inventing a new
exception-passing mechanism — Dapr won't preserve anything else.

### Tests: AssertJ only, no Hamcrest; framework-free unit tests separate from `*IntegrationTest`

```java
// WorkflowErrorsTest.java:3 — only AssertJ import, no Hamcrest
import static org.assertj.core.api.Assertions.assertThat;
```

Plain unit tests (`WorkflowErrorsTest`, `SchemaValidatorTest`) carry no Spring test annotations —
keep pure-logic tests framework-free. Tests that need the actual workflow runtime are named
`*IntegrationTest` (`InterpreterWorkflowIntegrationTest`) — don't blur that naming boundary.

### Comments: multi-paragraph rationale comments are the convention here, not a package-level style doc

There's no CONTRIBUTING.md for this package — non-obvious decisions (`WorkflowErrors`,
`DataFlowPipeline`, `WorkflowSupport`) are explained with class/method-level comments in place. When
you add a similarly non-obvious mechanism, document it the same way rather than a separate doc file.

### Formatting

Same Spotless + `googleJavaFormat` 1.32.0 config as `dws-controller`, auto-applied on
`process-sources` — no separate format command needed.

### Logging: no log call in workflow-function code; `DWS_LOG_FORMAT` selects the structured format

**No log call goes inside workflow-function code** — `InterpreterWorkflow` and the child workflows
(`ForkBranchWorkflow`, `ScopeRunnerWorkflow`). That code re-executes on replay, so a line there is
written again on every replay, and it does not run under a span the engine keeps stable
([ADR 0009](../docs/adr/0009-trace-context-across-workflow-replay.md), decisions 1–2). Log from
activities (they run under the `activity:<name>` span and carry trace context) or from
startup/shutdown code. Not enforced by a test; keep it in review.

**Never pass a secret to a log call**: no resolved `SECRET_*` value, bearer token, `Authorization`
header or credential. Log identifiers (app IDs, definition keys, node IDs, topics) rather than
payloads, and be careful with exception messages from the Dapr client, which can embed URLs or
headers. `SecretLoggingTest` covers a run with `use.secrets`; the JSON scrubber is defence in depth,
and text mode has none.

**`DWS_LOG_FORMAT`** is the package-wide log-format switch, specified in
[`docs/log-format.md`](../docs/log-format.md) (keys, severity map, scrubber, golden cases):

- `application.yaml` exposes it as `dws.log.format: ${DWS_LOG_FORMAT:text}` — the literal name must
  stay there, `dws-controller`'s `OrchestratorEnvContractTest` asserts the orchestrator reads it.
- `LogFormatEnvironmentPostProcessor` (registered in `META-INF/spring.factories` under
  `org.springframework.boot.EnvironmentPostProcessor`) maps a trimmed, case-insensitive `json` onto
  `logging.structured.format.console` = `DwsJsonLogFormatter` (and `spring.main.banner-mode=off`,
  since the banner is printed outside the logging system), before Logback initialises, so boot
  lines are JSON too. Anything else (unset, empty, `text`, `yaml`) sets nothing and leaves Spring
  Boot's default console output and banner unchanged. Do not set the Boot property to an empty
  string instead: that also suppresses the banner.
- `DwsJsonLogFormatter` takes `trace_id`/`span_id` from the MDC keys the OpenTelemetry Java agent
  populates (no OTel dependency), omits both when absent or invalid, and scrubs `message`,
  `exception.message` and `exception.stacktrace`. `LogFormatContractTest` runs golden cases 1–11 (12,
  `FATAL`, does not exist in Logback); keep it green when touching the formatter.
