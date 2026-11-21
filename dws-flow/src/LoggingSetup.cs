using Microsoft.Extensions.Logging.Console;

namespace Dws.Flow;

/// <summary>Console format (<c>DWS_LOG_FORMAT</c>) and the framework log-level default shared by Program.cs and its tests.</summary>
public static class LoggingSetup
{
    // The ASP.NET Core category whose request lifecycle (health probes) is quiet by default.
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
        // An empty value (Logging__LogLevel__Microsoft.AspNetCore=) is no level at all; .NET ignores it, so it keeps the default.
        if (string.IsNullOrWhiteSpace(configuration[FrameworkLevelKey]))
        {
            logging.AddFilter(FrameworkCategory, LogLevel.Warning);
        }
    }
}
