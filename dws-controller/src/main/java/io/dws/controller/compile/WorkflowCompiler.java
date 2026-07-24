package io.dws.controller.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.dws.controller.model.TopicBinding;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.EndpointUri;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.OpenAPIArguments;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.RunTaskConfigurationUnion;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.Workflow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure compile pass: parse + validate an Open Workflow Specification DSL 1.0 definition and walk it
 * into a {@link DeploymentPlan}. No Kubernetes calls; the only side effect is fetching each
 * referenced OpenAPI document to pin it by content hash (via {@link OpenApiDocumentFetcher}).
 */
public class WorkflowCompiler {

    private static final int ORCHESTRATOR_PORT = 8080;
    private static final String DEFINITION_KEY = "definition";

    private final ImageCatalog images;
    private final OpenApiDocumentFetcher documentFetcher;
    private final ObjectMapper json = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    public WorkflowCompiler(ImageCatalog images, OpenApiDocumentFetcher documentFetcher) {
        this.images = images;
        this.documentFetcher = documentFetcher;
    }

    public DeploymentPlan compile(String specText) {
        if (specText == null || specText.isBlank()) {
            throw new CompilationException(List.of("Definition is empty"));
        }
        WorkflowFormat format = detectFormat(specText);
        Workflow workflow = parseOrThrow(specText, format);

        List<String> errors = semanticErrors(workflow);
        if (!errors.isEmpty()) {
            throw new CompilationException(errors);
        }

        String w = Names.kebab(workflow.getDocument().getName());
        String versionId = SpecDigest.versionId(specText, format);
        String version = version(w, versionId);
        String defResource = Names.definitionResource(w, versionId);

        List<StepService> steps = new ArrayList<>();
        List<TopicBinding> bindings = new ArrayList<>();
        walk(workflow.getDo(), steps, bindings);

        OrchestratorSpec orchestrator = new OrchestratorSpec(
                Names.orchestrator(w, versionId),
                images.orchestrator(),
                w,
                ORCHESTRATOR_PORT,
                1,
                Map.of("DEFINITION_STORE", defResource, "DEFINITION_KEY", DEFINITION_KEY));

        return new DeploymentPlan(w, versionId, version, defResource, specText, steps, bindings, orchestrator);
    }

    /** The public version string: {@code <workflow>@v<sha256-8>}. */
    public static String version(String workflow, String versionId) {
        return workflow + "@" + versionId;
    }

    // ---- parsing / validation ------------------------------------------------

    private static WorkflowFormat detectFormat(String specText) {
        return specText.stripLeading().startsWith("{") ? WorkflowFormat.JSON : WorkflowFormat.YAML;
    }

    private Workflow parseOrThrow(String specText, WorkflowFormat format) {
        try {
            Workflow workflow = WorkflowReader.readWorkflowFromString(specText, format);
            if (workflow == null) {
                throw new CompilationException(List.of("Definition could not be parsed"));
            }
            return workflow;
        } catch (CompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompilationException(collectMessages(e));
        }
    }

    private static List<String> collectMessages(Throwable t) {
        List<String> messages = new ArrayList<>();
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c.getMessage() != null && !c.getMessage().isBlank()) {
                messages.add(c.getMessage());
            }
        }
        if (messages.isEmpty()) {
            messages.add(t.getClass().getSimpleName());
        }
        return messages;
    }

    private static List<String> semanticErrors(Workflow workflow) {
        List<String> errors = new ArrayList<>();
        Document doc = workflow.getDocument();
        if (doc == null) {
            errors.add("Missing 'document'");
        } else {
            if (isBlank(doc.getName())) {
                errors.add("Missing 'document.name'");
            }
            if (isBlank(doc.getVersion())) {
                errors.add("Missing 'document.version'");
            }
        }
        if (workflow.getDo() == null || workflow.getDo().isEmpty()) {
            errors.add("Workflow has no 'do' tasks");
        }
        return errors;
    }

    // ---- task walk -----------------------------------------------------------

    private void walk(List<TaskItem> tasks, List<StepService> steps, List<TopicBinding> bindings) {
        for (TaskItem item : tasks) {
            String taskName = item.getName();
            Task task = item.getTask();
            CallTask call = task.getCallTask();
            if (call != null && call.getCallHTTP() != null) {
                steps.add(httpStep(taskName, call.getCallHTTP()));
            } else if (call != null && call.getCallOpenAPI() != null) {
                steps.add(openApiStep(taskName, call.getCallOpenAPI()));
            } else if (task.getRunTask() != null) {
                steps.add(runStep(taskName, task.getRunTask()));
            } else if (task.getEmitTask() != null) {
                emitBinding(taskName, task.getEmitTask()).ifPresent(bindings::add);
            } else if (task.getListenTask() != null) {
                bindings.add(new TopicBinding(taskName, TopicBinding.Direction.LISTEN, taskName));
            }
            // switch/set/wait/for/try/raise (and do/fork) deploy nothing.
        }
    }

    private StepService httpStep(String taskName, CallHTTP call) {
        HTTPArguments with = call.getWith();
        Map<String, String> env = new LinkedHashMap<>();
        putIfPresent(env, "METHOD", with.getMethod());
        putIfPresent(env, "ENDPOINT", resolveEndpoint(with.getEndpoint()));
        if (with.getHeaders() != null) {
            if (with.getHeaders().getHTTPHeaders() != null) {
                putIfPresent(env, "HEADERS", toJson(with.getHeaders().getHTTPHeaders().getAdditionalProperties()));
            } else {
                putIfPresent(env, "HEADERS", with.getHeaders().getRuntimeExpression());
            }
        }
        if (with.getQuery() != null) {
            if (with.getQuery().getHTTPQuery() != null) {
                putIfPresent(env, "QUERY", toJson(with.getQuery().getHTTPQuery().getAdditionalProperties()));
            } else {
                putIfPresent(env, "QUERY", with.getQuery().getRuntimeExpression());
            }
        }
        if (with.getOutput() != null) {
            putIfPresent(env, "OUTPUT", with.getOutput().value());
        }
        if (call.getTimeout() != null) {
            putIfPresent(env, "TIMEOUT", toJson(call.getTimeout()));
        }
        return new StepService(Names.kebab(taskName), TaskKind.CALL_HTTP, images.callHttp(), env);
    }

    private StepService openApiStep(String taskName, CallOpenAPI call) {
        OpenAPIArguments with = call.getWith();
        Map<String, String> env = new LinkedHashMap<>();
        String documentUrl = with.getDocument() != null ? resolveEndpoint(with.getDocument().getEndpoint()) : null;
        putIfPresent(env, "DOCUMENT_URL", documentUrl);
        if (documentUrl != null) {
            putIfPresent(env, "DOCUMENT_SHA256", SpecDigest.sha256Hex(documentFetcher.fetch(documentUrl)));
        }
        putIfPresent(env, "OPERATION_ID", with.getOperationId());
        if (with.getParameters() != null) {
            putIfPresent(env, "PARAMETERS", toJson(with.getParameters().getAdditionalProperties()));
        }
        return new StepService(Names.kebab(taskName), TaskKind.CALL_OPENAPI, images.callOpenapi(), env);
    }

    private StepService runStep(String taskName, RunTask run) {
        Map<String, String> env = new LinkedHashMap<>();
        RunTaskConfigurationUnion cfg = run.getRun();
        if (cfg != null && cfg.getRunShell() != null) {
            putIfPresent(env, "COMMAND", cfg.getRunShell().getShell().getCommand());
        } else if (cfg != null && cfg.getRunScript() != null && cfg.getRunScript().getScript() != null) {
            var script = cfg.getRunScript().getScript();
            if (script.getInlineScript() != null) {
                putIfPresent(env, "SCRIPT", script.getInlineScript().getCode());
            }
        }
        return new StepService(Names.kebab(taskName), TaskKind.RUN, images.run(), env);
    }

    private java.util.Optional<TopicBinding> emitBinding(String taskName, EmitTask emit) {
        if (emit.getEmit() == null
                || emit.getEmit().getEvent() == null
                || emit.getEmit().getEvent().getWith() == null) {
            return java.util.Optional.empty();
        }
        String type = emit.getEmit().getEvent().getWith().getType();
        String topic = type != null ? type : taskName;
        return java.util.Optional.of(new TopicBinding(taskName, TopicBinding.Direction.EMIT, topic));
    }

    // ---- endpoint / json helpers --------------------------------------------

    private static String resolveEndpoint(Endpoint endpoint) {
        if (endpoint == null) {
            return null;
        }
        if (endpoint.getRuntimeExpression() != null) {
            return endpoint.getRuntimeExpression();
        }
        if (endpoint.getUriTemplate() != null) {
            return uriString(endpoint.getUriTemplate());
        }
        if (endpoint.getEndpointConfiguration() != null && endpoint.getEndpointConfiguration().getUri() != null) {
            return uriString(endpoint.getEndpointConfiguration().getUri());
        }
        return null;
    }

    private static String uriString(EndpointUri uri) {
        if (uri.getExpressionEndpointURI() != null) {
            return uri.getExpressionEndpointURI();
        }
        return uriString(uri.getLiteralEndpointURI());
    }

    private static String uriString(UriTemplate template) {
        if (template == null) {
            return null;
        }
        if (template.getLiteralUri() != null) {
            return template.getLiteralUri().toString();
        }
        return template.getLiteralUriTemplate();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.put(key, value);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
