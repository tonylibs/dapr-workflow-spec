package io.dws.controller.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.controller.model.TaskKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Per-step-image pull-secret overrides, validated once from {@code dws.images.pull-secrets.*}. A
 * {@link TaskKind} with an override runs as the override ServiceAccount and carries exactly the
 * listed Secrets (none for {@code []}); a kind without one keeps the default account and inherits
 * its pull secrets.
 *
 * @param overrides replacement Secret names per kind; absent kinds have no override
 */
public record ImagePullSecretPolicy(Map<TaskKind, List<String>> overrides) {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ImagePullSecretPolicy NONE = new ImagePullSecretPolicy(Map.of());

  public ImagePullSecretPolicy {
    Map<TaskKind, List<String>> copy = new EnumMap<>(TaskKind.class);
    overrides.forEach((kind, names) -> copy.put(kind, List.copyOf(names)));
    overrides = Map.copyOf(copy);
  }

  /** No overrides: every step keeps the default account. */
  public static ImagePullSecretPolicy none() {
    return NONE;
  }

  /**
   * Parses and validates every configured override.
   *
   * @throws IllegalArgumentException naming the property and environment variable when a value is
   *     not a JSON array of nonblank strings
   */
  public static ImagePullSecretPolicy from(DwsConfig.PullSecrets config) {
    Map<TaskKind, List<String>> overrides = new EnumMap<>(TaskKind.class);
    for (TaskKind kind : TaskKind.values()) {
      raw(config, kind).ifPresent(value -> overrides.put(kind, parse(kind, value)));
    }
    return new ImagePullSecretPolicy(overrides);
  }

  /** The replacement Secret names for {@code kind}, or empty when it inherits the default. */
  public Optional<List<String>> overrideFor(TaskKind kind) {
    return Optional.ofNullable(overrides.get(kind));
  }

  /**
   * Config property holding the override for {@code kind}, e.g. {@code ...pull-secrets.call-http}.
   */
  public static String propertyName(TaskKind kind) {
    return "dws.images.pull-secrets." + kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  /** Environment variable the chart sets for {@code kind}, e.g. {@code ..._CALL_HTTP}. */
  public static String environmentName(TaskKind kind) {
    return "DWS_IMAGES_PULL_SECRETS_" + kind.name();
  }

  private static Optional<String> raw(DwsConfig.PullSecrets config, TaskKind kind) {
    return switch (kind) {
      case CALL_HTTP -> config.callHttp();
      case CALL_OPENAPI -> config.callOpenapi();
      case CALL_GRPC -> config.callGrpc();
      case CALL_ASYNCAPI -> config.callAsyncapi();
      case CALL_A2A -> config.callA2a();
      case RUN_SHELL -> config.runShell();
      case RUN_SCRIPT_JS -> config.runScriptJs();
      case RUN_SCRIPT_PYTHON -> config.runScriptPython();
    };
  }

  private static List<String> parse(TaskKind kind, String value) {
    JsonNode array;
    try {
      array = JSON.readTree(value);
    } catch (JsonProcessingException e) {
      throw invalid(kind, "is not valid JSON");
    }
    if (array == null || !array.isArray()) {
      throw invalid(kind, "must be a JSON array of Secret names");
    }
    List<String> names = new ArrayList<>(array.size());
    for (JsonNode entry : array) {
      if (!entry.isTextual() || entry.asText().isBlank()) {
        throw invalid(kind, "must contain only nonblank Secret names");
      }
      names.add(entry.asText());
    }
    return names;
  }

  private static IllegalArgumentException invalid(TaskKind kind, String reason) {
    return new IllegalArgumentException(
        "invalid image pull-secret override "
            + propertyName(kind)
            + " ("
            + environmentName(kind)
            + "): value "
            + reason);
  }
}
