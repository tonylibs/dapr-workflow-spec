package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the catch verdict directly: static filtering, the dynamic conditions, retry-policy
 * resolution, limits, and the backoff/jitter arithmetic. The workflow method only branches on what
 * this returns, so everything worth asserting about catch semantics is asserted here.
 */
class CatchPolicyTest {

  private static final String FAILURE_503 =
      "step 'fetch-order' failed with status 503: upstream down";
  private static final String FAILURE_500 = "step 'fetch-order' failed with status 500: boom";

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void seedFixture() throws Exception {
    seed(WorkflowReader.readWorkflowFromClasspath("try-order.yaml"));
  }

  private void seed(Workflow definition) {
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "try-order-workflow",
        "try-order-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        /* daprClient (unused) */ null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  /** Re-seeds support from an inline definition so a test can vary the catch clause. */
  private void seedYaml(String yaml) throws Exception {
    seed(WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML));
  }

  private CatchDecisionRequest request(String failureMessage, int attempt) {
    return request(failureMessage, attempt, 0L, 1_000L);
  }

  private CatchDecisionRequest request(
      String failureMessage, int attempt, long firstFailure, long now) {
    return new CatchDecisionRequest(
        "guarded",
        "fetchOrder",
        failureMessage,
        attempt,
        firstFailure,
        now,
        mapper.createObjectNode(),
        mapper.createObjectNode());
  }

  // ---- static filtering ----------------------------------------------------

  @Test
  void matchingTypeAndStatusIsCaught() {
    CatchDecision decision = CatchPolicy.decide(request(FAILURE_503, 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.error().get("status").intValue()).isEqualTo(503);
    assertThat(decision.errorVariable()).isEqualTo("failure");
  }

  @Test
  void nonMatchingStatusIsNotCaught() {
    CatchDecision decision = CatchPolicy.decide(request(FAILURE_500, 1));

    assertThat(decision.caught()).isFalse();
    assertThat(decision.retry()).isFalse();
  }

  @Test
  void emptyCatchClauseCatchesEverything() throws Exception {
    seedYaml(catchYaml(""));

    CatchDecision decision = CatchPolicy.decide(request("task 'x' blew up", 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.retry()).isFalse();
    assertThat(decision.errorVariable()).isEqualTo("error");
  }

  @Test
  void zeroStatusInTheFilterIsTreatedAsAbsent() throws Exception {
    // The SDK's ErrorFilter.getStatus() is a primitive int, so an omitted status reads as 0.
    // A filter on type alone must therefore still match a 503.
    seedYaml(
        catchYaml(
            """
            errors:
              with:
                type: https://serverlessworkflow.io/spec/1.0.0/errors/communication
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).caught()).isTrue();
    assertThat(CatchPolicy.decide(request(FAILURE_500, 1)).caught()).isTrue();
  }

  // ---- dynamic filtering ---------------------------------------------------

  @Test
  void whenGatesAStaticallyMatchedError() throws Exception {
    seedYaml(catchYaml("when: '$error.status == 429'"));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).caught()).isFalse();
  }

  @Test
  void exceptWhenVetoesAMatch() throws Exception {
    seedYaml(catchYaml("exceptWhen: '$error.status == 503'"));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).caught()).isFalse();
    assertThat(CatchPolicy.decide(request(FAILURE_500, 1)).caught()).isTrue();
  }

  @Test
  void conditionReadsTheErrorUnderItsDeclaredName() throws Exception {
    seedYaml(
        catchYaml(
            """
            as: oops
            when: '$oops.status == 503'
            """));

    CatchDecision decision = CatchPolicy.decide(request(FAILURE_503, 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.errorVariable()).isEqualTo("oops");
  }

  // ---- retry policy resolution --------------------------------------------

  @Test
  void namedPolicyIsResolvedFromUseRetries() {
    // The fixture's catch references `thrice` by name.
    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).retry()).isTrue();
  }

  @Test
  void inlinePolicyBehavesLikeTheSameNamedPolicy() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 2
              backoff:
                exponential: {}
              limit:
                attempt:
                  count: 3
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis()).isEqualTo(2_000L);
    assertThat(CatchPolicy.decide(request(FAILURE_503, 2)).delayMillis()).isEqualTo(4_000L);
  }

  @Test
  void unresolvablePolicyNameFailsLoudly() throws Exception {
    seedYaml(catchYaml("retry: doesNotExist"));

    assertThatThrownBy(() -> CatchPolicy.decide(request(FAILURE_503, 1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doesNotExist");
  }

  @Test
  void perAttemptDurationLimitIsAcceptedAndDoesNotAffectTheVerdict() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 1
              limit:
                attempt:
                  duration:
                    seconds: 5
            """));

    CatchDecision decision = CatchPolicy.decide(request(FAILURE_503, 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.retry()).isTrue();
  }

  @Test
  void perAttemptTimeoutResolvesTheDeclaredDuration() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 1
              limit:
                attempt:
                  duration:
                    seconds: 5
            """));

    Duration duration =
        CatchPolicy.perAttemptTimeout(
            DefinitionLookup.taskByName("guarded").getTryTask().getCatch());

    assertThat(duration).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void perAttemptTimeoutIsNullWhenNotDeclared() {
    Duration duration =
        CatchPolicy.perAttemptTimeout(
            DefinitionLookup.taskByName("guarded").getTryTask().getCatch());

    assertThat(duration).isNull();
  }

  // ---- backoff, jitter, limits --------------------------------------------

  @Test
  void exponentialBackoffDoublesTheDelay() throws Exception {
    // No limit, so the growth is observable past the fixture's three-attempt cap.
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 2
              backoff:
                exponential: {}
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis()).isEqualTo(2_000L);
    assertThat(CatchPolicy.decide(request(FAILURE_503, 2)).delayMillis()).isEqualTo(4_000L);
    assertThat(CatchPolicy.decide(request(FAILURE_503, 3)).delayMillis()).isEqualTo(8_000L);
  }

  @Test
  void linearBackoffScalesByAttempt() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 2
              backoff:
                linear: {}
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis()).isEqualTo(2_000L);
    assertThat(CatchPolicy.decide(request(FAILURE_503, 3)).delayMillis()).isEqualTo(6_000L);
  }

  @Test
  void constantBackoffKeepsTheDelay() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 2
              backoff:
                constant: {}
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 4)).delayMillis()).isEqualTo(2_000L);
  }

  @Test
  void attemptLimitEndsRetrying() {
    // count is the maximum number of body executions, not the number of retries on top of the
    // first one: the fixture's count of 3 allows attempts 1 and 2 to be retried, and stops at 3.
    assertThat(CatchPolicy.decide(request(FAILURE_503, 1)).retry()).isTrue();
    assertThat(CatchPolicy.decide(request(FAILURE_503, 2)).retry()).isTrue();

    CatchDecision exhausted = CatchPolicy.decide(request(FAILURE_503, 3));

    assertThat(exhausted.caught()).isTrue();
    assertThat(exhausted.retry()).isFalse();
  }

  @Test
  void zeroAttemptCountIsTreatedAsAbsent() throws Exception {
    // RetryLimitAttempt.getCount() is a primitive int; an omitted count reads as 0, which must not
    // mean "zero attempts allowed".
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 1
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 99)).retry()).isTrue();
  }

  @Test
  void durationLimitEndsRetrying() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 1
              limit:
                duration:
                  seconds: 5
            """));

    assertThat(CatchPolicy.decide(request(FAILURE_503, 2, 0L, 4_000L)).retry()).isTrue();
    assertThat(CatchPolicy.decide(request(FAILURE_503, 2, 0L, 6_000L)).retry()).isFalse();
  }

  @Test
  void retryConditionsGateTheRetry() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              when: '$error.status == 429'
              delay:
                seconds: 1
            """));

    CatchDecision decision = CatchPolicy.decide(request(FAILURE_503, 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.retry()).isFalse();
  }

  @Test
  void jitterStaysWithinItsRangeAndVaries() throws Exception {
    seedYaml(
        catchYaml(
            """
            retry:
              delay:
                seconds: 2
              jitter:
                from:
                  seconds: 1
                to:
                  seconds: 2
            """));

    long first = CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis();
    boolean varied = false;
    for (int i = 0; i < 50; i++) {
      long delay = CatchPolicy.decide(request(FAILURE_503, 1)).delayMillis();
      assertThat(delay).isBetween(3_000L, 4_000L);
      varied |= delay != first;
    }
    assertThat(varied).as("jitter should not produce a constant delay").isTrue();
  }

  @Test
  void noRetryClauseMeansHandledImmediately() throws Exception {
    seedYaml(catchYaml(""));

    CatchDecision decision = CatchPolicy.decide(request(FAILURE_503, 1));

    assertThat(decision.caught()).isTrue();
    assertThat(decision.retry()).isFalse();
    assertThat(decision.delayMillis()).isZero();
  }

  /**
   * A one-task try wrapped around the supplied catch clause body, at the fixture's task names. The
   * body is given unindented and re-indented here, so a test reads as the clause it is about.
   */
  private static String catchYaml(String catchBody) {
    String indented = catchBody.isBlank() ? " {}\n" : "\n" + catchBody.stripTrailing().indent(8);
    return """
        document:
          dsl: 1.0.0
          namespace: examples
          name: try-order-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - fetchOrder:
                    call: http
                    with:
                      method: get
                      endpoint: http://order-service/run
              catch:\
        """
        + indented;
  }
}
