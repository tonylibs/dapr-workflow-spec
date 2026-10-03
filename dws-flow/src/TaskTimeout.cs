using System.Globalization;
using System.Text;
using System.Text.Json.Nodes;
using System.Xml;

namespace Dws.Flow;

/// <summary>
/// Parses a task's declared <c>timeout</c> (an ISO-8601 duration literal, or an inline object of
/// <c>days</c>/<c>hours</c>/<c>minutes</c>/<c>seconds</c>/<c>milliseconds</c>, optionally wrapped in an
/// <c>after</c> property), and formats a <see cref="TimeSpan"/> back to the ISO-8601 rendering v1's
/// <c>java.time.Duration.toString()</c> produces (e.g. <c>PT5S</c>). Formatting is hand-rolled rather than
/// delegated to <see cref="XmlConvert"/>: XML-Schema duration format carries a day component
/// (<c>P1D</c>/<c>P1DT1H</c>) that Java's <c>Duration.toString()</c> never emits — it always normalizes to
/// hours/minutes/seconds (e.g. 1 day is <c>PT24H</c>, 25 hours is <c>PT25H</c>).
/// </summary>
public static class TaskTimeout
{
    public static TimeSpan? Parse(JsonNode? timeout)
    {
        switch (timeout)
        {
            case null:
                return null;
            case JsonValue value when value.TryGetValue<string>(out string? iso):
                return ParseIso(iso);
            case JsonObject durationObject:
                return ParseObject(durationObject);
            default:
                throw new InvalidOperationException("config failure: timeout must be an ISO-8601 duration string or a duration object");
        }
    }

    public static string Format(TimeSpan duration)
    {
        long ticks = duration.Ticks;
        long wholeSeconds = ticks / TimeSpan.TicksPerSecond;
        long fractionTicks = ticks - wholeSeconds * TimeSpan.TicksPerSecond;

        long hours = wholeSeconds / 3600;
        long minutes = wholeSeconds / 60 % 60;
        long seconds = wholeSeconds % 60;

        bool hasFraction = fractionTicks != 0;
        bool includeSeconds = seconds != 0 || hasFraction || (hours == 0 && minutes == 0);

        StringBuilder builder = new("PT");
        if (hours != 0)
        {
            builder.Append(hours).Append('H');
        }

        if (minutes != 0)
        {
            builder.Append(minutes).Append('M');
        }

        if (includeSeconds)
        {
            builder.Append(seconds);
            if (hasFraction)
            {
                decimal fraction = (decimal)fractionTicks / TimeSpan.TicksPerSecond;
                builder.Append(fraction.ToString("0.#######", CultureInfo.InvariantCulture).TrimStart('0'));
            }

            builder.Append('S');
        }

        return builder.ToString();
    }

    private static TimeSpan ParseIso(string iso)
    {
        try
        {
            return XmlConvert.ToTimeSpan(iso);
        }
        catch (FormatException exception)
        {
            throw new InvalidOperationException($"config failure: timeout '{iso}' is not a valid ISO-8601 duration", exception);
        }
    }

    private static TimeSpan ParseObject(JsonObject durationObject)
    {
        if (durationObject["after"] is JsonNode after)
        {
            return Parse(after) ?? TimeSpan.Zero;
        }

        TimeSpan total = TimeSpan.Zero;
        total += Component(durationObject, "days", TimeSpan.FromDays);
        total += Component(durationObject, "hours", TimeSpan.FromHours);
        total += Component(durationObject, "minutes", TimeSpan.FromMinutes);
        total += Component(durationObject, "seconds", TimeSpan.FromSeconds);
        total += Component(durationObject, "milliseconds", TimeSpan.FromMilliseconds);
        return total;
    }

    private static TimeSpan Component(JsonObject durationObject, string field, Func<double, TimeSpan> toTimeSpan)
    {
        if (durationObject[field] is not JsonNode node)
        {
            return TimeSpan.Zero;
        }

        if (node is JsonValue value && value.TryGetValue<double>(out double amount))
        {
            return toTimeSpan(amount);
        }

        throw new InvalidOperationException($"config failure: timeout.{field} must be a number");
    }
}
