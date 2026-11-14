using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class JobDispatcherTests
{
    private static readonly IReadOnlyDictionary<string, string> NoMetadata = new Dictionary<string, string>();

    private readonly ManualTime time = new(new DateTimeOffset(2026, 11, 6, 10, 0, 0, TimeSpan.Zero));
    private readonly RecordingJobRegistry registry = new();
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();

    private JobDispatcher NewDispatcher(IClusterState cluster, TimeSpan? syncWait = null)
    {
        StartClaim claim = new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);
        AfterLifecycle after = new(store, registry, starter, claim, cluster, time, TimeSpan.FromSeconds(15), NullLogger<AfterLifecycle>.Instance);
        ScheduledJobHandler scheduled = new(cluster, registry, claim, time, NullLogger<ScheduledJobHandler>.Instance);
        return new JobDispatcher(scheduled, after, cluster, time, syncWait ?? TimeSpan.FromSeconds(5), NullLogger<JobDispatcher>.Instance);
    }

    private static void List(ClusterSnapshot snapshot, TriggerConfig trigger)
    {
        snapshot.ReplaceTriggers([new TriggerResource($"trigger-{trigger.Workflow}", NoMetadata, Triggers.Json(trigger))]);
        snapshot.ReplaceDeployments([KeyValuePair.Create($"{trigger.Workflow}-{trigger.Version}", new DeploymentInfo(trigger.Workflow, trigger.Version, NoMetadata))]);
    }

    [Fact]
    public async Task AcknowledgesAnUnknownJobWithoutDoingAnything()
    {
        JobOutcome outcome = await NewDispatcher(new StaticClusterState(true)).DispatchAsync("somebody-elses-job", "{}", default);

        outcome.Should().Be(JobOutcome.Success);
        registry.Deleted.Should().BeEmpty();
    }

    [Fact]
    public async Task RoutesByPrefix()
    {
        TriggerConfig cron = Triggers.Cron();
        JobDispatcher dispatcher = NewDispatcher(new StaticClusterState(true, cron));

        await dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(cron, time.GetUtcNow()).ToJson(), default);
        await dispatcher.DispatchAsync("dws-after-poll-order-sync", "{}", default);
        await dispatcher.DispatchAsync("dws-after-start-order-sync", "{}", default);

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().StartWith("dws-sched:order-sync:");
        registry.Deleted.Should().Contain("dws-after-poll-order-sync");
    }

    // Task 9.4 live run: Dapr delivered the missed ticks, and retried each at once, before the first
    // cluster list finished, so answering "retry" lost the whole replayed burst.
    [Fact]
    public async Task HoldsACallbackThatArrivesBeforeTheFirstListAndStartsOnceTheListCompletes()
    {
        TriggerConfig every = Triggers.Every(schedule: "PT1M");
        ClusterSnapshot snapshot = new();
        JobDispatcher dispatcher = NewDispatcher(snapshot);

        Task<JobOutcome> pending = dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(every, time.GetUtcNow().AddMinutes(-5)).ToJson(), default);
        await Task.Delay(50);
        pending.IsCompleted.Should().BeFalse("the callback must wait for the first full list, not answer retry");

        List(snapshot, every);

        (await pending).Should().Be(JobOutcome.Success);
        starter.Starts.Should().ContainSingle();
        registry.Deleted.Should().BeEmpty("an unlisted view must never delete a live job");
    }

    [Fact]
    public async Task CollapsesAReplayedBurstHeldDuringTheFirstListOntoOneInstance()
    {
        TriggerConfig every = Triggers.Every(schedule: "PT1M");
        ClusterSnapshot snapshot = new();
        JobDispatcher dispatcher = NewDispatcher(snapshot);
        string payload = JobPayload.For(every, time.GetUtcNow().AddMinutes(-5)).ToJson();

        // Four missed ticks, each with three immediate retries, as observed live.
        Task<JobOutcome>[] burst = Enumerable.Range(0, 16).Select(_ => dispatcher.DispatchAsync("dws-sched-order-sync", payload, default)).ToArray();
        await Task.Delay(50);
        List(snapshot, every);
        JobOutcome[] outcomes = await Task.WhenAll(burst);

        outcomes.Should().OnlyContain(outcome => outcome == JobOutcome.Success || outcome == JobOutcome.Retry);
        starter.Starts.Select(start => start.InstanceId).Distinct().Should().ContainSingle();
        starter.Starts.Should().ContainSingle();
    }

    [Fact]
    public async Task KeysTheFireByTheDeliveryTimeNotByWhenTheListCompleted()
    {
        TriggerConfig cron = Triggers.Cron(schedule: "* * * * *");
        ClusterSnapshot snapshot = new();
        JobDispatcher dispatcher = NewDispatcher(snapshot);
        time.Advance(TimeSpan.FromSeconds(50)); // delivered at 10:00:50

        Task<JobOutcome> pending = dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(cron, time.GetUtcNow()).ToJson(), default);
        await Task.Delay(50);
        time.Advance(TimeSpan.FromSeconds(30)); // the list completes at 10:01:20
        List(snapshot, cron);
        await pending;

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-sched:order-sync:20261106T100000Z");
    }

    [Fact]
    public async Task AsksForARetryWhenTheFirstListDoesNotCompleteInTime()
    {
        ClusterSnapshot snapshot = new();
        JobDispatcher dispatcher = NewDispatcher(snapshot, TimeSpan.FromMilliseconds(100));

        JobOutcome outcome = await dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(Triggers.Cron(), time.GetUtcNow()).ToJson(), default);

        outcome.Should().Be(JobOutcome.Retry);
        starter.Starts.Should().BeEmpty();
        registry.Deleted.Should().BeEmpty();
    }

    [Fact]
    public async Task DoesNotWaitOnceTheClusterIsListed()
    {
        TriggerConfig cron = Triggers.Cron();
        ClusterSnapshot snapshot = new();
        List(snapshot, cron);
        JobDispatcher dispatcher = NewDispatcher(snapshot, TimeSpan.FromMinutes(10));

        Task<JobOutcome> outcome = dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(cron, time.GetUtcNow()).ToJson(), default);

        (await outcome.WaitAsync(TimeSpan.FromSeconds(5))).Should().Be(JobOutcome.Success);
    }

    [Fact]
    public async Task PropagatesCancellationWhileWaitingForTheFirstList()
    {
        using CancellationTokenSource cancel = new();
        JobDispatcher dispatcher = NewDispatcher(new ClusterSnapshot(), TimeSpan.FromMinutes(10));

        Task<JobOutcome> pending = dispatcher.DispatchAsync("dws-sched-order-sync", JobPayload.For(Triggers.Cron(), time.GetUtcNow()).ToJson(), cancel.Token);
        await cancel.CancelAsync();

        await pending.Invoking(task => task).Should().ThrowAsync<OperationCanceledException>();
    }
}
