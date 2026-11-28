namespace Dws.Flow;

/// <summary>The single-node definition contract's <c>scope</c> values, shared by the loader and the dispatcher.</summary>
public static class Scopes
{
    public const string Main = "main";
    public const string Do = "do";
    public const string For = "for";
    public const string TryCatch = "try-catch";
    public const string Fork = "fork";
    public const string Wait = "wait";
    public const string Listen = "listen";

    public static readonly IReadOnlySet<string> All = new HashSet<string> { Main, Do, For, TryCatch, Fork, Wait, Listen };

    public static readonly IReadOnlySet<string> Sequencers = new HashSet<string> { Main, Do };

    /// <summary>Controllers with no execution children: their <c>tasks</c> and <c>children</c> are always empty (ADR 0006).</summary>
    public static readonly IReadOnlySet<string> LeafControllers = new HashSet<string> { Wait, Listen };
}
