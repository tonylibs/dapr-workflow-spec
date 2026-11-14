using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class ActiveTriggersTests
{
    private static readonly IReadOnlyDictionary<string, string> NoMetadata = new Dictionary<string, string>();

    private static TriggerResource Resource(TriggerConfig trigger) => new($"trigger-{trigger.Workflow}-{trigger.Version}", NoMetadata, Triggers.Json(trigger));

    private static DeploymentInfo Deployment(string workflow, string version, bool draining = false) =>
        new(workflow, version, draining ? new Dictionary<string, string> { [ClusterContract.DrainAnnotation] = "true" } : NoMetadata);

    [Fact]
    public void IgnoresTheDrainingVersion()
    {
        TriggerConfig a = Triggers.Cron(version: "a");
        TriggerConfig b = Triggers.Cron(version: "b", schedule: "30 * * * *");

        ActiveTriggerSet set = ActiveTriggers.Resolve([Resource(a), Resource(b)], [Deployment("order-sync", "a", draining: true), Deployment("order-sync", "b")]);

        set.Triggers.Should().ContainSingle().Which.Value.Should().Be(b);
    }

    [Fact]
    public void PinsTheDrainMarkerAsAnAnnotationWithValueTrue()
    {
        // dws-controller StackApplier.markForDrain writes the ANNOTATION dws.io/drain=true (not a label).
        ClusterContract.DrainAnnotation.Should().Be("dws.io/drain");
        ClusterContract.IsDraining(new DeploymentInfo("w", "v", new Dictionary<string, string> { ["dws.io/drain"] = "true" })).Should().BeTrue();
        ClusterContract.IsDraining(new DeploymentInfo("w", "v", new Dictionary<string, string> { ["dws.io/drain"] = "false" })).Should().BeFalse();
        ClusterContract.IsDraining(new DeploymentInfo("w", "v", NoMetadata)).Should().BeFalse();
    }

    [Fact]
    public void TakesOverWhenTheOldVersionStartsDraining()
    {
        TriggerConfig a = Triggers.Cron(version: "a");
        TriggerConfig b = Triggers.Cron(version: "b", schedule: "30 * * * *");
        TriggerResource[] resources = [Resource(a), Resource(b)];

        ActiveTriggers.Resolve(resources, [Deployment("order-sync", "a"), Deployment("order-sync", "b", draining: true)])
            .Triggers["order-sync"].Should().Be(a);
        ActiveTriggers.Resolve(resources, [Deployment("order-sync", "a", draining: true), Deployment("order-sync", "b")])
            .Triggers["order-sync"].Should().Be(b);
    }

    [Fact]
    public void YieldsNoEntryWhenTheNewVersionHasNoSchedule()
    {
        ActiveTriggerSet set = ActiveTriggers.Resolve([Resource(Triggers.Cron(version: "a"))], [Deployment("order-sync", "a", draining: true), Deployment("order-sync", "b")]);

        set.Triggers.Should().BeEmpty();
    }

    [Fact]
    public void YieldsNoEntryWhenTheWorkflowIsDeleted()
    {
        ActiveTriggers.Resolve([Resource(Triggers.Cron())], []).Triggers.Should().BeEmpty();
    }

    [Fact]
    public void YieldsNoEntryForATriggerOfAnotherWorkflowsDeployment()
    {
        ActiveTriggers.Resolve([Resource(Triggers.Cron())], [Deployment("other", "v1")]).Triggers.Should().BeEmpty();
    }

    [Fact]
    public void SkipsAndReportsAnInvalidTriggerWithoutLosingTheOthers()
    {
        TriggerResource broken = new("trigger-broken-v1", NoMetadata, "{ nope");
        TriggerConfig good = Triggers.Every(workflow: "good");

        ActiveTriggerSet set = ActiveTriggers.Resolve([broken, Resource(good)], [Deployment("good", "v1")]);

        set.Triggers.Should().ContainKey("good");
        set.Errors.Should().ContainSingle().Which.Should().Contain("trigger-broken-v1");
    }

    [Fact]
    public void SkipsATriggerWhoseWorkflowLabelDisagreesWithItsJson()
    {
        TriggerConfig trigger = Triggers.Every();
        TriggerResource mislabelled = new("trigger-x", new Dictionary<string, string> { [ClusterContract.WorkflowLabel] = "other" }, Triggers.Json(trigger));

        ActiveTriggerSet set = ActiveTriggers.Resolve([mislabelled], [Deployment("order-sync", "v1")]);

        set.Triggers.Should().BeEmpty();
        set.Errors.Should().ContainSingle();
    }

    [Fact]
    public void PicksTheHighestVersionWhenTwoAreLive()
    {
        TriggerConfig a = Triggers.Cron(version: "a");
        TriggerConfig b = Triggers.Cron(version: "b");

        ActiveTriggers.Resolve([Resource(b), Resource(a)], [Deployment("order-sync", "a"), Deployment("order-sync", "b")])
            .Triggers["order-sync"].Version.Should().Be("b");
    }
}
