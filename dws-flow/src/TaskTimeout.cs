using System.Text.Json.Nodes;
using System.Xml;

namespace Dws.Flow;

/// <summary>
/// Parses a task's declared <c>timeout</c> (an ISO-8601 duration literal, or an inline object of
/// <c>days</c>/<c>hours</c>/<c>minutes</c>/<c>seconds</c>/<c>milliseconds</c>, optionally wrapped in an
/// <c>after</c> property), and formats a <see cref="TimeSpan"/> back to the ISO-8601 rendering v1's
/// <c>java.time.Duration.toString()</c> produces (e.g. <c>PT5S</c>), via <see cref="XmlConvert"/>, whose
/// XML-Schema duration format matches it.
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
                return XmlConvert.ToTimeSpan(iso);
            case JsonObject durationObject:
                return ParseObject(durationObject);
            default:
                throw new InvalidOperationException("timeout must be an ISO-8601 duration string or a duration object");
        }
    }

    public static string Format(TimeSpan duration) => XmlConvert.ToString(duration);

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

    private static TimeSpan Component(JsonObject durationObject, string field, Func<double, TimeSpan> toTimeSpan) =>
        durationObject[field] is JsonValue value && value.TryGetValue<double>(out double amount) ? toTimeSpan(amount) : TimeSpan.Zero;
}
