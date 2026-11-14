using FluentAssertions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

/// <summary>Keeps time and environment access in their seams: an injected TimeProvider, and Environment only in Program.cs.</summary>
public class SourceScanTests
{
    private static readonly string[] Forbidden = ["Environment.", "DateTime.UtcNow", "DateTime.Now", "DateTimeOffset.UtcNow", "DateTimeOffset.Now"];

    private static string SourceDirectory()
    {
        DirectoryInfo? directory = new(AppContext.BaseDirectory);
        while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "dws-schedule-time.csproj")))
        {
            directory = directory.Parent;
        }

        return Path.Combine(directory?.FullName ?? throw new InvalidOperationException("dws-schedule-time.csproj not found above the test output"), "src");
    }

    [Fact]
    public void ReadsNoEnvironmentOrWallClockOutsideProgram()
    {
        IEnumerable<string> offenders = Directory.EnumerateFiles(SourceDirectory(), "*.cs")
            .Where(path => Path.GetFileName(path) != "Program.cs")
            .SelectMany(path => File.ReadAllLines(path)
                .Where(line => !line.TrimStart().StartsWith("//") && Forbidden.Any(line.Contains))
                .Select(line => $"{Path.GetFileName(path)}: {line.Trim()}"));

        offenders.Should().BeEmpty();
    }

    // Task 9.5: Program.cs is the only place DAPR_API_TOKEN may be read, and it must reach the invoke
    // client explicitly; the SDK's CreateInvokeHttpClient() does not read it from the environment.
    [Fact]
    public void ProgramHandsTheDaprApiTokenToTheInvokeClient()
    {
        string program = string.Join('\n', File.ReadAllLines(Path.Combine(SourceDirectory(), "Program.cs"))
            .Where(line => !line.TrimStart().StartsWith("//")));

        program.Should().Contain("Environment.GetEnvironmentVariable(\"DAPR_API_TOKEN\")");
        program.Should().Contain("OrchestratorInvokeStarter.CreateInvokeClient(daprApiToken)");
        program.Should().NotContain("DaprClient.CreateInvokeHttpClient()");
    }
}
