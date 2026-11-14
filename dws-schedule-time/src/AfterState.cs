using System.Globalization;
using System.Text;
using System.Text.Json;

namespace Dws.ScheduleTime;

/// <summary>The durable <c>after:&lt;workflow&gt;</c> bookkeeping: the sequence number, the version/app it runs on and whether it is running or waiting out the delay.</summary>
public sealed record AfterState(string Version, string AppId, long Seq, string Phase, DateTimeOffset? ReadyAt)
{
    public const string Running = "running";

    public const string Waiting = "waiting";

    public static string Key(string workflow) => "after:" + workflow;

    public string ToJson()
    {
        using MemoryStream buffer = new();
        using (Utf8JsonWriter writer = new(buffer))
        {
            writer.WriteStartObject();
            writer.WriteString("version", Version);
            writer.WriteString("appId", AppId);
            writer.WriteNumber("seq", Seq);
            writer.WriteString("phase", Phase);
            if (ReadyAt is { } readyAt)
            {
                writer.WriteString("readyAt", readyAt.UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'", CultureInfo.InvariantCulture));
            }

            writer.WriteEndObject();
        }

        return Encoding.UTF8.GetString(buffer.ToArray());
    }

    /// <summary>Parses stored state; null when it is unreadable.</summary>
    public static AfterState? TryParse(string value)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(value);
            JsonElement root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object
                || !root.TryGetProperty("version", out JsonElement version) || version.ValueKind != JsonValueKind.String
                || !root.TryGetProperty("appId", out JsonElement appId) || appId.ValueKind != JsonValueKind.String
                || !root.TryGetProperty("seq", out JsonElement seq) || !seq.TryGetInt64(out long sequence)
                || !root.TryGetProperty("phase", out JsonElement phase) || phase.ValueKind != JsonValueKind.String)
            {
                return null;
            }

            DateTimeOffset? readyAt = null;
            if (root.TryGetProperty("readyAt", out JsonElement ready)
                && ready.ValueKind == JsonValueKind.String
                && DateTimeOffset.TryParse(ready.GetString(), CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out DateTimeOffset parsed))
            {
                readyAt = parsed;
            }

            return new AfterState(version.GetString() ?? string.Empty, appId.GetString() ?? string.Empty, sequence, phase.GetString() ?? string.Empty, readyAt);
        }
        catch (JsonException)
        {
            return null;
        }
    }
}
