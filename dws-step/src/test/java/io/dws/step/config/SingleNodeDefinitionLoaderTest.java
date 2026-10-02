package io.dws.step.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingleNodeDefinitionLoaderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @TempDir Path tempDir;

  @Test
  void rejectsValidFlowShape() throws IOException {
    Path file = write("flow.json", flowDefinition());

    assertThatThrownBy(() -> load(file))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("kind");
  }

  @Test
  void acceptsCallStepOnlyWithFunctionAppId() throws IOException {
    SingleNodeDefinition definition = load(write("call.json", callStepDefinition()));

    assertThat(definition.taskKind()).isEqualTo("call");
    assertThat(definition.functionAppId()).isEqualTo("reserve-items-fn");
    assertThatThrownBy(
            () -> load(write("call-no-app-id.json", callStepDefinitionWithoutFunctionAppId())))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("functionAppId");
  }

  @Test
  void acceptsSetStepWithoutFunctionAppId() throws IOException {
    SingleNodeDefinition definition = load(write("set.json", setStepDefinition()));

    assertThat(definition.taskKind()).isEqualTo("set");
    assertThat(definition.functionAppId()).isNull();
  }

  @Test
  void resolvesSwitchTaskKindWhenNonKindFieldComesFirst() throws IOException {
    SingleNodeDefinition definition = load(write("switch.json", switchStepDefinition()));

    assertThat(definition.taskKind()).isEqualTo("switch");
    assertThat(definition.functionAppId()).isNull();
  }

  @Test
  void rejectsMalformedJson() throws IOException {
    assertThatThrownBy(() -> load(write("malformed.json", "{not json")))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("failed to load");
  }

  @Test
  void rejectsMissingFile() {
    assertThatThrownBy(() -> load(tempDir.resolve("missing.json")))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("failed to load");
  }

  @Test
  void rejectsWaitNamingAdr0006() throws IOException {
    assertThatThrownBy(() -> load(write("wait.json", stepWith("{\"wait\":{\"seconds\":5}}"))))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("ADR 0006")
        .hasMessageContaining("wait");
  }

  @Test
  void rejectsListenNamingAdr0006() throws IOException {
    assertThatThrownBy(() -> load(write("listen.json", stepWith("{\"listen\":{\"to\":{}}}"))))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("ADR 0006")
        .hasMessageContaining("listen");
  }

  @Test
  void rejectsWaitCombinedWithAnotherKind() throws IOException {
    assertThatThrownBy(
            () ->
                load(write("mixed.json", stepWith("{\"set\":{\"a\":1},\"wait\":{\"seconds\":1}}"))))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("ADR 0006");
  }

  @Test
  void rejectsUnknownKindAtStartup() throws IOException {
    assertThatThrownBy(() -> load(write("unknown.json", stepWith("{\"frobnicate\":{}}"))))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("supported task kind");
  }

  @Test
  void rejectsMoreThanOneKindAtStartup() throws IOException {
    assertThatThrownBy(() -> load(write("two.json", stepWith("{\"set\":{\"a\":1},\"emit\":{}}"))))
        .isInstanceOf(DefinitionLoadException.class)
        .hasMessageContaining("exactly one");
  }

  @Test
  void acceptsEveryStillSupportedKind() throws IOException {
    for (String kind : new String[] {"set", "emit", "raise"}) {
      SingleNodeDefinition definition =
          load(write(kind + ".json", stepWith("{\"" + kind + "\":{}}")));
      assertThat(definition.taskKind()).isEqualTo(kind);
    }
  }

  private String stepWith(String task) {
    return "{\"workflow\":\"order\",\"version\":\"order@v1\",\"nodeId\":\"n-1\","
        + "\"kind\":\"step\",\"task\":"
        + task
        + "}";
  }

  private SingleNodeDefinition load(Path file) {
    return new SingleNodeDefinitionLoader(mapper, file.toString()).load();
  }

  private Path write(String name, String definition) throws IOException {
    return Files.writeString(tempDir.resolve(name), definition);
  }

  private String flowDefinition() {
    return """
        {"workflow":"order","version":"order@v1","nodeId":"order-main","kind":"flow",
         "scope":"main","tasks":[],"children":{}}
        """;
  }

  private String callStepDefinition() {
    return """
        {"workflow":"order","version":"order@v1","nodeId":"reserve-items","kind":"step",
         "task":{"call":"http"},
         "functionAppId":"reserve-items-fn"}
        """;
  }

  private String setStepDefinition() {
    return """
        {"workflow":"order","version":"order@v1","nodeId":"validate-order","kind":"step",
         "task":{"set":{"valid":true}}}
        """;
  }

  private String callStepDefinitionWithoutFunctionAppId() {
    return """
        {"workflow":"order","version":"order@v1","nodeId":"reserve-items","kind":"step",
         "task":{"call":"http"}}
        """;
  }

  private String switchStepDefinition() {
    return """
        {"workflow":"order","version":"order@v1","nodeId":"route-order","kind":"step",
         "task":{"cases":[{"when":"${ .priority }","then":"priority-lane"}],
         "default":"standard-lane","switch":"jsonpath"}}
        """;
  }
}
