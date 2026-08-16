package io.dws.orchestrator.workflow.activity;

/**
 * Names the single canonical activity that migrated I/O steps ({@code call: http}, {@code run:
 * shell}, {@code run: script}) are dispatched through as Dapr multi-app activities.
 *
 * <p>Unlike the local {@code *Activity} classes in this package, {@code Run} is <em>not</em> a Java
 * {@link io.dapr.workflows.WorkflowActivity}: the target step's Go worker registers it under this
 * literal name. The interpreter therefore schedules it by this string — not by a class name —
 * targeting the step's Dapr app-id through {@code WorkflowTaskOptions}, and passes the current
 * workflow-data JSON straight through as the activity input (the shape the Go worker expects). No
 * request wrapper is introduced for it: the app-id lives in the options, so the input carries the
 * raw data alone.
 */
public final class StepActivity {

  /** The literal name every migrated step worker registers its single activity under. */
  public static final String NAME = "Run";

  private StepActivity() {}
}
