using System.Net;
using System.Text;
using System.Text.Json;
using Dapr.Client;

namespace Dws.ScheduleTime;

/// <summary>Starts v1 workflows through Dapr service invocation of the orchestrator's <c>POST workflows/{name}/instances?instanceId=</c> (ADR 0013, option b).</summary>
public sealed class OrchestratorInvokeStarter(HttpClient http) : IWorkflowStarter
{
    // http comes from CreateInvokeClient: a request to http://<app-id>/<method> goes to the sidecar's
    // /v1.0/invoke/<app-id>/method/<method>, query intact, with the dapr-api-token header when a token
    // was given. The orchestrator answers 400 without Content-Type: application/json, so the body is
    // always sent.

    /// <summary>
    /// The service-invocation client the starter needs. <paramref name="daprApiToken"/> must be passed
    /// explicitly: the SDK's CreateInvokeHttpClient reads the sidecar endpoint from the environment but
    /// not DAPR_API_TOKEN, so a client built without it gets 401 from a token-protected sidecar.
    /// </summary>
    public static HttpClient CreateInvokeClient(string? daprApiToken, string? daprEndpoint = null) =>
        DaprClient.CreateInvokeHttpClient(
            daprEndpoint: daprEndpoint,
            daprApiToken: string.IsNullOrWhiteSpace(daprApiToken) ? null : daprApiToken.Trim());

    public async Task<StartResult> StartAsync(string appId, string workflow, string instanceId, string inputJson, CancellationToken cancellationToken)
    {
        RequireValid(instanceId);
        Uri uri = new($"http://{appId}/workflows/{Uri.EscapeDataString(workflow)}/instances?instanceId={instanceId}");
        using HttpRequestMessage request = new(HttpMethod.Post, uri)
        {
            Content = new StringContent(inputJson, Encoding.UTF8, "application/json"),
        };
        try
        {
            using HttpResponseMessage response = await http.SendAsync(request, cancellationToken);
            return MapStart(response.StatusCode);
        }
        catch (Exception exception) when (IsTransport(exception, cancellationToken))
        {
            return new StartResult(StartOutcome.Retryable, $"transport error: {exception.Message}");
        }
    }

    public async Task<InstanceStatus> GetStatusAsync(string appId, string instanceId, CancellationToken cancellationToken)
    {
        RequireValid(instanceId);
        try
        {
            using HttpResponseMessage response = await http.GetAsync(new Uri($"http://{appId}/workflows/instances/{instanceId}"), cancellationToken);
            if (response.StatusCode == HttpStatusCode.NotFound)
            {
                return InstanceStatus.NotFound;
            }

            if (!response.IsSuccessStatusCode)
            {
                return InstanceStatus.Error;
            }

            return MapStatus(await response.Content.ReadAsStringAsync(cancellationToken));
        }
        catch (Exception exception) when (IsTransport(exception, cancellationToken))
        {
            return InstanceStatus.Error;
        }
    }

    /// <summary>202 (started) and 200 (already existed) mean the instance exists; 5xx and 429 are worth retrying; any other status is a permanent refusal.</summary>
    public static StartResult MapStart(HttpStatusCode status) => status switch
    {
        HttpStatusCode.Accepted or HttpStatusCode.OK => new StartResult(StartOutcome.Started, $"HTTP {(int)status}"),
        HttpStatusCode.TooManyRequests => new StartResult(StartOutcome.Retryable, "HTTP 429"),
        >= HttpStatusCode.InternalServerError => new StartResult(StartOutcome.Retryable, $"HTTP {(int)status}"),
        _ => new StartResult(StartOutcome.NonRetryable, $"HTTP {(int)status}"),
    };

    /// <summary>Maps the orchestrator's <c>InstanceStatusResponse</c> body (<c>{"runtimeStatus": "RUNNING"|...}</c>, Java SDK enum names) onto <see cref="InstanceStatus"/>.</summary>
    public static InstanceStatus MapStatus(string body)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(body);
            if (document.RootElement.ValueKind != JsonValueKind.Object
                || !document.RootElement.TryGetProperty("runtimeStatus", out JsonElement status)
                || status.ValueKind != JsonValueKind.String)
            {
                return InstanceStatus.Error;
            }

            return status.GetString()?.ToUpperInvariant() switch
            {
                "RUNNING" or "PENDING" or "SUSPENDED" or "CONTINUED_AS_NEW" => InstanceStatus.Running,
                "COMPLETED" or "FAILED" or "TERMINATED" or "CANCELED" or "CANCELLED" => InstanceStatus.Terminal,
                _ => InstanceStatus.Error,
            };
        }
        catch (JsonException)
        {
            return InstanceStatus.Error;
        }
    }

    private static void RequireValid(string instanceId)
    {
        if (!InstanceIds.IsValid(instanceId))
        {
            throw new ArgumentException($"instance ID '{instanceId}' is not one the orchestrator accepts");
        }
    }

    // A sidecar that is unreachable or slow is retryable; the caller's own cancellation is not swallowed.
    private static bool IsTransport(Exception exception, CancellationToken caller) =>
        !caller.IsCancellationRequested
        && exception is HttpRequestException or TaskCanceledException or TimeoutException;
}
