package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One task-list entry as it appears in the submitted document, i.e. the raw half of {@link
 * NodeClassifier}'s lockstep walk for a single {@link io.serverlessworkflow.api.types.TaskItem}.
 *
 * <p>Nodes render their {@code tasks}/{@code task} from this raw JSON rather than from the typed
 * model, which drops unknown fields and reorders keys.
 */
record RawTaskItem(JsonNode json) {

  /**
   * This entry's single task body, i.e. the value under {@code name}.
   *
   * <p>Rejects an entry that is not a one-property object (schema/workflow.yaml's {@code taskList}:
   * {@code minProperties: 1} / {@code maxProperties: 1}), or whose single property does not match
   * the name the typed model parsed — either means the parsed definition and the submitted document
   * disagree.
   */
  JsonNode body(String name) {
    if (json == null || !json.isObject() || json.size() != 1) {
      throw new CompilationException(
          List.of(
              "task item '"
                  + name
                  + "' must have exactly one property (schema/workflow.yaml's taskList: "
                  + "minProperties: 1 / maxProperties: 1); found "
                  + (json == null || !json.isObject() ? "a non-object" : json.size())
                  + " in the submitted document"));
    }
    JsonNode body = json.get(name);
    if (body == null || !body.isObject()) {
      throw new CompilationException(
          List.of(
              "task '"
                  + name
                  + "' could not be matched to its source text; the parsed definition and the "
                  + "submitted document disagree"));
    }
    return body;
  }

  /** A detached copy of this entry, safe to embed in a rendered single-node definition. */
  JsonNode copy() {
    return json.deepCopy();
  }
}
