package io.dws.controller.e2e;

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
 * {@code testResources}). The controller tier consumes the {@code deploy} expectations;
 * the {@code scenarios} section is parsed for strictness but exercised by the orchestrator
 * tier (see {@code dws-orchestrator}'s twin of this loader — the two modules are independent
 * Maven projects, so the ~100 lines are deliberately duplicated instead of shared).
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

    /** One scripted activity result (orchestrator tier). */
    public record ScriptedCall(String appId, JsonNode result) {
    }

    /** One scripted external event (orchestrator tier). */
    public record ScriptedEvent(String name, JsonNode payload) {
    }

    /** One expected pub/sub emission (orchestrator tier). */
    public record EmitExpectation(String pubsub, String topic) {
    }

    /** One runtime scenario (orchestrator tier). */
    public record Scenario(String name,
                           JsonNode input,
                           List<ScriptedCall> calls,
                           List<ScriptedEvent> events,
                           List<String> expectCalls,
                           List<String> expectTimers,
                           List<EmitExpectation> expectEmits,
                           JsonNode expectOutput) {
    }

    public record CatalogCase(String dir, String name, String definitionText, Deploy deploy,
                              List<Scenario> scenarios) {

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
