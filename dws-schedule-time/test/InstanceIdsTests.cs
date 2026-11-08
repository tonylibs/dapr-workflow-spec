using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class InstanceIdsTests
{
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
