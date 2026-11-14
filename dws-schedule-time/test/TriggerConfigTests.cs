using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class TriggerConfigTests
{
    private static string Fixture(string name) => File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "fixtures", name));

    [Fact]
    public void ParsesTheEveryFixture()
    {
        TriggerConfig config = TriggerConfig.Parse(Fixture("trigger-every.json"));

        config.Should().Be(new TriggerConfig("order-sync", "v5ac8d2eb", "order-sync", TriggerKind.Every, "PT1H30M", TimeSpan.FromMinutes(90)));
    }

    [Fact]
    public void ParsesTheCronFixture()
    {
        TriggerConfig config = TriggerConfig.Parse(Fixture("trigger-cron.json"));

        config.Kind.Should().Be(TriggerKind.Cron);
        config.Schedule.Should().Be("0 2 * * 1-5");
        config.Interval.Should().BeNull();
    }

    [Fact]
    public void ParsesTheAfterFixture()
    {
        TriggerConfig config = TriggerConfig.Parse(Fixture("trigger-after.json"));

        config.Kind.Should().Be(TriggerKind.After);
        config.Interval.Should().Be(TimeSpan.FromSeconds(30));
    }

    [Fact]
    public void NormalizesCronWhitespace()
    {
        Triggers.Cron(schedule: "0   0  * * MON-FRI").Schedule.Should().Be("0 0 * * MON-FRI");
    }

    [Theory]
    [InlineData("not json")]
    [InlineData("[]")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"every"}""")]
    [InlineData("""{"workflow":" ","version":"v","appId":"a","kind":"every","schedule":"PT1M"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"weekly","schedule":"PT1M"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"every","schedule":"every minute"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"every","schedule":"PT0.5S"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"after","schedule":"PT0S"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"cron","schedule":"* * * *"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"cron","schedule":"0 0 * * * *"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"cron","schedule":"0 0 ? * *"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"cron","schedule":"0 0 * FOO *"}""")]
    [InlineData("""{"workflow":"w","version":"v","appId":"a","kind":"cron","schedule":"MON 0 * * *"}""")]
    public void RejectsUnusableTriggers(string json)
    {
        Action act = () => TriggerConfig.Parse(json);

        act.Should().Throw<TriggerConfigException>();
    }

    [Fact]
    public void AcceptsNamedMonthsAndDays()
    {
        Triggers.Cron(schedule: "0 9 * JAN,jun mon-fri").Kind.Should().Be(TriggerKind.Cron);
    }
}
