using Dws.ScheduleTime;
using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class FireKeysTests
{
    private static readonly DateTimeOffset Anchor = DateTimeOffset.Parse("2026-11-05T09:00:00Z");

    [Fact]
    public void TruncatesCronDeliveryToTheMinute()
    {
        FireKeys.Cron(DateTimeOffset.Parse("2026-11-05T00:00:00.350Z")).Should().Be("20261105T000000Z");
    }

    [Fact]
    public void MapsRetriedCronDeliveryOfOneFireToTheSameKey()
    {
        string first = FireKeys.Cron(DateTimeOffset.Parse("2026-11-05T00:00:00.200Z"));
        string retry = FireKeys.Cron(DateTimeOffset.Parse("2026-11-05T00:00:30.200Z"));

        retry.Should().Be(first);
    }

    [Fact]
    public void MapsSlightlyEarlyCronDeliveryToTheDueMinute()
    {
        FireKeys.Cron(DateTimeOffset.Parse("2026-11-04T23:59:59.900Z")).Should().Be("20261105T000000Z");
    }

    [Fact]
    public void NormalizesCronDeliveryToUtc()
    {
        FireKeys.Cron(DateTimeOffset.Parse("2026-11-05T07:00:00.100+07:00")).Should().Be("20261105T000000Z");
    }

    [Fact]
    public void ComputesEveryTickFromTheAnchor()
    {
        TimeSpan hour = TimeSpan.FromHours(1);

        FireKeys.Every(Anchor, hour, DateTimeOffset.Parse("2026-11-05T10:00:00.400Z")).Should().Be("20261105T100000Z");
        FireKeys.Every(Anchor, hour, DateTimeOffset.Parse("2026-11-05T11:00:00.400Z")).Should().Be("20261105T110000Z");
    }

    [Fact]
    public void MapsRetriedEveryDeliveryOfOneTickToTheSameKey()
    {
        TimeSpan hour = TimeSpan.FromHours(1);
        string first = FireKeys.Every(Anchor, hour, DateTimeOffset.Parse("2026-11-05T10:00:00.400Z"));
        string retry = FireKeys.Every(Anchor, hour, DateTimeOffset.Parse("2026-11-05T10:00:30.400Z"));

        retry.Should().Be(first);
    }

    [Fact]
    public void MapsSlightlyEarlyEveryDeliveryToTheDueTick()
    {
        FireKeys.Every(Anchor, TimeSpan.FromHours(1), DateTimeOffset.Parse("2026-11-05T09:59:59.950Z")).Should().Be("20261105T100000Z");
    }

    [Fact]
    public void CollapsesReplayedEveryBurstOntoOneKey()
    {
        TimeSpan minute = TimeSpan.FromMinutes(1);
        DateTimeOffset afterDowntime = DateTimeOffset.Parse("2026-11-05T09:17:03Z");

        string[] keys = Enumerable.Range(0, 5)
            .Select(i => FireKeys.Every(Anchor, minute, afterDowntime.AddMilliseconds(i * 20)))
            .Distinct()
            .ToArray();

        keys.Should().ContainSingle().Which.Should().Be("20261105T091700Z");
    }

    [Fact]
    public void KeepsTheEarlyToleranceToAQuarterOfAShortInterval()
    {
        TimeSpan second = TimeSpan.FromSeconds(1);

        FireKeys.Every(Anchor, second, DateTimeOffset.Parse("2026-11-05T09:00:05.700Z")).Should().Be("20261105T090005Z");
        FireKeys.Every(Anchor, second, DateTimeOffset.Parse("2026-11-05T09:00:05.800Z")).Should().Be("20261105T090006Z");
    }

    [Fact]
    public void NeverComputesATickBeforeTheAnchor()
    {
        FireKeys.Every(Anchor, TimeSpan.FromHours(1), Anchor.AddMinutes(-10)).Should().Be("20261105T090000Z");
    }

    [Fact]
    public void RejectsIntervalsBelowOneSecond()
    {
        Action act = () => FireKeys.Every(Anchor, TimeSpan.FromMilliseconds(500), Anchor);

        act.Should().Throw<ArgumentOutOfRangeException>();
    }
}
