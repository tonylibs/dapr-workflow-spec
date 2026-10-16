using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Finds the <c>use</c> names this node's own tasks reference, so only those are fetched at boot.</summary>
public static class UseReferences
{
    public static IReadOnlySet<(UseKind Kind, string Name)> Collect(SingleNodeDefinition definition)
    {
        HashSet<(UseKind Kind, string Name)> references = new();
        foreach (JsonNode? task in definition.Tasks)
        {
            if (task is JsonObject taskObject && taskObject.Single().Value is JsonObject body)
            {
                Add(references, UseKind.Timeouts, body["timeout"]);
                Add(references, UseKind.Errors, (body["raise"] as JsonObject)?["error"]);
                Add(references, UseKind.Retries, (body["catch"] as JsonObject)?["retry"]);
            }
        }

        return references;
    }

    private static void Add(HashSet<(UseKind Kind, string Name)> references, UseKind kind, JsonNode? reference)
    {
        if (UseResolver.IsName(kind, reference) && reference is JsonValue value && value.TryGetValue<string>(out string? name))
        {
            references.Add((kind, name));
        }
    }
}
