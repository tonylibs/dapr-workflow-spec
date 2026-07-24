package io.dws.controller.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import io.dws.controller.k8s.Labels;
import io.dws.controller.k8s.StackApplier;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * "Visible" e2e tier: every catalog definition is POSTed to the real {@code /workflows} API
 * (against the CRUD Kubernetes mock server) and must then be observable through every read
 * endpoint — the list, the detail, and the recomputed plan, whose steps and topic bindings
 * are pinned by the case manifest — before DELETE tears the stack down again. Invalid
 * definitions must be rejected without touching the cluster.
 */
@QuarkusTest
@WithKubernetesTestServer
class WorkflowCatalogVisibilityE2eTest {

    private static final String YAML = "application/yaml";
    private static final String NAMESPACE = "default";

    @Inject
    KubernetesClient client;

    @Inject
    StackApplier applier;

    @BeforeAll
    static void encodeYamlAsText() {
        RestAssured.config = RestAssured.config()
                .encoderConfig(EncoderConfig.encoderConfig().encodeContentTypeAs(YAML, ContentType.TEXT));
    }

    static Stream<Named<E2eCatalog.CatalogCase>> validCases() {
        return cases(true);
    }

    static Stream<Named<E2eCatalog.CatalogCase>> invalidCases() {
        return cases(false);
    }

    private static Stream<Named<E2eCatalog.CatalogCase>> cases(boolean valid) {
        return E2eCatalog.cases().stream()
                .filter(c -> c.deploy().valid() == valid)
                .map(c -> Named.of(c.dir(), c));
    }

    @ParameterizedTest
    @MethodSource("validCases")
    void definitionIsDeployedAndVisible(E2eCatalog.CatalogCase c) {
        try {
            given().contentType(YAML)
                    .body(c.definitionText())
                    .when()
                    .post("/workflows")
                    .then()
                    .statusCode(201)
                    .body("workflow", equalTo(c.name()))
                    .body("version", startsWith(c.name() + "@v"))
                    .body("created", equalTo(true));

            // Re-POSTing identical content is an idempotent no-op.
            given().contentType(YAML)
                    .body(c.definitionText())
                    .when()
                    .post("/workflows")
                    .then()
                    .statusCode(200)
                    .body("created", equalTo(false));

            List<String> listed = given().when()
                    .get("/workflows")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getList("name", String.class);
            assertThat(listed).as("GET /workflows lists the deployed workflow").contains(c.name());

            given().when()
                    .get("/workflows/" + c.name())
                    .then()
                    .statusCode(200)
                    .body("name", equalTo(c.name()))
                    .body("version", startsWith(c.name() + "@v"))
                    .body("steps", hasSize(c.deploy().expectSteps().size()))
                    .body("versions", hasSize(1));

            var plan = given().when()
                    .get("/workflows/" + c.name() + "/plan")
                    .then()
                    .statusCode(200)
                    .body("workflow", equalTo(c.name()))
                    .body("orchestrator.replicas", equalTo(1))
                    .body("orchestrator.env.DEFINITION_KEY", equalTo("definition"))
                    .extract().jsonPath();
            assertThat(plan.getList("steps.name", String.class))
                    .as("plan step services, in walk order")
                    .containsExactlyElementsOf(c.deploy().expectSteps());
            assertThat(plan.getList("bindings.topic", String.class))
                    .as("plan topic bindings")
                    .containsExactlyElementsOf(c.deploy().expectBindings());

            given().when().delete("/workflows/" + c.name()).then().statusCode(204);
            given().when().get("/workflows/" + c.name()).then().statusCode(404);
            assertThat(configMapsFor(c.name())).as("stack torn down").isZero();
        } finally {
            // The mock server outlives each parameterized invocation; never leak a stack.
            applier.deleteWorkflow(c.name());
        }
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void invalidDefinitionIsRejected(E2eCatalog.CatalogCase c) {
        given().contentType(YAML)
                .body(c.definitionText())
                .when()
                .post("/workflows")
                .then()
                .statusCode(400)
                .body("errors", not(hasSize(0)));

        assertThat(configMapsFor(c.name())).as("nothing applied").isZero();
    }

    private int configMapsFor(String workflow) {
        return client.configMaps()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.workflow(workflow))
                .list()
                .getItems()
                .size();
    }
}
