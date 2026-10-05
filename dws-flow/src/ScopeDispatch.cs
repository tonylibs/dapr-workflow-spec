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
    private static readonly HashSet<string> SequencerScopes = ["main", "do"];

    public static ScopeKind Resolve(string scope) => SequencerScopes.Contains(scope) ? ScopeKind.Sequencer : ScopeKind.NotImplemented;

    public static string NotImplementedMessage(string scope) => $"config failure: scope '{scope}' is not implemented yet";
}
