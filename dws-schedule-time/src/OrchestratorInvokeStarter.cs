using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Dws.ScheduleTime;

/// <summary>Starts v1 workflows through Dapr service invocation of the orchestrator's start endpoint (ADR 0013, option b).</summary>
public sealed class OrchestratorInvokeStarter(HttpClient http) : IWorkflowStarter
{
    // http is DaprClient.CreateInvokeHttpClient(): a request to http://<app-id>/<method> goes to the
    // sidecar's /v1.0/invoke/<app-id>/method/<method>, query intact, with DAPR_API_TOKEN when set.
    // The orchestrator answers 400 without Content-Type: application/json, so the body is always sent.
    public async Task<StartResult> StartAsync(TriggerStart start, CancellationToken cancellationToken)
    {
        if (!InstanceIds.IsValid(start.InstanceId))
        {
            throw new ArgumentException($"instance ID '{start.InstanceId}' is not one the orchestrator accepts");
        }

        Uri uri = new($"http://{start.Target.AppId}/workflows/{start.Target.Workflow}/instances?instanceId={start.InstanceId}");
        using HttpRequestMessage request = new(HttpMethod.Post, uri)
        {
            Content = new StringContent(start.Input.ToJsonString(), Encoding.UTF8, "application/json"),
        };
        try
        {
            using HttpResponseMessage response = await http.SendAsync(request, cancellationToken);
            return response.StatusCode switch
            {
                HttpStatusCode.Accepted => new StartResult(StartOutcome.Started, 202),
                HttpStatusCode.OK => new StartResult(StartOutcome.AlreadyExisted, 200),
                HttpStatusCode status => new StartResult(
                    IsRetryable(status) ? StartOutcome.RetryableFailure : StartOutcome.PermanentFailure,
                    (int)status,
                    await response.Content.ReadAsStringAsync(cancellationToken)),
            };
        }
        catch (Exception exception) when (IsTransport(exception, cancellationToken))
        {
            return new StartResult(StartOutcome.RetryableFailure, Detail: exception.Message);
        }
    }

    public async Task<StatusResult> GetStatusAsync(StartTarget target, string instanceId, CancellationToken cancellationToken)
    {
        if (!InstanceIds.IsValid(instanceId))
        {
            throw new ArgumentException($"instance ID '{instanceId}' is not one the orchestrator accepts");
        }

        try
        {
            using HttpResponseMessage response = await http.GetAsync(new Uri($"http://{target.AppId}/workflows/instances/{instanceId}"), cancellationToken);
            string body = await response.Content.ReadAsStringAsync(cancellationToken);
            return response.StatusCode switch
            {
                HttpStatusCode.OK => RuntimeStatus(body) is { } status
                    ? new StatusResult(StatusOutcome.Found, status, 200)
                    : new StatusResult(StatusOutcome.PermanentFailure, HttpStatus: 200, Detail: "status response has no runtimeStatus"),
                HttpStatusCode.NotFound => new StatusResult(StatusOutcome.NotFound, HttpStatus: 404),
                HttpStatusCode status => new StatusResult(
                    IsRetryable(status) ? StatusOutcome.RetryableFailure : StatusOutcome.PermanentFailure,
                    HttpStatus: (int)status,
                    Detail: body),
            };
        }
        catch (Exception exception) when (IsTransport(exception, cancellationToken))
        {
            return new StatusResult(StatusOutcome.RetryableFailure, Detail: exception.Message);
        }
    }

    // 5xx and 429 mean the target is not ready yet; any other 4xx will not change on a retry.
    private static bool IsRetryable(HttpStatusCode status) => (int)status >= 500 || status == HttpStatusCode.TooManyRequests;

    // A sidecar that is unreachable or slow is retryable; the caller's own cancellation is not swallowed.
    private static bool IsTransport(Exception exception, CancellationToken cancellationToken) =>
        exception is HttpRequestException || (exception is TaskCanceledException && !cancellationToken.IsCancellationRequested);

    private static string? RuntimeStatus(string body)
    {
        try
        {
            return JsonNode.Parse(body)?["runtimeStatus"]?.GetValue<string>();
        }
        catch (Exception exception) when (exception is JsonException or InvalidOperationException or FormatException)
        {
            return null;
        }
    }
}
