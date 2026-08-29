using Dapr.Workflow;

namespace Dws.Flow;

/// <summary>Phase-zero no-op workflow; sequencing and child dispatch land in a later phase.</summary>
public sealed class FlowWorkflow : Workflow<object?, object?>
{
    public const string Name = "Flow";

    public override Task<object?> RunAsync(WorkflowContext context, object? input)
    {
        if (!context.IsReplaying)
        {
            SingleNodeDefinition definition = FlowDefinitionHolder.Definition;
            Console.WriteLine($"Running no-op Flow workflow for scope '{definition.Scope}' with {definition.Tasks.Count} task(s)");
        }

        return Task.FromResult<object?>(null);
    }
}
