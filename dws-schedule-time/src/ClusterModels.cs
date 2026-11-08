namespace Dws.ScheduleTime;

/// <summary>A snapshot of one trigger ConfigMap: its name, labels and the text of its <c>trigger.json</c> key.</summary>
public sealed record TriggerResource(string Name, IReadOnlyDictionary<string, string> Labels, string TriggerJson);

/// <summary>A snapshot of one managed orchestrator Deployment: its workflow, version and annotations.</summary>
public sealed record DeploymentInfo(string Workflow, string Version, IReadOnlyDictionary<string, string> Annotations);

/// <summary>The cluster contract the scheduler reads: label and annotation names, shared with dws-controller.</summary>
public static class ClusterContract
{
    public const string TriggerLabel = "dws.io/trigger";

    public const string TriggerLabelValue = "time";

    public const string ManagedByLabel = "dws.io/managed-by";

    public const string ManagedByValue = "dws-controller";

    public const string WorkflowLabel = "dws.io/workflow";

    public const string VersionLabel = "dws.io/version";

    public const string TriggerKey = "trigger.json";

    /// <summary>
    /// The drain marker is an ANNOTATION with value "true", matching dws-controller's
    /// StackApplier.markForDrain / isDraining. The spec text says "labeled", but the controller writes an annotation.
    /// </summary>
    public const string DrainAnnotation = "dws.io/drain";

    public static bool IsDraining(DeploymentInfo deployment) =>
        deployment.Annotations.TryGetValue(DrainAnnotation, out string? value) && value == "true";
}
