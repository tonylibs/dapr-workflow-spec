using System.Text.Json.Nodes;
using Dapr.Workflow;

namespace Dws.Flow;

/// <summary>
/// The production <see cref="IChildCaller"/>: a Step child is called as the <c>Step</c> activity on its
/// own Dapr app ID; a Flow child is called as a child workflow of type <see cref="FlowWorkflow.Name"/> on
/// its own app ID with the deterministic instance ID the caller computed. A child failure's message is
/// unwrapped from Dapr's <see cref="WorkflowTaskFailedException"/> and rethrown unchanged (no prefix).
///
/// <para>Note on the plan's sketched API: <c>WorkflowTaskOptions</c> in Dapr.Workflow 1.18.5 names its
/// app-id property <c>TargetAppId</c>, not <c>AppId</c> as the plan assumed; this type adapts to the real
/// SDK surface.</para>
/// </summary>
public sealed class DaprChildCaller : IChildCaller
{
    private readonly WorkflowContext context;

    public DaprChildCaller(WorkflowContext context)
    {
        this.context = context;
    }

    public Task<JsonNode?> CallStep(string appId, FlowInput input) =>
        Unwrap(context.CallActivityAsync<JsonNode?>(StepActivityName, input, new WorkflowTaskOptions(TargetAppId: appId)));

    public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input) =>
        Unwrap(context.CallChildWorkflowAsync<JsonNode?>(
            FlowWorkflow.Name,
            input,
            new ChildWorkflowTaskOptions(InstanceId: instanceId, TargetAppId: appId)));

    /// <summary>The <c>Step</c> activity's constant name, shared with the dws-step package's contract.</summary>
    public const string StepActivityName = "Step";

    private static async Task<JsonNode?> Unwrap(Task<JsonNode?> call)
    {
        try
        {
            return await call;
        }
        catch (WorkflowTaskFailedException exception)
        {
            throw new InvalidOperationException(exception.FailureDetails.ErrorMessage);
        }
    }
}
