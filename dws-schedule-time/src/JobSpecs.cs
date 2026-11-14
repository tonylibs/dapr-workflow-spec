using System.Globalization;
using System.Text;

namespace Dws.ScheduleTime;

/// <summary>Job names, Dapr schedule expressions and the failure policy constants for every job the scheduler registers.</summary>
public static class JobSpecs
{
    public const string ScheduledPrefix = "dws-sched-";

    public const string AfterPollPrefix = "dws-after-poll-";

    public const string AfterStartPrefix = "dws-after-start-";

    public const int FailureMaxRetries = 3;

    public static readonly TimeSpan FailureRetryInterval = TimeSpan.FromSeconds(10);

    public static string ScheduledName(string workflow) => ScheduledPrefix + workflow;

    public static string AfterPollName(string workflow) => AfterPollPrefix + workflow;

    public static string AfterStartName(string workflow) => AfterStartPrefix + workflow;

    /// <summary>Splits a job name into its prefix and workflow; false for a name that is not one of ours.</summary>
    public static bool TryParseName(string jobName, out string prefix, out string workflow)
    {
        foreach (string candidate in new[] { ScheduledPrefix, AfterPollPrefix, AfterStartPrefix })
        {
            if (jobName.StartsWith(candidate, StringComparison.Ordinal) && jobName.Length > candidate.Length)
            {
                prefix = candidate;
                workflow = jobName[candidate.Length..];
                return true;
            }
        }

        prefix = string.Empty;
        workflow = string.Empty;
        return false;
    }

    /// <summary>The Dapr schedule for an every or cron trigger: <c>@every &lt;go duration&gt;</c> or the 6-field cron with a 0 seconds field.</summary>
    public static string ScheduleExpression(TriggerConfig trigger) => trigger.Kind switch
    {
        TriggerKind.Every => PollExpression(trigger.Interval ?? throw new InvalidOperationException("every trigger without an interval")),
        TriggerKind.Cron => "0 " + trigger.Schedule,
        _ => throw new InvalidOperationException("after triggers have no recurring start schedule"),
    };

    public static string PollExpression(TimeSpan interval) => "@every " + GoDuration(interval);

    /// <summary>Formats a duration in the Go <c>time.ParseDuration</c> syntax, for example <c>1h30m</c>, <c>1m30s</c> or <c>1s500ms</c>.</summary>
    public static string GoDuration(TimeSpan duration)
    {
        if (duration <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(duration), "a Go duration for a schedule must be positive");
        }

        long ticks = duration.Ticks;
        long hours = ticks / TimeSpan.TicksPerHour;
        ticks %= TimeSpan.TicksPerHour;
        long minutes = ticks / TimeSpan.TicksPerMinute;
        ticks %= TimeSpan.TicksPerMinute;
        long seconds = ticks / TimeSpan.TicksPerSecond;
        ticks %= TimeSpan.TicksPerSecond;
        long milliseconds = ticks / TimeSpan.TicksPerMillisecond;
        ticks %= TimeSpan.TicksPerMillisecond;

        StringBuilder text = new();
        Append(text, hours, "h");
        Append(text, minutes, "m");
        Append(text, seconds, "s");
        Append(text, milliseconds, "ms");
        Append(text, ticks * 100, "ns");
        return text.ToString();
    }

    private static void Append(StringBuilder text, long value, string unit)
    {
        if (value != 0)
        {
            text.Append(value.ToString(CultureInfo.InvariantCulture)).Append(unit);
        }
    }
}
