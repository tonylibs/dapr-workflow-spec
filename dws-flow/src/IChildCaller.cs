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

    /// <summary>Races an already-started child call against a durable timer for <paramref name="timeout"/>,
    /// cancelling the timer when the call wins. Reports <c>TimedOut: true</c> when the timer wins instead of
    /// throwing, so the caller can render the v1-wording failure with the task name it alone knows.</summary>
    Task<(bool TimedOut, JsonNode? Result)> WithTimeout(TimeSpan timeout, Task<JsonNode?> call);
}
