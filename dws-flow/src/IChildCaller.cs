using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Calls a Step child activity or a Flow child workflow, with optional behavior supplied by decorators.</summary>
public interface IChildCaller
{
    Task<JsonNode?> CallStep(string appId, FlowInput input);

    Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input);
}
