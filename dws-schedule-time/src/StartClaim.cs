using System.Globalization;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>
/// Starts an instance at most once per instance ID: a first-write-wins claim in the state store, taken before the start.
/// A start that fails retryably deletes the claim so the retry can try again at once (the orchestrator's own
/// existence check still guards a running instance); a permanent refusal deletes it too.
/// </summary>
public sealed class StartClaim(IVersionedStore store, IWorkflowStarter starter, TimeProvider time, TimeSpan claimTtl, ILogger<StartClaim> logger)
{
    public static readonly TimeSpan StaleAfter = TimeSpan.FromSeconds(30);

    private const int MaxAttempts = 3;

    private const string Claiming = "claiming";

    private const string Started = "started";

    public static string Key(string instanceId) => "claim:" + instanceId;

    public async Task<StartResult> StartAsync(string appId, string workflow, string instanceId, CancellationToken cancellationToken)
    {
        string key = Key(instanceId);
        for (int attempt = 0; attempt < MaxAttempts; attempt++)
        {
            VersionedValue? current = await store.GetAsync(key, cancellationToken);
            VersionedValue? held;
            if (current is null)
            {
                held = await store.TryCreateAsync(key, Encode(Claiming), claimTtl, cancellationToken);
            }
            else
            {
                (string state, DateTimeOffset at) = Decode(current.Value);
                if (state == Started)
                {
                    return new StartResult(StartOutcome.Started, "already started");
                }

                if (time.GetUtcNow() - at < StaleAfter)
                {
                    return new StartResult(StartOutcome.Retryable, "another caller holds a fresh claim");
                }

                logger.LogWarning("Taking over stale claim for {InstanceId} (held since {Since:O})", instanceId, at);
                held = await store.TryUpdateAsync(key, Encode(Claiming), current.ETag, claimTtl, cancellationToken);
            }

            if (held is null)
            {
                continue;
            }

            return await StartHoldingClaimAsync(held, key, appId, workflow, instanceId, cancellationToken);
        }

        return new StartResult(StartOutcome.Retryable, "claim contended");
    }

    /// <summary>True when the claim for the instance ID records a completed start.</summary>
    public async Task<bool> IsStartedAsync(string instanceId, CancellationToken cancellationToken)
    {
        VersionedValue? current = await store.GetAsync(Key(instanceId), cancellationToken);
        return current is not null && Decode(current.Value).State == Started;
    }

    private async Task<StartResult> StartHoldingClaimAsync(
        VersionedValue held, string key, string appId, string workflow, string instanceId, CancellationToken cancellationToken)
    {
        StartResult result = await starter.StartAsync(appId, workflow, instanceId, "{}", cancellationToken);
        if (result.Outcome == StartOutcome.Started)
        {
            if (await store.TryUpdateAsync(key, Encode(Started), held.ETag, claimTtl, cancellationToken) is null)
            {
                logger.LogWarning("Claim for {InstanceId} changed while starting; the start itself succeeded", instanceId);
            }
        }
        else if (!await store.TryDeleteAsync(key, held.ETag, cancellationToken))
        {
            logger.LogWarning("Could not release claim for {InstanceId} after a failed start; it goes stale after {Stale}", instanceId, StaleAfter);
        }

        return result;
    }

    private string Encode(string state)
    {
        using MemoryStream buffer = new();
        using (Utf8JsonWriter writer = new(buffer))
        {
            writer.WriteStartObject();
            writer.WriteString("state", state);
            writer.WriteString("at", time.GetUtcNow().UtcDateTime.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'", CultureInfo.InvariantCulture));
            writer.WriteEndObject();
        }

        return System.Text.Encoding.UTF8.GetString(buffer.ToArray());
    }

    // An unreadable claim counts as a stale "claiming" so a corrupt value can never block a fire for good.
    private static (string State, DateTimeOffset At) Decode(string value)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(value);
            string state = document.RootElement.TryGetProperty("state", out JsonElement s) ? s.GetString() ?? Claiming : Claiming;
            DateTimeOffset at = document.RootElement.TryGetProperty("at", out JsonElement a)
                && DateTimeOffset.TryParse(a.GetString(), CultureInfo.InvariantCulture, DateTimeStyles.AssumeUniversal, out DateTimeOffset parsed)
                ? parsed
                : DateTimeOffset.MinValue;
            return (state, at);
        }
        catch (JsonException)
        {
            return (Claiming, DateTimeOffset.MinValue);
        }
    }
}
