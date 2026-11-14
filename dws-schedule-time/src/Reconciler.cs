using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Brings the registered Dapr jobs in line with the active triggers; idempotent, so replicas may reconcile concurrently.</summary>
public sealed class Reconciler(
    IJobRegistry registry, AfterLifecycle after, TimeProvider time, TimeSpan pollInterval, ILogger<Reconciler> logger)
{
    public async Task ReconcileAsync(IReadOnlyDictionary<string, TriggerConfig> active, CancellationToken cancellationToken)
    {
        IReadOnlyList<RegisteredJob> existing = await registry.ListAsync(cancellationToken);
        Dictionary<string, RegisteredJob> byName = existing.ToDictionary(job => job.Name, StringComparer.Ordinal);
        HashSet<string> wanted = new(StringComparer.Ordinal);

        foreach ((string workflow, TriggerConfig trigger) in active.OrderBy(pair => pair.Key, StringComparer.Ordinal))
        {
            if (trigger.Kind == TriggerKind.After)
            {
                string poll = JobSpecs.AfterPollName(workflow);
                wanted.Add(poll);
                wanted.Add(JobSpecs.AfterStartName(workflow));
                await EnsureAsync(poll, byName, PollPayload(workflow, trigger), JobSpecs.PollExpression(pollInterval), cancellationToken);
                await after.ActivateAsync(trigger, cancellationToken);
            }
            else
            {
                string name = JobSpecs.ScheduledName(workflow);
                wanted.Add(name);
                await EnsureAsync(name, byName, JobPayload.For(trigger, time.GetUtcNow()), JobSpecs.ScheduleExpression(trigger), cancellationToken);
            }
        }

        foreach (RegisteredJob job in existing.Where(job => JobSpecs.TryParseName(job.Name, out _, out _) && !wanted.Contains(job.Name)))
        {
            logger.LogInformation("Deleting job {Job} with no active trigger", job.Name);
            await registry.DeleteAsync(job.Name, cancellationToken);
        }
    }

    private JobPayload PollPayload(string workflow, TriggerConfig trigger) =>
        new(workflow, string.Empty, trigger.AppId, TriggerKind.After, JobSpecs.GoDuration(pollInterval), time.GetUtcNow());

    private async Task EnsureAsync(
        string name, Dictionary<string, RegisteredJob> known, JobPayload desired, string expression, CancellationToken cancellationToken)
    {
        // The anchor is taken before the schedule call, so a tick is never counted from after its registration.
        JobRequest Request() => new(name, expression, null, (desired with { Anchor = time.GetUtcNow() }).ToJson());

        if (!known.TryGetValue(name, out RegisteredJob? current))
        {
            if (await registry.ScheduleAsync(Request(), overwrite: false, cancellationToken))
            {
                logger.LogInformation("Registered job {Job} as {Expression}", name, expression);
                return;
            }

            // Another replica registered it between our list and our write; compare against what it wrote.
            current = await registry.GetAsync(name, cancellationToken);
        }

        if (current is not null && desired.SameJobAs(current.ParsedPayload))
        {
            return;
        }

        // Re-read right before overwriting so a replica that just fixed the job is not re-anchored by this one.
        RegisteredJob? latest = await registry.GetAsync(name, cancellationToken);
        if (latest is not null && desired.SameJobAs(latest.ParsedPayload))
        {
            return;
        }

        await registry.ScheduleAsync(Request(), overwrite: true, cancellationToken);
        logger.LogInformation("Overwrote job {Job} as {Expression}", name, expression);
    }
}
