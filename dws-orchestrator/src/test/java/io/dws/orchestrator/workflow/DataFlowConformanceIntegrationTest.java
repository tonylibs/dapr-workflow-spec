package io.dws.orchestrator.workflow;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.dapr.durabletask.Task;
import io.dapr.workflows.WorkflowContext;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.activity.AdminEventActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionActivity;
import io.dws.orchestrator.workflow.activity.CatchDecisionRequest;
import io.dws.orchestrator.workflow.activity.CatchPolicy;
import io.dws.orchestrator.workflow.activity.DataFlowInputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowInputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowOutputActivity;
import io.dws.orchestrator.workflow.activity.DataFlowOutputRequest;
import io.dws.orchestrator.workflow.activity.DataFlowPipeline;
import io.dws.orchestrator.workflow.activity.EvaluateForActivity;
import io.dws.orchestrator.workflow.activity.EvaluateForRequest;
import io.dws.orchestrator.workflow.activity.EvaluateSetActivity;
import io.dws.orchestrator.workflow.activity.EvaluateSetRequest;
import io.dws.orchestrator.workflow.activity.EvaluateWhileActivity;
import io.dws.orchestrator.workflow.activity.EvaluateWhileRequest;
import io.dws.orchestrator.workflow.activity.RaiseErrorActivity;
import io.dws.orchestrator.workflow.activity.RaiseErrorRequest;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.stubbing.Answer;

/**
 * The v1 runner for the shared data-flow conformance corpus ({@code conformance/data-flow} at the
 * repository root, ADR 0012 Decision 10). Every case is one dynamic test, reported as pass, fail or
 * known divergence, with a summary table printed after the run.
 *
 * <p>Each case's workflow document is parsed with the Open Workflow Specification SDK and driven
 * through the real {@link InterpreterWorkflow} against a mocked {@link WorkflowContext}, the same
 * technique {@link InterpreterWorkflowIntegrationTest} uses. Every in-process activity runs its
 * real body, and a {@code fork} branch runs the real {@link ForkBranchWorkflow} — so what is
 * compared is v1's own data flow, not a mock's arrangement. The corpus is restricted to pure task
 * kinds ({@code set}, {@code raise}, {@code for}, {@code fork}, {@code try}); anything else
 * reaching the context (a step call, a timer, an event) fails the case loudly rather than being
 * faked.
 *
 * <p>The observed result is read only through what crosses v1's boundaries: the completion output
 * passed to {@code ctx.complete}, the context the top-level scope ends with, and — for a fault —
 * the exception <em>message</em>, parsed with the same {@code task '<name>' <phase> data flow
 * failed: <detail>} shape that is all that survives the Dapr activity boundary.
 *
 * <p>Known divergences live in {@code divergences/v1.yaml}, never in the cases: a listed case that
 * still diverges is reported as aborted with its reason and follow-up, and a listed case that now
 * passes fails the build so its entry is removed.
 */
class DataFlowConformanceIntegrationTest {

  /** Overridable so the corpus can be run from elsewhere; defaults to the repository's copy. */
  private static final Path CORPUS =
      Path.of(System.getProperty("dws.conformance.dataflow", "../conformance/data-flow"))
          .toAbsolutePath()
          .normalize();

  /** The one fault shape v1 produces for a data-flow failure ({@code DataFlowException}). */
  private static final Pattern FAULT =
      Pattern.compile(
          "task '(?<task>[^']+)' (?<phase>input|output|export) data flow failed: (?<detail>.*)",
          Pattern.DOTALL);

  /** v1 renders schema errors as {@code <pointer>: <message>} entries joined by {@code "; "}. */
  private static final String SCHEMA_FAILURE = "schema validation failed: ";

  private static final List<String> FOLLOW_UPS = List.of("5.1", "5.2");

  /** The pure task kinds a case may use; anything else would need the runner to fake I/O. */
  private static final List<String> KINDS = List.of("set", "raise", "for", "fork", "try");

  /** Numbers compare by value, so a case's {@code 10} matches a jq {@code 10.0}. */
  private static final Comparator<JsonNode> NUMERIC_EQUALITY =
      (a, b) ->
          a.equals(b)
                  || (a.isNumber()
                      && b.isNumber()
                      && a.decimalValue().compareTo(b.decimalValue()) == 0)
              ? 0
              : 1;

  private static final List<Outcome> OUTCOMES = new ArrayList<>();

  private final ObjectMapper mapper = new ObjectMapper();
  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

  /**
   * One dynamic test per case, reported under the case's {@code test} name. A case without one
   * falls back to its path so it still runs, and then fails as malformed.
   */
  @TestFactory
  Stream<DynamicTest> givenCorpusCase_whenRunOnV1_thenConformsOrIsKnownDivergence()
      throws IOException {
    Schema caseSchema = caseSchema();
    Map<String, Divergence> divergences = divergences();
    return StreamEx.of(caseFiles())
        .map(
            file ->
                DynamicTest.dynamicTest(
                    testName(file), file.toUri(), () -> runCase(file, caseSchema, divergences)));
  }

  /** Test names are what a report shows, so two cases must never share one. */
  @Test
  void givenCorpus_whenTestNamesCollected_thenEachIsUnique() throws IOException {
    List<String> testNames = StreamEx.of(caseFiles()).map(this::testName).toList();
    assertThat(testNames).doesNotHaveDuplicates();
  }

  /**
   * The divergence list must stay honest on its own terms: every entry names a real case, gives a
   * one-line reason, and points at a follow-up task that owns the fix.
   */
  @Test
  void givenDivergenceList_whenLoaded_thenEveryEntryNamesExistingCaseAndFollowUp()
      throws IOException {
    List<String> names =
        StreamEx.of(caseFiles()).map(DataFlowConformanceIntegrationTest::caseName).toList();
    assertThat(divergences().values())
        .allSatisfy(
            divergence -> {
              assertThat(names)
                  .as("divergence names a corpus case")
                  .contains(divergence.caseName());
              assertThat(divergence.reason())
                  .as(divergence.caseName())
                  .isNotBlank()
                  .doesNotContain("\n");
              assertThat(FOLLOW_UPS).as(divergence.caseName()).contains(divergence.followUp());
            });
  }

  @AfterAll
  static void printReport() {
    if (OUTCOMES.isEmpty()) {
      return;
    }
    StringBuilder report =
        new StringBuilder("\nData-flow conformance corpus on v1 (dws-orchestrator)\n");
    StreamEx.of(OUTCOMES)
        .sortedBy(Outcome::caseName)
        .forEach(
            o ->
                report.append(
                    String.format(
                        "  %-17s %-48s %s%n", o.verdict().label, o.caseName(), o.note())));
    Map<Verdict, Long> totals =
        StreamEx.of(OUTCOMES).groupingBy(Outcome::verdict, Collectors.counting());
    report.append(
        String.format(
            "  %d cases: %d pass, %d fail, %d known divergence, %d stale divergence%n",
            OUTCOMES.size(),
            totals.getOrDefault(Verdict.PASS, 0L),
            totals.getOrDefault(Verdict.FAIL, 0L),
            totals.getOrDefault(Verdict.DIVERGES, 0L),
            totals.getOrDefault(Verdict.STALE, 0L)));
    System.out.println(report);
  }

  // ---- one case ------------------------------------------------------------

  private void runCase(Path file, Schema caseSchema, Map<String, Divergence> divergences)
      throws IOException {
    String name = caseName(file);
    JsonNode testCase = yaml.readTree(file.toFile());

    // A malformed case is a corpus bug, not a v1 result: fail it before running anything.
    List<String> problems = malformations(file, testCase, caseSchema);
    if (!problems.isEmpty()) {
      record(name, Verdict.FAIL, "malformed case: " + String.join("; ", problems));
      fail(name + " is malformed:\n  " + String.join("\n  ", problems));
    }

    List<String> mismatches = compare(testCase.path("expect"), observe(testCase));
    Divergence divergence = divergences.get(name);

    if (divergence == null) {
      record(
          name, mismatches.isEmpty() ? Verdict.PASS : Verdict.FAIL, String.join("; ", mismatches));
      if (!mismatches.isEmpty()) {
        fail(name + " does not conform:\n  " + String.join("\n  ", mismatches));
      }
      return;
    }
    if (mismatches.isEmpty()) {
      record(name, Verdict.STALE, "listed in divergences/v1.yaml but now conforms");
      fail(name + " now conforms; remove it from divergences/v1.yaml");
    }
    String note = "(" + divergence.followUp() + ") " + divergence.reason();
    record(name, Verdict.DIVERGES, note);
    Assumptions.abort("known divergence " + note + "; observed: " + String.join("; ", mismatches));
  }

  /** What makes a case file unusable: schema violations, or fields that disagree with the file. */
  private static List<String> malformations(Path file, JsonNode testCase, Schema caseSchema) {
    List<String> problems = new ArrayList<>();
    caseSchema.validate(testCase).forEach(error -> problems.add("case.schema.json: " + error));
    if (!testCase.path("name").asText().equals(caseName(file))) {
      problems.add("name is not the file path " + caseName(file));
    }
    if (!testCase.path("area").asText().equals(file.getParent().getFileName().toString())) {
      problems.add("area is not the directory " + file.getParent().getFileName());
    }
    TreeSet<String> declared =
        new TreeSet<>(StreamEx.of(testCase.path("uses").elements()).map(JsonNode::asText).toList());
    TreeSet<String> actual = usedKinds(testCase.path("workflow").path("do"));
    if (!declared.equals(actual)) {
      problems.add("uses " + declared + " but the workflow contains " + actual);
    }
    return problems;
  }

  /**
   * Every task kind in a task list, recursing into the lists {@code for}/{@code fork}/{@code try}
   * nest.
   */
  private static TreeSet<String> usedKinds(JsonNode taskList) {
    TreeSet<String> kinds = new TreeSet<>();
    for (JsonNode item : taskList) {
      JsonNode task = item.elements().next();
      StreamEx.of(KINDS)
          .filter(task::has)
          .findFirst()
          .ifPresentOrElse(kinds::add, () -> kinds.add("<unsupported>"));
      kinds.addAll(usedKinds(task.path("do")));
      kinds.addAll(usedKinds(task.path("try")));
      kinds.addAll(usedKinds(task.path("catch").path("do")));
      kinds.addAll(usedKinds(task.path("fork").path("branches")));
    }
    return kinds;
  }

  private List<String> compare(JsonNode expect, Observed observed) {
    List<String> mismatches = new ArrayList<>();
    JsonNode fault = expect.get("fault");
    if (fault == null) {
      if (observed.failure() != null) {
        mismatches.add("expected completion, failed with: " + observed.failure());
        return mismatches;
      }
      if (!expect.get("output").equals(NUMERIC_EQUALITY, observed.output())) {
        mismatches.add("output " + observed.output() + " != expected " + expect.get("output"));
      }
      if (!expect.get("context").equals(NUMERIC_EQUALITY, observed.context())) {
        mismatches.add("context " + observed.context() + " != expected " + expect.get("context"));
      }
      return mismatches;
    }

    if (observed.failure() == null) {
      mismatches.add("expected a fault, completed with output " + observed.output());
      return mismatches;
    }
    Optional<Matcher> dataFlowFault = dataFlowFault(observed.failure());
    if (dataFlowFault.isEmpty()) {
      mismatches.add("expected a data-flow fault, failed with: " + rootMessage(observed.failure()));
      return mismatches;
    }
    Matcher m = dataFlowFault.get();
    String detail = m.group("detail");
    if (!fault.path("task").asText().equals(m.group("task"))) {
      mismatches.add(
          "fault names task '"
              + m.group("task")
              + "', expected '"
              + fault.path("task").asText()
              + "'");
    }
    if (!fault.path("phase").asText().equals(m.group("phase"))) {
      mismatches.add(
          "fault names phase " + m.group("phase") + ", expected " + fault.path("phase").asText());
    }
    Optional.ofNullable(fault.get("location"))
        .map(JsonNode::asText)
        .filter(location -> !schemaLocations(detail).contains(location))
        .ifPresent(
            location -> mismatches.add("fault does not name location " + location + ": " + detail));
    StreamEx.of(fault.path("detailContains").elements())
        .map(JsonNode::asText)
        .remove(detail::contains)
        .forEach(missing -> mismatches.add("fault detail lacks '" + missing + "': " + detail));
    return mismatches;
  }

  /** The instance locations named in a v1 schema failure, {@code ""} standing for the root. */
  private static List<String> schemaLocations(String detail) {
    if (!detail.startsWith(SCHEMA_FAILURE)) {
      return List.of();
    }
    return StreamEx.split(detail.substring(SCHEMA_FAILURE.length()), "; ")
        .map(entry -> entry.substring(0, Math.max(entry.indexOf(": "), 0)))
        .map(location -> location.equals("<root>") ? "" : location)
        .toList();
  }

  /** The first message in the cause chain shaped like v1's data-flow fault. */
  private static Optional<Matcher> dataFlowFault(Throwable failure) {
    return StreamEx.iterate(failure, Objects::nonNull, Throwable::getCause)
        .map(Throwable::getMessage)
        .nonNull()
        .map(FAULT::matcher)
        .findFirst(Matcher::find);
  }

  private static String rootMessage(Throwable failure) {
    return StreamEx.iterate(failure, Objects::nonNull, Throwable::getCause)
        .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
        .joining(" <- ");
  }

  // ---- running a case on v1 ------------------------------------------------

  private record Observed(JsonNode output, JsonNode context, Throwable failure) {}

  private Observed observe(JsonNode testCase) throws IOException {
    Workflow definition =
        WorkflowReader.readWorkflowFromString(
            mapper.writeValueAsString(testCase.get("workflow")), WorkflowFormat.JSON);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "conformance",
        "conformance@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient: no case reaches the network */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");

    RecordingInterpreter interpreter = new RecordingInterpreter();
    AtomicReference<Object> completed = new AtomicReference<>();
    WorkflowContext ctx = context(testCase.get("input"), completed);
    try {
      interpreter.execute(ctx);
    } catch (RuntimeException e) {
      return new Observed(null, null, e);
    }
    return new Observed((JsonNode) completed.get(), interpreter.topLevelContext, null);
  }

  /**
   * Captures the context the top-level {@code do} list ends with. It is deliberately not part of
   * the completion output, so this override is the only place v1 exposes it. Nested scopes ({@code
   * try}, {@code for}) run at depth 1 and deeper and are not recorded.
   */
  private static final class RecordingInterpreter extends InterpreterWorkflow {
    private JsonNode topLevelContext;

    @Override
    ScopeResult runTaskList(
        WorkflowContext ctx,
        List<io.serverlessworkflow.api.types.TaskItem> items,
        JsonNode data,
        JsonNode context,
        Map<String, JsonNode> variables,
        int depth,
        AdminEventBuilder events,
        ObjectMapper mapper,
        DispatchContext dispatchContext) {
      ScopeResult result =
          super.runTaskList(
              ctx, items, data, context, variables, depth, events, mapper, dispatchContext);
      if (depth == 0) {
        topLevelContext = result.context();
      }
      return result;
    }
  }

  /**
   * A workflow context where every in-process activity runs its real body when awaited and a {@code
   * fork} branch runs the real {@link ForkBranchWorkflow} in a child context of its own. Anything
   * else throws, so a case can never silently depend on something this runner does not model.
   */
  private WorkflowContext context(Object input, AtomicReference<Object> completed) {
    WorkflowContext ctx = mock(WorkflowContext.class, UNSUPPORTED);
    doReturn("conformance").when(ctx).getInstanceId();
    doReturn(Instant.parse("2026-10-15T00:00:00Z")).when(ctx).getCurrentInstant();
    doReturn(input).when(ctx).getInput(any());
    doAnswer(
            inv -> {
              completed.set(inv.getArgument(0));
              return null;
            })
        .when(ctx)
        .complete(any());

    EntryStream.of(
            Map.<String, Function<Object, Object>>of(
                AdminEventActivity.class.getName(), request -> null,
                EvaluateSetActivity.class.getName(),
                    r -> EvaluateSetActivity.apply((EvaluateSetRequest) r),
                EvaluateForActivity.class.getName(),
                    r -> EvaluateForActivity.apply((EvaluateForRequest) r),
                EvaluateWhileActivity.class.getName(),
                    r -> EvaluateWhileActivity.apply((EvaluateWhileRequest) r),
                RaiseErrorActivity.class.getName(),
                    r -> RaiseErrorActivity.apply((RaiseErrorRequest) r),
                CatchDecisionActivity.class.getName(),
                    r -> CatchPolicy.decide((CatchDecisionRequest) r),
                DataFlowInputActivity.class.getName(),
                    r -> DataFlowPipeline.applyInput((DataFlowInputRequest) r),
                DataFlowOutputActivity.class.getName(),
                    r -> DataFlowPipeline.applyOutput((DataFlowOutputRequest) r)))
        .forKeyValue(
            (activity, body) ->
                doAnswer(inv -> deferred(() -> body.apply(inv.getArgument(1))))
                    .when(ctx)
                    .callActivity(eq(activity), any(), any(WorkflowTaskOptions.class), any()));

    doAnswer(
            inv -> {
              ForkBranchInput branch = inv.getArgument(1);
              return deferred(
                  () -> {
                    AtomicReference<Object> branchResult = new AtomicReference<>();
                    new ForkBranchWorkflow().execute(context(branch, branchResult));
                    return branchResult.get();
                  });
            })
        .when(ctx)
        .callChildWorkflow(
            eq(ForkBranchWorkflow.NAME), any(), eq(InterpreterWorkflow.Dispatch.class));

    doAnswer(
            inv -> {
              List<Task<Object>> handles = inv.getArgument(0);
              return deferred(() -> StreamEx.of(handles).map(Task::await).toList());
            })
        .when(ctx)
        .allOf(any(List.class));
    return ctx;
  }

  private static final Answer<Object> UNSUPPORTED =
      inv -> {
        if (inv.getMethod().getName().equals("toString")) {
          return "conformance WorkflowContext";
        }
        throw new UnsupportedOperationException(
            "the conformance runner does not model WorkflowContext."
                + inv.getMethod().getName()
                + "; corpus cases use only set, raise, for, fork and try");
      };

  /**
   * A durable task that runs {@code body} when awaited, as Dapr surfaces an activity's result or
   * failure at {@code await()}, and that honours {@code thenApply} like the real task API.
   */
  @SuppressWarnings("unchecked")
  private static <T> Task<T> deferred(Supplier<T> body) {
    Task<T> task = mock(Task.class);
    when(task.await()).thenAnswer(ignored -> body.get());
    when(task.thenApply(any()))
        .thenAnswer(
            inv -> {
              Function<T, Object> transform = inv.getArgument(0);
              return deferred(() -> transform.apply(body.get()));
            });
    return task;
  }

  // ---- corpus files --------------------------------------------------------

  private static List<Path> caseFiles() throws IOException {
    Path cases = CORPUS.resolve("cases");
    assertThat(cases)
        .as("conformance corpus (set -Ddws.conformance.dataflow to move it)")
        .isDirectory();
    try (Stream<Path> files = Files.walk(cases)) {
      return files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList();
    }
  }

  private static String caseName(Path file) {
    Path relative = CORPUS.resolve("cases").relativize(file);
    String name = relative.toString().replace('\\', '/');
    return name.substring(0, name.length() - ".yaml".length());
  }

  /** The case's givenX_whenY_thenZ {@code test} name, or its path when the file lacks one. */
  private String testName(Path file) {
    try {
      return Optional.ofNullable(yaml.readTree(file.toFile()).get("test"))
          .map(JsonNode::asText)
          .filter(not(String::isBlank))
          .orElseGet(() -> caseName(file));
    } catch (IOException e) {
      return caseName(file);
    }
  }

  private Schema caseSchema() throws IOException {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(mapper.readTree(CORPUS.resolve("case.schema.json").toFile()));
  }

  private record Divergence(String caseName, String reason, String followUp) {}

  private Map<String, Divergence> divergences() throws IOException {
    JsonNode list =
        yaml.readTree(CORPUS.resolve("divergences/v1.yaml").toFile()).path("divergences");
    return StreamEx.of(list.elements())
        .map(
            d ->
                new Divergence(
                    d.path("case").asText(),
                    d.path("reason").asText(),
                    d.path("followUp").asText()))
        .toMap(Divergence::caseName, Function.identity());
  }

  // ---- report --------------------------------------------------------------

  private enum Verdict {
    PASS("pass"),
    FAIL("FAIL"),
    DIVERGES("known divergence"),
    STALE("STALE divergence");

    private final String label;

    Verdict(String label) {
      this.label = label;
    }
  }

  private record Outcome(String caseName, Verdict verdict, String note) {}

  private static synchronized void record(String caseName, Verdict verdict, String note) {
    OUTCOMES.add(new Outcome(caseName, verdict, note));
  }
}
