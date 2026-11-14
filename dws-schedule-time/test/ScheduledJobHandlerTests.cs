using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class ScheduledJobHandlerTests
{
    private static readonly DateTimeOffset Anchor = new(2026, 11, 6, 10, 0, 0, TimeSpan.Zero);

    private readonly ManualTime time = new(Anchor);
    private readonly RecordingJobRegistry registry = new();
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();

    private ScheduledJobHandler NewHandler(StaticClusterState cluster)
    {
        StartClaim claim = new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);
        return new ScheduledJobHandler(cluster, registry, claim, time, NullLogger<ScheduledJobHandler>.Instance);
    }

    private static string PayloadFor(TriggerConfig trigger) => JobPayload.For(trigger, Anchor).ToJson();

    [Fact]
    public async Task StartsACronFireOnTheActiveTriggersAppWithAnEmptyInput()
    {
        TriggerConfig cron = Triggers.Cron(version: "b", schedule: "0 0 * * *");
        time.Advance(TimeSpan.FromSeconds(1.2));

        JobOutcome outcome = await NewHandler(new StaticClusterState(true, cron)).HandleAsync("order-sync", PayloadFor(cron), default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().ContainSingle().Which.Should().Be(new StartCall("order-sync-b", "order-sync", "dws-sched:order-sync:20261106T100000Z", "{}"));
    }

    [Fact]
    public async Task StartsAnEveryFireWithTheTickFromTheAnchor()
    {
        TriggerConfig every = Triggers.Every(schedule: "PT1M");
        time.Advance(TimeSpan.FromMinutes(3) + TimeSpan.FromSeconds(0.8));

        await NewHandler(new StaticClusterState(true, every)).HandleAsync("order-sync", PayloadFor(every), default);

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-sched:order-sync:20261106T100300Z");
    }

    [Fact]
    public async Task StartsOnTheTakeoverVersionWhileKeepingTheJobsOwnFireKey()
    {
        TriggerConfig oldEvery = Triggers.Every(version: "a", schedule: "PT1M");
        TriggerConfig newEvery = Triggers.Every(version: "b", schedule: "PT1M");
        time.Advance(TimeSpan.FromMinutes(2));

        await NewHandler(new StaticClusterState(true, newEvery)).HandleAsync("order-sync", PayloadFor(oldEvery), default);

        starter.Starts.Should().ContainSingle().Which.AppId.Should().Be("order-sync-b");
    }

    [Fact]
    public async Task DeletesTheJobAndStartsNothingWhenThereIsNoActiveTrigger()
    {
        TriggerConfig cron = Triggers.Cron();

        JobOutcome outcome = await NewHandler(new StaticClusterState(true)).HandleAsync("order-sync", PayloadFor(cron), default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().BeEmpty();
        registry.Deleted.Should().Equal("dws-sched-order-sync");
    }

    [Fact]
    public async Task DeletesTheJobWhenTheActiveTriggerIsAnAfterTrigger()
    {
        JobOutcome outcome = await NewHandler(new StaticClusterState(true, Triggers.After())).HandleAsync("order-sync", PayloadFor(Triggers.Cron()), default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().BeEmpty();
        registry.Deleted.Should().Equal("dws-sched-order-sync");
    }

    [Fact]
    public async Task AsksForARetryBeforeTheClusterListIsComplete()
    {
        JobOutcome outcome = await NewHandler(new StaticClusterState(false)).HandleAsync("order-sync", PayloadFor(Triggers.Cron()), default);

        outcome.Should().Be(JobOutcome.Retry);
        registry.Deleted.Should().BeEmpty();
        starter.Starts.Should().BeEmpty();
    }

    [Fact]
    public async Task ReportsFailureOnARetryableStartError()
    {
        TriggerConfig cron = Triggers.Cron();
        starter.Enqueue(new StartResult(StartOutcome.Retryable, "HTTP 503"));

        JobOutcome outcome = await NewHandler(new StaticClusterState(true, cron)).HandleAsync("order-sync", PayloadFor(cron), default);

        outcome.Should().Be(JobOutcome.Retry);
    }

    [Fact]
    public async Task StartsExactlyOnceWhenTheTargetRecoversOnTheSecondAttempt()
    {
        TriggerConfig cron = Triggers.Cron();
        starter.Enqueue(new StartResult(StartOutcome.Retryable, "HTTP 503"));
        ScheduledJobHandler handler = NewHandler(new StaticClusterState(true, cron));

        JobOutcome first = await handler.HandleAsync("order-sync", PayloadFor(cron), default);
        JobOutcome second = await handler.HandleAsync("order-sync", PayloadFor(cron), default);

        first.Should().Be(JobOutcome.Retry);
        second.Should().Be(JobOutcome.Success);
        starter.Starts.Select(start => start.InstanceId).Distinct().Should().ContainSingle();
        store.Peek("claim:" + starter.Starts[0].InstanceId).Should().Contain("started");
    }

    [Fact]
    public async Task LogsAndSucceedsOnANonRetryableStartError()
    {
        TriggerConfig cron = Triggers.Cron();
        starter.Enqueue(new StartResult(StartOutcome.NonRetryable, "HTTP 400"));

        JobOutcome outcome = await NewHandler(new StaticClusterState(true, cron)).HandleAsync("order-sync", PayloadFor(cron), default);

        outcome.Should().Be(JobOutcome.Success);
    }

    [Fact]
    public async Task CollapsesAReplayedBurstOntoOneInstance()
    {
        TriggerConfig every = Triggers.Every(schedule: "PT10S");
        time.Advance(TimeSpan.FromSeconds(34));
        ScheduledJobHandler handler = NewHandler(new StaticClusterState(true, every));

        await Task.WhenAll(Enumerable.Range(0, 4).Select(_ => handler.HandleAsync("order-sync", PayloadFor(every), default)));

        starter.Starts.Should().ContainSingle();
    }

    [Fact]
    public async Task DropsAFireWhosePayloadIsUnreadable()
    {
        JobOutcome outcome = await NewHandler(new StaticClusterState(true, Triggers.Cron())).HandleAsync("order-sync", "garbage", default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().BeEmpty();
    }
}
