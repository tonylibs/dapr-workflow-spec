package io.dws.orchestrator.workflow;

/**
 * How a task scope finished.
 *
 * <p>The distinction only becomes observable once task lists nest: at the top level {@link #EXIT}
 * and {@link #END} both complete the instance, because exiting the outermost scope <em>is</em>
 * completing. Inside a {@code try}/{@code catch.do} list they differ — {@code exit} hands control
 * back to the enclosing task, {@code end} terminates the whole instance.
 */
public enum ScopeEnd {
  /** The list ran to completion with no terminating directive. */
  FELL_THROUGH,
  /** {@code exit}: complete this scope only; an enclosing task continues. */
  EXIT,
  /** {@code end}: complete the whole workflow instance from any depth. */
  END
}
