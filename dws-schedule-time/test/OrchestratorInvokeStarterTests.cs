using System.Net;
using System.Text.Json.Nodes;
using Dapr.Client;
using Dws.ScheduleTime;
using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class OrchestratorInvokeStarterTests
{
    private const string FireId = "dws-sched:order-sync:20261105T000000Z";
    private static readonly StartTarget Target = new("order-sync", "dws-order-sync-3f9a");

    [Fact]
    public async Task PostsToTheOrchestratorStartEndpointWithTheInstanceIdAndEmptyJsonInput()
    {
        RecordingHandler handler = new(HttpStatusCode.Accepted);
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Started);
        result.Succeeded.Should().BeTrue();
        RecordedRequest request = handler.Requests.Should().ContainSingle().Subject;
        request.Method.Should().Be(HttpMethod.Post);
        request.Uri.Should().Be(new Uri($"http://dws-order-sync-3f9a/workflows/order-sync/instances?instanceId={FireId}"));
        request.ContentType.Should().Be("application/json");
        request.Body.Should().Be("{}");
    }

    [Fact]
    public void BuildsTimeTriggeredStartsWithAnEmptyObjectInput()
    {
        TriggerStart start = TriggerStart.ForTimeTrigger(Target, FireId);

        start.Input.Should().BeOfType<JsonObject>().Which.Count.Should().Be(0);
        start.Target.Should().Be(Target);
        start.InstanceId.Should().Be(FireId);
    }

    [Fact]
    public async Task TreatsOkAsTheExistingInstance()
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(HttpStatusCode.OK)));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.AlreadyExisted);
        result.Succeeded.Should().BeTrue();
    }

    [Theory]
    [InlineData(HttpStatusCode.InternalServerError)]
    [InlineData(HttpStatusCode.BadGateway)]
    [InlineData(HttpStatusCode.ServiceUnavailable)]
    [InlineData(HttpStatusCode.TooManyRequests)]
    public async Task ReportsServerErrorsAndThrottlingAsRetryable(HttpStatusCode status)
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(status)));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.RetryableFailure);
        result.Retryable.Should().BeTrue();
        result.Succeeded.Should().BeFalse();
        result.HttpStatus.Should().Be((int)status);
    }

    [Theory]
    [InlineData(HttpStatusCode.BadRequest)]
    [InlineData(HttpStatusCode.NotFound)]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    public async Task ReportsOtherClientErrorsAsNonRetryable(HttpStatusCode status)
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(status)));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.PermanentFailure);
        result.Retryable.Should().BeFalse();
        result.Succeeded.Should().BeFalse();
    }

    [Fact]
    public async Task ReportsAnUnreachableSidecarAsRetryable()
    {
        RecordingHandler handler = new(_ => throw new HttpRequestException("connection refused"));
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.RetryableFailure);
        result.HttpStatus.Should().BeNull();
    }

    [Fact]
    public async Task ReportsAnHttpClientTimeoutAsRetryable()
    {
        RecordingHandler handler = new(_ => throw new TaskCanceledException("timed out", new TimeoutException()));
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.RetryableFailure);
    }

    [Fact]
    public async Task PropagatesCallerCancellation()
    {
        using CancellationTokenSource cancelled = new();
        await cancelled.CancelAsync();
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(HttpStatusCode.Accepted)));

        Func<Task> act = () => starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), cancelled.Token);

        await act.Should().ThrowAsync<OperationCanceledException>();
    }

    [Fact]
    public async Task ReadsTheRuntimeStatusOfAnExistingInstance()
    {
        RecordingHandler handler = new(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent($$$"""{"instanceId":"{{{FireId}}}","runtimeStatus":"COMPLETED","output":{"ok":true}}"""),
        });
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StatusResult result = await starter.GetStatusAsync(Target, FireId, CancellationToken.None);

        result.Outcome.Should().Be(StatusOutcome.Found);
        result.RuntimeStatus.Should().Be("COMPLETED");
        RecordedRequest request = handler.Requests.Should().ContainSingle().Subject;
        request.Method.Should().Be(HttpMethod.Get);
        request.Uri.Should().Be(new Uri($"http://dws-order-sync-3f9a/workflows/instances/{FireId}"));
    }

    [Fact]
    public async Task ReportsAnUnknownInstanceAsNotFound()
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(HttpStatusCode.NotFound)));

        StatusResult result = await starter.GetStatusAsync(Target, FireId, CancellationToken.None);

        result.Outcome.Should().Be(StatusOutcome.NotFound);
    }

    [Fact]
    public async Task ReportsAStatusResponseWithoutRuntimeStatusAsNonRetryable()
    {
        RecordingHandler handler = new(_ => new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("not json") });
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        StatusResult result = await starter.GetStatusAsync(Target, FireId, CancellationToken.None);

        result.Outcome.Should().Be(StatusOutcome.PermanentFailure);
    }

    [Theory]
    [InlineData(HttpStatusCode.ServiceUnavailable, StatusOutcome.RetryableFailure)]
    [InlineData(HttpStatusCode.TooManyRequests, StatusOutcome.RetryableFailure)]
    [InlineData(HttpStatusCode.Forbidden, StatusOutcome.PermanentFailure)]
    public async Task ClassifiesStatusFailuresLikeStartFailures(HttpStatusCode status, StatusOutcome expected)
    {
        OrchestratorInvokeStarter starter = new(new HttpClient(new RecordingHandler(status)));

        StatusResult result = await starter.GetStatusAsync(Target, FireId, CancellationToken.None);

        result.Outcome.Should().Be(expected);
    }

    [Fact]
    public async Task RejectsAnInstanceIdTheOrchestratorWouldRejectWithoutCallingIt()
    {
        RecordingHandler handler = new(HttpStatusCode.Accepted);
        OrchestratorInvokeStarter starter = new(new HttpClient(handler));

        Func<Task> act = () => starter.StartAsync(TriggerStart.ForTimeTrigger(Target, "bad id/with spaces"), CancellationToken.None);

        await act.Should().ThrowAsync<ArgumentException>();
        handler.Requests.Should().BeEmpty();
    }

    [Fact]
    public async Task RoutesThroughTheDaprSidecarInvokeApiWithTheQueryIntact()
    {
        RecordingHandler sidecar = new(HttpStatusCode.Accepted);
        // The handler DaprClient.CreateInvokeHttpClient() wires in production.
        InvocationHandler invocation = new() { InnerHandler = sidecar, DaprEndpoint = "http://127.0.0.1:3500" };
        OrchestratorInvokeStarter starter = new(new HttpClient(invocation));

        StartResult result = await starter.StartAsync(TriggerStart.ForTimeTrigger(Target, FireId), CancellationToken.None);

        result.Outcome.Should().Be(StartOutcome.Started);
        sidecar.Requests.Should().ContainSingle().Which.Uri.Should().Be(
            new Uri($"http://127.0.0.1:3500/v1.0/invoke/dws-order-sync-3f9a/method/workflows/order-sync/instances?instanceId={FireId}"));
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
