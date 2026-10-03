using System.Text.Json.Nodes;
using System.Text.Json.Serialization;

namespace Dws.Flow;

/// <summary>
/// The envelope passed to the Flow workflow itself and, unchanged in shape, to every Step/Flow child it
/// dispatches: current workflow data, scope-local variables, the root workflow instance ID and the
/// enclosing iteration index. Mirrors dws-step's <c>StepInput</c> field names exactly.
/// </summary>
public sealed record FlowInput(
    [property: JsonPropertyName("data")] JsonNode? Data,
    [property: JsonPropertyName("variables")] IReadOnlyDictionary<string, JsonNode?>? Variables,
    [property: JsonPropertyName("workflowInstanceId")] string? RootInstanceId,
    [property: JsonPropertyName("iterationIndex")] string? IterationIndex);
