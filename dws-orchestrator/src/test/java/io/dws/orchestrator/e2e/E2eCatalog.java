package io.dws.orchestrator.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the shared e2e definition catalog from {@code /e2e-catalog} on the test classpath
 * (mounted from the repo's top-level {@code e2e/catalog} directory via the pom's
 * {@code testResources}). Each case pairs a workflow definition with the expectations both
 * e2e tiers assert: deployability/visibility ({@code deploy}) and runtime behaviour
 * ({@code scenarios}).
 *
 * <p>Manifests are deserialized strictly — an unknown key fails the load, keeping the
 * manifest vocabulary honest.
 */
public final class E2eCatalog {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private E2eCatalog() {
  }

  /** Deploy-time expectations: compiled step services and topic bindings, in walk order. */
  public record Deploy(boolean valid, List<String> expectSteps, List<String> expectBindings) {
    public Deploy {
      expectSteps = orEmpty(expectSteps);
      expectBindings = orEmpty(expectBindings);
    }
  }

  /** One scripted activity result: the app-id the interpreter must target and the data it returns. */
  public record ScriptedCall(String appId, JsonNode result) {
  }

  /** One scripted external event, keyed by the listen task's name. */
  public record ScriptedEvent(String name, JsonNode payload) {
  }

  /** One expected pub/sub emission. */
  public record EmitExpectation(String pubsub, String topic) {
  }

  /** One runtime scenario: an input plus everything the flow is expected to do with it. */
  public record Scenario(String name,
                         JsonNode input,
                         List<ScriptedCall> calls,
                         List<ScriptedEvent> events,
                         List<String> expectCalls,
                         List<String> expectTimers,
                         List<EmitExpectation> expectEmits,
                         JsonNode expectOutput) {
    public Scenario {
      calls = orEmpty(calls);
      events = orEmpty(events);
      expectCalls = orEmpty(expectCalls);
      expectTimers = orEmpty(expectTimers);
      expectEmits = orEmpty(expectEmits);
    }
  }

  public record CatalogCase(String dir, String name, String definitionText, Deploy deploy,
                            List<Scenario> scenarios) {
    public CatalogCase {
      scenarios = orEmpty(scenarios);
    }

    @Override
    public String toString() {
      return dir;
    }
  }

  private record Index(List<String> cases) {
  }

  private record Manifest(String name, Deploy deploy, List<Scenario> scenarios) {
  }

  public static List<CatalogCase> cases() {
    Index index = read("/e2e-catalog/index.yaml", Index.class);
    return index.cases().stream()
        .map(dir -> {
          Manifest manifest = read("/e2e-catalog/" + dir + "/manifest.yaml", Manifest.class);
          String definition = text("/e2e-catalog/" + dir + "/definition.yaml");
          return new CatalogCase(dir, manifest.name(), definition, manifest.deploy(),
              manifest.scenarios());
        })
        .toList();
  }

  private static <T> T read(String resource, Class<T> type) {
    try {
      return YAML.readValue(text(resource), type);
    } catch (IOException e) {
      throw new UncheckedIOException("invalid catalog file " + resource, e);
    }
  }

  private static String text(String resource) {
    try (InputStream in = E2eCatalog.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new AssertionError("missing catalog resource " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? List.of() : List.copyOf(list);
  }
}
