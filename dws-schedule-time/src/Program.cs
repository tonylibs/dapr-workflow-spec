using Dapr.Client;
using Dws.ScheduleTime;
using Microsoft.Extensions.Logging.Console;

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
if (DwsJsonConsoleFormatter.IsJsonMode(Environment.GetEnvironmentVariable("DWS_LOG_FORMAT")))
{
    builder.Logging.AddConsole(options => options.FormatterName = DwsJsonConsoleFormatter.FormatterName);
    builder.Logging.AddConsoleFormatter<DwsJsonConsoleFormatter, ConsoleFormatterOptions>();
}

// Starts go through Dapr service invocation of the orchestrator (ADR 0013, option b). The invoke client
// reads the sidecar endpoint and DAPR_API_TOKEN through the Dapr SDK's own defaults.
builder.Services.AddSingleton<IWorkflowStarter>(new OrchestratorInvokeStarter(DaprClient.CreateInvokeHttpClient()));

WebApplication app = builder.Build();
app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));
app.Run();
