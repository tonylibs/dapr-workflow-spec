using FluentAssertions;
using k8s.Models;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class ClusterSnapshotTests
{
    private static readonly IReadOnlyDictionary<string, string> NoMetadata = new Dictionary<string, string>();

    private static TriggerResource Resource(TriggerConfig trigger) => new($"trigger-{trigger.Workflow}", NoMetadata, Triggers.Json(trigger));

    [Fact]
    public void IsNotSyncedUntilBothListsArrive()
    {
        ClusterSnapshot snapshot = new();
        snapshot.Synced.Should().BeFalse();

        snapshot.ReplaceTriggers([]);
        snapshot.Synced.Should().BeFalse();

        snapshot.ReplaceDeployments([]);
        snapshot.Synced.Should().BeTrue();
    }

    [Fact]
    public void RecomputesTheActiveTriggersOnEveryChange()
    {
        ClusterSnapshot snapshot = new();
        snapshot.ReplaceTriggers([Resource(Triggers.Cron())]);
        snapshot.ReplaceDeployments([]);
        snapshot.ActiveTriggers.Should().BeEmpty();

        snapshot.UpsertDeployment("order-sync-v1", new DeploymentInfo("order-sync", "v1", NoMetadata));
        snapshot.ActiveTriggers.Should().ContainKey("order-sync");

        snapshot.UpsertDeployment("order-sync-v1", new DeploymentInfo("order-sync", "v1", new Dictionary<string, string> { ["dws.io/drain"] = "true" }));
        snapshot.ActiveTriggers.Should().BeEmpty();

        snapshot.UpsertDeployment("order-sync-v1", new DeploymentInfo("order-sync", "v1", NoMetadata));
        snapshot.RemoveTrigger("trigger-order-sync");
        snapshot.ActiveTriggers.Should().BeEmpty();
    }

    [Fact]
    public void ReplacingTheListDropsEntriesThatVanished()
    {
        ClusterSnapshot snapshot = new();
        snapshot.ReplaceTriggers([Resource(Triggers.Cron())]);
        snapshot.ReplaceDeployments([KeyValuePair.Create("d", new DeploymentInfo("order-sync", "v1", NoMetadata))]);
        snapshot.ActiveTriggers.Should().ContainKey("order-sync");

        snapshot.ReplaceDeployments([]);

        snapshot.ActiveTriggers.Should().BeEmpty();
    }

    [Fact]
    public void ExposesSkippedTriggersAsErrors()
    {
        ClusterSnapshot snapshot = new();
        snapshot.ReplaceDeployments([]);
        snapshot.ReplaceTriggers([new TriggerResource("broken", NoMetadata, "{")]);

        snapshot.Errors.Should().ContainSingle();
    }

    [Fact]
    public void MapsAConfigMapToATriggerResource()
    {
        V1ConfigMap configMap = new()
        {
            Metadata = new V1ObjectMeta { Name = "trigger-x", Labels = new Dictionary<string, string> { ["dws.io/trigger"] = "time" } },
            Data = new Dictionary<string, string> { ["trigger.json"] = "{}" },
        };

        TriggerResource resource = ClusterMapping.ToTrigger(configMap);

        resource.Name.Should().Be("trigger-x");
        resource.TriggerJson.Should().Be("{}");
        resource.Labels.Should().ContainKey("dws.io/trigger");
    }

    [Fact]
    public void MapsADeploymentCarryingTheDrainAnnotation()
    {
        V1Deployment deployment = new()
        {
            Metadata = new V1ObjectMeta
            {
                Name = "d",
                Labels = new Dictionary<string, string> { ["dws.io/workflow"] = "order-sync", ["dws.io/version"] = "v1" },
                Annotations = new Dictionary<string, string> { ["dws.io/drain"] = "true" },
            },
        };

        DeploymentInfo? info = ClusterMapping.ToDeployment(deployment);

        info.Should().NotBeNull();
        ClusterContract.IsDraining(info!).Should().BeTrue();
    }

    [Fact]
    public void SkipsADeploymentWithoutWorkflowLabels()
    {
        ClusterMapping.ToDeployment(new V1Deployment { Metadata = new V1ObjectMeta { Name = "d" } }).Should().BeNull();
    }
}
