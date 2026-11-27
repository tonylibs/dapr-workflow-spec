namespace Dws.Flow;

/// <summary>Which engine runs a node's scope.</summary>
public enum ScopeKind
{
    Sequencer,
    NotImplemented,
}

/// <summary>Single routing point from a node's declared scope to the engine that runs it.</summary>
public static class ScopeDispatch
{
    public static ScopeKind Resolve(string scope) => Scopes.Sequencers.Contains(scope) ? ScopeKind.Sequencer : ScopeKind.NotImplemented;

    public static string NotImplementedMessage(string scope) => $"config failure: scope '{scope}' is not implemented yet";
}
