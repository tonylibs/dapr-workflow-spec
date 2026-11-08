using System.Net;
using System.Text;
using System.Text.Json;
using Dapr.Client;

namespace Dws.ScheduleTime;

#pragma warning disable CS0618 // InvokeMethodWithResponseAsync is marked obsolete, but it is the call that carries the client's DAPR_API_TOKEN and the sidecar endpoint.

/// <summary>Starts instances through Dapr service invocation of the v1 orchestrator's <c>POST workflows/{name}/instances?instanceId=</c>.</summary>
public sealed class OrchestratorInvokeStarter(DaprClient client) : IWorkflowStarter
{
    public async Task<StartResult> StartAsync(string appId, string workflow, string instanceId, string inputJson, CancellationToken cancellationToken)
    {
        using StringContent body = new(inputJson, Encoding.UTF8, "application/json");
        using HttpRequestMessage request = client.CreateInvokeMethodRequest(
            HttpMethod.Post,
            appId,
            $"workflows/{Uri.EscapeDataString(workflow)}/instances",
            [new KeyValuePair<string, string>("instanceId", instanceId)],
            body);
        try
        {
            using HttpResponseMessage response = await client.InvokeMethodWithResponseAsync(request, cancellationToken);
            return MapStart(response.StatusCode);
        }
        catch (Exception exception) when (IsTransport(exception, cancellationToken))
        {
            return new StartResult(StartOutcome.Retryable, $"transport error: {exception.Message}");
        }
    }

    public async Task<InstanceStatus> GetStatusAsync(string appId, string instanceId, CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = client.CreateInvokeMethodRequest(
            HttpMethod.Get,
            appId,
            $"workflows/instances/{Uri.EscapeDataString(instanceId)}");
        try
        {
            using HttpResponseMessage response = await client.InvokeMethodWithResponseAsync(request, cancellationToken);
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

    /// <summary>202 and 200 mean the instance exists; 5xx and 429 are worth retrying; any other status is a permanent refusal.</summary>
    public static StartResult MapStart(HttpStatusCode status) => status switch
    {
        HttpStatusCode.Accepted or HttpStatusCode.OK => new StartResult(StartOutcome.Started, $"HTTP {(int)status}"),
        HttpStatusCode.TooManyRequests => new StartResult(StartOutcome.Retryable, "HTTP 429"),
        >= HttpStatusCode.InternalServerError => new StartResult(StartOutcome.Retryable, $"HTTP {(int)status}"),
        _ => new StartResult(StartOutcome.NonRetryable, $"HTTP {(int)status}"),
    };

    /// <summary>Maps the orchestrator's <c>{"status": "RUNNING"|...}</c> body (Java enum names) onto <see cref="InstanceStatus"/>.</summary>
    public static InstanceStatus MapStatus(string body)
    {
        try
        {
            using JsonDocument document = JsonDocument.Parse(body);
            if (document.RootElement.ValueKind != JsonValueKind.Object
                || !document.RootElement.TryGetProperty("status", out JsonElement status)
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

    // A cancelled caller token is a shutdown, not a transport failure.
    private static bool IsTransport(Exception exception, CancellationToken caller) =>
        !caller.IsCancellationRequested
        && exception is HttpRequestException or Dapr.Client.InvocationException or TaskCanceledException or TimeoutException;
}
