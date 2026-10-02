using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>
/// The Sequencer's one seam onto Dapr's durable workflow context: calling a Step child activity, calling
/// a Flow child workflow, and racing a child call against a task's declared timeout. Tests substitute a
/// recording stand-in; production code uses the Dapr-backed implementation over <c>WorkflowContext</c>.
/// </summary>
public interface IChildCaller
{
    Task<JsonNode?> CallStep(string appId, FlowInput input);

    Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input);
}
