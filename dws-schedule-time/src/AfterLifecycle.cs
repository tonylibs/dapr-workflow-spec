using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>The <c>after</c> lifecycle: activation, the status poll, and the one-shot start, all over ETag-guarded state.</summary>
public sealed class AfterLifecycle(
    IVersionedStore store,
    IJobRegistry registry,
    IWorkflowStarter starter,
    StartClaim claim,
    IClusterState cluster,
    TimeProvider time,
    TimeSpan pollInterval,
    ILogger<AfterLifecycle> logger)
{
    /// <summary>Starts sequence 1 when the workflow has no <c>after</c> state yet; a lost first write means another replica activated it.</summary>
    public async Task ActivateAsync(TriggerConfig trigger, CancellationToken cancellationToken)
    {
        string key = AfterState.Key(trigger.Workflow);
        if (await store.GetAsync(key, cancellationToken) is not null)
        {
            return;
        }

        AfterState first = new(trigger.Version, trigger.AppId, 1, AfterState.Running, null);
        if (await store.TryCreateAsync(key, first.ToJson(), null, cancellationToken) is null)
        {
            return;
        }

        StartResult result = await claim.StartAsync(trigger.AppId, trigger.Workflow, InstanceIds.After(trigger.Workflow, 1), cancellationToken);
        if (result.Outcome != StartOutcome.Started)
        {
            logger.LogError(
                "First after start failed for workflow {Workflow} instance {InstanceId}: {Cause}; the poll job retries it",
                trigger.Workflow,
                InstanceIds.After(trigger.Workflow, 1),
                result.Detail);
        }
    }

    /// <summary>Handles one tick of <c>dws-after-poll-&lt;workflow&gt;</c>.</summary>
    public async Task<JobOutcome> PollAsync(string workflow, CancellationToken cancellationToken)
    {
        if (!cluster.Synced)
        {
            return JobOutcome.Retry;
        }

        if (!cluster.ActiveTriggers.TryGetValue(workflow, out TriggerConfig? trigger) || trigger.Kind != TriggerKind.After)
        {
            await registry.DeleteAsync(JobSpecs.AfterPollName(workflow), cancellationToken);
            await registry.DeleteAsync(JobSpecs.AfterStartName(workflow), cancellationToken);
            return JobOutcome.Success;
        }

        VersionedValue? stored = await store.GetAsync(AfterState.Key(workflow), cancellationToken);
        if (stored is null)
        {
            await ActivateAsync(trigger, cancellationToken);
            return JobOutcome.Success;
        }

        AfterState? state = AfterState.TryParse(stored.Value);
        if (state is null)
        {
            logger.LogError("Unreadable after state for workflow {Workflow}; leaving it untouched", workflow);
            return JobOutcome.Success;
        }

        if (state.Phase == AfterState.Waiting)
        {
            if (state.ReadyAt is { } readyAt && time.GetUtcNow() >= readyAt + 2 * pollInterval)
            {
                logger.LogWarning("One-shot start for workflow {Workflow} is overdue; the poll starts the next run itself", workflow);
                return await StartNextAsync(workflow, cancellationToken);
            }

            return JobOutcome.Success;
        }

        return await PollRunningAsync(workflow, trigger, stored, state, cancellationToken);
    }

    /// <summary>Handles the fire of <c>dws-after-start-&lt;workflow&gt;</c> (and the poll's overdue fallback): starts sequence+1 on the currently active version.</summary>
    public async Task<JobOutcome> StartNextAsync(string workflow, CancellationToken cancellationToken)
    {
        if (!cluster.Synced)
        {
            return JobOutcome.Retry;
        }

        VersionedValue? stored = await store.GetAsync(AfterState.Key(workflow), cancellationToken);
        AfterState? state = stored is null ? null : AfterState.TryParse(stored.Value);
        if (stored is null || state is null || state.Phase != AfterState.Waiting)
        {
            return JobOutcome.Success;
        }

        if (!cluster.ActiveTriggers.TryGetValue(workflow, out TriggerConfig? trigger) || trigger.Kind != TriggerKind.After)
        {
            return JobOutcome.Success;
        }

        AfterState next = new(trigger.Version, trigger.AppId, state.Seq + 1, AfterState.Running, null);
        if (await store.TryUpdateAsync(AfterState.Key(workflow), next.ToJson(), stored.ETag, null, cancellationToken) is null)
        {
            return JobOutcome.Success;
        }

        string instanceId = InstanceIds.After(workflow, next.Seq);
        StartResult result = await claim.StartAsync(trigger.AppId, workflow, instanceId, cancellationToken);
        if (result.Outcome == StartOutcome.Retryable)
        {
            logger.LogError("After start failed for workflow {Workflow} instance {InstanceId}: {Cause}", workflow, instanceId, result.Detail);
            return JobOutcome.Retry;
        }

        if (result.Outcome == StartOutcome.NonRetryable)
        {
            logger.LogError("After start refused for workflow {Workflow} instance {InstanceId}: {Cause}", workflow, instanceId, result.Detail);
        }

        return JobOutcome.Success;
    }

    private async Task<JobOutcome> PollRunningAsync(
        string workflow, TriggerConfig trigger, VersionedValue stored, AfterState state, CancellationToken cancellationToken)
    {
        string instanceId = InstanceIds.After(workflow, state.Seq);
        InstanceStatus status = await starter.GetStatusAsync(state.AppId, instanceId, cancellationToken);
        switch (status)
        {
            case InstanceStatus.Terminal:
                return await MarkWaitingAsync(workflow, trigger, stored, state, cancellationToken);
            case InstanceStatus.NotFound:
                if (await claim.IsStartedAsync(instanceId, cancellationToken))
                {
                    logger.LogWarning("Instance {InstanceId} was started but is now unknown to the orchestrator; treating it as finished", instanceId);
                    return await MarkWaitingAsync(workflow, trigger, stored, state, cancellationToken);
                }

                StartResult retry = await claim.StartAsync(state.AppId, workflow, instanceId, cancellationToken);
                if (retry.Outcome != StartOutcome.Started)
                {
                    logger.LogError("Restart of {InstanceId} failed: {Cause}; the next poll tries again", instanceId, retry.Detail);
                }

                return JobOutcome.Success;
            case InstanceStatus.Error:
                logger.LogWarning("Could not read the status of {InstanceId}; the next poll tries again", instanceId);
                return JobOutcome.Success;
            default:
                return JobOutcome.Success;
        }
    }

    private async Task<JobOutcome> MarkWaitingAsync(
        string workflow, TriggerConfig trigger, VersionedValue stored, AfterState state, CancellationToken cancellationToken)
    {
        DateTimeOffset readyAt = time.GetUtcNow() + (trigger.Interval ?? throw new InvalidOperationException("after trigger without a delay"));
        AfterState waiting = state with { Phase = AfterState.Waiting, ReadyAt = readyAt };
        if (await store.TryUpdateAsync(AfterState.Key(workflow), waiting.ToJson(), stored.ETag, null, cancellationToken) is null)
        {
            return JobOutcome.Success;
        }

        JobPayload payload = JobPayload.For(trigger, time.GetUtcNow());
        await registry.ScheduleAsync(new JobRequest(JobSpecs.AfterStartName(workflow), null, readyAt, payload.ToJson()), overwrite: true, cancellationToken);
        return JobOutcome.Success;
    }
}
