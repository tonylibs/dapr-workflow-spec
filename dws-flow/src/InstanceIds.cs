namespace Dws.Flow;

/// <summary>Builds a Flow child's deterministic workflow instance ID (ADR 0006).</summary>
public static class InstanceIds
{
    /// <summary>The iteration segment used when a task is not nested inside any <c>for</c> loop.</summary>
    public const string DefaultIteration = "0";

    public static string For(string rootInstanceId, string childAppId, string iteration) => $"{rootInstanceId}:{childAppId}:{iteration}";
}
