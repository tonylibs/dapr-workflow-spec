namespace Dws.ScheduleTime.Tests;

internal sealed class ManualTime(DateTimeOffset start) : TimeProvider
{
    private DateTimeOffset now = start;

    public override DateTimeOffset GetUtcNow() => now;

    public void Advance(TimeSpan by) => now += by;
}

internal sealed class StaticClusterState(bool synced, params TriggerConfig[] triggers) : IClusterState
{
    public bool Synced => synced;

    public Task<bool> WaitForSyncAsync(TimeSpan timeout, CancellationToken cancellationToken) => Task.FromResult(synced);

    public IReadOnlyDictionary<string, TriggerConfig> ActiveTriggers { get; set; } = triggers.ToDictionary(trigger => trigger.Workflow);
}

internal sealed record ScheduleCall(JobRequest Job, bool Overwrite, bool Applied);

internal sealed class RecordingJobRegistry : IJobRegistry
{
    private readonly object gate = new();
    private readonly Dictionary<string, RegisteredJob> jobs = [];

    public List<ScheduleCall> Calls { get; } = [];

    public List<string> Deleted { get; } = [];

    public IReadOnlyDictionary<string, RegisteredJob> Jobs
    {
        get
        {
            lock (gate)
            {
                return new Dictionary<string, RegisteredJob>(jobs);
            }
        }
    }

    public void Seed(string name, string? schedule, string? payload)
    {
        lock (gate)
        {
            jobs[name] = new RegisteredJob(name, schedule, payload);
        }
    }

    public async Task<RegisteredJob?> GetAsync(string name, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            return jobs.GetValueOrDefault(name);
        }
    }

    public async Task<IReadOnlyList<RegisteredJob>> ListAsync(CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            return jobs.Values.ToList();
        }
    }

    public async Task<bool> ScheduleAsync(JobRequest job, bool overwrite, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            bool applied = overwrite || !jobs.ContainsKey(job.Name);
            Calls.Add(new ScheduleCall(job, overwrite, applied));
            if (applied)
            {
                jobs[job.Name] = new RegisteredJob(job.Name, job.Expression ?? job.DueTime?.ToString("O"), job.Payload);
            }

            return applied;
        }
    }

    public Task DeleteAsync(string name, CancellationToken cancellationToken)
    {
        lock (gate)
        {
            jobs.Remove(name);
            Deleted.Add(name);
        }

        return Task.CompletedTask;
    }
}

internal sealed class InMemoryVersionedStore : IVersionedStore
{
    private readonly object gate = new();
    private readonly Dictionary<string, VersionedValue> values = [];
    private long counter;

    public async Task<VersionedValue?> GetAsync(string key, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            return values.GetValueOrDefault(key);
        }
    }

    public async Task<VersionedValue?> TryCreateAsync(string key, string value, TimeSpan? ttl, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            if (values.ContainsKey(key))
            {
                return null;
            }

            return values[key] = new VersionedValue(value, (++counter).ToString());
        }
    }

    public async Task<VersionedValue?> TryUpdateAsync(string key, string value, string etag, TimeSpan? ttl, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            if (!values.TryGetValue(key, out VersionedValue? current) || current.ETag != etag)
            {
                return null;
            }

            return values[key] = new VersionedValue(value, (++counter).ToString());
        }
    }

    public async Task<bool> TryDeleteAsync(string key, string etag, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            return values.TryGetValue(key, out VersionedValue? current) && current.ETag == etag && values.Remove(key);
        }
    }

    public void Put(string key, string value)
    {
        lock (gate)
        {
            values[key] = new VersionedValue(value, (++counter).ToString());
        }
    }

    public string? Peek(string key)
    {
        lock (gate)
        {
            return values.GetValueOrDefault(key)?.Value;
        }
    }
}

internal sealed record StartCall(string AppId, string Workflow, string InstanceId, string Input);

internal sealed class RecordingStarter : IWorkflowStarter
{
    private readonly object gate = new();
    private readonly Queue<StartResult> results = [];

    public List<StartCall> Starts { get; } = [];

    public Dictionary<string, InstanceStatus> Statuses { get; } = [];

    public List<string> StatusQueries { get; } = [];

    public void Enqueue(StartResult result)
    {
        lock (gate)
        {
            results.Enqueue(result);
        }
    }

    public async Task<StartResult> StartAsync(string appId, string workflow, string instanceId, string inputJson, CancellationToken cancellationToken)
    {
        await Task.Yield();
        lock (gate)
        {
            Starts.Add(new StartCall(appId, workflow, instanceId, inputJson));
            return results.Count > 0 ? results.Dequeue() : new StartResult(StartOutcome.Started, "HTTP 202");
        }
    }

    public Task<InstanceStatus> GetStatusAsync(string appId, string instanceId, CancellationToken cancellationToken)
    {
        lock (gate)
        {
            StatusQueries.Add($"{appId}/{instanceId}");
            return Task.FromResult(Statuses.GetValueOrDefault(instanceId, InstanceStatus.NotFound));
        }
    }
}

internal static class Triggers
{
    public static TriggerConfig Every(string workflow = "order-sync", string version = "v1", string schedule = "PT1H") =>
        TriggerConfig.Parse($$"""{"workflow":"{{workflow}}","version":"{{version}}","appId":"{{workflow}}-{{version}}","kind":"every","schedule":"{{schedule}}"}""");

    public static TriggerConfig Cron(string workflow = "order-sync", string version = "v1", string schedule = "0 * * * *") =>
        TriggerConfig.Parse($$"""{"workflow":"{{workflow}}","version":"{{version}}","appId":"{{workflow}}-{{version}}","kind":"cron","schedule":"{{schedule}}"}""");

    public static TriggerConfig After(string workflow = "order-sync", string version = "v1", string schedule = "PT10M") =>
        TriggerConfig.Parse($$"""{"workflow":"{{workflow}}","version":"{{version}}","appId":"{{workflow}}-{{version}}","kind":"after","schedule":"{{schedule}}"}""");

    public static string Json(TriggerConfig trigger) =>
        $$"""{"workflow":"{{trigger.Workflow}}","version":"{{trigger.Version}}","appId":"{{trigger.AppId}}","kind":"{{trigger.Kind.ToString().ToLowerInvariant()}}","schedule":"{{trigger.Schedule}}"}""";
}
