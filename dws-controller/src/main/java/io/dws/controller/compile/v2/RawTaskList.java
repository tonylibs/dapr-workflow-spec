package io.dws.controller.compile.v2;

import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.compile.CompilationException;
import io.serverlessworkflow.api.types.TaskItem;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import one.util.streamex.StreamEx;

/**
 * One scope's task list as it appears in the submitted document — the raw half of {@link
 * NodeClassifier}'s lockstep walk, already checked against the typed half it accompanies.
 *
 * <p>Reading and aligning are the same operation ({@link #in}), so a scope cannot be walked raw
 * without its lengths having been compared: a disagreement between the parsed definition and the
 * submitted document is rejected as a {@link CompilationException} rather than silently papered
 * over.
 */
record RawTaskList(List<RawTaskItem> items) {

  RawTaskList {
    items = List.copyOf(items);
  }

  /**
   * Reads the array under {@code field} of {@code parent} and checks it carries the same number of
   * elements as the typed task list it accompanies.
   *
   * @param parent the enclosing raw object, or {@code null} when the typed model reached a scope
   *     the submitted document does not contain
   * @param field the array-valued property to read, e.g. {@code do}, {@code try}, {@code branches}
   * @param typed the typed task list walked in lockstep with this one, possibly {@code null}
   * @throws CompilationException if the two lists differ in length
   */
  static RawTaskList in(JsonNode parent, String field, List<TaskItem> typed) {
    List<TaskItem> typedItems = Optional.ofNullable(typed).orElseGet(List::of);
    List<RawTaskItem> rawItems =
        Optional.ofNullable(parent)
            .map(node -> node.get(field))
            .filter(JsonNode::isArray)
            .map(RawTaskList::elements)
            .orElseGet(Collections::emptyList);
    if (rawItems.size() != typedItems.size()) {
      throw new CompilationException(
          "a task list could not be matched to its source text ("
              + typedItems.size()
              + " parsed tasks, "
              + rawItems.size()
              + " in the submitted document)");
    }
    return new RawTaskList(rawItems);
  }

  /** The entry at {@code index}, which the alignment check guarantees the typed walk also has. */
  RawTaskItem get(int index) {
    return items.get(index);
  }

  /** Detached copies of every entry, in source order, for a flow node's rendered {@code tasks}. */
  List<JsonNode> copies() {
    return items.stream().map(RawTaskItem::copy).toList();
  }

  private static List<RawTaskItem> elements(JsonNode array) {
    return StreamEx.of(array.elements()).map(RawTaskItem::new).toList();
  }
}
