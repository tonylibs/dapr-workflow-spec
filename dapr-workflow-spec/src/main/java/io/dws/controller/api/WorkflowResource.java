package io.dws.controller.api;

import io.dws.controller.compile.WorkflowCompiler;
import io.dws.controller.k8s.StackApplier;
import io.dws.controller.k8s.StackReader;
import io.dws.controller.model.ApplyResult;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.WorkflowDetail;
import io.dws.controller.model.WorkflowSummary;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/** The controller's public API. All reads are answered from live cluster state. */
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowResource {

    private final WorkflowCompiler compiler;
    private final StackApplier applier;
    private final StackReader reader;

    public WorkflowResource(WorkflowCompiler compiler, StackApplier applier, StackReader reader) {
        this.compiler = compiler;
        this.applier = applier;
        this.reader = reader;
    }

    /** Accepts a DSL 1.0 definition as YAML or JSON. {@code ?dryRun=true} compiles without applying. */
    @POST
    @Consumes(MediaType.WILDCARD)
    public Response deploy(@QueryParam("dryRun") boolean dryRun, String definition) {
        DeploymentPlan plan = compiler.compile(definition);
        if (dryRun) {
            return Response.ok(plan).build();
        }
        ApplyResult result = applier.apply(plan);
        return Response.status(result.created() ? Response.Status.CREATED : Response.Status.OK)
                .entity(result)
                .build();
    }

    @GET
    public List<WorkflowSummary> list() {
        return reader.list();
    }

    @GET
    @Path("/{name}")
    public WorkflowDetail get(@PathParam("name") String name) {
        return reader.get(name).orElseThrow(() -> new WorkflowNotFoundException(name));
    }

    /** Recomputes the plan for the deployed definition — a dry run over what is already in the cluster. */
    @GET
    @Path("/{name}/plan")
    public DeploymentPlan plan(@PathParam("name") String name) {
        return reader.definitionText(name)
                .map(compiler::compile)
                .orElseThrow(() -> new WorkflowNotFoundException(name));
    }

    @DELETE
    @Path("/{name}")
    public Response delete(@PathParam("name") String name) {
        if (!applier.deleteWorkflow(name)) {
            throw new WorkflowNotFoundException(name);
        }
        return Response.noContent().build();
    }
}
