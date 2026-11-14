using System.Text.RegularExpressions;
using Dws.ScheduleTime;
using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class InstanceIdsTests
{
    /// <summary>The orchestrator's accepted <c>instanceId</c> shape (WorkflowController.INSTANCE_ID).</summary>
    private static readonly Regex OrchestratorInstanceId = new("^[A-Za-z0-9:._-]{1,256}$");

    [Fact]
    public void BuildsScheduledIdFromWorkflowAndFireKey()
    {
        InstanceIds.Scheduled("order-sync", "20261105T000000Z").Should().Be("dws-sched:order-sync:20261105T000000Z");
    }

    [Fact]
    public void BuildsAfterIdFromWorkflowAndSequence()
    {
        InstanceIds.After("order-sync", 3).Should().Be("dws-after:order-sync:3");
    }

    [Fact]
    public void ReservesEventSourceForPhase6b()
    {
        InstanceIds.EventSource.Should().Be("dws-event");
        InstanceIds.ScheduledSource.Should().Be("dws-sched");
        InstanceIds.AfterSource.Should().Be("dws-after");
    }

    [Fact]
    public void ProducesIdsTheOrchestratorAccepts()
    {
        string cron = InstanceIds.Scheduled("order-sync", FireKeys.Cron(DateTimeOffset.Parse("2026-11-05T00:00:00.120Z")));
        string after = InstanceIds.After("order-sync", 42);

        OrchestratorInstanceId.IsMatch(cron).Should().BeTrue();
        OrchestratorInstanceId.IsMatch(after).Should().BeTrue();
    }

    [Theory]
    [InlineData("")]
    [InlineData("order sync")]
    [InlineData("order/sync")]
    public void RejectsWorkflowNamesOutsideTheInstanceIdCharset(string workflow)
    {
        Action act = () => InstanceIds.Scheduled(workflow, "20261105T000000Z");

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void RejectsIdsLongerThan256Characters()
    {
        Action act = () => InstanceIds.Scheduled(new string('w', 240), "20261105T000000Z");

        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void RejectsNonPositiveSequence()
    {
        Action act = () => InstanceIds.After("order-sync", 0);

        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Fact]
    public void TruncatesACronFireToTheMinute()
    {
        InstanceIds.Cron("order-sync", new DateTimeOffset(2026, 11, 6, 0, 0, 1, 450, TimeSpan.Zero))
            .Should().Be("dws-sched:order-sync:20261106T000000Z");
    }

    [Fact]
    public void ConvertsACronDeliveryToUtc()
    {
        InstanceIds.Cron("w", new DateTimeOffset(2026, 11, 6, 2, 5, 30, TimeSpan.FromHours(2)))
            .Should().Be("dws-sched:w:20261106T000500Z");
    }

    [Fact]
    public void UsesTheIntervalTickFromTheAnchorForEvery()
    {
        DateTimeOffset anchor = new(2026, 11, 6, 10, 0, 0, TimeSpan.Zero);

        InstanceIds.Every("w", anchor, TimeSpan.FromMinutes(1), anchor.AddMinutes(3).AddSeconds(1.4))
            .Should().Be("dws-sched:w:20261106T100300Z");
    }

    [Fact]
    public void CollapsesAReplayedBurstOntoOneEveryKey()
    {
        DateTimeOffset anchor = new(2026, 11, 6, 10, 0, 0, TimeSpan.Zero);
        TimeSpan interval = TimeSpan.FromSeconds(10);

        string first = InstanceIds.Every("w", anchor, interval, anchor.AddSeconds(34.000));
        string last = InstanceIds.Every("w", anchor, interval, anchor.AddSeconds(34.025));

        last.Should().Be(first);
    }

    [Fact]
    public void FallsBackToTheAnchorWhenDeliveredBeforeIt()
    {
        DateTimeOffset anchor = new(2026, 11, 6, 10, 0, 5, TimeSpan.Zero);

        InstanceIds.Every("w", anchor, TimeSpan.FromMinutes(1), anchor.AddSeconds(-1)).Should().Be("dws-sched:w:20261106T100005Z");
    }

    [Fact]
    public void NumbersAfterInstancesBySequence()
    {
        InstanceIds.After("order-sync", 7).Should().Be("dws-after:order-sync:7");
    }
}
