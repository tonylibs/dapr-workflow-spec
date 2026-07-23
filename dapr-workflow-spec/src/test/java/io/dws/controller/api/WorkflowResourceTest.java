package io.dws.controller.api;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
@WithKubernetesTestServer
class WorkflowResourceTest {

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

    @BeforeEach
    void cleanCluster() {
        applier.deleteWorkflow("order");
    }

    @Test
    @DisplayName("POST deploys the stack and returns 201 with the content-addressed version")
    void postDeploysStack() {
        given().contentType(YAML)
                .body(fixture("order.yaml"))
                .when()
                .post("/workflows")
                .then()
                .statusCode(201)
                .body("workflow", equalTo("order"))
                .body("version", startsWith("order@v"))
                .body("created", equalTo(true));
    }

    @Test
    @DisplayName("re-POSTing identical content returns 200 and deploys nothing new")
    void rePostIsIdempotent() {
        deployOrder();

        given().contentType(YAML)
                .body(fixture("order.yaml"))
                .when()
                .post("/workflows")
                .then()
                .statusCode(200)
                .body("created", equalTo(false));
    }

    @Test
    @DisplayName("POST ?dryRun=true returns the computed plan without touching the cluster")
    void dryRunAppliesNothing() {
        given().contentType(YAML)
                .queryParam("dryRun", true)
                .body(fixture("order.yaml"))
                .when()
                .post("/workflows")
                .then()
                .statusCode(200)
                .body("workflow", equalTo("order"))
                .body("steps", hasSize(3))
                .body("steps[0].name", equalTo("check-inventory"))
                .body("orchestrator.replicas", equalTo(1));

        assertThat(managedConfigMaps()).isZero();
    }

    @Test
    @DisplayName("an invalid definition is rejected with 400 and nothing is applied")
    void invalidDefinitionIsRejected() {
        given().contentType(YAML)
                .body(fixture("broken.yaml"))
                .when()
                .post("/workflows")
                .then()
                .statusCode(400)
                .body("errors", not(hasSize(0)));

        assertThat(managedConfigMaps()).isZero();
    }

    @Test
    @DisplayName("GET /workflows lists name, version and status from the cluster")
    void listsDeployedWorkflows() {
        deployOrder();

        given().when()
                .get("/workflows")
                .then()
                .statusCode(200)
                .body("[0].name", equalTo("order"))
                .body("[0].version", startsWith("order@v"))
                .body("[0].status", equalTo("PENDING"));
    }

    @Test
    @DisplayName("GET /workflows/{name} returns detail including the current version's steps")
    void returnsWorkflowDetail() {
        deployOrder();

        given().when()
                .get("/workflows/order")
                .then()
                .statusCode(200)
                .body("name", equalTo("order"))
                .body("version", startsWith("order@v"))
                .body("steps", hasSize(3))
                .body("versions", hasSize(1));
    }

    @Test
    @DisplayName("GET /workflows/{name}/plan recomputes the plan from the deployed definition")
    void returnsComputedPlan() {
        deployOrder();

        given().when()
                .get("/workflows/order/plan")
                .then()
                .statusCode(200)
                .body("workflow", equalTo("order"))
                .body("steps", hasSize(3))
                .body("orchestrator.env.DEFINITION_KEY", equalTo("definition"));
    }

    @Test
    @DisplayName("DELETE tears the stack down and subsequent reads 404")
    void deleteTearsDownStack() {
        deployOrder();

        given().when().delete("/workflows/order").then().statusCode(204);

        given().when().get("/workflows/order").then().statusCode(404);
        assertThat(managedConfigMaps()).isZero();
    }

    @Test
    @DisplayName("unknown workflow reads and deletes return 404")
    void unknownWorkflowIs404() {
        given().when().get("/workflows/nope").then().statusCode(404);
        given().when().get("/workflows/nope/plan").then().statusCode(404);
        given().when().delete("/workflows/nope").then().statusCode(404);
    }

    private void deployOrder() {
        given().contentType(YAML)
                .body(fixture("order.yaml"))
                .when()
                .post("/workflows")
                .then()
                .statusCode(201);
    }

    private int managedConfigMaps() {
        return client.configMaps()
                .inNamespace(NAMESPACE)
                .withLabels(Labels.managed())
                .list()
                .getItems()
                .size();
    }

    private static String fixture(String name) {
        try (var in = WorkflowResourceTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new AssertionError("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
