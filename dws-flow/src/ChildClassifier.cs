using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Which kind of child a task dispatches to.</summary>
public enum ChildKind
{
    Step,
    Flow,
}

/// <summary>
/// Classifies a task body as a Step or Flow child. The definition schema carries no explicit type for a
/// child app ID, so the task body itself is the only source: <c>for</c>/<c>try</c>/<c>fork</c>/
/// <c>wait</c>/<c>listen</c> tasks are controller scopes dispatched as a Flow child workflow; every other
/// task (<c>call</c>, <c>run</c>, <c>set</c>, <c>switch</c>) is dispatched as a Step activity.
/// </summary>
public static class ChildClassifier
{
    private static readonly HashSet<string> FlowTaskKeys = ["for", "try", "fork", "wait", "listen"];

    public static ChildKind Classify(JsonObject taskBody) => FlowTaskKeys.Any(taskBody.ContainsKey) ? ChildKind.Flow : ChildKind.Step;
}
