using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Validated, immutable representation of this process's one Flow node.</summary>
public sealed record SingleNodeDefinition(
    string Workflow,
    string Version,
    string NodeId,
    string Scope,
    JsonArray Tasks,
    JsonObject Children,
    string? Catch);
