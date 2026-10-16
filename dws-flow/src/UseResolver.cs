using System.Diagnostics.CodeAnalysis;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>The three <c>use</c> kinds a Flow node can reference by name; each is a key prefix in the version's store.</summary>
public enum UseKind
{
    Errors,
    Retries,
    Timeouts,
}

/// <summary>Resolves a <c>use</c> reference to its entry: the inline value as written, or the named entry loaded at
/// boot. This is the only place that decides whether a reference is inline or a name, and it does no I/O.</summary>
public sealed partial class UseResolver
{
    public static readonly UseResolver Empty = new(new Dictionary<(UseKind, string), string>());

    private readonly IReadOnlyDictionary<(UseKind Kind, string Name), string> entries;

    public UseResolver(IReadOnlyDictionary<(UseKind Kind, string Name), string> entries)
    {
        this.entries = entries;
    }

    public static string KindName(UseKind kind) => kind switch
    {
        UseKind.Errors => "errors",
        UseKind.Retries => "retries",
        _ => "timeouts",
    };

    public static bool IsName(UseKind kind, JsonNode? reference) => TryGetName(kind, reference, out _);

    public JsonNode? Resolve(UseKind kind, JsonNode? reference)
    {
        if (!TryGetName(kind, reference, out string? name))
        {
            return reference;
        }

        return entries.TryGetValue((kind, name), out string? json)
            ? JsonNode.Parse(json)
            : throw new InvalidOperationException($"config failure: use.{KindName(kind)} '{name}' was not loaded at boot");
    }

    public TimeSpan? Timeout(JsonNode? reference) => TaskTimeout.Parse(Resolve(UseKind.Timeouts, reference));

    private static bool TryGetName(UseKind kind, JsonNode? reference, [NotNullWhen(true)] out string? name)
    {
        if (reference is JsonValue value && value.TryGetValue<string>(out name))
        {
            return !(kind == UseKind.Timeouts && DurationDesignator().IsMatch(name));
        }

        name = null;
        return false;
    }

    [GeneratedRegex("^[+-]?P[0-9T]")]
    private static partial Regex DurationDesignator();
}
