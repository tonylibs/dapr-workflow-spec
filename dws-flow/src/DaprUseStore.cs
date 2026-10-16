using Dapr.Client;

namespace Dws.Flow;

/// <summary>Production <see cref="IUseStore"/>: reads the version's store through the sidecar's configuration API.
/// The client is created on first use, so a node with no references never connects.</summary>
public sealed class DaprUseStore : IUseStore
{
    private static readonly TimeSpan SidecarWait = TimeSpan.FromSeconds(30);

    public async Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys)
    {
        using DaprClient client = new DaprClientBuilder().Build();
        using CancellationTokenSource wait = new(SidecarWait);
        await client.WaitForSidecarAsync(wait.Token);

        GetConfigurationResponse response = await client.GetConfiguration(storeName, keys.ToList());
        return response.Items.ToDictionary(item => item.Key, item => item.Value.Value);
    }
}
