package io.dws.controller.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkflowCompilerTest {

    private static final ImageCatalog IMAGES =
            new ImageCatalog("sw-call-http:1.0", "sw-call-openapi:1.0", "sw-run:1.0", "sw-orchestrator:1.0");
    private static final byte[] OPENAPI_DOC = "OPENAPI-DOCUMENT-BYTES".getBytes(StandardCharsets.UTF_8);

    private final WorkflowCompiler compiler = new WorkflowCompiler(IMAGES, url -> OPENAPI_DOC);

    @Test
    @DisplayName("order.yaml compiles to three call-http steps with exact env")
    void orderCompilesToThreeHttpSteps() {
        DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps()).allSatisfy(s -> {
            assertThat(s.kind()).isEqualTo(TaskKind.CALL_HTTP);
            assertThat(s.image()).isEqualTo("sw-call-http:1.0");
        });
        assertThat(plan.steps()).extracting(StepService::name)
                .containsExactly("check-inventory", "charge-payment", "notify-out-of-stock");

        assertThat(step(plan, "check-inventory").env())
                .isEqualTo(Map.of("METHOD", "get", "ENDPOINT", "http://inventory.local/api/check"));
        assertThat(step(plan, "charge-payment").env())
                .isEqualTo(Map.of("METHOD", "post", "ENDPOINT", "http://payment.local/api/charge"));
        assertThat(step(plan, "notify-out-of-stock").env())
                .isEqualTo(Map.of("METHOD", "post", "ENDPOINT", "http://notify.local/api/oos"));
    }

    @Test
    @DisplayName("order.yaml produces a one-replica orchestrator wired to the definition ConfigMap")
    void orderOrchestratorSpec() {
        DeploymentPlan plan = compiler.compile(fixture("order.yaml"));

        assertThat(plan.workflow()).isEqualTo("order");
        assertThat(plan.orchestrator().name()).isEqualTo("order-" + plan.versionId());
        assertThat(plan.orchestrator().image()).isEqualTo("sw-orchestrator:1.0");
        assertThat(plan.orchestrator().appId()).isEqualTo("order");
        assertThat(plan.orchestrator().appPort()).isEqualTo(8080);
        assertThat(plan.orchestrator().replicas()).isEqualTo(1);
        assertThat(plan.orchestrator().env())
                .isEqualTo(Map.of("DEFINITION_STORE", plan.definitionResource(), "DEFINITION_KEY", "definition"));
        assertThat(plan.definitionResource()).isEqualTo("dws-def-order-" + plan.versionId());
    }

    @Test
    @DisplayName("version is content-addressed and stable across reruns")
    void versionIsStable() {
        String text = fixture("order.yaml");

        DeploymentPlan first = compiler.compile(text);
        DeploymentPlan second = compiler.compile(text);

        assertThat(first.version()).isEqualTo(second.version());
        assertThat(first.version()).startsWith("order@v");
        assertThat(first.versionId()).hasSize(9).startsWith("v");
    }

    @Test
    @DisplayName("reordering keys / whitespace does not change the version")
    void versionIgnoresFormatting() {
        DeploymentPlan a = compiler.compile(fixture("order.yaml"));
        String reformatted = fixture("order.yaml").replace("  ", "   ").replaceAll("\r\n", "\n");

        DeploymentPlan b = compiler.compile(reformatted);

        assertThat(b.version()).isEqualTo(a.version());
    }

    @Test
    @DisplayName("call openapi pins the document by sha256 and injects operation/parameters")
    void openApiStepPinsDocumentHash() {
        DeploymentPlan plan = compiler.compile(fixture("petstore-openapi.yaml"));

        assertThat(plan.steps()).hasSize(1);
        StepService step = plan.steps().get(0);
        assertThat(step.name()).isEqualTo("find-pet");
        assertThat(step.kind()).isEqualTo(TaskKind.CALL_OPENAPI);
        assertThat(step.image()).isEqualTo("sw-call-openapi:1.0");
        assertThat(step.env()).containsEntry("DOCUMENT_URL", "http://petstore.local/openapi.json");
        assertThat(step.env()).containsEntry("DOCUMENT_SHA256", SpecDigest.sha256Hex(OPENAPI_DOC));
        assertThat(step.env()).containsEntry("OPERATION_ID", "findPetById");
        assertThat(step.env().get("PARAMETERS")).contains("petId");
    }

    @Test
    @DisplayName("invalid definition throws with a non-empty error list and nothing is produced")
    void invalidDefinitionThrows() {
        assertThatThrownBy(() -> compiler.compile(fixture("broken.yaml")))
                .isInstanceOf(CompilationException.class)
                .satisfies(e -> assertThat(((CompilationException) e).errors()).isNotEmpty());
    }

    private static StepService step(DeploymentPlan plan, String name) {
        return plan.steps().stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step " + name));
    }

    private static String fixture(String name) {
        try (var in = WorkflowCompilerTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new AssertionError("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
