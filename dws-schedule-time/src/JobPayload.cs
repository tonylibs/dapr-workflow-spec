using System.Globalization;
using System.Text;
using System.Text.Json;

namespace Dws.ScheduleTime;

/// <summary>The data stored with each job: what Dapr hands back on every fire and what reconcile compares against.</summary>
public sealed record JobPayload(string Workflow, string Version, string AppId, TriggerKind Kind, string Schedule, DateTimeOffset Anchor)
{
    private const string AnchorFormat = "yyyy-MM-dd'T'HH:mm:ss.fff'Z'";

    public static JobPayload For(TriggerConfig trigger, DateTimeOffset anchor) =>
        new(trigger.Workflow, trigger.Version, trigger.AppId, trigger.Kind, trigger.Schedule, anchor);

    public string ToJson()
    {
        using MemoryStream buffer = new();
        using (Utf8JsonWriter writer = new(buffer))
        {
            writer.WriteStartObject();
            writer.WriteString("workflow", Workflow);
            writer.WriteString("version", Version);
            writer.WriteString("appId", AppId);
            writer.WriteString("kind", Kind.ToString().ToLowerInvariant());
            writer.WriteString("schedule", Schedule);
            writer.WriteString("anchor", Anchor.UtcDateTime.ToString(AnchorFormat, CultureInfo.InvariantCulture));
            writer.WriteEndObject();
        }

        return Encoding.UTF8.GetString(buffer.ToArray());
    }

    /// <summary>Parses a payload; null for anything unreadable. A JSON string wrapping the object (double encoding) is unwrapped once.</summary>
    public static JobPayload? TryParse(string? text)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            return null;
        }

        try
        {
            using JsonDocument document = JsonDocument.Parse(text);
            JsonElement root = document.RootElement;
            if (root.ValueKind == JsonValueKind.String)
            {
                return TryParse(root.GetString());
            }

            if (root.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            string? workflow = Text(root, "workflow");
            string? version = Text(root, "version");
            string? appId = Text(root, "appId");
            string? kind = Text(root, "kind");
            string? schedule = Text(root, "schedule");
            string? anchor = Text(root, "anchor");
            if (workflow is null || version is null || appId is null || kind is null || schedule is null || anchor is null
                || !Enum.TryParse(kind, ignoreCase: true, out TriggerKind parsedKind)
                || !DateTimeOffset.TryParse(anchor, CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal, out DateTimeOffset parsedAnchor))
            {
                return null;
            }

            return new JobPayload(workflow, version, appId, parsedKind, schedule, parsedAnchor);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    /// <summary>True when the registered job still describes the same version, kind and schedule (the anchor is deliberately ignored).</summary>
    public bool SameJobAs(JobPayload? other) =>
        other is not null && Version == other.Version && Kind == other.Kind && Schedule == other.Schedule;

    private static string? Text(JsonElement root, string name) =>
        root.TryGetProperty(name, out JsonElement element) && element.ValueKind == JsonValueKind.String ? element.GetString() : null;
}
