using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class JobDispatcherTests
{
    private readonly ManualTime time = new(new DateTimeOffset(2026, 11, 6, 10, 0, 0, TimeSpan.Zero));
    private readonly RecordingJobRegistry registry = new();
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();

    private JobDispatcher NewDispatcher(StaticClusterState cluster)
    {
        StartClaim claim = new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);
        AfterLifecycle after = new(store, registry, starter, claim, cluster, time, TimeSpan.FromSeconds(15), NullLogger<AfterLifecycle>.Instance);
        ScheduledJobHandler scheduled = new(cluster, registry, claim, time, NullLogger<ScheduledJobHandler>.Instance);
        return new JobDispatcher(scheduled, after, NullLogger<JobDispatcher>.Instance);
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
}
