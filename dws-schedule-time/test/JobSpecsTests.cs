using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class JobSpecsTests
{
    [Theory]
    [InlineData("PT1H30M", "@every 1h30m")]
    [InlineData("PT1H", "@every 1h")]
    [InlineData("PT90S", "@every 1m30s")]
    [InlineData("PT1S", "@every 1s")]
    [InlineData("PT1.5S", "@every 1s500ms")]
    [InlineData("PT1M1.25S", "@every 1m1s250ms")]
    [InlineData("P1D", "@every 24h")]
    [InlineData("P1DT2H", "@every 26h")]
    public void FormatsEveryAsAGoDuration(string iso, string expected)
    {
        JobSpecs.ScheduleExpression(Triggers.Every(schedule: iso)).Should().Be(expected);
    }

    [Fact]
    public void FormatsSubMillisecondRemaindersInNanoseconds()
    {
        JobSpecs.GoDuration(TimeSpan.FromSeconds(1) + TimeSpan.FromTicks(5)).Should().Be("1s500ns");
    }

    [Fact]
    public void PrefixesCronWithAZeroSecondsField()
    {
        JobSpecs.ScheduleExpression(Triggers.Cron(schedule: "*/5 * * * *")).Should().Be("0 */5 * * * *");
    }

    [Fact]
    public void RejectsAnAfterTriggerAsARecurringSchedule()
    {
        Action act = () => JobSpecs.ScheduleExpression(Triggers.After());

        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void NamesJobsByWorkflow()
    {
        JobSpecs.ScheduledName("order-sync").Should().Be("dws-sched-order-sync");
        JobSpecs.AfterPollName("order-sync").Should().Be("dws-after-poll-order-sync");
        JobSpecs.AfterStartName("order-sync").Should().Be("dws-after-start-order-sync");
    }

    [Theory]
    [InlineData("dws-sched-a-b", "dws-sched-", "a-b")]
    [InlineData("dws-after-poll-a", "dws-after-poll-", "a")]
    [InlineData("dws-after-start-a", "dws-after-start-", "a")]
    public void ParsesOwnJobNames(string name, string prefix, string workflow)
    {
        JobSpecs.TryParseName(name, out string parsedPrefix, out string parsedWorkflow).Should().BeTrue();

        parsedPrefix.Should().Be(prefix);
        parsedWorkflow.Should().Be(workflow);
    }

    [Theory]
    [InlineData("other-job")]
    [InlineData("dws-sched-")]
    public void IgnoresForeignJobNames(string name)
    {
        JobSpecs.TryParseName(name, out _, out _).Should().BeFalse();
    }

    [Fact]
    public void KeepsTheFailurePolicyAtThreeRetriesTenSecondsApart()
    {
        JobSpecs.FailureMaxRetries.Should().Be(3);
        JobSpecs.FailureRetryInterval.Should().Be(TimeSpan.FromSeconds(10));
    }

    [Fact]
    public void RoundTripsThePayload()
    {
        JobPayload payload = JobPayload.For(Triggers.Every(schedule: "PT1H"), new DateTimeOffset(2026, 11, 6, 10, 0, 0, 250, TimeSpan.Zero));

        string json = payload.ToJson();

        json.Should().Contain("\"anchor\":\"2026-11-06T10:00:00.250Z\"").And.Contain("\"kind\":\"every\"");
        JobPayload.TryParse(json).Should().Be(payload);
    }

    [Fact]
    public void UnwrapsADoubleEncodedPayload()
    {
        JobPayload payload = JobPayload.For(Triggers.Cron(), DateTimeOffset.UnixEpoch);
        string wrapped = System.Text.Json.JsonSerializer.Serialize(payload.ToJson());

        JobPayload.TryParse(wrapped).Should().Be(payload);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("nope")]
    [InlineData("{}")]
    public void ReturnsNullForAnUnreadablePayload(string? text)
    {
        JobPayload.TryParse(text).Should().BeNull();
    }
}
