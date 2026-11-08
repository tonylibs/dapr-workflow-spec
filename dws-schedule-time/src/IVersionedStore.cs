namespace Dws.ScheduleTime;

/// <summary>A stored value together with the ETag that guards the next write.</summary>
public sealed record VersionedValue(string Value, string ETag);

/// <summary>The seam around the Dapr state API with first-write ETag semantics, used for start claims and the <c>after</c> bookkeeping.</summary>
public interface IVersionedStore
{
    Task<VersionedValue?> GetAsync(string key, CancellationToken cancellationToken);

    /// <summary>Writes the key only if it does not exist; null when another writer got there first.</summary>
    Task<VersionedValue?> TryCreateAsync(string key, string value, TimeSpan? ttl, CancellationToken cancellationToken);

    /// <summary>Writes the key only if its ETag still matches; null when it no longer does.</summary>
    Task<VersionedValue?> TryUpdateAsync(string key, string value, string etag, TimeSpan? ttl, CancellationToken cancellationToken);

    /// <summary>Deletes the key only if its ETag still matches.</summary>
    Task<bool> TryDeleteAsync(string key, string etag, CancellationToken cancellationToken);
}
