using Dws.ScheduleTime;
using Microsoft.Extensions.Logging.Console;

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
if (DwsJsonConsoleFormatter.IsJsonMode(Environment.GetEnvironmentVariable("DWS_LOG_FORMAT")))
{
    builder.Logging.AddConsole(options => options.FormatterName = DwsJsonConsoleFormatter.FormatterName);
    builder.Logging.AddConsoleFormatter<DwsJsonConsoleFormatter, ConsoleFormatterOptions>();
}

WebApplication app = builder.Build();
app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));
app.Run();
