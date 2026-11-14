using System.Net;
using Dapr.Client;
using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class OrchestratorInvokeStarterTests
{
    private const string FireId = "dws-sched:order-sync:20261105T000000Z";
    private const string AppId = "dws-order-sync-3f9a";

    [Fact]
    public async Task PostsToTheOrchestratorStartEndpointWithTheInstanceIdAndJsonInput()
    {
        RecordingHandler handler = new(HttpStatusCode.Accepted);
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StartResult result = await starter.StartAsync(AppId, "order-sync", FireId, "{}", CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Started);
        RecordedRequest request = handler.Requests.Should().ContainSingle().Subject;
        request.Method.Should().Be(HttpMethod.Post);
        request.Uri.Should().Be(new Uri($"http://{AppId}/workflows/order-sync/instances?instanceId={FireId}"));
        request.ContentType.Should().Be("application/json");
        request.Body.Should().Be("{}");
    }

    [Theory]
    [InlineData(HttpStatusCode.Accepted, StartOutcome.Started)]
    [InlineData(HttpStatusCode.OK, StartOutcome.Started)]
    [InlineData(HttpStatusCode.InternalServerError, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.BadGateway, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.ServiceUnavailable, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.TooManyRequests, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.BadRequest, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.NotFound, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.Unauthorized, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.Forbidden, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.Conflict, StartOutcome.NonRetryable)]
    public async Task MapsStartStatusCodes(HttpStatusCode status, StartOutcome expected)
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(status)));

        StartResult result = await starter.StartAsync(AppId, "order-sync", FireId, "{}", CancellationToken.None);

        result.Outcome.Should().Be(expected);
        OrchestratorInvokeStarter.MapStart(status).Outcome.Should().Be(expected);
    }

    [Fact]
    public async Task ReportsAnUnreachableSidecarAsRetryable()
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(_ => throw new HttpRequestException("connection refused"))));

        StartResult result = await starter.StartAsync(AppId, "order-sync", FireId, "{}", CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Retryable);
    }

    [Fact]
    public async Task ReportsAnHttpClientTimeoutAsRetryable()
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(_ => throw new TaskCanceledException("timed out", new TimeoutException()))));

        StartResult result = await starter.StartAsync(AppId, "order-sync", FireId, "{}", CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Retryable);
    }

    [Fact]
    public async Task PropagatesCallerCancellation()
    {
        using CancellationTokenSource cancelled = new();
        await cancelled.CancelAsync();
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(HttpStatusCode.Accepted)));

        Func<Task> act = () => starter.StartAsync(AppId, "order-sync", FireId, "{}", cancelled.Token);

        await act.Should().ThrowAsync<OperationCanceledException>();
    }

    [Fact]
    public async Task RejectsAnInstanceIdTheOrchestratorWouldRejectWithoutCallingIt()
    {
        RecordingHandler handler = new(HttpStatusCode.Accepted);
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        Func<Task> act = () => starter.StartAsync(AppId, "order-sync", "bad id/with spaces", "{}", CancellationToken.None);

        await act.Should().ThrowAsync<ArgumentException>();
        handler.Requests.Should().BeEmpty();
    }

    [Fact]
    public async Task ReadsTheRuntimeStatusOfAnExistingInstance()
    {
        RecordingHandler handler = new(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            // The orchestrator's InstanceStatusResponse(instanceId, runtimeStatus, output).
            Content = new StringContent($$$"""{"instanceId":"{{{FireId}}}","runtimeStatus":"COMPLETED","output":{"ok":true}}"""),
        });
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        InstanceStatus status = await starter.GetStatusAsync(AppId, FireId, CancellationToken.None);

        status.Should().Be(InstanceStatus.Terminal);
        RecordedRequest request = handler.Requests.Should().ContainSingle().Subject;
        request.Method.Should().Be(HttpMethod.Get);
        request.Uri.Should().Be(new Uri($"http://{AppId}/workflows/instances/{FireId}"));
    }

    [Theory]
    [InlineData(HttpStatusCode.NotFound, InstanceStatus.NotFound)]
    [InlineData(HttpStatusCode.ServiceUnavailable, InstanceStatus.Error)]
    [InlineData(HttpStatusCode.Forbidden, InstanceStatus.Error)]
    public async Task MapsStatusLookupFailures(HttpStatusCode status, InstanceStatus expected)
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(status)));

        (await starter.GetStatusAsync(AppId, FireId, CancellationToken.None)).Should().Be(expected);
    }

    [Fact]
    public async Task ReportsAnUnreachableSidecarOnStatusAsError()
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(_ => throw new HttpRequestException("connection refused"))));

        (await starter.GetStatusAsync(AppId, FireId, CancellationToken.None)).Should().Be(InstanceStatus.Error);
    }

    [Theory]
    [InlineData("""{"instanceId":"x","runtimeStatus":"RUNNING"}""", InstanceStatus.Running)]
    [InlineData("""{"runtimeStatus":"pending"}""", InstanceStatus.Running)]
    [InlineData("""{"runtimeStatus":"SUSPENDED"}""", InstanceStatus.Running)]
    [InlineData("""{"runtimeStatus":"CONTINUED_AS_NEW"}""", InstanceStatus.Running)]
    [InlineData("""{"runtimeStatus":"COMPLETED","output":{}}""", InstanceStatus.Terminal)]
    [InlineData("""{"runtimeStatus":"FAILED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"runtimeStatus":"TERMINATED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"runtimeStatus":"CANCELED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"runtimeStatus":"WHAT"}""", InstanceStatus.Error)]
    [InlineData("""{"status":"RUNNING"}""", InstanceStatus.Error)]
    [InlineData("not json", InstanceStatus.Error)]
    [InlineData("[]", InstanceStatus.Error)]
    public void MapsStatusBodies(string body, InstanceStatus expected)
    {
        OrchestratorInvokeStarter.MapStatus(body).Should().Be(expected);
    }

    [Fact]
    public async Task RoutesThroughTheDaprSidecarInvokeApiWithTheQueryIntact()
    {
        RecordingHandler sidecar = new(HttpStatusCode.Accepted);
        // The handler DaprClient.CreateInvokeHttpClient() wires in production.
        InvocationHandler invocation = new() { InnerHandler = sidecar, DaprEndpoint = "http://127.0.0.1:3500" };
        OrchestratorInvokeStarter starter = new(new HttpClient(invocation));

        StartResult result = await starter.StartAsync(AppId, "order-sync", FireId, "{}", CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Started);
        sidecar.Requests.Should().ContainSingle().Which.Uri.Should().Be(
            new Uri($"http://127.0.0.1:3500/v1.0/invoke/{AppId}/method/workflows/order-sync/instances?instanceId={FireId}"));
    }

    private sealed record RecordedRequest(HttpMethod Method, Uri? Uri, string? ContentType, string? Body);

    private sealed class RecordingHandler(Func<HttpRequestMessage, HttpResponseMessage> respond) : HttpMessageHandler
    {
        public RecordingHandler(HttpStatusCode status)
            : this(_ => new HttpResponseMessage(status) { Content = new StringContent("{}") })
        {
        }

        public List<RecordedRequest> Requests { get; } = [];

        protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested();
            string? body = request.Content is { } content ? await content.ReadAsStringAsync(cancellationToken) : null;
            Requests.Add(new RecordedRequest(request.Method, request.RequestUri, request.Content?.Headers.ContentType?.MediaType, body));
            return respond(request);
        }
    }
}
