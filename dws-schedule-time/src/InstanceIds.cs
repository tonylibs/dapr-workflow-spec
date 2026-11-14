using System.Globalization;
using System.Text.RegularExpressions;

namespace Dws.ScheduleTime;

/// <summary>Builds the deterministic <c>&lt;source&gt;:&lt;workflow&gt;:&lt;fire-key&gt;</c> instance IDs of the workflow start contract.</summary>
public static partial class InstanceIds
{
    /// <summary>Source of <c>every</c> and <c>cron</c> fires.</summary>
    public const string ScheduledSource = "dws-sched";

    /// <summary>Source of <c>after</c> starts.</summary>
    public const string AfterSource = "dws-after";

    /// <summary>Reserved for Phase 6b event triggers (<c>dws-schedule-event</c>); not produced here.</summary>
    public const string EventSource = "dws-event";

    /// <summary>The longest instance ID the orchestrator accepts.</summary>
    public const int MaxLength = 256;

    public static string Scheduled(string workflow, string fireKey) => Build(ScheduledSource, workflow, fireKey);

    public static string After(string workflow, long sequence)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(sequence);
        return Build(AfterSource, workflow, sequence.ToString(CultureInfo.InvariantCulture));
    }

    /// <summary>The instance ID of a cron fire, keyed by <see cref="FireKeys.Cron"/>.</summary>
    public static string Cron(string workflow, DateTimeOffset deliveredAt) => Scheduled(workflow, FireKeys.Cron(deliveredAt));

    /// <summary>The instance ID of an every fire, keyed by <see cref="FireKeys.Every"/> from the job's anchor.</summary>
    public static string Every(string workflow, DateTimeOffset anchor, TimeSpan interval, DateTimeOffset deliveredAt) =>
        Scheduled(workflow, FireKeys.Every(anchor, interval, deliveredAt));

    /// <summary>Whether the orchestrator's start endpoint accepts <paramref name="instanceId"/> (1-256 of <c>[A-Za-z0-9:._-]</c>).</summary>
    public static bool IsValid(string instanceId) => AllowedId().IsMatch(instanceId);

    private static string Build(string source, string workflow, string key)
    {
        if (!AllowedSegment().IsMatch(workflow) || !AllowedSegment().IsMatch(key))
        {
            throw new ArgumentException($"workflow '{workflow}' and key '{key}' must be non-empty and use only [A-Za-z0-9._-]");
        }

        string instanceId = $"{source}:{workflow}:{key}";
        if (instanceId.Length > MaxLength)
        {
            throw new ArgumentException($"instance ID for workflow '{workflow}' is longer than {MaxLength} characters");
        }

        return instanceId;
    }

    // Segments exclude ':' so the three parts stay unambiguous.
    [GeneratedRegex("^[A-Za-z0-9._-]+$")]
    private static partial Regex AllowedSegment();

    [GeneratedRegex("^[A-Za-z0-9:._-]{1,256}$")]
    private static partial Regex AllowedId();
}
