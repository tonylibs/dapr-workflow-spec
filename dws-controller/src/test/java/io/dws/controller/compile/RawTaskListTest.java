package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.serverlessworkflow.api.types.TaskItem;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests of the raw half of {@link NodeClassifier}'s lockstep walk. End-to-end compilation cannot
 * reach most of these guards: DSL 1.0's {@code do}/{@code try}/{@code catch.do}/{@code
 * fork.branches} are always JSON arrays of one-property objects (schema/workflow.yaml's {@code
 * taskList}), so a real document's typed and raw walks never disagree. These construct that
 * disagreement directly, the way a future SDK bug or upstream drift could.
 */
class RawTaskListTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void readsAnArrayThatMatchesTheTypedWalk() {
    RawTaskList tasks = RawTaskList.in(parent("first", "second"), "do", typed("first", "second"));

    assertThat(tasks.copies()).hasSize(2);
    assertThat(tasks.get(0).body("first").get("set").get("a").asInt()).isEqualTo(1);
    assertThat(tasks.get(1).body("second").get("set").get("a").asInt()).isEqualTo(1);
  }

  @Test
  void treatsAMissingFieldAsAnEmptyList() {
    RawTaskList tasks = RawTaskList.in(JSON.createObjectNode(), "do", List.of());

    assertThat(tasks.copies()).isEmpty();
  }

  @Test
  void treatsANullParentAsAnEmptyList() {
    assertThat(RawTaskList.in(null, "do", List.of()).copies()).isEmpty();
  }

  @Test
  void rejectsARawListShorterThanTheTypedList() {
    assertThatThrownBy(() -> RawTaskList.in(parent("first"), "do", typed("first", "second")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("2 parsed tasks")
        .hasMessageContaining("1 in the submitted document");
  }

  @Test
  void rejectsARawListLongerThanTheTypedList() {
    assertThatThrownBy(() -> RawTaskList.in(parent("first", "second"), "do", typed("first")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("1 parsed tasks")
        .hasMessageContaining("2 in the submitted document");
  }

  @Test
  void rejectsAMissingFieldWhenTheTypedListIsNotEmpty() {
    assertThatThrownBy(() -> RawTaskList.in(JSON.createObjectNode(), "do", typed("first")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("1 parsed tasks")
        .hasMessageContaining("0 in the submitted document");
  }

  @Test
  void rejectsAFieldThatIsNotAnArray() {
    ObjectNode parent = JSON.createObjectNode();
    parent.put("do", "not-a-list");

    assertThatThrownBy(() -> RawTaskList.in(parent, "do", typed("first")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("could not be matched to its source text");
  }

  /** A node's rendered {@code tasks} must not alias the document the rest of the walk reads. */
  @Test
  void copiesAreDetachedFromTheSourceDocument() {
    ObjectNode parent = parent("first");
    List<JsonNode> copies = RawTaskList.in(parent, "do", typed("first")).copies();

    ((ObjectNode) parent.get("do").get(0).get("first").get("set")).put("a", 99);

    assertThat(copies.get(0).get("first").get("set").get("a").asInt()).isEqualTo(1);
  }

  @Test
  void rejectsAnEntryThatIsNotAnObject() {
    assertThatThrownBy(() -> new RawTaskItem(JSON.getNodeFactory().textNode("nope")).body("first"))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("must have exactly one property")
        .hasMessageContaining("a non-object");
  }

  @Test
  void rejectsAnEntryWithMoreThanOneProperty() {
    ObjectNode entry = JSON.createObjectNode();
    entry.putObject("first");
    entry.putObject("second");

    assertThatThrownBy(() -> new RawTaskItem(entry).body("first"))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("must have exactly one property")
        .hasMessageContaining("found 2");
  }

  @Test
  void rejectsAnEntryWhosePropertyIsNotTheParsedTaskName() {
    assertThatThrownBy(() -> new RawTaskItem(entry("other")).body("first"))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("task 'first' could not be matched to its source text");
  }

  private static ObjectNode parent(String... names) {
    ObjectNode parent = JSON.createObjectNode();
    ArrayNode array = parent.putArray("do");
    for (String name : names) {
      array.add(entry(name));
    }
    return parent;
  }

  private static ObjectNode entry(String name) {
    ObjectNode entry = JSON.createObjectNode();
    entry.putObject(name).putObject("set").put("a", 1);
    return entry;
  }

  private static List<TaskItem> typed(String... names) {
    return Arrays.stream(names).map(name -> new TaskItem(name, null)).toList();
  }
}
