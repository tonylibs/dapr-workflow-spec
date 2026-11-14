using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class AfterLifecycleTests
{
    private static readonly TimeSpan Poll = TimeSpan.FromSeconds(15);

    private readonly ManualTime time = new(new DateTimeOffset(2026, 11, 6, 12, 0, 0, TimeSpan.Zero));
    private readonly RecordingJobRegistry registry = new();
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();

    private AfterLifecycle NewLifecycle(StaticClusterState cluster)
    {
        StartClaim claim = new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);
        return new AfterLifecycle(store, registry, starter, claim, cluster, time, Poll, NullLogger<AfterLifecycle>.Instance);
    }

    private AfterState State(string workflow = "order-sync") => AfterState.TryParse(store.Peek(AfterState.Key(workflow)) ?? "")!;

    private void PutState(AfterState state) => store.Put(AfterState.Key("order-sync"), state.ToJson());

    [Fact]
    public async Task StartsSequenceOneImmediatelyOnFirstActivation()
    {
        TriggerConfig trigger = Triggers.After();

        await NewLifecycle(new StaticClusterState(true, trigger)).ActivateAsync(trigger, default);

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-after:order-sync:1");
        State().Should().Be(new AfterState("v1", "order-sync-v1", 1, AfterState.Running, null));
    }

    [Fact]
    public async Task OnlyOneOfTwoConcurrentActivationsStartsSequenceOne()
    {
        TriggerConfig trigger = Triggers.After();
        AfterLifecycle first = NewLifecycle(new StaticClusterState(true, trigger));
        AfterLifecycle second = NewLifecycle(new StaticClusterState(true, trigger));

        await Task.WhenAll(first.ActivateAsync(trigger, default), second.ActivateAsync(trigger, default));

        starter.Starts.Should().ContainSingle();
    }

    [Fact]
    public async Task DoesNotActivateAgainWhenStateExists()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 7, AfterState.Running, null));

        await NewLifecycle(new StaticClusterState(true, trigger)).ActivateAsync(trigger, default);

        starter.Starts.Should().BeEmpty();
        State().Seq.Should().Be(7);
    }

    [Fact]
    public async Task ResumesPollingTheStoredSequenceAfterARestart()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 7, AfterState.Running, null));
        starter.Statuses["dws-after:order-sync:7"] = InstanceStatus.Running;

        JobOutcome outcome = await NewLifecycle(new StaticClusterState(true, trigger)).PollAsync("order-sync", default);

        outcome.Should().Be(JobOutcome.Success);
        starter.StatusQueries.Should().Equal("order-sync-v1/dws-after:order-sync:7");
        starter.Starts.Should().BeEmpty();
        State().Seq.Should().Be(7);
    }

    [Fact]
    public async Task DoesNothingWhileTheInstanceIsStillRunning()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));
        starter.Statuses["dws-after:order-sync:3"] = InstanceStatus.Running;

        await NewLifecycle(new StaticClusterState(true, trigger)).PollAsync("order-sync", default);

        State().Phase.Should().Be(AfterState.Running);
        registry.Calls.Should().BeEmpty();
    }

    [Fact]
    public async Task SchedulesTheOneShotStartADelayAfterATerminalStatusEvenWhenTheRunFailed()
    {
        TriggerConfig trigger = Triggers.After(schedule: "PT10M");
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));
        starter.Statuses["dws-after:order-sync:3"] = InstanceStatus.Terminal;
        DateTimeOffset readyAt = time.GetUtcNow() + TimeSpan.FromMinutes(10);

        await NewLifecycle(new StaticClusterState(true, trigger)).PollAsync("order-sync", default);

        State().Should().Be(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, readyAt));
        ScheduleCall call = registry.Calls.Should().ContainSingle().Subject;
        call.Job.Name.Should().Be("dws-after-start-order-sync");
        call.Job.DueTime.Should().Be(readyAt);
        call.Overwrite.Should().BeTrue();
        starter.Starts.Should().BeEmpty();
    }

    [Fact]
    public async Task OnlyOneOfTwoReplicasSchedulesTheOneShot()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));
        starter.Statuses["dws-after:order-sync:3"] = InstanceStatus.Terminal;
        StaticClusterState cluster = new(true, trigger);

        await Task.WhenAll(NewLifecycle(cluster).PollAsync("order-sync", default), NewLifecycle(cluster).PollAsync("order-sync", default));

        registry.Calls.Should().ContainSingle();
    }

    [Fact]
    public async Task TreatsAStartedButUnknownInstanceAsTerminal()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));
        store.Put(StartClaim.Key("dws-after:order-sync:3"), """{"state":"started","at":"2026-11-06T11:00:00.000Z"}""");

        await NewLifecycle(new StaticClusterState(true, trigger)).PollAsync("order-sync", default);

        State().Phase.Should().Be(AfterState.Waiting);
        starter.Starts.Should().BeEmpty();
    }

    [Fact]
    public async Task RetriesTheStartOfTheSameSequenceWhenTheInstanceWasNeverStarted()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));

        await NewLifecycle(new StaticClusterState(true, trigger)).PollAsync("order-sync", default);

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-after:order-sync:3");
        State().Seq.Should().Be(3);
    }

    [Fact]
    public async Task DeletesThePollAndOneShotJobsWhenThereIsNoActiveAfterTrigger()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));

        JobOutcome outcome = await NewLifecycle(new StaticClusterState(true, Triggers.Cron())).PollAsync("order-sync", default);

        outcome.Should().Be(JobOutcome.Success);
        registry.Deleted.Should().BeEquivalentTo("dws-after-poll-order-sync", "dws-after-start-order-sync");
        State().Seq.Should().Be(3);
    }

    [Fact]
    public async Task StartsTheNextSequenceFromTheOneShotOnTheCurrentlyActiveVersion()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, time.GetUtcNow()));
        TriggerConfig takeover = Triggers.After(version: "v2");

        JobOutcome outcome = await NewLifecycle(new StaticClusterState(true, takeover)).StartNextAsync("order-sync", default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().ContainSingle().Which.Should().Be(new StartCall("order-sync-v2", "order-sync", "dws-after:order-sync:4", "{}"));
        State().Should().Be(new AfterState("v2", "order-sync-v2", 4, AfterState.Running, null));
    }

    [Fact]
    public async Task IgnoresTheOneShotWhenThePhaseIsNotWaiting()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Running, null));

        JobOutcome outcome = await NewLifecycle(new StaticClusterState(true, Triggers.After())).StartNextAsync("order-sync", default);

        outcome.Should().Be(JobOutcome.Success);
        starter.Starts.Should().BeEmpty();
        State().Seq.Should().Be(3);
    }

    [Fact]
    public async Task IgnoresTheOneShotWhenNoAfterTriggerIsActive()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, time.GetUtcNow()));

        await NewLifecycle(new StaticClusterState(true)).StartNextAsync("order-sync", default);

        starter.Starts.Should().BeEmpty();
        State().Phase.Should().Be(AfterState.Waiting);
    }

    [Fact]
    public async Task OnlyOneOfTwoOneShotsStartsTheNextSequence()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, time.GetUtcNow()));
        StaticClusterState cluster = new(true, Triggers.After());

        await Task.WhenAll(NewLifecycle(cluster).StartNextAsync("order-sync", default), NewLifecycle(cluster).StartNextAsync("order-sync", default));

        starter.Starts.Should().ContainSingle();
        State().Seq.Should().Be(4);
    }

    [Fact]
    public async Task ReportsFailureFromTheOneShotOnARetryableStartError()
    {
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, time.GetUtcNow()));
        starter.Enqueue(new StartResult(StartOutcome.Retryable, "HTTP 503"));

        JobOutcome outcome = await NewLifecycle(new StaticClusterState(true, Triggers.After())).StartNextAsync("order-sync", default);

        outcome.Should().Be(JobOutcome.Retry);
    }

    [Fact]
    public async Task PollStartsTheNextRunItselfWhenTheOneShotIsOverdue()
    {
        TriggerConfig trigger = Triggers.After();
        DateTimeOffset readyAt = time.GetUtcNow();
        PutState(new AfterState("v1", "order-sync-v1", 3, AfterState.Waiting, readyAt));
        AfterLifecycle lifecycle = NewLifecycle(new StaticClusterState(true, trigger));

        time.Advance(2 * Poll - TimeSpan.FromSeconds(1));
        await lifecycle.PollAsync("order-sync", default);
        starter.Starts.Should().BeEmpty();

        time.Advance(TimeSpan.FromSeconds(1));
        await lifecycle.PollAsync("order-sync", default);

        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-after:order-sync:4");
    }

    [Fact]
    public async Task RetriesBeforeTheClusterListIsComplete()
    {
        AfterLifecycle lifecycle = NewLifecycle(new StaticClusterState(false));

        (await lifecycle.PollAsync("order-sync", default)).Should().Be(JobOutcome.Retry);
        (await lifecycle.StartNextAsync("order-sync", default)).Should().Be(JobOutcome.Retry);
        registry.Deleted.Should().BeEmpty();
    }

    [Fact]
    public async Task KeepsSequenceNumbersMonotonicAcrossReActivation()
    {
        TriggerConfig trigger = Triggers.After();
        PutState(new AfterState("v1", "order-sync-v1", 7, AfterState.Running, null));
        await NewLifecycle(new StaticClusterState(true)).PollAsync("order-sync", default);

        await NewLifecycle(new StaticClusterState(true, trigger)).ActivateAsync(trigger, default);

        starter.Starts.Should().BeEmpty();
        State().Seq.Should().Be(7);
    }
}
