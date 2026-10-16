namespace Dws.Flow;

/// <summary>Reads named <c>use</c> entries from the version's configuration store; keys that are absent are omitted.</summary>
public interface IUseStore
{
    Task<IReadOnlyDictionary<string, string>> Get(string storeName, IReadOnlyCollection<string> keys);
}
