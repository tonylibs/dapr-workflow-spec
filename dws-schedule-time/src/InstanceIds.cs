using System.Globalization;

namespace Dws.ScheduleTime;

/// <summary>Deterministic workflow instance IDs: <c>&lt;source&gt;:&lt;workflow&gt;:&lt;fire-key&gt;</c>.</summary>
public static class InstanceIds
{
    private const string TimeFormat = "yyyyMMdd'T'HHmmss'Z'";

    /// <summary>The instance ID of a cron fire: the delivery time truncated to the minute, in UTC.</summary>
    public static string Cron(string workflow, DateTimeOffset delivered)
    {
        DateTime utc = delivered.UtcDateTime;
        DateTime minute = new(utc.Year, utc.Month, utc.Day, utc.Hour, utc.Minute, 0, DateTimeKind.Utc);
        return Scheduled(workflow, minute);
    }

    /// <summary>The instance ID of an every fire: the latest interval tick from the job's anchor that is not after the delivery time.</summary>
    public static string Every(string workflow, DateTimeOffset anchor, TimeSpan interval, DateTimeOffset delivered)
    {
        long ticks = delivered > anchor ? (delivered - anchor).Ticks / interval.Ticks : 0;
        return Scheduled(workflow, anchor.UtcDateTime.AddTicks(ticks * interval.Ticks));
    }

    public static string After(string workflow, long sequence) =>
        $"dws-after:{workflow}:{sequence.ToString(CultureInfo.InvariantCulture)}";

    private static string Scheduled(string workflow, DateTime tick) =>
        $"dws-sched:{workflow}:{tick.ToString(TimeFormat, CultureInfo.InvariantCulture)}";
}
