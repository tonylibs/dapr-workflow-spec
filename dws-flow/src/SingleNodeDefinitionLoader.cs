using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>Loads and validates the Flow half of the shared single-node definition contract.</summary>
public sealed partial class SingleNodeDefinitionLoader
{
    public const string DefinitionPathEnvironmentVariable = "DWS_FLOW_DEFINITION_PATH";

    private static readonly HashSet<string> ValidScopes = ["main", "for", "try", "catch", "forkBranch"];
    private readonly string? definitionPath;

    public SingleNodeDefinitionLoader(string? definitionPath)
    {
        this.definitionPath = definitionPath;
    }

    public SingleNodeDefinition Load()
    {
        if (string.IsNullOrWhiteSpace(definitionPath))
        {
            throw new DefinitionLoadException($"{DefinitionPathEnvironmentVariable} is required but was not set");
        }

        JsonObject definition = ReadDefinition();
        string workflow = RequiredString(definition, "workflow");
        string version = RequiredString(definition, "version");
        string nodeId = RequiredString(definition, "nodeId");
        if (nodeId.Length > 63 || !Dns1123Label().IsMatch(nodeId))
        {
            throw new DefinitionLoadException($"nodeId must be a DNS-1123 label: '{nodeId}'");
        }

        if (RequiredString(definition, "kind") != "flow")
        {
            throw new DefinitionLoadException("definition kind must be 'flow'");
        }

        string scope = RequiredString(definition, "scope");
        if (!ValidScopes.Contains(scope))
        {
            throw new DefinitionLoadException($"scope must be one of: {string.Join(", ", ValidScopes)}");
        }

        if (definition["tasks"] is not JsonArray tasks || tasks.Any(task => task is not JsonObject taskObject || taskObject.Count == 0))
        {
            throw new DefinitionLoadException("flow definition must contain array 'tasks' of non-empty task objects");
        }

        if (definition["children"] is not JsonObject children || !ChildrenAreValid(children))
        {
            throw new DefinitionLoadException("flow definition must contain object 'children' with non-empty app ID values");
        }

        string? catchAppId = OptionalString(definition, "catch");
        if (definition.ContainsKey("catch") && string.IsNullOrWhiteSpace(catchAppId))
        {
            throw new DefinitionLoadException("catch must be a non-empty app ID string when present");
        }

        return new SingleNodeDefinition(workflow, version, nodeId, scope, tasks, children, catchAppId);
    }

    private JsonObject ReadDefinition()
    {
        try
        {
            return JsonNode.Parse(File.ReadAllText(definitionPath!)) as JsonObject
                ?? throw new DefinitionLoadException("single-node definition must be a JSON object");
        }
        catch (DefinitionLoadException)
        {
            throw;
        }
        catch (Exception exception) when (exception is IOException or JsonException or ArgumentException)
        {
            throw new DefinitionLoadException($"failed to load definition '{definitionPath}': {exception.Message}", exception);
        }
    }

    private static string RequiredString(JsonObject definition, string field)
    {
        if (definition[field] is JsonValue value && value.TryGetValue<string>(out string? text) && !string.IsNullOrWhiteSpace(text))
        {
            return text;
        }

        throw new DefinitionLoadException($"definition must contain non-empty string '{field}'");
    }

    private static string? OptionalString(JsonObject definition, string field)
    {
        return definition[field] is JsonValue value && value.TryGetValue<string>(out string? text) ? text : null;
    }

    private static bool ChildrenAreValid(JsonObject children)
    {
        foreach ((string _, JsonNode? value) in children)
        {
            if (value is not JsonValue jsonValue || !jsonValue.TryGetValue<string>(out string? appId) || string.IsNullOrWhiteSpace(appId))
            {
                return false;
            }
        }

        return true;
    }

    [GeneratedRegex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")]
    private static partial Regex Dns1123Label();
}
