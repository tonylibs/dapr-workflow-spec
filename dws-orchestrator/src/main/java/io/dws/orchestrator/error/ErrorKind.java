package io.dws.orchestrator.error;

/**
 * The failure classes this phase distinguishes, each with the Open Workflow Specification error
 * type URI it maps to and the status used when no upstream status can be recovered.
 *
 * <p>Three is deliberately the whole list: it is the minimum that makes {@code catch.errors.with}
 * meaningful, since a filter can only match on values something actually produces. The standard
 * error-type catalogue and RFC 7807 Problem Details formatting are Phase 3, and will enrich these
 * same fields rather than replace them.
 */
public enum ErrorKind {
  /** A {@code from}/{@code as} transform or an {@code input}/{@code output} schema violation. */
  VALIDATION("validation", 400, "Validation error"),
  /** A step service could not be reached or answered with a failure status. */
  COMMUNICATION("communication", 502, "Communication error"),
  /** Anything else thrown while interpreting a task. */
  RUNTIME("runtime", 500, "Runtime error");

  private static final String TYPE_PREFIX =
      "https://open-workflow-specification.org/dsl/errors/types/";

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
