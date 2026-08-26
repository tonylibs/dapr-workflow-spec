package io.dws.controller.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.dws.controller.model.BindingComponent;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.EnvValue;
import io.dws.controller.model.ImageCatalog;
import io.dws.controller.model.OAuthEndpoint;
import io.dws.controller.model.OAuthMiddleware;
import io.dws.controller.model.OrchestratorSpec;
import io.dws.controller.model.StepService;
import io.dws.controller.model.TaskKind;
import io.dws.controller.model.TopicBinding;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.AsyncApiArguments;
import io.serverlessworkflow.api.types.AuthenticationPolicyUnion;
import io.serverlessworkflow.api.types.BasicAuthenticationPolicy;
import io.serverlessworkflow.api.types.BasicAuthenticationProperties;
import io.serverlessworkflow.api.types.BearerAuthenticationPolicy;
import io.serverlessworkflow.api.types.BearerAuthenticationProperties;
import io.serverlessworkflow.api.types.CallAsyncAPI;
import io.serverlessworkflow.api.types.CallGRPC;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.CallTask;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.EmitTask;
import io.serverlessworkflow.api.types.Endpoint;
import io.serverlessworkflow.api.types.EndpointConfiguration;
import io.serverlessworkflow.api.types.EndpointUri;
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.GRPCArguments;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.InlineScript;
import io.serverlessworkflow.api.types.OAuth2AuthenticationData;
import io.serverlessworkflow.api.types.OAuth2AuthenticationDataClient;
import io.serverlessworkflow.api.types.OAuth2AuthenticationPolicy;
import io.serverlessworkflow.api.types.OAuth2ConnectAuthenticationProperties;
import io.serverlessworkflow.api.types.OpenAPIArguments;
import io.serverlessworkflow.api.types.ReferenceableAuthenticationPolicy;
import io.serverlessworkflow.api.types.RunScript;
import io.serverlessworkflow.api.types.RunShell;
import io.serverlessworkflow.api.types.RunTask;
import io.serverlessworkflow.api.types.RunTaskConfiguration;
import io.serverlessworkflow.api.types.RunTaskConfigurationUnion;
import io.serverlessworkflow.api.types.ScriptUnion;
import io.serverlessworkflow.api.types.Shell;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.TryTask;
import io.serverlessworkflow.api.types.UriTemplate;
import io.serverlessworkflow.api.types.WithGRPCService;
import io.serverlessworkflow.api.types.Workflow;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure compile pass: parse + validate an Open Workflow Specification DSL 1.0 definition and walk it
 * into a {@link DeploymentPlan}. No Kubernetes calls; the only side effect is fetching each
 * referenced OpenAPI document to pin it by content hash (via {@link OpenApiDocumentFetcher}).
 */
public class WorkflowCompiler {

  private static final int ORCHESTRATOR_PORT = 8080;
  private static final String DEFINITION_KEY = "definition";
  private static final String SECRET_KEY = "value";
  private static final Pattern SECRET_REFERENCE =
      Pattern.compile(
          "^\\$\\{\\s*\\$secrets(?:\\.([A-Za-z][A-Za-z0-9_]*)|\\[\"([a-z0-9](?:[-a-z0-9.]*[a-z0-9])?)\"\\])\\s*}$");
  private static final Pattern DNS_1123_SUBDOMAIN =
      Pattern.compile(
          "^(?=.{1,253}$)[a-z0-9](?:[-a-z0-9]*[a-z0-9])?(?:\\.[a-z0-9](?:[-a-z0-9]*[a-z0-9])?)*$");
  private static final Pattern OPENAPI_SERVER_VARIABLE = Pattern.compile("\\{([^{}]+)}");
  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  private final ImageCatalog images;
  private final OpenApiDocumentFetcher documentFetcher;
  private final ObjectMapper json =
      JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();

  // Deliberately does NOT sort keys — unlike `json` above, which is used for content-addressed
  // versioning and canonicalization. `run.shell`/`run.script` arguments must preserve the
  // definition's document order because dws-run renders `--key value` pairs positionally.
  private final ObjectMapper orderedJson = JsonMapper.builder().build();

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

    CompileContext context =
        new CompileContext(
            w, versionId, declaredSecrets(workflow), declaredAuthentications(workflow));
    List<StepService> steps = new ArrayList<>();
    List<TopicBinding> bindings = new ArrayList<>();
    walk(workflow.getDo(), steps, bindings, context);

    OrchestratorSpec orchestrator =
        new OrchestratorSpec(
            Names.orchestrator(w, versionId),
            images.orchestrator(),
            w,
            ORCHESTRATOR_PORT,
            1,
            orchestratorEnv(defResource, context.secrets()));

    return new DeploymentPlan(
        w,
        versionId,
        version,
        defResource,
        specText,
        steps,
        bindings,
        orchestrator,
        context.oauthEndpoints(),
        context.bindingComponents());
  }

  /** The public version string: {@code <workflow>@v<sha256-8>}. */
  public static String version(String workflow, String versionId) {
    return workflow + "@" + versionId;
  }

  private static Map<String, EnvValue> orchestratorEnv(
      String definitionResource, Set<String> secrets) {
    Map<String, EnvValue> env = new LinkedHashMap<>();
    env.put("DEFINITION_STORE", new EnvValue.Literal(definitionResource));
    env.put("DEFINITION_KEY", new EnvValue.Literal(DEFINITION_KEY));
    for (String secret : secrets) {
      env.put("SECRET_" + secret, new EnvValue.SecretKeyRef(secret, SECRET_KEY));
    }
    return env;
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
    Set<String> secretNames = new LinkedHashSet<>();
    if (workflow.getUse() != null && workflow.getUse().getSecrets() != null) {
      for (String secret : workflow.getUse().getSecrets()) {
        if (isBlank(secret)) {
          errors.add("Secret declarations must be non-empty scalar names");
        } else if (!DNS_1123_SUBDOMAIN.matcher(secret).matches()) {
          errors.add(
              "Secret declaration '" + secret + "' must be a DNS-1123 Kubernetes Secret name");
        } else if (!secretNames.add(secret)) {
          errors.add("Duplicate secret declaration '" + secret + "'");
        }
      }
    }
    for (String duplicate : duplicateTaskNames(workflow.getDo())) {
      errors.add(
          "Duplicate task name '"
              + duplicate
              + "': task names must be unique across the whole definition, including nested "
              + "try/catch lists");
    }
    return errors;
  }

  /**
   * Task names must be unique across the whole definition, at every depth: a {@code call}/{@code
   * run} task's Dapr app-id — and therefore its deployed Knative Service name — is derived from the
   * task name alone, and the orchestrator resolves tasks by name at runtime. Two tasks sharing a
   * name would collide on one deployed object, so this is rejected at POST time rather than
   * discovered after deployment.
   */
  private static Set<String> duplicateTaskNames(List<TaskItem> tasks) {
    Set<String> duplicates = new LinkedHashSet<>();
    collectTaskNames(tasks, new LinkedHashSet<>(), duplicates);
    return duplicates;
  }

  private static void collectTaskNames(
      List<TaskItem> tasks, Set<String> seen, Set<String> duplicates) {
    if (tasks == null) {
      return;
    }
    for (TaskItem item : tasks) {
      if (!seen.add(item.getName())) {
        duplicates.add(item.getName());
      }
      TryTask tryTask = item.getTask() == null ? null : item.getTask().getTryTask();
      if (tryTask != null) {
        collectTaskNames(tryTask.getTry(), seen, duplicates);
        if (tryTask.getCatch() != null) {
          collectTaskNames(tryTask.getCatch().getDo(), seen, duplicates);
        }
      }
      ForkTask forkTask = item.getTask() == null ? null : item.getTask().getForkTask();
      if (forkTask != null) {
        collectTaskNames(forkTask.getFork().getBranches(), seen, duplicates);
      }
      ForTask forTask = item.getTask() == null ? null : item.getTask().getForTask();
      if (forTask != null) {
        collectTaskNames(forTask.getDo(), seen, duplicates);
      }
    }
  }

  // ---- task walk -----------------------------------------------------------

  private void walk(
      List<TaskItem> tasks,
      List<StepService> steps,
      List<TopicBinding> bindings,
      CompileContext context) {
    if (tasks == null) {
      return;
    }
    for (TaskItem item : tasks) {
      String taskName = item.getName();
      Task task = item.getTask();
      CallTask call = task.getCallTask();
      if (call != null && call.getCallHTTP() != null) {
        steps.add(httpStep(taskName, call.getCallHTTP(), context));
      } else if (call != null && call.getCallOpenAPI() != null) {
        steps.add(openApiStep(taskName, call.getCallOpenAPI(), context));
      } else if (call != null && call.getCallGRPC() != null) {
        steps.add(grpcStep(taskName, call.getCallGRPC(), context));
      } else if (call != null && call.getCallAsyncAPI() != null) {
        steps.add(asyncApiStep(taskName, call.getCallAsyncAPI(), context));
      } else if (task.getRunTask() != null) {
        steps.add(runStep(taskName, task.getRunTask()));
      } else if (task.getEmitTask() != null) {
        emitBinding(taskName, task.getEmitTask()).ifPresent(bindings::add);
      } else if (task.getListenTask() != null) {
        bindings.add(new TopicBinding(taskName, TopicBinding.Direction.LISTEN, taskName));
      } else if (task.getTryTask() != null) {
        // A try task deploys nothing itself, but the tasks nested in its try/catch.do lists are
        // ordinary tasks and need their own step services — the orchestrator invokes them by the
        // same kebab-cased app-id it uses for a top-level task.
        TryTask tryTask = task.getTryTask();
        walk(tryTask.getTry(), steps, bindings, context);
        if (tryTask.getCatch() != null) {
          walk(tryTask.getCatch().getDo(), steps, bindings, context);
        }
      } else if (task.getForkTask() != null) {
        // Same reasoning as try: a fork task deploys nothing itself, but each branch's task is
        // dispatched exactly like a top-level task, so it needs its own step service/binding.
        walk(task.getForkTask().getFork().getBranches(), steps, bindings, context);
      } else if (task.getForTask() != null) {
        // Same reasoning again: the body of for.do is dispatched once per iteration exactly like
        // a top-level task list.
        walk(task.getForTask().getDo(), steps, bindings, context);
      }
      // switch/set/wait/raise deploy nothing themselves; their nested container types (try, fork,
      // for) are all walked above.
    }
  }

  private StepService httpStep(String taskName, CallHTTP call, CompileContext context) {
    HTTPArguments with = call.getWith();
    Map<String, EnvValue> env = new LinkedHashMap<>();
    putIfPresent(env, "METHOD", with.getMethod());
    String endpoint = resolveEndpoint(with.getEndpoint());
    putIfPresent(env, "ENDPOINT", endpoint);
    applyAuth(env, resolveAuth(taskName, with.getEndpoint(), endpoint, context));
    if (with.getHeaders() != null) {
      if (with.getHeaders().getHTTPHeaders() != null) {
        putIfPresent(
            env, "HEADERS", toJson(with.getHeaders().getHTTPHeaders().getAdditionalProperties()));
      } else {
        putIfPresent(env, "HEADERS", with.getHeaders().getRuntimeExpression());
      }
    }
    if (with.getQuery() != null) {
      if (with.getQuery().getHTTPQuery() != null) {
        putIfPresent(
            env, "QUERY", toJson(with.getQuery().getHTTPQuery().getAdditionalProperties()));
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

  private StepService openApiStep(String taskName, CallOpenAPI call, CompileContext context) {
    OpenAPIArguments with = call.getWith();
    Map<String, EnvValue> env = new LinkedHashMap<>();
    String documentUrl =
        with.getDocument() != null ? resolveEndpoint(with.getDocument().getEndpoint()) : null;
    putIfPresent(env, "DOCUMENT_URL", documentUrl);
    byte[] document = documentUrl == null ? null : documentFetcher.fetch(documentUrl);
    ReferenceableAuthenticationPolicy authentication = with.getAuthentication();
    if (authentication == null && with.getDocument() != null) {
      authentication = endpointAuthentication(with.getDocument().getEndpoint());
    }
    if (authentication != null) {
      ReferenceableAuthenticationPolicy selectedAuthentication = authentication;
      applyAuth(
          env,
          resolveAuth(
              taskName,
              selectedAuthentication,
              () ->
                  effectiveOpenApiOAuthEndpoint(
                      taskName, documentUrl, document, with.getOperationId()),
              context));
    }
    if (document != null) {
      putIfPresent(env, "DOCUMENT_SHA256", SpecDigest.sha256Hex(document));
    }
    putIfPresent(env, "OPERATION_ID", with.getOperationId());
    if (with.getParameters() != null) {
      putIfPresent(env, "PARAMETERS", toJson(with.getParameters().getAdditionalProperties()));
    }
    return new StepService(Names.kebab(taskName), TaskKind.CALL_OPENAPI, images.callOpenapi(), env);
  }

  /**
   * Compiles a {@code call: asyncapi} task. Does a <em>light</em> read of the fetched AsyncAPI
   * document (first server protocol/host + the operation's channel address) to select a Dapr output
   * binding type and register a version-scoped binding {@link BindingComponent}; the runner
   * ({@code dws-call-asyncapi}) does the full parse and payload validation at runtime. Only
   * outbound {@code send} operations are supported here — {@code subscription} (receive) belongs to
   * a {@code listen} task. Unsupported broker protocols are rejected. Broker credentials, if any,
   * are projected as {@code secretKeyRef} metadata reusing the Phase 4 {@code use.secrets} machinery.
   */
  private StepService asyncApiStep(String taskName, CallAsyncAPI call, CompileContext context) {
    AsyncApiArguments with = call.getWith();
    if (with == null) {
      throw invalid(taskName, "asyncapi call has no 'with' arguments");
    }
    if (with.getSubscription() != null) {
      throw invalid(
          taskName, "asyncapi 'subscription' (receive) is not supported; use a listen task");
    }
    String documentUrl =
        with.getDocument() != null ? resolveEndpoint(with.getDocument().getEndpoint()) : null;
    if (isBlank(documentUrl)) {
      throw invalid(taskName, "asyncapi call requires a static document endpoint");
    }
    String operationId = with.getOperation();
    if (isBlank(operationId)) {
      throw invalid(
          taskName, "asyncapi call requires an 'operation' reference (AsyncAPI 3.0 operation id)");
    }

    byte[] document = documentFetcher.fetch(documentUrl);
    JsonNode api = parseAsyncApiDocument(taskName, document);
    AsyncApiServer server = firstAsyncApiServer(taskName, api);
    BindingType binding = bindingTypeFor(taskName, server.protocol());
    String address = asyncApiChannelAddress(taskName, api, operationId);

    Map<String, EnvValue> metadata = new LinkedHashMap<>();
    if (binding.hostKey() != null && !isBlank(server.host())) {
      metadata.put(binding.hostKey(), new EnvValue.Literal(server.host()));
    }
    metadata.put(binding.destinationKey(), new EnvValue.Literal(address));
    applyBrokerCredentials(taskName, with.getAuthentication(), binding, metadata, context);

    String bindingName = context.registerBinding(taskName, binding.daprType(), metadata);

    Map<String, EnvValue> env = new LinkedHashMap<>();
    putIfPresent(env, "DOC_ENDPOINT", documentUrl);
    putIfPresent(env, "DOC_SHA256", SpecDigest.sha256Hex(document));
    putIfPresent(env, "OPERATION_ID", operationId);
    putIfPresent(env, "BINDING_NAME", bindingName);
    env.put("OPERATION", new EnvValue.Literal("create"));
    return new StepService(
        Names.kebab(taskName), TaskKind.CALL_ASYNCAPI, images.callAsyncapi(), env);
  }

  private void applyBrokerCredentials(
      String taskName,
      ReferenceableAuthenticationPolicy reference,
      BindingType binding,
      Map<String, EnvValue> metadata,
      CompileContext context) {
    if (reference == null) {
      return;
    }
    AuthenticationPolicyUnion policy = resolvePolicy(taskName, reference, context);
    BasicAuthenticationPolicy basic = policy.getBasicAuthenticationPolicy();
    if (basic == null
        || basic.getBasic() == null
        || basic.getBasic().getBasicAuthenticationProperties() == null) {
      throw invalid(
          taskName, "asyncapi broker authentication supports only basic username/password");
    }
    if (binding.userKey() == null || binding.passwordKey() == null) {
      throw invalid(
          taskName,
          "asyncapi broker authentication is not supported for this protocol in v1; pre-create the"
              + " binding component's secret metadata instead");
    }
    BasicAuthenticationProperties properties = basic.getBasic().getBasicAuthenticationProperties();
    metadata.put(
        binding.userKey(), secretRef(taskName, "broker username", properties.getUsername(), context));
    metadata.put(
        binding.passwordKey(),
        secretRef(taskName, "broker password", properties.getPassword(), context));
  }

  private static AuthenticationPolicyUnion resolvePolicy(
      String taskName, ReferenceableAuthenticationPolicy reference, CompileContext context) {
    AuthenticationPolicyUnion policy;
    if (reference.getAuthenticationPolicyReference() != null) {
      String name = reference.getAuthenticationPolicyReference().getUse();
      policy = context.authentications().get(name);
      if (policy == null) {
        throw invalid(taskName, "authentication policy '" + name + "' is not declared");
      }
    } else {
      policy = reference.getAuthenticationPolicy();
    }
    if (policy == null) {
      throw invalid(taskName, "authentication policy is empty or unrecognized");
    }
    return policy;
  }

  private static BindingType bindingTypeFor(String taskName, String protocol) {
    String p = protocol == null ? "" : protocol.toLowerCase(Locale.ROOT);
    return switch (p) {
      case "kafka" ->
          new BindingType("bindings.kafka", "brokers", "publishTopic", "saslUsername", "saslPassword");
      case "amqp" -> new BindingType("bindings.rabbitmq", "host", "queueName", null, null);
      case "mqtt", "mqtt5" -> new BindingType("bindings.mqtt3", "url", "topic", "username", "password");
      case "sqs" -> new BindingType("bindings.aws.sqs", null, "queueName", "accessKey", "secretKey");
      case "googlepubsub" -> new BindingType("bindings.gcp.pubsub", null, "topic", null, null);
      default ->
          throw invalid(
              taskName,
              "AsyncAPI server protocol '"
                  + protocol
                  + "' has no supported Dapr binding (supported: kafka, amqp, mqtt, mqtt5, sqs,"
                  + " googlepubsub)");
    };
  }

  private static AsyncApiServer firstAsyncApiServer(String taskName, JsonNode api) {
    JsonNode servers = api.get("servers");
    if (servers == null || !servers.isObject() || servers.isEmpty()) {
      throw invalid(taskName, "AsyncAPI document must declare at least one server");
    }
    JsonNode server = servers.properties().iterator().next().getValue();
    String protocol = textValue(server.get("protocol"));
    if (isBlank(protocol)) {
      throw invalid(taskName, "AsyncAPI server must declare a protocol");
    }
    return new AsyncApiServer(protocol, textValue(server.get("host")));
  }

  private static String asyncApiChannelAddress(String taskName, JsonNode api, String operationId) {
    JsonNode operations = api.get("operations");
    JsonNode operation = operations == null ? null : operations.get(operationId);
    if (operation == null || !operation.isObject()) {
      throw invalid(taskName, "AsyncAPI operation '" + operationId + "' was not found");
    }
    String action = textValue(operation.get("action"));
    if (!"send".equals(action)) {
      throw invalid(taskName, "AsyncAPI operation '" + operationId + "' must have action 'send'");
    }
    JsonNode channelRef = operation.get("channel");
    String ref = channelRef == null ? null : textValue(channelRef.get("$ref"));
    JsonNode channel = resolveAsyncApiRef(taskName, api, ref);
    String address = textValue(channel.get("address"));
    if (isBlank(address)) {
      throw invalid(taskName, "AsyncAPI channel for operation '" + operationId + "' has no address");
    }
    return address;
  }

  private static JsonNode resolveAsyncApiRef(String taskName, JsonNode api, String ref) {
    if (ref == null || !ref.startsWith("#/")) {
      throw invalid(taskName, "AsyncAPI operation channel must use an internal $ref");
    }
    JsonNode node = api;
    for (String segment : ref.substring(2).split("/")) {
      String token = segment.replace("~1", "/").replace("~0", "~");
      node = node == null ? null : node.get(token);
    }
    if (node == null || !node.isObject()) {
      throw invalid(taskName, "AsyncAPI reference '" + ref + "' does not resolve");
    }
    return node;
  }

  private static JsonNode parseAsyncApiDocument(String taskName, byte[] document) {
    try {
      String text = new String(document, StandardCharsets.UTF_8);
      return detectFormat(text).mapper().readTree(document);
    } catch (Exception e) {
      throw invalid(taskName, "asyncapi call requires a parseable AsyncAPI document");
    }
  }

  private record AsyncApiServer(String protocol, String host) {}

  private record BindingType(
      String daprType,
      String hostKey,
      String destinationKey,
      String userKey,
      String passwordKey) {}

  private StepService grpcStep(String taskName, CallGRPC call, CompileContext context) {
    GRPCArguments with = call.getWith();
    if (with == null) {
      throw invalid(taskName, "grpc call requires 'with' arguments");
    }
    WithGRPCService service = with.getService();
    if (service == null || isBlank(service.getHost()) || service.getPort() <= 0) {
      throw invalid(taskName, "grpc call requires 'with.service' with a host and a positive port");
    }
    if (isBlank(service.getName())) {
      throw invalid(taskName, "grpc call requires 'with.service.name' (the fully-qualified service)");
    }
    if (isBlank(with.getMethod())) {
      throw invalid(taskName, "grpc call requires 'with.method'");
    }

    Map<String, EnvValue> env = new LinkedHashMap<>();
    env.put("SERVICE_ADDR", new EnvValue.Literal(service.getHost() + ":" + service.getPort()));
    // The runner's METHOD is the fully-qualified service plus method name; the DSL splits them
    // across service.name and method. SERVICE_ADDR is the host:port, distinct from the descriptor.
    env.put("METHOD", new EnvValue.Literal(service.getName() + "/" + with.getMethod()));

    // Descriptor source: pin the fetched FileDescriptorSet by content hash for the runner to
    // verify at boot, mirroring openApiStep's DOCUMENT_SHA256. When absent, the runner falls back
    // to server reflection.
    if (with.getProto() != null) {
      String protoUrl = resolveEndpoint(with.getProto().getEndpoint());
      putIfPresent(env, "PROTO_ENDPOINT", protoUrl);
      if (protoUrl != null && !protoUrl.isBlank()) {
        byte[] descriptor = documentFetcher.fetch(protoUrl);
        putIfPresent(env, "PROTO_SHA256", SpecDigest.sha256Hex(descriptor));
      }
    }

    applyAuth(env, resolveGrpcAuth(taskName, service, context));

    if (call.getTimeout() != null) {
      putIfPresent(env, "TIMEOUT", toJson(call.getTimeout()));
    }

    return new StepService(Names.kebab(taskName), TaskKind.CALL_GRPC, images.callGrpc(), env);
  }
  private StepService runStep(String taskName, RunTask run) {
    RunTaskConfigurationUnion cfg = run.getRun();
    if (cfg == null) {
      throw new CompilationException(
          List.of("task '" + taskName + "': run task has no configuration"));
    }

    if (cfg.getRunShell() != null) {
      RunShell runShell = cfg.getRunShell();
      Shell shell = runShell.getShell();
      Map<String, EnvValue> env = new LinkedHashMap<>();
      putIfPresent(env, "COMMAND", shell.getCommand());
      if (shell.getArguments() != null) {
        putIfPresent(
            env, "ARGUMENTS", toOrderedJson(shell.getArguments().getAdditionalProperties()));
      }
      if (shell.getEnvironment() != null) {
        putIfPresent(
            env, "ENVIRONMENT", toOrderedJson(shell.getEnvironment().getAdditionalProperties()));
      }
      env.put("RETURN", new EnvValue.Literal(returnValue(runShell)));
      return new StepService(Names.kebab(taskName), TaskKind.RUN_SHELL, images.runShell(), env);
    }

    if (cfg.getRunScript() != null) {
      return scriptStep(taskName, cfg.getRunScript());
    }

    if (cfg.getRunContainer() != null) {
      throw new CompilationException(
          List.of("task '" + taskName + "': run: container is not yet supported"));
    }

    if (cfg.getRunWorkflow() != null) {
      throw new CompilationException(
          List.of("task '" + taskName + "': run: workflow is not yet supported"));
    }

    throw new CompilationException(
        List.of("task '" + taskName + "': unrecognized run configuration"));
  }

  private StepService scriptStep(String taskName, RunScript runScript) {
    ScriptUnion union = runScript.getScript();
    if (union == null) {
      throw new CompilationException(
          List.of("task '" + taskName + "': run.script has no configuration"));
    }
    if (union.getExternalScript() != null) {
      throw new CompilationException(
          List.of(
              "task '"
                  + taskName
                  + "': run.script external script sources are not supported; use inline 'code'"));
    }
    InlineScript inline = union.getInlineScript();
    if (inline == null) {
      throw new CompilationException(
          List.of("task '" + taskName + "': run.script requires inline 'code'"));
    }

    String language = inline.getLanguage() == null ? "" : inline.getLanguage().toLowerCase();
    TaskKind kind;
    String image;
    switch (language) {
      case "js" -> {
        kind = TaskKind.RUN_SCRIPT_JS;
        image = images.runScriptJs();
      }
      case "python" -> {
        kind = TaskKind.RUN_SCRIPT_PYTHON;
        image = images.runScriptPython();
      }
      default ->
          throw new CompilationException(
              List.of(
                  "task '"
                      + taskName
                      + "': run.script language '"
                      + inline.getLanguage()
                      + "' is not supported; use 'js' or 'python'"));
    }

    Map<String, EnvValue> env = new LinkedHashMap<>();
    putIfPresent(env, "SCRIPT", inline.getCode());
    if (inline.getArguments() != null) {
      Map<String, Object> args = inline.getArguments().getAdditionalProperties();
      args.keySet().forEach(name -> requireIdentifier(taskName, name, language));
      putIfPresent(env, "ARGUMENTS", toOrderedJson(args));
    }
    if (inline.getEnvironment() != null) {
      putIfPresent(
          env, "ENVIRONMENT", toOrderedJson(inline.getEnvironment().getAdditionalProperties()));
    }
    env.put("RETURN", new EnvValue.Literal(returnValue(runScript)));

    return new StepService(Names.kebab(taskName), kind, image, env);
  }

  // Identifiers the generated prelude in dws-run itself declares (see
  // dws-run/internal/runner/arguments.go's reservedInternalNames). An argument sharing one of
  // these names would redeclare it -- a SyntaxError in both target languages -- so these are
  // rejected regardless of language. Keep this set in sync with dws-run.
  private static final Set<String> RESERVED_INTERNAL_NAMES =
      Set.of("__dwsArgs", "__dws_args", "__dws_json", "__dws_os");

  // ECMAScript keywords and reserved words. Binding one as `const <word> = ...;` is a
  // SyntaxError, so these are rejected for language "js". Mirrors
  // dws-run/internal/runner/arguments.go's jsReservedWords -- keep the two in sync.
  private static final Set<String> JS_RESERVED_WORDS =
      Set.of(
          "break",
          "case",
          "catch",
          "class",
          "const",
          "continue",
          "debugger",
          "default",
          "delete",
          "do",
          "else",
          "enum",
          "export",
          "extends",
          "false",
          "finally",
          "for",
          "function",
          "if",
          "implements",
          "import",
          "in",
          "instanceof",
          "interface",
          "let",
          "new",
          "null",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "static",
          "super",
          "switch",
          "this",
          "throw",
          "true",
          "try",
          "typeof",
          "var",
          "void",
          "while",
          "with",
          "yield",
          "await");

  // Python's reserved keywords (keyword.kwlist). Binding one as `<word> = ...` is a SyntaxError,
  // so these are rejected for language "python". Mirrors
  // dws-run/internal/runner/arguments.go's pythonReservedWords -- keep the two in sync.
  private static final Set<String> PYTHON_RESERVED_WORDS =
      Set.of(
          "False",
          "None",
          "True",
          "and",
          "as",
          "assert",
          "async",
          "await",
          "break",
          "class",
          "continue",
          "def",
          "del",
          "elif",
          "else",
          "except",
          "finally",
          "for",
          "from",
          "global",
          "if",
          "import",
          "in",
          "is",
          "lambda",
          "nonlocal",
          "not",
          "or",
          "pass",
          "raise",
          "return",
          "try",
          "while",
          "with",
          "yield");

  /**
   * Script arguments become in-scope variables in the generated prelude, so a name that is a valid
   * map key but not a valid bindable identifier for the target language would produce a syntax
   * error inside a deployed container. Reject it at compile time instead: names that aren't
   * JS/Python identifiers at all, names that collide with the prelude's own internal variables, and
   * names that are reserved keywords in the target language (a name invalid in one script language
   * may be perfectly valid in the other -- {@code def} is a Python keyword but a fine JS
   * identifier; {@code const} is the reverse).
   */
  private static void requireIdentifier(String taskName, String name, String language) {
    if (name == null || name.isEmpty() || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new CompilationException(
          List.of(
              "task '" + taskName + "': argument name '" + name + "' is not a valid identifier"));
    }
    if (RESERVED_INTERNAL_NAMES.contains(name)) {
      throw new CompilationException(
          List.of(
              "task '"
                  + taskName
                  + "': argument name '"
                  + name
                  + "' collides with an identifier the generated prelude uses internally"));
    }
    boolean reserved =
        switch (language) {
          case "js" -> JS_RESERVED_WORDS.contains(name);
          case "python" -> PYTHON_RESERVED_WORDS.contains(name);
          default -> false;
        };
    if (reserved) {
      throw new CompilationException(
          List.of(
              "task '"
                  + taskName
                  + "': argument name '"
                  + name
                  + "' is a reserved "
                  + (language.equals("js") ? "JavaScript" : "Python")
                  + " keyword"));
    }
  }

  /**
   * Resolves the DSL's process return type, defaulting to {@code stdout} so the deployed step's
   * behavior is explicit in its manifest rather than dependent on an image default.
   */
  private static String returnValue(RunTaskConfiguration cfg) {
    return cfg.getReturn() != null
        ? cfg.getReturn().value()
        : RunTaskConfiguration.ProcessReturnType.STDOUT.value();
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

  // ---- secret / authentication resolution --------------------------------

  private static Set<String> declaredSecrets(Workflow workflow) {
    if (workflow.getUse() == null || workflow.getUse().getSecrets() == null) {
      return Set.of();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(workflow.getUse().getSecrets()));
  }

  private static Map<String, AuthenticationPolicyUnion> declaredAuthentications(Workflow workflow) {
    if (workflow.getUse() == null || workflow.getUse().getAuthentications() == null) {
      return Map.of();
    }
    return Collections.unmodifiableMap(
        new LinkedHashMap<>(workflow.getUse().getAuthentications().getAdditionalProperties()));
  }

  private ResolvedAuth resolveAuth(
      String taskName, Endpoint endpoint, String endpointUrl, CompileContext context) {
    ReferenceableAuthenticationPolicy reference = endpointAuthentication(endpoint);
    if (reference == null) {
      return ResolvedAuth.NONE;
    }
    return resolveAuth(taskName, reference, () -> endpointUrl, context);
  }

  private ResolvedAuth resolveAuth(
      String taskName,
      ReferenceableAuthenticationPolicy reference,
      Supplier<String> oauthEndpoint,
      CompileContext context) {
    return resolveAuthPolicy(taskName, oauthEndpoint, policyOf(taskName, reference, context), context);
  }

  /** Resolves an inline or named authentication policy reference to its policy union. */
  private AuthenticationPolicyUnion policyOf(
      String taskName, ReferenceableAuthenticationPolicy reference, CompileContext context) {
    AuthenticationPolicyUnion policy;
    if (reference.getAuthenticationPolicyReference() != null) {
      String name = reference.getAuthenticationPolicyReference().getUse();
      policy = context.authentications().get(name);
      if (policy == null) {
        throw invalid(taskName, "authentication policy '" + name + "' is not declared");
      }
    } else {
      policy = reference.getAuthenticationPolicy();
    }
    if (policy == null) {
      throw invalid(taskName, "authentication policy is empty or unrecognized");
    }
    return policy;
  }

  /**
   * Resolves a gRPC service's authentication to a runner env contract, supporting basic and bearer
   * only. oauth2 is rejected: Dapr has no gRPC-invocation OAuth2 middleware equivalent (unlike the
   * HTTP sidecar path used by call: http / call: openapi), and runner-managed OAuth2 is out of
   * scope.
   */
  private ResolvedAuth resolveGrpcAuth(
      String taskName, WithGRPCService service, CompileContext context) {
    ReferenceableAuthenticationPolicy reference =
        service == null ? null : service.getAuthentication();
    if (reference == null) {
      return ResolvedAuth.NONE;
    }
    AuthenticationPolicyUnion policy = policyOf(taskName, reference, context);
    if (policy.getOAuth2AuthenticationPolicy() != null) {
      throw invalid(taskName, "oauth2 authentication is not supported for gRPC calls");
    }
    return resolveAuthPolicy(
        taskName,
        () -> {
          throw invalid(taskName, "oauth2 authentication is not supported for gRPC calls");
        },
        policy,
        context);
  }

  private ResolvedAuth resolveAuthPolicy(
      String taskName,
      Supplier<String> oauthEndpoint,
      AuthenticationPolicyUnion policy,
      CompileContext context) {
    BasicAuthenticationPolicy basic = policy.getBasicAuthenticationPolicy();
    if (basic != null) {
      if (basic.getBasic() == null || basic.getBasic().getBasicAuthenticationProperties() == null) {
        throw invalid(
            taskName,
            "basic authentication must declare username and password as scalar secret references");
      }
      BasicAuthenticationProperties properties =
          basic.getBasic().getBasicAuthenticationProperties();
      return new ResolvedAuth(
          AuthScheme.BASIC,
          Map.of(
              "AUTH_USERNAME",
              secretRef(taskName, "basic username", properties.getUsername(), context),
              "AUTH_PASSWORD",
              secretRef(taskName, "basic password", properties.getPassword(), context)),
          Optional.empty());
    }

    BearerAuthenticationPolicy bearer = policy.getBearerAuthenticationPolicy();
    if (bearer != null) {
      if (bearer.getBearer() == null
          || bearer.getBearer().getBearerAuthenticationProperties() == null) {
        throw invalid(
            taskName, "bearer authentication must declare a token as a scalar secret reference");
      }
      BearerAuthenticationProperties properties =
          bearer.getBearer().getBearerAuthenticationProperties();
      return new ResolvedAuth(
          AuthScheme.BEARER,
          Map.of("AUTH_TOKEN", secretRef(taskName, "bearer token", properties.getToken(), context)),
          Optional.empty());
    }

    OAuth2AuthenticationPolicy oauth = policy.getOAuth2AuthenticationPolicy();
    if (oauth != null) {
      if (oauth.getOauth2() == null
          || oauth.getOauth2().getOAuth2ConnectAuthenticationProperties() == null) {
        throw invalid(
            taskName, "oauth2 authentication must use an inline client_credentials configuration");
      }
      OAuth2ConnectAuthenticationProperties properties =
          oauth.getOauth2().getOAuth2ConnectAuthenticationProperties();
      if (properties.getGrant()
          != OAuth2AuthenticationData.OAuth2AuthenticationDataGrant.CLIENT_CREDENTIALS) {
        throw invalid(taskName, "oauth2 authentication supports only the client_credentials grant");
      }
      OAuth2AuthenticationDataClient client = properties.getClient();
      if (client == null) {
        throw invalid(taskName, "oauth2 client_credentials authentication requires a client");
      }
      if (!isBlank(client.getAssertion())) {
        throw invalid(
            taskName, "oauth2 client assertions are not supported for client_credentials");
      }
      String clientAuthentication =
          client.getAuthentication() == null
              ? OAuth2AuthenticationDataClient.ClientAuthentication.CLIENT_SECRET_POST.value()
              : client.getAuthentication().value();
      List<String> scopes = properties.getScopes();
      if (scopes == null || scopes.isEmpty()) {
        throw invalid(
            taskName, "oauth2 client_credentials authentication requires at least one scope");
      }
      if (scopes.stream().anyMatch(scope -> scope == null || scope.isBlank())) {
        throw invalid(taskName, "oauth2 scopes must not contain blank entries");
      }
      List<String> normalizedScopes = scopes.stream().map(String::strip).toList();
      OAuthMiddleware middleware =
          new OAuthMiddleware(
              oauthTokenUrl(taskName, properties),
              secretRef(taskName, "oauth2 client id", client.getId(), context),
              secretRef(taskName, "oauth2 client secret", client.getSecret(), context),
              clientAuthentication,
              normalizedScopes);
      String endpointName = context.registerOAuth(taskName, oauthEndpoint.get(), middleware);
      return new ResolvedAuth(AuthScheme.OAUTH2, Map.of(), Optional.of(endpointName));
    }

    throw invalid(taskName, "authentication type is unsupported; use basic, bearer, or oauth2");
  }

  private static EnvValue.SecretKeyRef secretRef(
      String taskName, String field, String expression, CompileContext context) {
    Matcher matcher =
        expression == null ? SECRET_REFERENCE.matcher("") : SECRET_REFERENCE.matcher(expression);
    if (!matcher.matches()) {
      throw invalid(
          taskName,
          field
              + " credential must reference a declared secret as ${ $secrets.NAME } or ${ "
              + "$secrets[\"dns-name\"] }");
    }
    String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    if (!context.secrets().contains(name)) {
      throw invalid(taskName, field + " references secret '" + name + "' which is not declared");
    }
    return new EnvValue.SecretKeyRef(name, SECRET_KEY);
  }

  private static String oauthTokenUrl(
      String taskName, OAuth2ConnectAuthenticationProperties properties) {
    String authority = uriString(properties.getAuthority());
    if (isBlank(authority)) {
      throw invalid(taskName, "oauth2 client_credentials authentication requires an authority");
    }
    URI authorityUri = absoluteUri(taskName, authority, "oauth2 authority");
    String tokenPath =
        properties.getEndpoints() == null ? "/oauth2/token" : properties.getEndpoints().getToken();
    if (isBlank(tokenPath)) {
      throw invalid(taskName, "oauth2 token endpoint must not be empty");
    }
    return authorityUri.resolve(tokenPath).normalize().toString();
  }

  private static void applyAuth(Map<String, EnvValue> env, ResolvedAuth auth) {
    if (auth.scheme() == AuthScheme.NONE) {
      return;
    }
    env.put("AUTH_SCHEME", new EnvValue.Literal(auth.scheme().value));
    env.putAll(auth.credentials());
    auth.oauthEndpoint().ifPresent(name -> env.put("OAUTH_ENDPOINT", new EnvValue.Literal(name)));
  }

  private static CompilationException invalid(String taskName, String message) {
    return new CompilationException(List.of("task '" + taskName + "': " + message));
  }

  private static ReferenceableAuthenticationPolicy endpointAuthentication(Endpoint endpoint) {
    EndpointConfiguration configuration =
        endpoint == null ? null : endpoint.getEndpointConfiguration();
    return configuration == null ? null : configuration.getAuthentication();
  }

  private static String effectiveOpenApiOAuthEndpoint(
      String taskName, String documentUrl, byte[] document, String operationId) {
    if (isBlank(documentUrl) || document == null) {
      throw invalid(taskName, "OpenAPI OAuth requires a static document URI");
    }

    JsonNode api = parseOpenApiDocument(taskName, document);
    OpenApiOperation operation = findOpenApiOperation(taskName, api, operationId);
    JsonNode server =
        firstServer(operation.operation().get("servers"))
            .or(() -> firstServer(operation.pathItem().get("servers")))
            .or(() -> firstServer(api.get("servers")))
            .orElse(null);
    String serverUrl = server == null ? "/" : textValue(server.get("url"));
    if (isBlank(serverUrl)) {
      throw invalid(taskName, "OpenAPI OAuth server must declare a usable URL");
    }

    String expandedServer = expandOpenApiServer(taskName, serverUrl, server);
    URI serverUri;
    try {
      serverUri = URI.create(expandedServer);
    } catch (IllegalArgumentException e) {
      throw invalid(taskName, "OpenAPI OAuth server URL is invalid");
    }

    URI effective = serverUri;
    if (!serverUri.isAbsolute()) {
      URI documentUri = httpUri(documentUrl);
      if (documentUri == null) {
        throw invalid(
            taskName, "OpenAPI OAuth relative server requires an HTTP(S) document URL as its base");
      }
      effective = documentUri.resolve(serverUri).normalize();
    }

    if (!isHttp(effective)
        || effective.getRawAuthority() == null
        || effective.getUserInfo() != null) {
      throw invalid(taskName, "OpenAPI OAuth server must be an HTTP(S) URL without user info");
    }
    return effective.toString();
  }

  private static JsonNode parseOpenApiDocument(String taskName, byte[] document) {
    try {
      String text = new String(document, StandardCharsets.UTF_8);
      return detectFormat(text).mapper().readTree(document);
    } catch (Exception e) {
      throw invalid(taskName, "OpenAPI OAuth requires a parseable OpenAPI document");
    }
  }

  private static OpenApiOperation findOpenApiOperation(
      String taskName, JsonNode api, String operationId) {
    JsonNode paths = api.get("paths");
    if (isBlank(operationId) || paths == null || !paths.isObject()) {
      throw invalid(taskName, "OpenAPI OAuth operationId was not found in the document");
    }
    for (var path : paths.properties()) {
      JsonNode pathItem = path.getValue();
      if (!pathItem.isObject()) {
        continue;
      }
      for (var field : pathItem.properties()) {
        JsonNode candidate = field.getValue();
        if (HTTP_METHODS.contains(field.getKey().toLowerCase(Locale.ROOT))
            && candidate.isObject()
            && operationId.equals(textValue(candidate.get("operationId")))) {
          return new OpenApiOperation(pathItem, candidate);
        }
      }
    }
    throw invalid(taskName, "OpenAPI OAuth operationId '" + operationId + "' was not found");
  }

  private static Optional<JsonNode> firstServer(JsonNode servers) {
    if (servers == null || !servers.isArray() || servers.isEmpty() || !servers.get(0).isObject()) {
      return Optional.empty();
    }
    return Optional.of(servers.get(0));
  }

  private static String expandOpenApiServer(String taskName, String serverUrl, JsonNode server) {
    JsonNode variables = server == null ? null : server.get("variables");
    Matcher matcher = OPENAPI_SERVER_VARIABLE.matcher(serverUrl);
    StringBuffer expanded = new StringBuffer();
    while (matcher.find()) {
      JsonNode definition = variables == null ? null : variables.get(matcher.group(1));
      String value = definition == null ? null : textValue(definition.get("default"));
      if (value == null) {
        throw invalid(
            taskName,
            "OpenAPI OAuth server URL has unresolved variable '" + matcher.group(1) + "'");
      }
      matcher.appendReplacement(expanded, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(expanded);
    return expanded.toString();
  }

  private static String textValue(JsonNode node) {
    return node != null && node.isTextual() ? node.textValue() : null;
  }

  private static URI httpUri(String value) {
    try {
      URI uri = URI.create(value);
      return isHttp(uri) && uri.getRawAuthority() != null ? uri : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean isHttp(URI uri) {
    return uri.getScheme() != null
        && (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"));
  }

  private record OpenApiOperation(JsonNode pathItem, JsonNode operation) {}

  private enum AuthScheme {
    NONE("none"),
    BASIC("basic"),
    BEARER("bearer"),
    OAUTH2("oauth2");

    private final String value;

    AuthScheme(String value) {
      this.value = value;
    }
  }

  private record ResolvedAuth(
      AuthScheme scheme,
      Map<String, EnvValue.SecretKeyRef> credentials,
      Optional<String> oauthEndpoint) {

    private static final ResolvedAuth NONE =
        new ResolvedAuth(AuthScheme.NONE, Map.of(), Optional.empty());

    private ResolvedAuth {
      credentials = Map.copyOf(credentials);
    }
  }

  private record OAuthKey(String baseUrl, OAuthMiddleware middleware) {}

  private static final class OAuthAccumulator {

    private final String name;
    private final String baseUrl;
    private final OAuthMiddleware middleware;
    private final Set<String> paths = new LinkedHashSet<>();
    private final Set<String> appIds = new LinkedHashSet<>();

    private OAuthAccumulator(String name, String baseUrl, OAuthMiddleware middleware) {
      this.name = name;
      this.baseUrl = baseUrl;
      this.middleware = middleware;
    }

    private OAuthEndpoint descriptor() {
      return new OAuthEndpoint(name, baseUrl, paths, appIds, middleware);
    }
  }

  private static final class CompileContext {

    private final String workflow;
    private final String versionId;
    private final Set<String> secrets;
    private final Map<String, AuthenticationPolicyUnion> authentications;
    private final Map<OAuthKey, OAuthAccumulator> oauth = new LinkedHashMap<>();
    private final List<BindingComponent> bindingComponents = new ArrayList<>();

    private CompileContext(
        String workflow,
        String versionId,
        Set<String> secrets,
        Map<String, AuthenticationPolicyUnion> authentications) {
      this.workflow = workflow;
      this.versionId = versionId;
      this.secrets = secrets;
      this.authentications = authentications;
    }

    private Set<String> secrets() {
      return secrets;
    }

    private Map<String, AuthenticationPolicyUnion> authentications() {
      return authentications;
    }

    private String registerOAuth(String taskName, String endpointUrl, OAuthMiddleware middleware) {
      EndpointTarget target = endpointTarget(taskName, endpointUrl);
      OAuthKey key = new OAuthKey(target.baseUrl(), middleware);
      OAuthAccumulator endpoint =
          oauth.computeIfAbsent(
              key,
              ignored ->
                  new OAuthAccumulator(
                      oauthName(workflow, versionId, key), key.baseUrl(), middleware));
      endpoint.paths.add(target.path());
      endpoint.appIds.add(Names.kebab(taskName));
      return endpoint.name;
    }

    private List<OAuthEndpoint> oauthEndpoints() {
      return oauth.values().stream().map(OAuthAccumulator::descriptor).toList();
    }

    /**
     * Registers one version-scoped Dapr binding Component for a {@code call: asyncapi} step and
     * returns its name (the step's {@code BINDING_NAME}). The name is content-addressed over the
     * binding type, requesting app-id, and metadata so re-posting an identical definition version
     * yields a stable resource name that updates in place and is label-GC'd like every other
     * version-scoped resource.
     */
    private String registerBinding(String taskName, String type, Map<String, EnvValue> metadata) {
      String appId = Names.kebab(taskName);
      String name = bindingName(workflow, versionId, type, appId, metadata);
      bindingComponents.add(new BindingComponent(name, type, metadata, appId));
      return name;
    }

    private List<BindingComponent> bindingComponents() {
      return List.copyOf(bindingComponents);
    }
  }

  private static String bindingName(
      String workflow, String versionId, String type, String appId, Map<String, EnvValue> metadata) {
    StringBuilder canonical = new StringBuilder(type).append('\n').append(appId);
    new TreeMap<>(metadata)
        .forEach((key, value) -> canonical.append('\n').append(key).append('=').append(describe(value)));
    String hash =
        SpecDigest.sha256Hex(canonical.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    return workflow + "-" + versionId + "-binding-" + hash;
  }

  private static String describe(EnvValue value) {
    if (value instanceof EnvValue.SecretKeyRef secret) {
      return "secret:" + secret.name() + "/" + secret.key();
    }
    return "lit:" + ((EnvValue.Literal) value).value();
  }

  private record EndpointTarget(String baseUrl, String path) {}

  private static EndpointTarget endpointTarget(String taskName, String endpointUrl) {
    if (isBlank(endpointUrl)) {
      throw invalid(taskName, "oauth2 authentication requires a static endpoint URI");
    }
    URI uri = absoluteUri(taskName, endpointUrl, "oauth2 endpoint");
    if (uri.getRawAuthority() == null || uri.getUserInfo() != null) {
      throw invalid(taskName, "oauth2 endpoint must identify an external host without user info");
    }
    String baseUrl =
        uri.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + uri.getRawAuthority().toLowerCase(Locale.ROOT);
    String path = isBlank(uri.getRawPath()) ? "/" : uri.getRawPath();
    return new EndpointTarget(baseUrl, path);
  }

  private static URI absoluteUri(String taskName, String value, String field) {
    try {
      URI uri = URI.create(value);
      if (!uri.isAbsolute()) {
        throw new IllegalArgumentException("relative");
      }
      return uri;
    } catch (IllegalArgumentException e) {
      throw invalid(taskName, field + " must be a static absolute URI");
    }
  }

  private static String oauthName(String workflow, String versionId, OAuthKey key) {
    OAuthMiddleware middleware = key.middleware();
    String canonical =
        String.join(
            "\n",
            key.baseUrl(),
            middleware.tokenUrl(),
            middleware.clientId().name(),
            middleware.clientSecret().name(),
            middleware.clientAuthentication(),
            String.join("\u001f", middleware.scopes()));
    String hash = SpecDigest.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
    return workflow + "-" + versionId + "-oauth-" + hash;
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
    if (endpoint.getEndpointConfiguration() != null
        && endpoint.getEndpointConfiguration().getUri() != null) {
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

  /**
   * Like {@link #toJson}, but preserves the source map's key order instead of sorting it. Used for
   * {@code run.shell}/{@code run.script} arguments and environment, whose document order is
   * observable by the deployed step (dws-run renders {@code --key value} pairs positionally).
   */
  private String toOrderedJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return orderedJson.writeValueAsString(value);
    } catch (Exception e) {
      return String.valueOf(value);
    }
  }

  private static void putIfPresent(Map<String, EnvValue> env, String key, String value) {
    if (value != null && !value.isBlank()) {
      env.put(key, new EnvValue.Literal(value));
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
