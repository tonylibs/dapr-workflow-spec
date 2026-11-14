using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class ReconcilerTests
{
    private static readonly DateTimeOffset T0 = new(2026, 11, 6, 10, 0, 0, TimeSpan.Zero);

    private readonly ManualTime time = new(T0);
    private readonly RecordingJobRegistry registry = new();
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();
    private readonly StaticClusterState cluster = new(true);

    private Reconciler NewReconciler()
    {
        StartClaim claim = new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);
        AfterLifecycle after = new(store, registry, starter, claim, cluster, time, TimeSpan.FromSeconds(15), NullLogger<AfterLifecycle>.Instance);
        return new Reconciler(registry, after, time, TimeSpan.FromSeconds(15), NullLogger<Reconciler>.Instance);
    }

    private static Dictionary<string, TriggerConfig> Active(params TriggerConfig[] triggers) => triggers.ToDictionary(trigger => trigger.Workflow);

    [Fact]
    public async Task RegistersACronJobWithTheSixFieldSchedule()
    {
        await NewReconciler().ReconcileAsync(Active(Triggers.Cron(schedule: "*/5 * * * *")), default);

        RegisteredJob job = registry.Jobs["dws-sched-order-sync"];
        job.Schedule.Should().Be("0 */5 * * * *");
        job.ParsedPayload.Should().Be(new JobPayload("order-sync", "v1", "order-sync-v1", TriggerKind.Cron, "*/5 * * * *", T0));
    }

    [Fact]
    public async Task RegistersAnEveryJobAnchoredAtRegistration()
    {
        await NewReconciler().ReconcileAsync(Active(Triggers.Every(schedule: "PT1H")), default);

        registry.Jobs["dws-sched-order-sync"].Schedule.Should().Be("@every 1h");
        registry.Jobs["dws-sched-order-sync"].ParsedPayload!.Anchor.Should().Be(T0);
    }

    [Fact]
    public async Task DoesNotReAnchorAnUnchangedTrigger()
    {
        Reconciler reconciler = NewReconciler();
        Dictionary<string, TriggerConfig> active = Active(Triggers.Every(schedule: "PT1H"));
        await reconciler.ReconcileAsync(active, default);
        registry.Calls.Clear();
        time.Advance(TimeSpan.FromMinutes(20));

        await reconciler.ReconcileAsync(active, default);

        registry.Calls.Should().BeEmpty();
        registry.Jobs["dws-sched-order-sync"].ParsedPayload!.Anchor.Should().Be(T0);
    }

    [Fact]
    public async Task OverwritesWhenANewVersionTakesOver()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Cron(version: "a", schedule: "0 * * * *")), default);
        time.Advance(TimeSpan.FromMinutes(5));

        await reconciler.ReconcileAsync(Active(Triggers.Cron(version: "b", schedule: "30 * * * *")), default);

        registry.Jobs["dws-sched-order-sync"].Schedule.Should().Be("0 30 * * * *");
        registry.Jobs["dws-sched-order-sync"].ParsedPayload!.Should().Match<JobPayload>(p => p.Version == "b" && p.AppId == "order-sync-b");
        registry.Calls.Last().Overwrite.Should().BeTrue();
    }

    [Fact]
    public async Task OverwritesWhenOnlyTheScheduleChanges()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Every(schedule: "PT1H")), default);

        await reconciler.ReconcileAsync(Active(Triggers.Every(schedule: "PT30M")), default);

        registry.Jobs["dws-sched-order-sync"].Schedule.Should().Be("@every 30m");
    }

    [Fact]
    public async Task OverwritesAJobWhoseKindChanged()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Cron(schedule: "0 * * * *")), default);

        await reconciler.ReconcileAsync(Active(Triggers.Every(schedule: "PT1H")), default);

        registry.Jobs["dws-sched-order-sync"].ParsedPayload!.Kind.Should().Be(TriggerKind.Every);
    }

    [Fact]
    public async Task OverwritesAJobWithAnUnreadablePayload()
    {
        registry.Seed("dws-sched-order-sync", "@every 1h", "garbage");

        await NewReconciler().ReconcileAsync(Active(Triggers.Every(schedule: "PT1H")), default);

        registry.Jobs["dws-sched-order-sync"].ParsedPayload.Should().NotBeNull();
    }

    [Fact]
    public async Task DeletesTheJobWhenTheNewVersionDropsTheSchedule()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Cron(version: "a")), default);

        await reconciler.ReconcileAsync(Active(), default);

        registry.Jobs.Should().BeEmpty();
        registry.Deleted.Should().Contain("dws-sched-order-sync");
    }

    [Fact]
    public async Task DeletesTheJobsOfADeletedWorkflowAndKeepsTheOthers()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Cron(workflow: "keep"), Triggers.Cron(workflow: "gone")), default);

        await reconciler.ReconcileAsync(Active(Triggers.Cron(workflow: "keep")), default);

        registry.Jobs.Keys.Should().BeEquivalentTo("dws-sched-keep");
    }

    [Fact]
    public async Task IgnoresJobsItDoesNotOwn()
    {
        registry.Seed("somebody-elses-job", "@every 1h", "{}");

        await NewReconciler().ReconcileAsync(Active(), default);

        registry.Jobs.Should().ContainKey("somebody-elses-job");
        registry.Deleted.Should().BeEmpty();
    }

    [Fact]
    public async Task RebuildsEveryJobAfterARestartWithAnEmptyRegistry()
    {
        await NewReconciler().ReconcileAsync(
            Active(Triggers.Cron(workflow: "a"), Triggers.Every(workflow: "b"), Triggers.Cron(workflow: "c", schedule: "0 0 * * *")),
            default);

        registry.Jobs.Keys.Should().BeEquivalentTo("dws-sched-a", "dws-sched-b", "dws-sched-c");
    }

    [Fact]
    public async Task TwoReplicasReconcilingConcurrentlyRegisterOneJobWithoutOverwriting()
    {
        Dictionary<string, TriggerConfig> active = Active(Triggers.Every(schedule: "PT1H"));
        Reconciler first = NewReconciler();
        Reconciler second = NewReconciler();

        await Task.WhenAll(first.ReconcileAsync(active, default), second.ReconcileAsync(active, default));

        registry.Jobs.Should().ContainSingle();
        registry.Calls.Should().OnlyContain(call => !call.Overwrite);
        registry.Calls.Count(call => call.Applied).Should().Be(1);
    }

    [Fact]
    public async Task RegistersThePollJobAndStartsTheFirstAfterRunOnce()
    {
        cluster.ActiveTriggers = Active(Triggers.After());
        Reconciler reconciler = NewReconciler();

        await reconciler.ReconcileAsync(cluster.ActiveTriggers.ToDictionary(), default);
        await reconciler.ReconcileAsync(cluster.ActiveTriggers.ToDictionary(), default);

        registry.Jobs["dws-after-poll-order-sync"].Schedule.Should().Be("@every 15s");
        starter.Starts.Should().ContainSingle().Which.InstanceId.Should().Be("dws-after:order-sync:1");
    }

    [Fact]
    public async Task DeletesAfterJobsButKeepsTheirStateWhenTheTriggerGoesAway()
    {
        cluster.ActiveTriggers = Active(Triggers.After());
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(cluster.ActiveTriggers.ToDictionary(), default);
        registry.Seed("dws-after-start-order-sync", "x", "{}");

        await reconciler.ReconcileAsync(Active(), default);

        registry.Jobs.Should().BeEmpty();
        store.Peek("after:order-sync").Should().NotBeNull();
    }

    [Fact]
    public async Task SwitchesFromEveryToAfterByDeletingTheScheduledJob()
    {
        Reconciler reconciler = NewReconciler();
        await reconciler.ReconcileAsync(Active(Triggers.Every()), default);
        cluster.ActiveTriggers = Active(Triggers.After(version: "v2"));

        await reconciler.ReconcileAsync(cluster.ActiveTriggers.ToDictionary(), default);

        registry.Jobs.Keys.Should().BeEquivalentTo("dws-after-poll-order-sync");
    }
}
