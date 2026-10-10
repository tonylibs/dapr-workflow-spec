package io.dws.controller.k8s;

import static io.dws.controller.k8s.ObservabilitySettings.ENABLED_KEY;
import static io.dws.controller.k8s.ObservabilitySettings.INSTRUMENTATION_KEY;
import static io.dws.controller.k8s.ObservabilitySettings.STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class ObservabilityFlagsTest {

  private static final String NAMESPACE = "dws";

  private final DaprClient daprClient = mock(DaprClient.class);
  private final KubernetesClient client = mock(KubernetesClient.class);
  private Resource<GenericKubernetesResource> tracingResource;
  private final ObservabilityFlags flags = new ObservabilityFlags(daprClient, client);

  /** The store answers the single two-key lookup with exactly these values (null = null value). */
  private void stubStore(Map<String, String> values) {
    Map<String, ConfigurationItem> items = new HashMap<>();
    values.forEach((key, value) -> items.put(key, new ConfigurationItem(key, value, "1")));
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.just(items));
  }

  /** The block the chart's dws-tracing carries; the controller must copy it as read. */
  private static final Map<String, Object> TRACING =
      Map.of(
          "samplingRate",
          "1",
          "otel",
          Map.of("endpointAddress", "collector:4318", "isSecure", false, "protocol", "http"));

  private void tracingConfigurationExists(boolean exists) {
    GenericKubernetesResource tracing = new GenericKubernetesResource();
    tracing.setAdditionalProperty("spec", Map.of("tracing", TRACING));
    when(tracingLookup().get()).thenReturn(exists ? tracing : null);
  }

  /**
   * The terminal {@code withName("dws-tracing")} node of the Fabric8 DSL chain, as a mock. (Mockito
   * deep stubs cannot resolve the DSL's self-referential generics, so the chain is wired
   * explicitly.)
   */
  @SuppressWarnings("unchecked")
  private Resource<GenericKubernetesResource> tracingLookup() {
    if (tracingResource == null) {
      var all = mock(MixedOperation.class);
      var inNamespace = mock(NonNamespaceOperation.class);
      tracingResource = mock(Resource.class);
      when(client.genericKubernetesResources(ResourceContexts.DAPR_CONFIGURATION)).thenReturn(all);
      when(all.inNamespace(NAMESPACE)).thenReturn(inNamespace);
      when(inNamespace.withName(ObservabilitySettings.TRACING_CONFIGURATION))
          .thenReturn(tracingResource);
    }
    return tracingResource;
  }

  @Test
  @DisplayName(
      "enabled=true with dws-tracing present turns observability on with default injection")
  void enabledWithConfigurationIsOn() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE))
        .isEqualTo(new ObservabilitySettings(true, "true", TRACING));
  }

  @Test
  @DisplayName("enabled value is trimmed and compared case-insensitively")
  void enabledIsTrimmedAndCaseInsensitive() {
    stubStore(Map.of(ENABLED_KEY, " TRUE "));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE).enabled()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "yes", "1", "on", "", "  "})
  @DisplayName("any enabled value other than true means off")
  void otherEnabledValuesAreOff(String value) {
    stubStore(Map.of(ENABLED_KEY, value));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("an absent enabled key means off")
  void absentEnabledKeyIsOff() {
    stubStore(Map.of(INSTRUMENTATION_KEY, "dws-system/dws-instrumentation"));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("a null enabled value means off")
  void nullEnabledValueIsOff() {
    Map<String, String> values = new HashMap<>();
    values.put(ENABLED_KEY, null);
    values.put(INSTRUMENTATION_KEY, "x");
    stubStore(values);
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("an empty store map means off")
  void emptyMapIsOff() {
    stubStore(Map.of());
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("a custom Instrumentation reference is carried through")
  void instrumentationReferenceIsCarried() {
    stubStore(Map.of(ENABLED_KEY, "true", INSTRUMENTATION_KEY, "dws-system/dws-instrumentation"));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE))
        .isEqualTo(new ObservabilitySettings(true, "dws-system/dws-instrumentation", TRACING));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("a blank Instrumentation reference falls back to true")
  void blankInstrumentationFallsBackToTrue(String blank) {
    stubStore(Map.of(ENABLED_KEY, "true", INSTRUMENTATION_KEY, blank));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE))
        .isEqualTo(new ObservabilitySettings(true, "true", TRACING));
  }

  @Test
  @DisplayName("an absent Instrumentation key falls back to true")
  void absentInstrumentationFallsBackToTrue() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE).instrumentation()).isEqualTo("true");
  }

  @Test
  @DisplayName("a store error means off without throwing")
  void storeErrorIsOff() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.error(new RuntimeException("sidecar down")));
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("a store call that throws synchronously means off")
  void storeCallThrowingIsOff() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenThrow(new IllegalStateException("boom"));

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("an empty store response means off")
  void emptyStoreResponseIsOff() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.empty());
    tracingConfigurationExists(true);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("a store response that never completes means off within the bound")
  void neverCompletingStoreIsOffWithinBound() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.never());
    tracingConfigurationExists(true);

    long start = System.nanoTime();
    ObservabilitySettings resolved =
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> flags.resolve(NAMESPACE));
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(resolved).isEqualTo(ObservabilitySettings.OFF);
    assertThat(ObservabilityFlags.STORE_TIMEOUT).isEqualTo(Duration.ofSeconds(1));
    assertThat(elapsed).isLessThan(Duration.ofMillis(1500));
  }

  // ---- logging: one WARN per failure that could matter, nothing louder than DEBUG otherwise ----

  private static void assertSingleLineWarning(LogCapture logs, String... fragments) {
    List<LogRecord> warnings = logs.at(Level.WARNING);
    assertThat(warnings).hasSize(1);
    LogRecord warning = warnings.getFirst();
    assertThat(warning.getThrown()).as("single line, no stack trace").isNull();
    assertThat(LogCapture.message(warning)).doesNotContain("\n").contains(fragments);
  }

  @Test
  @DisplayName("a missing dws-tracing Configuration logs exactly one WARN")
  void missingTracingConfigurationLogsOneWarning() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    tracingConfigurationExists(false);

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      flags.resolve(NAMESPACE);

      assertSingleLineWarning(logs, "dws-tracing", "not found");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"no-spec", "no-tracing", "empty-tracing"})
  @DisplayName("a dws-tracing without a spec.tracing block is off and logs exactly one WARN")
  void tracingConfigurationWithoutBlockIsOff(String shape) {
    stubStore(Map.of(ENABLED_KEY, "true"));
    GenericKubernetesResource resource = new GenericKubernetesResource();
    switch (shape) {
      case "no-tracing" -> resource.setAdditionalProperty("spec", Map.of("metric", Map.of()));
      case "empty-tracing" -> resource.setAdditionalProperty("spec", Map.of("tracing", Map.of()));
      default -> {}
    }
    when(tracingLookup().get()).thenReturn(resource);

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);

      assertSingleLineWarning(logs, "dws-tracing", "spec.tracing");
    }
  }

  @Test
  @DisplayName("a forbidden Configuration lookup logs exactly one WARN")
  void forbiddenConfigurationLookupLogsOneWarning() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    when(tracingLookup().get()).thenThrow(new KubernetesClientException("forbidden", 403, null));

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      flags.resolve(NAMESPACE);

      assertSingleLineWarning(logs, "dws-tracing", "could not be read", "forbidden");
    }
  }

  @Test
  @DisplayName("an API error on the Configuration lookup logs exactly one WARN")
  void apiErrorConfigurationLookupLogsOneWarning() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    when(tracingLookup().get()).thenThrow(new IllegalStateException("api server down"));

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      flags.resolve(NAMESPACE);

      assertSingleLineWarning(logs, "dws-tracing", "could not be read", "api server down");
    }
  }

  @Test
  @DisplayName("a throwing store logs exactly one single-line WARN")
  void storeErrorLogsOneWarning() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.error(new RuntimeException("sidecar down")));

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);

      assertSingleLineWarning(logs, "sidecar down");
    }
  }

  @Test
  @DisplayName("a synchronously throwing store call logs exactly one single-line WARN")
  void storeCallThrowingLogsOneWarning() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenThrow(new IllegalStateException("boom"));

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      flags.resolve(NAMESPACE);

      assertSingleLineWarning(logs, "boom");
    }
  }

  @Test
  @DisplayName("a store that times out logs exactly one single-line WARN")
  void storeTimeoutLogsOneWarning() {
    when(daprClient.getConfiguration(STORE, ENABLED_KEY, INSTRUMENTATION_KEY))
        .thenReturn(Mono.never());

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);

      assertSingleLineWarning(logs, "Observability flags unavailable");
    }
  }

  @Test
  @DisplayName("an absent flag key logs no WARN")
  void absentFlagKeyLogsNoWarning() {
    stubStore(Map.of());
    tracingConfigurationExists(true);

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);

      assertThat(logs.at(Level.WARNING)).isEmpty();
    }
  }

  @Test
  @DisplayName("a disabled flag and a missing sidecar log no WARN")
  void disabledFlagAndNoSidecarLogNoWarning() {
    stubStore(Map.of(ENABLED_KEY, "false"));

    try (LogCapture logs = new LogCapture(ObservabilityFlags.class)) {
      flags.resolve(NAMESPACE);
      new ObservabilityFlags(null, client).resolve(NAMESPACE);

      assertThat(logs.at(Level.WARNING)).isEmpty();
    }
  }

  @Test
  @DisplayName("a null Dapr client (no sidecar) means off")
  void nullDaprClientIsOff() {
    ObservabilityFlags noSidecar = new ObservabilityFlags(null, client);

    assertThat(noSidecar.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("enabled=true but dws-tracing absent means off")
  void missingTracingConfigurationIsOff() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    tracingConfigurationExists(false);

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("a forbidden Configuration lookup means off without throwing")
  void forbiddenConfigurationLookupIsOff() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    when(tracingLookup().get()).thenThrow(new KubernetesClientException("forbidden", 403, null));

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("any other Configuration lookup failure means off without throwing")
  void failingConfigurationLookupIsOff() {
    stubStore(Map.of(ENABLED_KEY, "true"));
    when(tracingLookup().get()).thenThrow(new IllegalStateException("api server down"));

    assertThat(flags.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
  }

  @Test
  @DisplayName("the Configuration is not looked up when the flag is off")
  void configurationNotLookedUpWhenOff() {
    stubStore(Map.of(ENABLED_KEY, "false"));
    KubernetesClient untouched = mock(KubernetesClient.class);
    ObservabilityFlags off = new ObservabilityFlags(daprClient, untouched);

    assertThat(off.resolve(NAMESPACE)).isEqualTo(ObservabilitySettings.OFF);
    verifyNoInteractions(untouched);
  }
}
