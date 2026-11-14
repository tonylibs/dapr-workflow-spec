namespace Dws.ScheduleTime;

/// <summary>The scheduler's view of the cluster, read by job handlers at fire time.</summary>
public interface IClusterState
{
    /// <summary>False until the first full list of both resource kinds has been applied; handlers must not act on an empty view before then.</summary>
    bool Synced { get; }

    /// <summary>Completes with true once <see cref="Synced"/> holds, or with false when <paramref name="timeout"/> passes first.</summary>
    Task<bool> WaitForSyncAsync(TimeSpan timeout, CancellationToken cancellationToken);

    IReadOnlyDictionary<string, TriggerConfig> ActiveTriggers { get; }
}
