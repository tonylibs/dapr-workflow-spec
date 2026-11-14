namespace Dws.ScheduleTime;

/// <summary>Thread-safe in-memory copy of the trigger ConfigMaps and managed Deployments, with the active triggers derived from it.</summary>
public sealed class ClusterSnapshot : IClusterState
{
    private readonly object gate = new();
    private readonly Dictionary<string, TriggerResource> triggers = [];
    private readonly Dictionary<string, DeploymentInfo> deployments = [];
    private bool triggersListed;
    private bool deploymentsListed;
    private ActiveTriggerSet resolved = new(new Dictionary<string, TriggerConfig>(), []);
    private readonly TaskCompletionSource synced = new(TaskCreationOptions.RunContinuationsAsynchronously);

    public bool Synced
    {
        get
        {
            lock (gate)
            {
                return triggersListed && deploymentsListed;
            }
        }
    }

    public async Task<bool> WaitForSyncAsync(TimeSpan timeout, CancellationToken cancellationToken)
    {
        try
        {
            await synced.Task.WaitAsync(timeout, cancellationToken);
            return true;
        }
        catch (TimeoutException)
        {
            return false;
        }
    }

    public IReadOnlyDictionary<string, TriggerConfig> ActiveTriggers => Resolved.Triggers;

    public IReadOnlyList<string> Errors => Resolved.Errors;

    public ActiveTriggerSet Resolved
    {
        get
        {
            lock (gate)
            {
                return resolved;
            }
        }
    }

    /// <summary>Replaces every known trigger ConfigMap with the result of a fresh list.</summary>
    public void ReplaceTriggers(IEnumerable<TriggerResource> items) => Mutate(() =>
    {
        triggers.Clear();
        foreach (TriggerResource item in items)
        {
            triggers[item.Name] = item;
        }

        triggersListed = true;
    });

    public void UpsertTrigger(TriggerResource item) => Mutate(() => triggers[item.Name] = item);

    public void RemoveTrigger(string name) => Mutate(() => triggers.Remove(name));

    /// <summary>Replaces every known Deployment with the result of a fresh list, keyed by name.</summary>
    public void ReplaceDeployments(IEnumerable<KeyValuePair<string, DeploymentInfo>> items) => Mutate(() =>
    {
        deployments.Clear();
        foreach ((string name, DeploymentInfo info) in items)
        {
            deployments[name] = info;
        }

        deploymentsListed = true;
    });

    public void UpsertDeployment(string name, DeploymentInfo info) => Mutate(() => deployments[name] = info);

    public void RemoveDeployment(string name) => Mutate(() => deployments.Remove(name));

    private void Mutate(Action change)
    {
        lock (gate)
        {
            change();
            resolved = Resolve();
            if (triggersListed && deploymentsListed)
            {
                synced.TrySetResult();
            }
        }
    }

    private ActiveTriggerSet Resolve() => Dws.ScheduleTime.ActiveTriggers.Resolve(triggers.Values, deployments.Values);
}
