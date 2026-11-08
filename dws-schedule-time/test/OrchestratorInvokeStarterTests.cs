using System.Net;
using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class OrchestratorInvokeStarterTests
{
    [Theory]
    [InlineData(HttpStatusCode.Accepted, StartOutcome.Started)]
    [InlineData(HttpStatusCode.OK, StartOutcome.Started)]
    [InlineData(HttpStatusCode.InternalServerError, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.BadGateway, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.ServiceUnavailable, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.TooManyRequests, StartOutcome.Retryable)]
    [InlineData(HttpStatusCode.BadRequest, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.NotFound, StartOutcome.NonRetryable)]
    [InlineData(HttpStatusCode.Conflict, StartOutcome.NonRetryable)]
    public void MapsStartStatusCodes(HttpStatusCode status, StartOutcome expected)
    {
        OrchestratorInvokeStarter.MapStart(status).Outcome.Should().Be(expected);
    }

    [Theory]
    [InlineData("""{"instanceId":"x","status":"RUNNING"}""", InstanceStatus.Running)]
    [InlineData("""{"status":"pending"}""", InstanceStatus.Running)]
    [InlineData("""{"status":"SUSPENDED"}""", InstanceStatus.Running)]
    [InlineData("""{"status":"CONTINUED_AS_NEW"}""", InstanceStatus.Running)]
    [InlineData("""{"status":"COMPLETED","output":"{}"}""", InstanceStatus.Terminal)]
    [InlineData("""{"status":"FAILED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"status":"TERMINATED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"status":"CANCELED"}""", InstanceStatus.Terminal)]
    [InlineData("""{"status":"WHAT"}""", InstanceStatus.Error)]
    [InlineData("""{"state":"RUNNING"}""", InstanceStatus.Error)]
    [InlineData("not json", InstanceStatus.Error)]
    [InlineData("[]", InstanceStatus.Error)]
    public void MapsStatusBodies(string body, InstanceStatus expected)
    {
        OrchestratorInvokeStarter.MapStatus(body).Should().Be(expected);
    }
}
