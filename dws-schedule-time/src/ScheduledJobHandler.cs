using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Handles a fire of <c>dws-sched-&lt;workflow&gt;</c>: resolves the active trigger now, computes the fire key and starts through the claim.</summary>
public sealed class ScheduledJobHandler(
    IClusterState cluster, IJobRegistry registry, StartClaim claim, TimeProvider time, ILogger<ScheduledJobHandler> logger)
{
    public Task<JobOutcome> HandleAsync(string workflow, string payloadText, CancellationToken cancellationToken) =>
        HandleAsync(workflow, payloadText, time.GetUtcNow(), cancellationToken);

    /// <summary>Handles a fire whose callback arrived at <paramref name="deliveredAt"/>; the fire key derives from that time.</summary>
    public async Task<JobOutcome> HandleAsync(string workflow, string payloadText, DateTimeOffset deliveredAt, CancellationToken cancellationToken)
    {
        string jobName = JobSpecs.ScheduledName(workflow);
        JobPayload? payload = JobPayload.TryParse(payloadText);
        if (payload is null)
        {
            logger.LogError("Dropping fire of {Job}: its payload is unreadable", jobName);
            return JobOutcome.Success;
        }

        // Before the first full list the view is empty; acting on it would delete live jobs after a restart.
        if (!cluster.Synced)
        {
            return JobOutcome.Retry;
        }

        if (!cluster.ActiveTriggers.TryGetValue(workflow, out TriggerConfig? active) || active.Kind == TriggerKind.After)
        {
            logger.LogInformation("Deleting job {Job}: workflow {Workflow} has no active every/cron trigger", jobName, workflow);
            await registry.DeleteAsync(jobName, cancellationToken);
            return JobOutcome.Success;
        }

        DateTimeOffset delivered = deliveredAt;
        string? instanceId = FireKey(payload, workflow, delivered);
        if (instanceId is null)
        {
            logger.LogError("Dropping fire of {Job}: payload kind {Kind} / schedule {Schedule} is not an every or cron job", jobName, payload.Kind, payload.Schedule);
            return JobOutcome.Success;
        }

        StartResult result = await claim.StartAsync(active.AppId, workflow, instanceId, cancellationToken);
        switch (result.Outcome)
        {
            case StartOutcome.Retryable:
                logger.LogError("Fire failed for workflow {Workflow} fire key {FireKey}: {Cause}", workflow, instanceId, result.Detail);
                return JobOutcome.Retry;
            case StartOutcome.NonRetryable:
                logger.LogError("Fire refused for workflow {Workflow} fire key {FireKey}: {Cause}", workflow, instanceId, result.Detail);
                return JobOutcome.Success;
            default:
                return JobOutcome.Success;
        }
    }

    private static string? FireKey(JobPayload payload, string workflow, DateTimeOffset delivered)
    {
        if (payload.Kind == TriggerKind.Cron)
        {
            return InstanceIds.Cron(workflow, delivered);
        }

        if (payload.Kind == TriggerKind.Every)
        {
            try
            {
                return InstanceIds.Every(workflow, payload.Anchor, TriggerConfig.ParseDuration(payload.Schedule), delivered);
            }
            catch (TriggerConfigException)
            {
                return null;
            }
        }

        return null;
    }
}
