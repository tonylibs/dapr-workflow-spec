package io.dws.orchestrator.workflow.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.orchestrator.error.WorkflowErrors;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.types.ErrorFilter;
import io.serverlessworkflow.api.types.Retry;
import io.serverlessworkflow.api.types.RetryBackoff;
import io.serverlessworkflow.api.types.RetryLimit;
import io.serverlessworkflow.api.types.RetryLimitAttempt;
import io.serverlessworkflow.api.types.RetryPolicy;
import io.serverlessworkflow.api.types.RetryPolicyJitter;
import io.serverlessworkflow.api.types.TimeoutAfter;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.TryTaskCatch;
import io.serverlessworkflow.api.types.UseRetries;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The whole catch verdict in one place: synthesise the runtime error, match it against the static
 * filter, evaluate the dynamic conditions, resolve the retry policy, enforce the limits, and
 * compute the delay.
 *
 * <p>Consolidated deliberately. The workflow method must stay replay-deterministic, so the two
 * impure ingredients — the jitter draw and the elapsed-time arithmetic — cannot live there. Once
 * one activity is needed for those, splitting the rest across further activities would only add
 * history records per failed attempt without separating anything.
 *
 * <p>Default name for the error variable when {@code catch.as} is absent, per the DSL.
 */
public final class CatchPolicy {

  private static final String DEFAULT_ERROR_VARIABLE = "error";

  /** Applied when a retry policy declares no {@code delay}. */
  private static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);

  /** Verdict shorthand: no further retry applies. */
  private static final long NO_RETRY = -1L;

  private CatchPolicy() {}

  /** Decides whether the failure is handled here, and if so whether to try the body again. */
  public static CatchDecision decide(CatchDecisionRequest request) {
    ObjectMapper mapper = WorkflowSupport.mapper();
    TryTaskCatch clause = catchClauseOf(request.tryTaskName());

    JsonNode error = WorkflowErrors.of(request.failureMessage(), request.failedTaskName(), mapper);
    String variable =
        (clause == null || clause.getAs() == null || clause.getAs().isBlank())
            ? DEFAULT_ERROR_VARIABLE
            : clause.getAs();

    if (clause == null
        || !matchesFilter(clause, error)
        || !conditionsAllow(clause, request, error, variable)) {
      return new CatchDecision(false, false, 0L, error, variable);
    }

    long delay = retryDelay(clause, request, error, variable);
    return new CatchDecision(true, delay != NO_RETRY, Math.max(delay, 0L), error, variable);
  }

  private static TryTaskCatch catchClauseOf(String tryTaskName) {
    TryTask tryTask = DefinitionLookup.taskByName(tryTaskName).getTryTask();
    if (tryTask == null) {
      throw new IllegalStateException("task '" + tryTaskName + "' is not a try task");
    }
    // A try task with no catch clause catches nothing: returning null lets the original failure
    // propagate unchanged, rather than masking it with an error about the definition's shape.
    return tryTask.getCatch();
  }

  // ---- static filtering ----------------------------------------------------

  /**
   * Every field the filter declares must equal the error's. A filter that declares nothing — or no
   * filter at all — matches everything, which is the DSL's {@code catch: {}} behaviour.
   *
   * <p>{@link ErrorFilter#getStatus()} is a primitive {@code int}, so an omitted status is
   * indistinguishable from an explicit zero. There is no HTTP status 0, so 0 means "not specified".
   */
  private static boolean matchesFilter(TryTaskCatch clause, JsonNode error) {
    if (clause.getErrors() == null || clause.getErrors().getWith() == null) {
      return true;
    }
    ErrorFilter filter = clause.getErrors().getWith();
    return matchesText(filter.getType(), error, "type")
        && matchesText(filter.getInstance(), error, "instance")
        && matchesText(filter.getTitle(), error, "title")
        // The SDK names this field `details`; the current spec names the error field `detail`.
        && matchesText(filter.getDetails(), error, "detail")
        && (filter.getStatus() == 0 || filter.getStatus() == error.path("status").intValue());
  }

  private static boolean matchesText(String expected, JsonNode error, String field) {
    if (expected == null || expected.isBlank()) {
      return true;
    }
    return expected.equals(error.path(field).asText());
  }

  // ---- dynamic filtering ---------------------------------------------------

  /** {@code when} must hold (when present) and {@code exceptWhen} must not (when present). */
  private static boolean conditionsAllow(
      TryTaskCatch clause, CatchDecisionRequest request, JsonNode error, String variable) {
    return allows(clause.getWhen(), clause.getExceptWhen(), request, error, variable);
  }

  private static boolean allows(
      String when,
      String exceptWhen,
      CatchDecisionRequest request,
      JsonNode error,
      String variable) {
    if (when != null && !when.isBlank() && !truthy(when, request, error, variable)) {
      return false;
    }
    return exceptWhen == null
        || exceptWhen.isBlank()
        || !truthy(exceptWhen, request, error, variable);
  }

  private static boolean truthy(
      String expression, CatchDecisionRequest request, JsonNode error, String variable) {
    JqEvaluator jq = WorkflowSupport.jq();
    Map<String, JsonNode> variables =
        Map.of("context", orEmpty(request.context()), variable, error);
    return jq.evaluateBoolean(expression, orEmpty(request.data()), variables);
  }

  private static JsonNode orEmpty(JsonNode node) {
    return (node == null || node.isNull()) ? WorkflowSupport.mapper().createObjectNode() : node;
  }

  // ---- retry ---------------------------------------------------------------

  /** The delay before the next attempt, or {@link #NO_RETRY} when no further attempt applies. */
  private static long retryDelay(
      TryTaskCatch clause, CatchDecisionRequest request, JsonNode error, String variable) {
    if (clause.getRetry() == null) {
      return NO_RETRY;
    }
    RetryPolicy policy = resolvePolicy(clause.getRetry());

    if (!allows(policy.getWhen(), policy.getExceptWhen(), request, error, variable)) {
      return NO_RETRY;
    }
    if (limitsExceeded(policy.getLimit(), request)) {
      return NO_RETRY;
    }
    return delayFor(policy, request.attempt());
  }

  /** Inline policy, or one named in the document's {@code use.retries}. */
  private static RetryPolicy resolvePolicy(Retry retry) {
    RetryPolicy inline = retry.getRetryPolicyDefinition();
    if (inline != null) {
      return inline;
    }
    String name = retry.getRetryPolicyReference();
    UseRetries retries =
        WorkflowSupport.definition().getUse() == null
            ? null
            : WorkflowSupport.definition().getUse().getRetries();
    RetryPolicy named =
        (retries == null || retries.getAdditionalProperties() == null)
            ? null
            : retries.getAdditionalProperties().get(name);
    if (named == null) {
      throw new IllegalStateException(
          "retry policy '" + name + "' is not defined in the document's use.retries");
    }
    return named;
  }

  /**
   * The per-attempt duration a {@code try} task's retry policy declares, or {@code null} when none
   * applies. Resolved directly here (not via {@link CatchDecisionActivity}) because a per-attempt
   * timeout must bound an attempt before it runs, not be decided after it already failed — {@link
   * io.dws.orchestrator.workflow.InterpreterWorkflow#dispatchTry} calls this once per attempt,
   * before running the attempt's task list.
   */
  public static Duration perAttemptTimeout(TryTaskCatch clause) {
    if (clause == null || clause.getRetry() == null) {
      return null;
    }
    RetryLimit limit = resolvePolicy(clause.getRetry()).getLimit();
    TimeoutAfter duration =
        limit == null || limit.getAttempt() == null ? null : limit.getAttempt().getDuration();
    return duration == null ? null : WorkflowSupport.durationOf(duration);
  }

  /**
   * {@code limit.attempt.count} caps the number of body executions; {@code limit.duration} caps the
   * wall-clock span since the first failure. A count of 0 means "not specified" — the SDK accessor
   * is a primitive {@code int}, and a zero-attempt retry policy is not a retry policy.
   */
  private static boolean limitsExceeded(RetryLimit limit, CatchDecisionRequest request) {
    if (limit == null) {
      return false;
    }
    RetryLimitAttempt attempt = limit.getAttempt();
    if (attempt != null && attempt.getCount() != 0 && request.attempt() >= attempt.getCount()) {
      return true;
    }
    if (limit.getDuration() != null) {
      long elapsed = request.nowEpochMillis() - request.firstFailureEpochMillis();
      return elapsed > WorkflowSupport.durationOf(limit.getDuration()).toMillis();
    }
    return false;
  }

  /**
   * The delay for a given 1-based attempt.
   *
   * <p>The backoff kinds carry no parameters in the schema — {@code ConstantBackoff}, {@code
   * LinearBackoff} and {@code ExponentialBackOff} wrap types with no properties — so the multiplier
   * convention is this runtime's to define: constant keeps the delay, linear scales it by the
   * attempt number, exponential doubles it per attempt.
   */
  private static long delayFor(RetryPolicy policy, int attempt) {
    Duration base =
        policy.getDelay() == null ? DEFAULT_DELAY : WorkflowSupport.durationOf(policy.getDelay());
    long millis = base.toMillis();

    RetryBackoff backoff = policy.getBackoff();
    if (backoff != null && backoff.getLinearBackoff() != null) {
      millis = millis * attempt;
    } else if (backoff != null && backoff.getExponentialBackOff() != null) {
      millis = millis << (attempt - 1);
    }

    RetryPolicyJitter jitter = policy.getJitter();
    if (jitter != null) {
      long from = WorkflowSupport.durationOf(jitter.getFrom()).toMillis();
      long to = WorkflowSupport.durationOf(jitter.getTo()).toMillis();
      long span = Math.max(to - from, 0L);
      // Drawn here, inside the activity: Dapr records the result, so replay reuses this value.
      millis += from + (span == 0 ? 0 : ThreadLocalRandom.current().nextLong(span));
    }
    return millis;
  }
}
