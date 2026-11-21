using Microsoft.Extensions.Logging.Console;

namespace Dws.ScheduleTime;

/// <summary>Console format (<c>DWS_LOG_FORMAT</c>) and the framework log-level default shared by Program.cs and its tests.</summary>
public static class LoggingSetup
{
    /// <summary>The ASP.NET Core category whose request lifecycle (health probes, job callbacks) is quiet by default.</summary>
    public const string FrameworkCategory = "Microsoft.AspNetCore";

    private const string FrameworkLevelKey = "Logging:LogLevel:" + FrameworkCategory;

    public static void Configure(ILoggingBuilder logging, IConfiguration configuration, string? logFormat)
    {
        if (DwsJsonConsoleFormatter.IsJsonMode(logFormat))
        {
            logging.AddConsole(consoleOptions => consoleOptions.FormatterName = DwsJsonConsoleFormatter.FormatterName);
            logging.AddConsoleFormatter<DwsJsonConsoleFormatter, ConsoleFormatterOptions>();
        }

        // A code rule registered after the configuration rules wins a tie, so adding it unconditionally would
        // override Logging__LogLevel__Microsoft.AspNetCore. Provider-specific keys already beat this provider-less rule.
        if (configuration[FrameworkLevelKey] is null)
        {
            logging.AddFilter(FrameworkCategory, LogLevel.Warning);
        }
    }
}
