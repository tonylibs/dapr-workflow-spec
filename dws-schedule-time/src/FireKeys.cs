using System.Globalization;

namespace Dws.ScheduleTime;

/// <summary>Computes the fire key of a time-trigger delivery from its delivery time, since a Dapr job callback carries no due time (ADR 0013).</summary>
public static class FireKeys
{
    /// <summary>How early a delivery may arrive, by this pod's clock, and still count as the due fire.</summary>
    public static readonly TimeSpan MaxEarlyDelivery = TimeSpan.FromSeconds(2);

    private const string Format = "yyyyMMdd'T'HHmmss'Z'";

    // Every delivery of one fire (retries 10 s apart, a replayed burst after downtime) must map to one key,
    // so a key is the latest due time at or before the delivery. A pod clock slightly behind the Dapr
    // Scheduler's would put an on-time delivery just before its due time and onto the previous key, while
    // the retry lands on the right one: two keys, two instances. So the delivery time is first moved
    // forward by a small tolerance, capped at a quarter of the interval to leave late deliveries room.
    public static string Cron(DateTimeOffset deliveredAt)
    {
        DateTimeOffset shifted = deliveredAt.ToUniversalTime() + MaxEarlyDelivery;
        DateTimeOffset minute = new(shifted.Year, shifted.Month, shifted.Day, shifted.Hour, shifted.Minute, 0, TimeSpan.Zero);
        return minute.ToString(Format, CultureInfo.InvariantCulture);
    }

    public static string Every(DateTimeOffset anchor, TimeSpan interval, DateTimeOffset deliveredAt)
    {
        ArgumentOutOfRangeException.ThrowIfLessThan(interval, TimeSpan.FromSeconds(1));
        TimeSpan tolerance = interval / 4 < MaxEarlyDelivery ? interval / 4 : MaxEarlyDelivery;
        long elapsed = Math.Max(0, (deliveredAt - anchor + tolerance).Ticks);
        DateTimeOffset tick = anchor.ToUniversalTime().AddTicks(elapsed / interval.Ticks * interval.Ticks);
        return tick.ToString(Format, CultureInfo.InvariantCulture);
    }
}
