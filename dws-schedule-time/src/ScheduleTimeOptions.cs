namespace Dws.ScheduleTime;

/// <summary>The settings read from the environment in Program.cs and handed to the services.</summary>
public sealed record ScheduleTimeOptions(string StateStore, TimeSpan AfterPollInterval, TimeSpan ClaimTtl, string? Namespace)
{
    public static readonly TimeSpan DefaultAfterPollInterval = TimeSpan.FromSeconds(15);

    public static readonly TimeSpan DefaultClaimTtl = TimeSpan.FromHours(24);

    public static readonly TimeSpan Resync = TimeSpan.FromSeconds(60);

    /// <summary>Builds the options from raw environment values; blank values fall back to defaults and an unparsable duration throws.</summary>
    public static ScheduleTimeOptions From(string? stateStore, string? pollInterval, string? claimTtl, string? podNamespace) =>
        new(
            string.IsNullOrWhiteSpace(stateStore) ? "statestore" : stateStore.Trim(),
            Duration("DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL", pollInterval, DefaultAfterPollInterval),
            Duration("DWS_SCHEDULE_TIME_CLAIM_TTL", claimTtl, DefaultClaimTtl),
            string.IsNullOrWhiteSpace(podNamespace) ? null : podNamespace.Trim());

    private static TimeSpan Duration(string name, string? value, TimeSpan fallback)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return fallback;
        }

        try
        {
            return TriggerConfig.ParseDuration(value.Trim());
        }
        catch (TriggerConfigException exception)
        {
            throw new InvalidOperationException($"{name}: {exception.Message}", exception);
        }
    }
}
