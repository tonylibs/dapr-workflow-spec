using k8s;
using k8s.Models;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Lists and watches the trigger ConfigMaps and managed Deployments of one namespace into a <see cref="ClusterSnapshot"/>; re-lists after any watch failure.</summary>
public sealed class ClusterWatcher(
    string? podNamespace, ClusterSnapshot snapshot, ChangeSignal signal, TimeProvider time, ILogger<ClusterWatcher> logger) : BackgroundService
{
    private static readonly TimeSpan Backoff = TimeSpan.FromSeconds(5);

    private static readonly string TriggerSelector = $"{ClusterContract.TriggerLabel}={ClusterContract.TriggerLabelValue}";

    private static readonly string DeploymentSelector = $"{ClusterContract.ManagedByLabel}={ClusterContract.ManagedByValue}";

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (string.IsNullOrWhiteSpace(podNamespace))
        {
            logger.LogError("No namespace (POD_NAMESPACE or the service account namespace file); the cluster watch is disabled and no job will be reconciled");
            return;
        }

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await WatchOnceAsync(podNamespace, stoppingToken);
            }
            catch (Exception exception) when (!stoppingToken.IsCancellationRequested)
            {
                // A watch problem of any kind must end in a re-list, never in a crashed host.
                logger.LogWarning(exception, "Cluster watch ended; re-listing in {Backoff}", Backoff);
            }

            try
            {
                await Task.Delay(Backoff, time, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                return;
            }
        }
    }

    private async Task WatchOnceAsync(string ns, CancellationToken stoppingToken)
    {
        KubernetesClientConfiguration configuration = KubernetesClientConfiguration.IsInCluster()
            ? KubernetesClientConfiguration.InClusterConfig()
            : KubernetesClientConfiguration.BuildConfigFromConfigFile();
        using Kubernetes client = new(configuration);

        V1ConfigMapList configMaps = await client.CoreV1.ListNamespacedConfigMapAsync(ns, labelSelector: TriggerSelector, cancellationToken: stoppingToken);
        V1DeploymentList deployments = await client.AppsV1.ListNamespacedDeploymentAsync(ns, labelSelector: DeploymentSelector, cancellationToken: stoppingToken);
        snapshot.ReplaceTriggers(configMaps.Items.Select(ClusterMapping.ToTrigger));
        snapshot.ReplaceDeployments(deployments.Items
            .Select(item => (Name: item.Metadata?.Name ?? string.Empty, Info: ClusterMapping.ToDeployment(item)))
            .Where(entry => entry.Info is not null)
            .Select(entry => KeyValuePair.Create(entry.Name, entry.Info!)));
        signal.Notify();
        logger.LogInformation("Initial cluster list: {Triggers} trigger ConfigMaps, {Deployments} Deployments", configMaps.Items.Count, deployments.Items.Count);

        using CancellationTokenSource stop = CancellationTokenSource.CreateLinkedTokenSource(stoppingToken);
        Task configMapWatch = WatchConfigMapsAsync(client, ns, configMaps.Metadata?.ResourceVersion, stop.Token);
        Task deploymentWatch = WatchDeploymentsAsync(client, ns, deployments.Metadata?.ResourceVersion, stop.Token);
        Task finished = await Task.WhenAny(configMapWatch, deploymentWatch);
        await stop.CancelAsync();
        try
        {
            await Task.WhenAll(configMapWatch, deploymentWatch);
        }
        catch (OperationCanceledException) when (stop.IsCancellationRequested)
        {
            // the surviving watch was cancelled on purpose
        }

        await finished;
    }

    private async Task WatchConfigMapsAsync(Kubernetes client, string ns, string? resourceVersion, CancellationToken cancellationToken)
    {
        await foreach ((WatchEventType type, V1ConfigMap item) in client.CoreV1.WatchListNamespacedConfigMapAsync(
            ns,
            allowWatchBookmarks: true,
            labelSelector: TriggerSelector,
            resourceVersion: resourceVersion,
            cancellationToken: cancellationToken))
        {
            switch (type)
            {
                case WatchEventType.Added or WatchEventType.Modified:
                    snapshot.UpsertTrigger(ClusterMapping.ToTrigger(item));
                    signal.Notify();
                    break;
                case WatchEventType.Deleted:
                    snapshot.RemoveTrigger(item.Metadata?.Name ?? string.Empty);
                    signal.Notify();
                    break;
                case WatchEventType.Error:
                    throw new InvalidOperationException("ConfigMap watch reported an error event");
            }
        }
    }

    private async Task WatchDeploymentsAsync(Kubernetes client, string ns, string? resourceVersion, CancellationToken cancellationToken)
    {
        await foreach ((WatchEventType type, V1Deployment item) in client.AppsV1.WatchListNamespacedDeploymentAsync(
            ns,
            allowWatchBookmarks: true,
            labelSelector: DeploymentSelector,
            resourceVersion: resourceVersion,
            cancellationToken: cancellationToken))
        {
            string name = item.Metadata?.Name ?? string.Empty;
            DeploymentInfo? info = ClusterMapping.ToDeployment(item);
            switch (type)
            {
                case WatchEventType.Added or WatchEventType.Modified when info is not null:
                    snapshot.UpsertDeployment(name, info);
                    signal.Notify();
                    break;
                case WatchEventType.Deleted:
                    snapshot.RemoveDeployment(name);
                    signal.Notify();
                    break;
                case WatchEventType.Error:
                    throw new InvalidOperationException("Deployment watch reported an error event");
            }
        }
    }
}
