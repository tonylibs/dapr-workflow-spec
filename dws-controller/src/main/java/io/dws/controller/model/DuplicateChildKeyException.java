package io.dws.controller.model;

/**
 * Two of a {@link FlowNode}'s children resolve to the same {@link CompiledNode#key()}.
 *
 * <p>A model-level invariant violation, not a user-facing compile error: the compile pass catches
 * this and re-reports it as a {@code CompilationException}, so the message here is written to be
 * shown verbatim to whoever submitted the definition.
 */
public class DuplicateChildKeyException extends RuntimeException {

  public DuplicateChildKeyException(String previousAppId, String appId, String key) {
    super(
        "children '"
            + previousAppId
            + "' and '"
            + appId
            + "' both resolve to the key '"
            + key
            + "'; a runtime dispatches a flow's children through this key, so it must be unique");
  }
}
