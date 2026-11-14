using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class ScheduleTimeOptionsTests
{
    [Fact]
    public void AppliesDefaultsForBlankValues()
    {
        ScheduleTimeOptions options = ScheduleTimeOptions.From(null, " ", "", null);

        options.Should().Be(new ScheduleTimeOptions("statestore", TimeSpan.FromSeconds(15), TimeSpan.FromHours(24), null));
    }

    [Fact]
    public void ReadsConfiguredValues()
    {
        ScheduleTimeOptions options = ScheduleTimeOptions.From("core", "PT30S", "PT1H", "dws");

        options.Should().Be(new ScheduleTimeOptions("core", TimeSpan.FromSeconds(30), TimeSpan.FromHours(1), "dws"));
    }

    [Fact]
    public void RejectsAnInvalidDurationNamingTheVariable()
    {
        Action act = () => ScheduleTimeOptions.From(null, "soon", null, null);

        act.Should().Throw<InvalidOperationException>().Which.Message.Should().Contain("DWS_SCHEDULE_TIME_AFTER_POLL_INTERVAL");
    }
}
