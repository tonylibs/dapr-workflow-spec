using System.Text.Json.Nodes;
using Dapr.Workflow;

namespace Dws.Flow;

/// <summary>Thin entry point: routes the pinned node's scope through <see cref="ScopeDispatch"/> and runs it.</summary>
public sealed class FlowWorkflow : Workflow<FlowInput, JsonNode?>
{
    public const string Name = "Flow";

    public override Task<JsonNode?> RunAsync(WorkflowContext context, FlowInput? input)
    {
        SingleNodeDefinition definition = FlowDefinitionHolder.Definition;
        FlowInput effectiveInput = input ?? new FlowInput(null, null, null, null);
        FlowInput resolvedInput = effectiveInput with { RootInstanceId = effectiveInput.RootInstanceId ?? context.InstanceId };

        if (ScopeDispatch.Resolve(definition.Scope) == ScopeKind.NotImplemented)
        {
            throw new InvalidOperationException(ScopeDispatch.NotImplementedMessage(definition.Scope));
        }

        return Task.FromResult(resolvedInput.Data);
    }
}
