using k8s.Models;

namespace Dws.ScheduleTime;

/// <summary>Maps Kubernetes API objects onto the scheduler's snapshot types.</summary>
public static class ClusterMapping
{
    public static TriggerResource ToTrigger(V1ConfigMap configMap) =>
        new(
            configMap.Metadata?.Name ?? string.Empty,
            Copy(configMap.Metadata?.Labels),
            configMap.Data is not null && configMap.Data.TryGetValue(ClusterContract.TriggerKey, out string? json) ? json : string.Empty);

    /// <summary>Maps a Deployment; null when it lacks the workflow or version label and so cannot be an orchestrator of ours.</summary>
    public static DeploymentInfo? ToDeployment(V1Deployment deployment)
    {
        IDictionary<string, string>? labels = deployment.Metadata?.Labels;
        if (labels is null
            || !labels.TryGetValue(ClusterContract.WorkflowLabel, out string? workflow)
            || !labels.TryGetValue(ClusterContract.VersionLabel, out string? version))
        {
            return null;
        }

        return new DeploymentInfo(workflow, version, Copy(deployment.Metadata?.Annotations));
    }

    private static Dictionary<string, string> Copy(IDictionary<string, string>? source) =>
        source is null ? [] : new Dictionary<string, string>(source);
}
