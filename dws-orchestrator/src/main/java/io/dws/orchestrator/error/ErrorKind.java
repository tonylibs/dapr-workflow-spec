package io.dws.orchestrator.error;

/**
 * The failure classes this phase distinguishes, each with the Open Workflow Specification error
 * type URI it maps to and the status used when no upstream status can be recovered.
 *
 * <p>{@code VALIDATION}, {@code COMMUNICATION}, {@code AUTHORIZATION}, {@code EXPRESSION}, and
 * {@code TIMEOUT} are the standard catalogue's own kinds. {@code RUNTIME} is this runtime's own
 * addition — a catch-all for a failure the standard catalogue does not name — published under the
 * same URI namespace rather than a separate one, so a {@code catch.errors.with.type} filter never
 * has to know which of two domains a given kind lives on. Not every kind is reachable from {@link
 * WorkflowErrors#classify}: {@code AUTHORIZATION} has no producer until authentication exists, and
 * an expression/transform failure classifies as {@code VALIDATION} rather than {@code EXPRESSION}
 * (see {@link WorkflowErrors} for why). Both are still declared here so a {@code
 * catch.errors.with.type} filter — or a future {@code raise} task's author-chosen error — can
 * already reference them.
 */
public enum ErrorKind {
  /** A {@code from}/{@code as} transform or an {@code input}/{@code output} schema violation. */
  VALIDATION("validation", 400, "Validation error"),
  /** A step service could not be reached or answered with a failure status. */
  COMMUNICATION("communication", 502, "Communication error"),
  /** An authorization failure. Not yet produced by this runtime; authentication is Phase 4. */
  AUTHORIZATION("authorization", 403, "Authorization error"),
  /**
   * A runtime-expression evaluation failure. Not yet produced by {@link WorkflowErrors#classify}.
   */
  EXPRESSION("expression", 400, "Expression error"),
  /** A task, workflow, or retry-attempt deadline elapsing before the guarded work completed. */
  TIMEOUT("timeout", 408, "Timeout error"),
  /** Anything else thrown while interpreting a task, not named by the standard catalogue. */
  RUNTIME("runtime", 500, "Runtime error");

  private static final String TYPE_PREFIX = "https://serverlessworkflow.io/spec/1.0.0/errors/";

  private final String slug;
  private final int defaultStatus;
  private final String title;

  ErrorKind(String slug, int defaultStatus, String title) {
    this.slug = slug;
    this.defaultStatus = defaultStatus;
    this.title = title;
  }

  /** The error {@code type} URI a {@code catch.errors.with.type} filter is compared against. */
  public String typeUri() {
    return TYPE_PREFIX + slug;
  }

  public int defaultStatus() {
    return defaultStatus;
  }

  public String title() {
    return title;
  }
}
