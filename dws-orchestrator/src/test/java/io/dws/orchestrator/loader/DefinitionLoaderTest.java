package io.dws.orchestrator.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.orchestrator.model.TaskDef;
import io.dws.orchestrator.model.TaskType;
import io.dws.orchestrator.model.WorkflowDef;
import io.dws.orchestrator.registry.DefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionLoaderTest {

  private final DefinitionLoader loader = new DefinitionLoader(new ObjectMapper());

  private static final String ORDER_WORKFLOW = """
      {
        "document": { "dsl": "1.0.0", "namespace": "examples", "name": "order-workflow", "version": "1.0.0" },
        "start": "checkInventory",
        "tasks": {
          "checkInventory":  { "type": "call", "service": "inventory-service", "then": "decide" },
          "decide": {
            "type": "switch",
            "cases": [ { "when": "${ .inStock }", "then": "chargePayment" } ],
            "default": "notifyOutOfStock"
          },
          "chargePayment":    { "type": "call", "service": "payment-service", "then": "done" },
          "notifyOutOfStock": { "type": "call", "service": "notification-service", "then": "failed" },
          "done":   { "type": "end" },
          "failed": { "type": "end" }
        }
      }
      """;

  @Test
  void parsesDocumentMetadataAndStart() throws Exception {
    WorkflowDef def = loader.parse(ORDER_WORKFLOW);

    assertThat(def.name()).isEqualTo("order-workflow");
    assertThat(def.version()).isEqualTo("1.0.0");
    assertThat(def.start()).isEqualTo("checkInventory");
    assertThat(def.tasks()).hasSize(6);
  }

  @Test
  void parsesTaskTypesAndFlowDirectives() throws Exception {
    WorkflowDef def = loader.parse(ORDER_WORKFLOW);

    TaskDef checkInventory = def.task("checkInventory");
    assertThat(checkInventory.type()).isEqualTo(TaskType.CALL);
    assertThat(checkInventory.service()).isEqualTo("inventory-service");
    assertThat(checkInventory.pathOrDefault()).isEqualTo("run");
    assertThat(checkInventory.flowTarget()).isEqualTo("decide");

    TaskDef decide = def.task("decide");
    assertThat(decide.type()).isEqualTo(TaskType.SWITCH);
    assertThat(decide.cases()).hasSize(1);
    assertThat(decide.cases().get(0).when()).isEqualTo("${ .inStock }");
    assertThat(decide.cases().get(0).then()).isEqualTo("chargePayment");
    assertThat(decide.defaultCase()).isEqualTo("notifyOutOfStock");

    assertThat(def.task("done").type()).isEqualTo(TaskType.END);
  }

  @Test
  void rejectsDefinitionWithoutStart() {
    String json = """
        { "document": { "name": "x", "version": "1.0.0" }, "tasks": { "a": { "type": "end" } } }
        """;

    assertThatThrownBy(() -> loader.parse(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("start");
  }

  @Test
  void rejectsStartThatIsNotADefinedTask() {
    String json = """
        { "document": { "name": "x", "version": "1.0.0" }, "start": "ghost",
          "tasks": { "a": { "type": "end" } } }
        """;

    assertThatThrownBy(() -> loader.parse(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void rejectsDefinitionWithoutTasks() {
    String json = """
        { "document": { "name": "x", "version": "1.0.0" }, "start": "a" }
        """;

    assertThatThrownBy(() -> loader.parse(json))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tasks");
  }

  @Test
  void loadsDirectoryIntoRegistryAndResolvesLatest(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("order-workflow.json"), ORDER_WORKFLOW);

    DefinitionRegistry registry = loader.loadDirectory(dir);

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.find("order-workflow", "1.0.0")).isPresent();
    assertThat(registry.findLatest("order-workflow"))
        .get()
        .extracting(WorkflowDef::start)
        .isEqualTo("checkInventory");
  }

  @Test
  void missingDirectoryYieldsEmptyRegistry(@TempDir Path dir) {
    DefinitionRegistry registry = loader.loadDirectory(dir.resolve("does-not-exist"));

    assertThat(registry.size()).isZero();
  }
}
