namespace Dws.ScheduleTime;

/// <summary>The active triggers per workflow plus a message for every trigger ConfigMap that was skipped.</summary>
public sealed record ActiveTriggerSet(IReadOnlyDictionary<string, TriggerConfig> Triggers, IReadOnlyList<string> Errors);

/// <summary>Resolves each workflow's active trigger: the trigger of the version whose orchestrator Deployment is not draining.</summary>
public static class ActiveTriggers
{
    public static ActiveTriggerSet Resolve(IEnumerable<TriggerResource> triggers, IEnumerable<DeploymentInfo> deployments)
    {
        HashSet<(string Workflow, string Version)> live = deployments
            .Where(deployment => !ClusterContract.IsDraining(deployment))
            .Select(deployment => (deployment.Workflow, deployment.Version))
            .ToHashSet();

        List<string> errors = [];
        Dictionary<string, TriggerConfig> active = [];

        foreach (TriggerResource resource in triggers.OrderBy(resource => resource.Name, StringComparer.Ordinal))
        {
            TriggerConfig config;
            try
            {
                config = TriggerConfig.Parse(resource.TriggerJson);
            }
            catch (TriggerConfigException exception)
            {
                errors.Add($"trigger ConfigMap '{resource.Name}' skipped: {exception.Message}");
                continue;
            }

            if (resource.Labels.TryGetValue(ClusterContract.WorkflowLabel, out string? labelled) && labelled != config.Workflow)
            {
                errors.Add($"trigger ConfigMap '{resource.Name}' skipped: label {ClusterContract.WorkflowLabel}='{labelled}' disagrees with workflow '{config.Workflow}'");
                continue;
            }

            if (!live.Contains((config.Workflow, config.Version)))
            {
                continue;
            }

            // Two live versions only coexist briefly during a takeover; the highest version name wins so every replica agrees.
            if (!active.TryGetValue(config.Workflow, out TriggerConfig? current)
                || string.CompareOrdinal(config.Version, current.Version) > 0)
            {
                active[config.Workflow] = config;
            }
        }

        return new ActiveTriggerSet(active, errors);
    }
}
