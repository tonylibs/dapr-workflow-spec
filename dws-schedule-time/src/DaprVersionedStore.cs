using System.Globalization;
using Dapr.Client;

namespace Dws.ScheduleTime;

/// <summary>Production <see cref="IVersionedStore"/> over the Dapr state API (first-write concurrency, <c>ttlInSeconds</c> metadata); needs a sidecar and has no unit test.</summary>
public sealed class DaprVersionedStore(DaprClient client, string storeName) : IVersionedStore
{
    private static readonly StateOptions FirstWrite = new() { Concurrency = ConcurrencyMode.FirstWrite };

    public async Task<VersionedValue?> GetAsync(string key, CancellationToken cancellationToken)
    {
        (string? value, string etag) = await client.GetStateAndETagAsync<string>(storeName, key, cancellationToken: cancellationToken);
        return string.IsNullOrEmpty(value) ? null : new VersionedValue(value, etag);
    }

    public async Task<VersionedValue?> TryCreateAsync(string key, string value, TimeSpan? ttl, CancellationToken cancellationToken)
    {
        // An empty ETag under first-write means "create only if absent". Confirm against the live store (tasks 9.x).
        bool saved = await client.TrySaveStateAsync(storeName, key, value, string.Empty, FirstWrite, Metadata(ttl), cancellationToken);
        return saved ? await GetAsync(key, cancellationToken) ?? new VersionedValue(value, string.Empty) : null;
    }

    public async Task<VersionedValue?> TryUpdateAsync(string key, string value, string etag, TimeSpan? ttl, CancellationToken cancellationToken)
    {
        bool saved = await client.TrySaveStateAsync(storeName, key, value, etag, FirstWrite, Metadata(ttl), cancellationToken);
        return saved ? await GetAsync(key, cancellationToken) ?? new VersionedValue(value, string.Empty) : null;
    }

    public Task<bool> TryDeleteAsync(string key, string etag, CancellationToken cancellationToken) =>
        client.TryDeleteStateAsync(storeName, key, etag, FirstWrite, cancellationToken: cancellationToken);

    private static Dictionary<string, string>? Metadata(TimeSpan? ttl) =>
        ttl is { } span
            ? new Dictionary<string, string> { ["ttlInSeconds"] = ((long)Math.Ceiling(span.TotalSeconds)).ToString(CultureInfo.InvariantCulture) }
            : null;
}
