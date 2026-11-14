using System.Text.Json;
using System.Text.RegularExpressions;
using System.Xml;

namespace Dws.ScheduleTime;

/// <summary>The parsed contents of a trigger ConfigMap's <c>trigger.json</c> (written by the controller).</summary>
public sealed partial record TriggerConfig(string Workflow, string Version, string AppId, TriggerKind Kind, string Schedule, TimeSpan? Interval)
{
    public static readonly TimeSpan MinimumInterval = TimeSpan.FromSeconds(1);

    private static readonly string[] MonthNames = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

    private static readonly string[] DayNames = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];

    [GeneratedRegex(@"^[0-9*,/\-]+$")]
    private static partial Regex NumericField();

    [GeneratedRegex(@"^[0-9A-Za-z*,/\-]+$")]
    private static partial Regex NamedField();

    [GeneratedRegex(@"[A-Za-z]+")]
    private static partial Regex Letters();

    [GeneratedRegex(@"\s+")]
    private static partial Regex Whitespace();

    /// <summary>Parses and validates a <c>trigger.json</c> document; anything the scheduler cannot act on is a <see cref="TriggerConfigException"/>.</summary>
    public static TriggerConfig Parse(string json)
    {
        JsonElement root;
        try
        {
            using JsonDocument document = JsonDocument.Parse(json);
            root = document.RootElement.Clone();
        }
        catch (JsonException exception)
        {
            throw new TriggerConfigException($"trigger.json is not valid JSON: {exception.Message}", exception);
        }

        if (root.ValueKind != JsonValueKind.Object)
        {
            throw new TriggerConfigException("trigger.json must be a JSON object");
        }

        string workflow = RequiredString(root, "workflow");
        string version = RequiredString(root, "version");
        string appId = RequiredString(root, "appId");
        string kindText = RequiredString(root, "kind");
        string schedule = RequiredString(root, "schedule");

        TriggerKind kind = kindText switch
        {
            "every" => TriggerKind.Every,
            "cron" => TriggerKind.Cron,
            "after" => TriggerKind.After,
            _ => throw new TriggerConfigException($"unknown trigger kind '{kindText}' (expected every, cron or after)"),
        };

        if (kind == TriggerKind.Cron)
        {
            return new TriggerConfig(workflow, version, appId, kind, NormalizeCron(schedule), null);
        }

        return new TriggerConfig(workflow, version, appId, kind, schedule, ParseDuration(schedule));
    }

    /// <summary>Parses a canonical ISO-8601 duration of at least one second.</summary>
    public static TimeSpan ParseDuration(string schedule)
    {
        TimeSpan interval;
        try
        {
            interval = XmlConvert.ToTimeSpan(schedule);
        }
        catch (Exception exception) when (exception is FormatException or OverflowException)
        {
            throw new TriggerConfigException($"schedule '{schedule}' is not a valid ISO-8601 duration", exception);
        }

        if (interval < MinimumInterval)
        {
            throw new TriggerConfigException($"schedule '{schedule}' is shorter than the 1 second minimum");
        }

        return interval;
    }

    private static string NormalizeCron(string expression)
    {
        string[] fields = Whitespace().Split(expression.Trim());
        if (fields.Length != 5)
        {
            throw new TriggerConfigException($"cron '{expression}' must have exactly 5 whitespace-separated fields");
        }

        for (int index = 0; index < fields.Length; index++)
        {
            ValidateCronField(expression, fields[index], index);
        }

        return string.Join(' ', fields);
    }

    private static void ValidateCronField(string expression, string field, int index)
    {
        bool named = index >= 3;
        if (!(named ? NamedField() : NumericField()).IsMatch(field))
        {
            throw new TriggerConfigException($"cron '{expression}' has an invalid field '{field}'");
        }

        string[] names = index == 3 ? MonthNames : DayNames;
        foreach (Match word in Letters().Matches(field))
        {
            if (!names.Contains(word.Value, StringComparer.OrdinalIgnoreCase))
            {
                throw new TriggerConfigException($"cron '{expression}' has an unknown name '{word.Value}'");
            }
        }
    }

    private static string RequiredString(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out JsonElement element) || element.ValueKind != JsonValueKind.String)
        {
            throw new TriggerConfigException($"trigger.json field '{name}' is required and must be a string");
        }

        string? value = element.GetString();
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new TriggerConfigException($"trigger.json field '{name}' must not be blank");
        }

        return value.Trim();
    }
}
