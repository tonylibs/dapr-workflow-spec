package io.dws.orchestrator.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the minimal runtime error object {@code {type, status, instance, title, detail}} that a
 * {@code catch} clause filters against and that a recovery block reads as a jq variable.
 *
 * <p>Classification reads the failure <em>message</em> rather than the exception type. That is not
 * a shortcut: a failure raised inside a Dapr activity reaches the workflow method as an opaque
 * activity failure whose message is the only surviving detail. {@link StepInvocationException} and
 * {@code DataFlowException} both write a stable marker into their message precisely so this
 * classification is possible.
 *
 * <p>RFC 7807 Problem Details formatting and the standard Open Workflow Specification error-type
 * catalogue are out of scope here (Phase 3); these five fields are the DSL's own, so that phase
 * enriches them rather than replacing the concept.
 */
public final class WorkflowErrors {

  private static final Pattern STEP_STATUS =
      Pattern.compile("^step '[^']+' failed with status (\\d{3})");

  /** The {@code task '<name>' …} prefix the interpreter and the data-flow fault both use. */
  private static final Pattern TASK_NAME = Pattern.compile("^task '([^']+)'");

  private static final String STEP_MARKER = "step '";
  private static final String DATA_FLOW_MARKER = "data flow failed:";

  /**
   * Markers a migrated step's activity worker folds into its failure message (full form {@code step
   * '<task>' upstream failure: <detail>} / {@code step '<task>' config failure: <detail>}). Only
   * the message crosses the activity boundary, so this is the sole surviving signal of which kind
   * of fault occurred. {@code upstream} is the retryable transport/upstream fault — the
   * activity-path equivalent of the HTTP path's {@code 502}; {@code config} is a non-retryable
   * configuration or shaping fault and classifies as a runtime error.
   */
  private static final String UPSTREAM_MARKER = "upstream failure:";

  private static final String CONFIG_MARKER = "config failure:";

  /**
   * Prefix {@link RaisedErrorException} folds its resolved error object's JSON behind. Matched as a
   * <em>prefix</em>, not a substring, so an error whose {@code detail} happens to quote another
   * failure's text is still recognised as raised rather than reclassified from its own payload.
   */
  static final String RAISE_MARKER = "raised error: ";

  private WorkflowErrors() {}

  /** Classifies a failure from its message. Anything unrecognised is a runtime error. */
  public static ErrorKind classify(String failureMessage) {
    String message = failureMessage == null ? "" : failureMessage;
    if (message.contains(DATA_FLOW_MARKER)) {
      return ErrorKind.VALIDATION;
    }
    // A config-failure activity message also opens with `step '…'`, so it must be caught before the
    // STEP_MARKER check below, which would otherwise classify it as a communication error.
    if (message.contains(CONFIG_MARKER)) {
      return ErrorKind.RUNTIME;
    }
    if (message.startsWith(STEP_MARKER) || message.contains(UPSTREAM_MARKER)) {
      // Both the HTTP `step '<name>' failed with status NNN` shape and the activity-path
      // `step '<name>' upstream failure: …` shape are communication faults; statusOf recovers the
      // NNN for the former and falls back to the 502 default for the latter.
      return ErrorKind.COMMUNICATION;
    }
    return ErrorKind.RUNTIME;
  }

  /**
   * The error's {@code status}: the upstream HTTP status when the failure carries a recoverable
   * one, otherwise the kind's default.
   */
  public static int statusOf(String failureMessage, ErrorKind kind) {
    if (kind == ErrorKind.COMMUNICATION && failureMessage != null) {
      Matcher matcher = STEP_STATUS.matcher(failureMessage);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
    }
    return kind.defaultStatus();
  }

  /**
   * The task the error should be attributed to. A failure that names its own task (the {@code task
   * '<name>' …} shape) wins; otherwise the caller's fallback is used — for a step invocation that
   * is the enclosing task, because the message carries the kebab-cased app-id rather than the task
   * name, and the two are not the same string.
   */
  public static String failingTaskName(String failureMessage, String fallback) {
    if (failureMessage == null) {
      return fallback;
    }
    Matcher matcher = TASK_NAME.matcher(failureMessage);
    return matcher.find() ? matcher.group(1) : fallback;
  }

  /**
   * Assembles the error object. {@code instance} is a JSON-Pointer-shaped reference to the failing
   * task rather than a full path into the definition — enough to identify it, and stable regardless
   * of how deeply the task is nested.
   */
  public static ObjectNode build(
      ErrorKind kind, int status, String taskName, String detail, ObjectMapper mapper) {
    ObjectNode error = mapper.createObjectNode();
    error.put("type", kind.typeUri());
    error.put("status", status);
    error.put("instance", "/" + (taskName == null ? "" : taskName));
    error.put("title", kind.title());
    error.put("detail", detail == null ? "" : detail);
    return error;
  }

  /**
   * Convenience: classify a failure message and build the whole error object from it — unless the
   * failure is a {@link RaisedErrorException}, whose five fields the author already chose and which
   * are therefore returned unchanged rather than re-derived.
   */
  public static ObjectNode of(String failureMessage, String fallbackTaskName, ObjectMapper mapper) {
    if (failureMessage != null && failureMessage.startsWith(RAISE_MARKER)) {
      return raised(failureMessage, mapper);
    }
    ErrorKind kind = classify(failureMessage);
    return build(
        kind,
        statusOf(failureMessage, kind),
        failingTaskName(failureMessage, fallbackTaskName),
        failureMessage,
        mapper);
  }

  /** Reads a raised error's own object back out of {@link RaisedErrorException}'s message. */
  private static ObjectNode raised(String failureMessage, ObjectMapper mapper) {
    String json = failureMessage.substring(RAISE_MARKER.length());
    try {
      JsonNode parsed = mapper.readTree(json);
      if (!parsed.isObject()) {
        throw new IllegalStateException("raised error is not a JSON object: " + json);
      }
      return (ObjectNode) parsed;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("raised error could not be parsed: " + json, e);
    }
  }
}
