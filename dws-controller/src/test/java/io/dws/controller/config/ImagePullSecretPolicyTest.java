package io.dws.controller.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dws.controller.model.TaskKind;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImagePullSecretPolicyTest {

  /** The environment variables the chart's controller Deployment always sets. */
  private static final Map<String, String> REQUIRED_IMAGES =
      Map.of(
          "DWS_IMAGES_CALL_HTTP", "call-http:1",
          "DWS_IMAGES_CALL_OPENAPI", "call-openapi:1",
          "DWS_IMAGES_CALL_GRPC", "call-grpc:1",
          "DWS_IMAGES_CALL_ASYNCAPI", "call-asyncapi:1",
          "DWS_IMAGES_CALL_A2A", "call-a2a:1",
          "DWS_IMAGES_RUN_SHELL", "run-shell:1",
          "DWS_IMAGES_RUN_SCRIPT_JS", "run-script-js:1",
          "DWS_IMAGES_RUN_SCRIPT_PYTHON", "run-script-python:1",
          "DWS_IMAGES_ORCHESTRATOR", "orchestrator:1");

  @Test
  @DisplayName("no configured override leaves every kind on the default account")
  void absentOverridesInheritTheDefault() {
    ImagePullSecretPolicy policy = ImagePullSecretPolicy.from(values(Map.of()));

    for (TaskKind kind : TaskKind.values()) {
      assertThat(policy.overrideFor(kind)).isEmpty();
    }
    assertThat(ImagePullSecretPolicy.none().overrides()).isEmpty();
  }

  @Test
  @DisplayName("a JSON array becomes the ordered replacement list and [] an empty override")
  void arraysAndEmptyArraysAreOverrides() {
    ImagePullSecretPolicy policy =
        ImagePullSecretPolicy.from(
            values(
                Map.of(
                    TaskKind.CALL_HTTP, "[\"http-cred\",\"mirror\"]",
                    TaskKind.RUN_SHELL, "[]")));

    assertThat(policy.overrideFor(TaskKind.CALL_HTTP)).contains(List.of("http-cred", "mirror"));
    assertThat(policy.overrideFor(TaskKind.RUN_SHELL)).contains(List.of());
    assertThat(policy.overrideFor(TaskKind.CALL_OPENAPI)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-json", "[\"\"]", "[\" \"]", "[3]", "[null]", "\"cred\"", "{}"})
  @DisplayName("a malformed override fails fast naming the property and environment variable")
  void malformedOverrideFailsFast(String value) {
    assertThatThrownBy(() -> ImagePullSecretPolicy.from(values(Map.of(TaskKind.CALL_GRPC, value))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dws.images.pull-secrets.call-grpc")
        .hasMessageContaining("DWS_IMAGES_PULL_SECRETS_CALL_GRPC");
  }

  @Test
  @DisplayName("the policy cannot be changed through the map it was built from or returns")
  void policyIsImmutable() {
    Map<TaskKind, List<String>> source = new EnumMap<>(TaskKind.class);
    source.put(TaskKind.CALL_HTTP, new java.util.ArrayList<>(List.of("a")));
    ImagePullSecretPolicy policy = new ImagePullSecretPolicy(source);

    source.get(TaskKind.CALL_HTTP).add("b");
    source.put(TaskKind.RUN_SHELL, List.of());

    assertThat(policy.overrideFor(TaskKind.CALL_HTTP)).contains(List.of("a"));
    assertThat(policy.overrideFor(TaskKind.RUN_SHELL)).isEmpty();
    assertThatThrownBy(() -> policy.overrides().put(TaskKind.RUN_SHELL, List.of()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @EnumSource(TaskKind.class)
  @DisplayName("each chart environment variable binds the override for its own task kind")
  void environmentVariableBindsItsKind(TaskKind kind) {
    Map<String, String> env = new HashMap<>(REQUIRED_IMAGES);
    env.put(ImagePullSecretPolicy.environmentName(kind), "[\"" + kind.name() + "\"]");

    ImagePullSecretPolicy policy = ImagePullSecretPolicy.from(config(env).images().pullSecrets());

    assertThat(policy.overrides()).containsOnlyKeys(kind);
    assertThat(policy.overrideFor(kind)).contains(List.of(kind.name()));
  }

  @Test
  @DisplayName("the override account defaults to dws-task-override and binds its env var")
  void overrideAccountBindsItsEnvironmentVariable() {
    assertThat(config(REQUIRED_IMAGES).task().overrideServiceAccount())
        .isEqualTo("dws-task-override");

    Map<String, String> env = new HashMap<>(REQUIRED_IMAGES);
    env.put("DWS_TASK_OVERRIDE_SERVICE_ACCOUNT", "custom-override");
    assertThat(config(env).task().overrideServiceAccount()).isEqualTo("custom-override");
  }

  private static DwsConfig config(Map<String, String> env) {
    SmallRyeConfig config =
        new SmallRyeConfigBuilder()
            .withMapping(DwsConfig.class)
            .withSources(new EnvConfigSource(env, EnvConfigSource.ORDINAL))
            .build();
    return config.getConfigMapping(DwsConfig.class);
  }

  private static DwsConfig.PullSecrets values(Map<TaskKind, String> raw) {
    return new DwsConfig.PullSecrets() {
      @Override
      public Optional<String> callHttp() {
        return Optional.ofNullable(raw.get(TaskKind.CALL_HTTP));
      }

      @Override
      public Optional<String> callOpenapi() {
        return Optional.ofNullable(raw.get(TaskKind.CALL_OPENAPI));
      }

      @Override
      public Optional<String> callGrpc() {
        return Optional.ofNullable(raw.get(TaskKind.CALL_GRPC));
      }

      @Override
      public Optional<String> callAsyncapi() {
        return Optional.ofNullable(raw.get(TaskKind.CALL_ASYNCAPI));
      }

      @Override
      public Optional<String> callA2a() {
        return Optional.ofNullable(raw.get(TaskKind.CALL_A2A));
      }

      @Override
      public Optional<String> runShell() {
        return Optional.ofNullable(raw.get(TaskKind.RUN_SHELL));
      }

      @Override
      public Optional<String> runScriptJs() {
        return Optional.ofNullable(raw.get(TaskKind.RUN_SCRIPT_JS));
      }

      @Override
      public Optional<String> runScriptPython() {
        return Optional.ofNullable(raw.get(TaskKind.RUN_SCRIPT_PYTHON));
      }
    };
  }
}
