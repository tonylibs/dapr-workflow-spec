using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class SourceRulesTests
{
    private static readonly string SourceDirectory = Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "src");

    [Fact]
    public void ReadsTheEnvironmentOnlyInProgram()
    {
        string[] offenders = Directory.GetFiles(SourceDirectory, "*.cs", SearchOption.AllDirectories)
            .Where(path => Path.GetFileName(path) != "Program.cs")
            .Where(path => File.ReadAllText(path).Contains("Environment.", StringComparison.Ordinal))
            .Select(Path.GetFileName)
            .OfType<string>()
            .ToArray();

        Directory.GetFiles(SourceDirectory, "*.cs").Should().NotBeEmpty();
        offenders.Should().BeEmpty();
    }
}
