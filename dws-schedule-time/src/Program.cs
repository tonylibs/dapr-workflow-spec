using Dapr.Client;
using Dapr.Jobs.Extensions;
using Dws.ScheduleTime;
using Microsoft.Extensions.Logging.Console;

const string NamespaceFile = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";
string? podNamespace = Environment.GetEnvironmentVariable("POD_NAMESPACE");
if (string.IsNullOrWhiteSpace(podNamespace) && File.Exists(NamespaceFile))
{
    podNamespace = File.ReadAllText(NamespaceFile);
}

ScheduleTimeOptions options = ScheduleTimeOptions.From(
    Environment.GetEnvironmentVariable("DWS_SCHEDULE_TIME_STATE_STORE"),
    Environment.GetEnvironmentVariable("DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL"),
    Environment.GetEnvironmentVariable("DWS_SCHEDULE_TIME_CLAIM_TTL"),
    podNamespace);

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
if (DwsJsonConsoleFormatter.IsJsonMode(Environment.GetEnvironmentVariable("DWS_LOG_FORMAT")))
{
    builder.Logging.AddConsole(consoleOptions => consoleOptions.FormatterName = DwsJsonConsoleFormatter.FormatterName);
    builder.Logging.AddConsoleFormatter<DwsJsonConsoleFormatter, ConsoleFormatterOptions>();
}

// DaprClientBuilder reads DAPR_API_TOKEN and the sidecar endpoints itself.
builder.Services.AddSingleton(_ => new DaprClientBuilder().Build());
builder.Services.AddDaprJobsClient();
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddSingleton(options);
builder.Services.AddSingleton<ClusterSnapshot>();
builder.Services.AddSingleton<IClusterState>(services => services.GetRequiredService<ClusterSnapshot>());
builder.Services.AddSingleton<ChangeSignal>();
builder.Services.AddSingleton<IJobRegistry, DaprJobRegistry>();
builder.Services.AddSingleton<IVersionedStore>(services => new DaprVersionedStore(services.GetRequiredService<DaprClient>(), options.StateStore));
builder.Services.AddSingleton<IWorkflowStarter, OrchestratorInvokeStarter>();
builder.Services.AddSingleton(services => ActivatorUtilities.CreateInstance<StartClaim>(services, options.ClaimTtl));
builder.Services.AddSingleton(services => ActivatorUtilities.CreateInstance<AfterLifecycle>(services, options.AfterPollInterval));
builder.Services.AddSingleton(services => ActivatorUtilities.CreateInstance<Reconciler>(services, options.AfterPollInterval));
builder.Services.AddSingleton<ScheduledJobHandler>();
builder.Services.AddSingleton<JobDispatcher>();
builder.Services.AddHostedService(services => ActivatorUtilities.CreateInstance<ClusterWatcher>(services, options.Namespace ?? string.Empty));
builder.Services.AddHostedService<ReconcileLoop>();

WebApplication app = builder.Build();
app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));

// The job callback is mapped by hand rather than with the SDK's MapDaprScheduledJobHandler so the 200 (done or
// dropped) versus 500 (Dapr retries per the job's failure policy) decision is explicit here.
app.MapPost("/job/{name}", async (string name, HttpRequest request, JobDispatcher dispatcher, CancellationToken cancellationToken) =>
{
    using StreamReader reader = new(request.Body);
    string body = await reader.ReadToEndAsync(cancellationToken);
    JobOutcome outcome = await dispatcher.DispatchAsync(name, body, cancellationToken);
    return outcome == JobOutcome.Success ? Results.Ok() : Results.StatusCode(StatusCodes.Status500InternalServerError);
});

app.Run();
