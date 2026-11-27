using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace Dws.Flow;

/// <summary>Loads and validates the Flow half of the shared single-node definition contract.</summary>
public sealed partial class SingleNodeDefinitionLoader
{
    public const string DefinitionPathEnvironmentVariable = "DWS_FLOW_DEFINITION_PATH";

    private const int MaxLabelLength = 63;
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
        if (!IsDns1123Label(nodeId))
        {
            throw new DefinitionLoadException($"nodeId must be a DNS-1123 label: '{nodeId}'");
        }

        if (RequiredString(definition, "kind") != "flow")
        {
            throw new DefinitionLoadException("definition kind must be 'flow'");
        }

        string scope = ReadScope(definition);
        JsonArray tasks = ReadTasks(definition);
        JsonObject children = ReadChildren(definition);
        string? catchAppId = ReadCatch(definition);
        if (Scopes.LeafControllers.Contains(scope) && (tasks.Count != 0 || children.Count != 0))
        {
            throw new DefinitionLoadException($"scope '{scope}' requires empty 'tasks' and 'children'");
        }

        JsonNode? wait = ReadWait(definition, scope);
        (JsonObject? to, JsonNode? timeout, string? read) = ReadListen(definition, scope);
        string? dataflow = ReadDataflow(definition, scope);
        return new SingleNodeDefinition(workflow, version, nodeId, scope, tasks, children, catchAppId,
            wait, to, timeout, read, dataflow);
    }

    private static string ReadScope(JsonObject definition)
    {
        string scope = RequiredString(definition, "scope");
        if (!Scopes.All.Contains(scope))
        {
            throw new DefinitionLoadException($"scope must be one of: {string.Join(", ", Scopes.All)}");
        }

        return scope;
    }

    private static JsonArray ReadTasks(JsonObject definition)
    {
        if (definition["tasks"] is not JsonArray tasks || tasks.Any(task => task is not JsonObject taskObject || taskObject.Count == 0))
        {
            throw new DefinitionLoadException("flow definition must contain array 'tasks' of non-empty task objects");
        }

        return tasks;
    }

    private static JsonObject ReadChildren(JsonObject definition)
    {
        if (definition["children"] is not JsonObject children || !ChildrenAreValid(children))
        {
            throw new DefinitionLoadException("flow definition must contain object 'children' with non-empty app ID values");
        }

        return children;
    }

    private static string? ReadCatch(JsonObject definition)
    {
        string? catchAppId = OptionalString(definition, "catch");
        if (definition.ContainsKey("catch") && string.IsNullOrWhiteSpace(catchAppId))
        {
            throw new DefinitionLoadException("catch must be a non-empty app ID string when present");
        }

        return catchAppId;
    }

    private static JsonNode? ReadWait(JsonObject definition, string scope)
    {
        if (scope != Scopes.Wait)
        {
            RejectFields(definition, scope, "wait");
            return null;
        }

        return ReadDuration(definition, "wait");
    }

    private static (JsonObject? To, JsonNode? Timeout, string? Read) ReadListen(JsonObject definition, string scope)
    {
        if (scope != Scopes.Listen)
        {
            RejectFields(definition, scope, "to", "timeout", "read");
            return (null, null, null);
        }

        JsonObject to = definition["to"] as JsonObject
            ?? throw new DefinitionLoadException("listen definition must contain object 'to'");
        JsonNode? timeout = definition.ContainsKey("timeout") ? ReadDuration(definition, "timeout") : null;
        string? read = OptionalString(definition, "read");
        if (definition.ContainsKey("read") && string.IsNullOrEmpty(read))
        {
            throw new DefinitionLoadException("read must be a non-empty string when present");
        }

        return (to, timeout, read);
    }

    private static string? ReadDataflow(JsonObject definition, string scope)
    {
        if (!definition.ContainsKey("dataflow"))
        {
            return null;
        }

        if (scope == Scopes.Main)
        {
            throw new DefinitionLoadException("dataflow is not allowed on scope 'main'");
        }

        string? dataflow = OptionalString(definition, "dataflow");
        if (dataflow is null || !IsDns1123Label(dataflow))
        {
            throw new DefinitionLoadException("dataflow must be a DNS-1123 label");
        }

        return dataflow;
    }

    private static JsonNode ReadDuration(JsonObject definition, string field)
    {
        JsonNode? value = definition[field];
        if (value is JsonObject || value is JsonValue jsonValue && jsonValue.TryGetValue<string>(out _))
        {
            return value;
        }

        throw new DefinitionLoadException($"'{field}' must be a string or an object");
    }

    private static void RejectFields(JsonObject definition, string scope, params string[] fields)
    {
        foreach (string field in fields)
        {
            if (definition.ContainsKey(field))
            {
                throw new DefinitionLoadException($"'{field}' is not allowed on scope '{scope}'");
            }
        }
    }

    private static bool IsDns1123Label(string value) => value.Length <= MaxLabelLength && Dns1123Label().IsMatch(value);


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

    [GeneratedRegex(@"^[a-z0-9]([-a-z0-9]*[a-z0-9])?\z")]
    private static partial Regex Dns1123Label();
}
