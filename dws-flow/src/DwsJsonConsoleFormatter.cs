using System.Diagnostics;
using System.Globalization;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Logging.Console;

namespace Dws.Flow;

/// <summary>Writes each log record as one JSON line following docs/log-format.md, selected by <c>DWS_LOG_FORMAT=json</c>.</summary>
public sealed class DwsJsonConsoleFormatter : ConsoleFormatter
{
    public const string FormatterName = "dws-json";

    public const string RedactedPlaceholder = "[REDACTED]";

    private static readonly TimeSpan ScrubTimeout = TimeSpan.FromMilliseconds(500);

    private static readonly Regex CredentialKeyValue = new(
        """([\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\w.-]*)("?\s*[:=]\s*"?)(?:(?:bearer|basic)\s+)?[^\s"',;&}]+""",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant,
        ScrubTimeout);

    private static readonly Regex BareCredential = new(
        """\b(bearer|basic)\s+[A-Za-z0-9._~+/-]{8,}=*""",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant,
        ScrubTimeout);

    private static readonly JsonWriterOptions WriterOptions = new() { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping };

    public DwsJsonConsoleFormatter()
        : base(FormatterName)
    {
    }

    /// <summary>True when the <c>DWS_LOG_FORMAT</c> value (trimmed, case-insensitive) selects JSON mode; anything else keeps text mode.</summary>
    public static bool IsJsonMode(string? value) => string.Equals(value?.Trim(), "json", StringComparison.OrdinalIgnoreCase);

    public override void Write<TState>(in LogEntry<TState> logEntry, IExternalScopeProvider? scopeProvider, TextWriter textWriter)
    {
        string? message = logEntry.Formatter?.Invoke(logEntry.State, logEntry.Exception);
        if (message is null && logEntry.Exception is null)
        {
            return;
        }

        Activity? current = Activity.Current;
        ActivityTraceId traceId = current?.TraceId ?? default;
        ActivitySpanId spanId = current?.SpanId ?? default;

        textWriter.Write(Render(
            TimeProvider.System.GetUtcNow(),
            logEntry.LogLevel,
            logEntry.Category,
            message ?? string.Empty,
            logEntry.Exception,
            traceId,
            spanId));
        textWriter.Write('\n');
    }

    /// <summary>Builds the JSON object (without the trailing newline); a default or all-zero trace or span ID omits both ID keys.</summary>
    public static string Render(
        DateTimeOffset timestamp,
        LogLevel level,
        string logger,
        string message,
        Exception? exception,
        ActivityTraceId traceId,
        ActivitySpanId spanId)
    {
        using MemoryStream buffer = new();
        using (Utf8JsonWriter writer = new(buffer, WriterOptions))
        {
            writer.WriteStartObject();
            writer.WriteString("timestamp", timestamp.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'", CultureInfo.InvariantCulture));
            writer.WriteString("severity", Severity(level));
            writer.WriteString("logger", logger);
            writer.WriteString("message", Scrub(message));
            if (IsValid(traceId, spanId))
            {
                writer.WriteString("trace_id", traceId.ToHexString());
                writer.WriteString("span_id", spanId.ToHexString());
            }

            if (exception is not null)
            {
                writer.WriteString("exception.type", exception.GetType().FullName ?? exception.GetType().Name);
                writer.WriteString("exception.message", Scrub(exception.Message));
                writer.WriteString("exception.stacktrace", Scrub(exception.ToString()));
            }

            writer.WriteEndObject();
        }

        return Encoding.UTF8.GetString(buffer.ToArray());
    }

    /// <summary>Applies the two contract scrubber expressions in order; a regex timeout redacts the whole value rather than leaking it.</summary>
    public static string Scrub(string value)
    {
        try
        {
            string credentialsRedacted = CredentialKeyValue.Replace(value, "$1$2" + RedactedPlaceholder);
            return BareCredential.Replace(credentialsRedacted, "$1 " + RedactedPlaceholder);
        }
        catch (RegexMatchTimeoutException)
        {
            return RedactedPlaceholder;
        }
    }

    private static bool IsValid(ActivityTraceId traceId, ActivitySpanId spanId) =>
        traceId.ToHexString().Any(digit => digit != '0') && spanId.ToHexString().Any(digit => digit != '0');

    private static string Severity(LogLevel level) => level switch
    {
        LogLevel.Critical => "FATAL",
        LogLevel.Error => "ERROR",
        LogLevel.Warning => "WARN",
        LogLevel.Information => "INFO",
        LogLevel.Debug => "DEBUG",
        LogLevel.Trace => "TRACE",
        _ => "INFO",
    };
}
