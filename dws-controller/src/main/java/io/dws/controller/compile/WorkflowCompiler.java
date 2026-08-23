package io.dws.controller.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.dws.controller.model.DeploymentPlan;
import io.dws.controller.model.EnvValue;
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
import io.serverlessworkflow.api.types.ForTask;
import io.serverlessworkflow.api.types.ForkTask;
import io.serverlessworkflow.api.types.HTTPArguments;
import io.serverlessworkflow.api.types.InlineScript;
import io.serverlessworkflow.api.types.OpenAPIArguments;
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
import io.serverlessworkflow.api.types.Workflow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    List<StepService> steps = new ArrayList<>();
    List<TopicBinding> bindings = new ArrayList<>();
    walk(workflow.getDo(), steps, bindings);

    OrchestratorSpec orchestrator =
        new OrchestratorSpec(
            Names.orchestrator(w, versionId),
            images.orchestrator(),
            w,
            ORCHESTRATOR_PORT,
            1,
            Map.of(
                "DEFINITION_STORE", new EnvValue.Literal(defResource),
                "DEFINITION_KEY", new EnvValue.Literal(DEFINITION_KEY)));

    return new DeploymentPlan(
        w, versionId, version, defResource, specText, steps, bindings, orchestrator);
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

  private void walk(List<TaskItem> tasks, List<StepService> steps, List<TopicBinding> bindings) {
    if (tasks == null) {
      return;
    }
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
      } else if (task.getTryTask() != null) {
        // A try task deploys nothing itself, but the tasks nested in its try/catch.do lists are
        // ordinary tasks and need their own step services — the orchestrator invokes them by the
        // same kebab-cased app-id it uses for a top-level task.
        TryTask tryTask = task.getTryTask();
        walk(tryTask.getTry(), steps, bindings);
        if (tryTask.getCatch() != null) {
          walk(tryTask.getCatch().getDo(), steps, bindings);
        }
      } else if (task.getForkTask() != null) {
        // Same reasoning as try: a fork task deploys nothing itself, but each branch's task is
        // dispatched exactly like a top-level task, so it needs its own step service/binding.
        walk(task.getForkTask().getFork().getBranches(), steps, bindings);
      } else if (task.getForTask() != null) {
        // Same reasoning again: the body of for.do is dispatched once per iteration exactly like
        // a top-level task list.
        walk(task.getForTask().getDo(), steps, bindings);
      }
      // switch/set/wait/raise deploy nothing themselves; their nested container types (try, fork,
      // for) are all walked above.
    }
  }

  private StepService httpStep(String taskName, CallHTTP call) {
    HTTPArguments with = call.getWith();
    Map<String, EnvValue> env = new LinkedHashMap<>();
    putIfPresent(env, "METHOD", with.getMethod());
    putIfPresent(env, "ENDPOINT", resolveEndpoint(with.getEndpoint()));
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

  private StepService openApiStep(String taskName, CallOpenAPI call) {
    OpenAPIArguments with = call.getWith();
    Map<String, EnvValue> env = new LinkedHashMap<>();
    String documentUrl =
        with.getDocument() != null ? resolveEndpoint(with.getDocument().getEndpoint()) : null;
    putIfPresent(env, "DOCUMENT_URL", documentUrl);
    if (documentUrl != null) {
      putIfPresent(
          env, "DOCUMENT_SHA256", SpecDigest.sha256Hex(documentFetcher.fetch(documentUrl)));
    }
    putIfPresent(env, "OPERATION_ID", with.getOperationId());
    if (with.getParameters() != null) {
      putIfPresent(env, "PARAMETERS", toJson(with.getParameters().getAdditionalProperties()));
    }
    return new StepService(Names.kebab(taskName), TaskKind.CALL_OPENAPI, images.callOpenapi(), env);
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
