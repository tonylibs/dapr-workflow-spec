using System.Diagnostics;
using System.Text.Json;
using System.Text.RegularExpressions;
using Dws.Flow;
using FluentAssertions;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.Flow.Tests;

/// <summary>Conformance test for docs/log-format.md: runs the 12 golden cases against the JSON console formatter.</summary>
public sealed partial class DwsJsonConsoleFormatterTests
{
    private const string GoldenLogger = "dws.golden";
    private const string GoldenTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    private const string GoldenSpanId = "00f067aa0ba902b7";

    [GeneratedRegex(@"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$")]
    private static partial Regex TimestampPattern();

    private static string Write(LogLevel level, string message, Exception? exception = null)
    {
        using StringWriter output = new();
        new DwsJsonConsoleFormatter().Write(
            new LogEntry<string>(level, GoldenLogger, new EventId(0), message, exception, (state, _) => state),
            null,
            output);
        return output.ToString();
    }

    private static JsonElement ParseSingleLine(string output)
    {
        output.Should().EndWith("\n");
        output.TrimEnd('\n').Should().NotContain("\n");
        output.Should().NotContain("\r");
        JsonElement record = JsonDocument.Parse(output).RootElement.Clone();
        TimestampPattern().IsMatch(record.GetProperty("timestamp").GetString()!).Should().BeTrue();
        record.GetProperty("logger").GetString().Should().Be(GoldenLogger);
        return record;
    }

    private static Activity StartActivityWithTrace(string traceId)
    {
        using ActivityListener listener = new()
        {
            ShouldListenTo = source => source.Name == GoldenLogger,
            Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllData,
        };
        ActivitySource.AddActivityListener(listener);
        using ActivitySource source = new(GoldenLogger);
        ActivityContext parent = new(
            ActivityTraceId.CreateFromString(traceId),
            ActivitySpanId.CreateFromString(GoldenSpanId),
            ActivityTraceFlags.Recorded);
        return source.StartActivity("golden", ActivityKind.Internal, parent)
            ?? throw new InvalidOperationException("listener did not sample the golden activity");
    }

    [Fact]
    public void WritesAPlainRecordWithoutTraceIds()
    {
        JsonElement record = ParseSingleLine(Write(LogLevel.Information, "Starting Dapr workflow runtime"));

        record.GetProperty("severity").GetString().Should().Be("INFO");
        record.GetProperty("message").GetString().Should().Be("Starting Dapr workflow runtime");
        record.TryGetProperty("trace_id", out _).Should().BeFalse();
        record.TryGetProperty("span_id", out _).Should().BeFalse();
        record.TryGetProperty("exception.type", out _).Should().BeFalse();
    }

    [Fact]
    public void WritesTheActiveTraceAndSpanFromActivityCurrent()
    {
        using Activity activity = StartActivityWithTrace(GoldenTraceId);

        JsonElement record = ParseSingleLine(Write(LogLevel.Information, "Invoking step"));

        Activity.Current.Should().BeSameAs(activity);
        record.GetProperty("trace_id").GetString().Should().Be(GoldenTraceId);
        record.GetProperty("span_id").GetString().Should().Be(activity.SpanId.ToHexString());
        record.GetProperty("span_id").GetString().Should().MatchRegex("^[0-9a-f]{16}$");
    }

    [Fact]
    public void RendersTheExactGoldenInSpanLine()
    {
        string line = DwsJsonConsoleFormatter.Render(
            new DateTimeOffset(2026, 1, 2, 3, 4, 5, 678, TimeSpan.Zero),
            LogLevel.Information,
            GoldenLogger,
            "Invoking step",
            null,
            ActivityTraceId.CreateFromString(GoldenTraceId),
            ActivitySpanId.CreateFromString(GoldenSpanId));

        line.Should().Be(
            """{"timestamp":"2026-01-02T03:04:05.678Z","severity":"INFO","logger":"dws.golden","message":"Invoking step","trace_id":"4bf92f3577b34da6a3ce929d0e0e4736","span_id":"00f067aa0ba902b7"}""");
    }

    [Fact]
    public void MapsWarningToWarnWithoutIdsOutsideASpan()
    {
        JsonElement record = ParseSingleLine(Write(LogLevel.Warning, "Definition store not reachable, retrying"));

        record.GetProperty("severity").GetString().Should().Be("WARN");
        record.GetProperty("message").GetString().Should().Be("Definition store not reachable, retrying");
        record.TryGetProperty("trace_id", out _).Should().BeFalse();
        record.TryGetProperty("span_id", out _).Should().BeFalse();
    }

    [Fact]
    public void OmitsIdsForAnInvalidContext()
    {
        using Activity activity = new Activity("invalid").SetIdFormat(ActivityIdFormat.Hierarchical).Start();
        activity.TraceId.ToHexString().Should().Be("00000000000000000000000000000000");

        JsonElement record = ParseSingleLine(Write(LogLevel.Information, "Invalid context"));

        record.GetProperty("message").GetString().Should().Be("Invalid context");
        record.TryGetProperty("trace_id", out _).Should().BeFalse();
        record.TryGetProperty("span_id", out _).Should().BeFalse();
    }

    [Fact]
    public void OmitsIdsForADefaultContext()
    {
        string line = DwsJsonConsoleFormatter.Render(
            DateTimeOffset.UtcNow,
            LogLevel.Information,
            GoldenLogger,
            "Invalid context",
            null,
            default,
            default);

        line.Should().NotContain("trace_id").And.NotContain("span_id");
    }

    [Fact]
    public void WritesExceptionTypeMessageAndStacktraceOnOneLine()
    {
        InvalidOperationException exception = ThrowAndCatch(new InvalidOperationException("boom"));

        JsonElement record = ParseSingleLine(Write(LogLevel.Error, "Activity failed", exception));

        record.GetProperty("severity").GetString().Should().Be("ERROR");
        record.GetProperty("message").GetString().Should().Be("Activity failed");
        record.GetProperty("exception.type").GetString().Should().Be(typeof(InvalidOperationException).FullName);
        record.GetProperty("exception.message").GetString().Should().Be("boom");
        record.GetProperty("exception.stacktrace").GetString().Should().Contain("boom").And.Contain("\n");
    }

    [Fact]
    public void KeepsMultiLineMessagesOnOneOutputLine()
    {
        JsonElement record = ParseSingleLine(Write(LogLevel.Information, "line one\nline two"));

        record.GetProperty("message").GetString().Should().Be("line one\nline two");
    }

    [Fact]
    public void RedactsABearerAuthorizationHeader()
    {
        string output = Write(
            LogLevel.Information,
            "calling admin with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkd3MifQ.c2lnbmF0dXJl");

        output.Should().NotContain("eyJhbGciOi");
        ParseSingleLine(output).GetProperty("message").GetString().Should().Be("calling admin with Authorization: [REDACTED]");
    }

    [Fact]
    public void RedactsABareBearerCredential()
    {
        string output = Write(LogLevel.Warning, "upstream rejected Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig12345");

        output.Should().NotContain("eyJhbGciOi");
        JsonElement record = ParseSingleLine(output);
        record.GetProperty("severity").GetString().Should().Be("WARN");
        record.GetProperty("message").GetString().Should().Be("upstream rejected Bearer [REDACTED]");
    }

    [Fact]
    public void RedactsAPasswordValue()
    {
        string output = Write(LogLevel.Information, "connecting with user=dws password=hunter2");

        output.Should().NotContain("hunter2");
        ParseSingleLine(output).GetProperty("message").GetString().Should().Be("connecting with user=dws password=[REDACTED]");
    }

    [Fact]
    public void RedactsAJsonSecretValue()
    {
        string output = Write(LogLevel.Information, """payload {"client_secret":"s3cr3t-value","scope":"read"}""");

        output.Should().NotContain("s3cr3t-value");
        ParseSingleLine(output).GetProperty("message").GetString()
            .Should().Be("""payload {"client_secret":"[REDACTED]","scope":"read"}""");
    }

    [Fact]
    public void RedactsSecretsInExceptionMessageAndStacktrace()
    {
        InvalidOperationException exception = ThrowAndCatch(new InvalidOperationException("401 for token=abc12345"));

        string output = Write(LogLevel.Error, "Call failed", exception);

        output.Should().NotContain("abc12345");
        JsonElement record = ParseSingleLine(output);
        record.GetProperty("exception.message").GetString().Should().Be("401 for token=[REDACTED]");
        record.GetProperty("exception.stacktrace").GetString().Should().Contain("token=[REDACTED]");
    }

    [Fact]
    public void MapsCriticalToFatal()
    {
        JsonElement record = ParseSingleLine(Write(LogLevel.Critical, "Runtime terminated"));

        record.GetProperty("severity").GetString().Should().Be("FATAL");
        record.GetProperty("message").GetString().Should().Be("Runtime terminated");
    }

    [Theory]
    [InlineData(LogLevel.Trace, "TRACE")]
    [InlineData(LogLevel.Debug, "DEBUG")]
    [InlineData(LogLevel.Information, "INFO")]
    [InlineData(LogLevel.Warning, "WARN")]
    [InlineData(LogLevel.Error, "ERROR")]
    [InlineData(LogLevel.Critical, "FATAL")]
    public void MapsEveryNativeLevelToTheContractSeverity(LogLevel level, string severity)
    {
        ParseSingleLine(Write(level, "m")).GetProperty("severity").GetString().Should().Be(severity);
    }

    [Fact]
    public void RedactsCaseInsensitivelyAndKeepsTheKeyName()
    {
        DwsJsonConsoleFormatter.Scrub("DB.PASSWORD=Hunter2 SECRET_DB_URL: postgres://x").Should()
            .Be("DB.PASSWORD=[REDACTED] SECRET_DB_URL: [REDACTED]");
    }

    [Fact]
    public void LeavesMessagesWithoutCredentialsUntouched()
    {
        DwsJsonConsoleFormatter.Scrub("Executing step 'fetch' for instance abc").Should().Be("Executing step 'fetch' for instance abc");
    }

    [Fact]
    public void WritesNothingForAnEntryWithNoMessageAndNoException()
    {
        using StringWriter output = new();
        new DwsJsonConsoleFormatter().Write(
            new LogEntry<string>(LogLevel.Information, GoldenLogger, new EventId(0), "x", null, (_, _) => null!),
            null,
            output);

        output.ToString().Should().BeEmpty();
    }

    [Theory]
    [InlineData("json", true)]
    [InlineData("JSON", true)]
    [InlineData("  Json \t", true)]
    [InlineData("text", false)]
    [InlineData("", false)]
    [InlineData("  ", false)]
    [InlineData(null, false)]
    [InlineData("yaml", false)]
    public void SelectsJsonModeOnlyForTheJsonValue(string? value, bool expected)
    {
        DwsJsonConsoleFormatter.IsJsonMode(value).Should().Be(expected);
    }

    private static T ThrowAndCatch<T>(T exception)
        where T : Exception
    {
        try
        {
            throw exception;
        }
        catch (T caught)
        {
            return caught;
        }
    }
}
