using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>Builds the boot-time <see cref="UseResolver"/>: fetches exactly the names the node references and fails
/// the boot, naming kind and name, when any is missing or invalid.</summary>
public static partial class UseResolverLoader
{
    public const string StoreEnvironmentVariable = "DWS_FLOW_USE_STORE";

    public static async Task<UseResolver> Load(SingleNodeDefinition definition, string? storeName, IUseStore store)
    {
        IReadOnlyList<(UseKind Kind, string Name)> references = UseReferences.Collect(definition)
            .OrderBy(reference => reference.Kind).ThenBy(reference => reference.Name, StringComparer.Ordinal).ToList();
        if (references.Count == 0)
        {
            return UseResolver.Empty;
        }

        foreach ((UseKind kind, string name) in references.Where(reference => !KeyName().IsMatch(reference.Name)))
        {
            throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} name '{name}' must match [-._a-zA-Z0-9]+");
        }

        if (string.IsNullOrWhiteSpace(storeName))
        {
            (UseKind firstKind, string firstName) = references[0];
            throw new DefinitionLoadException(
                $"{StoreEnvironmentVariable} is required because this node references use.{UseResolver.KindName(firstKind)} '{firstName}'");
        }

        string[] keys = references.Select(reference => $"{UseResolver.KindName(reference.Kind)}.{reference.Name}").ToArray();
        IReadOnlyDictionary<string, string> found = await store.Get(storeName, keys);

        Dictionary<(UseKind Kind, string Name), string> entries = new();
        foreach ((UseKind kind, string name) in references)
        {
            string key = $"{UseResolver.KindName(kind)}.{name}";
            if (!found.TryGetValue(key, out string? json) || string.IsNullOrWhiteSpace(json))
            {
                throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not defined in configuration store '{storeName}'");
            }

            Validate(kind, name, json);
            entries[(kind, name)] = json;
        }

        return new UseResolver(entries);
    }

    private static void Validate(UseKind kind, string name, string json)
    {
        try
        {
            if (JsonNode.Parse(json) is not JsonObject entry)
            {
                throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not a valid entry: expected a JSON object");
            }

            if (kind == UseKind.Timeouts)
            {
                TaskTimeout.Parse(entry);
            }
        }
        catch (DefinitionLoadException)
        {
            throw; // don't double-wrap
        }
        catch (Exception exception) when (exception is System.Text.Json.JsonException or InvalidOperationException)
        {
            throw new DefinitionLoadException($"use.{UseResolver.KindName(kind)} '{name}' is not a valid entry: {exception.Message}", exception);
        }
    }

    [GeneratedRegex("^[-._a-zA-Z0-9]+$")]
    private static partial Regex KeyName();
}
