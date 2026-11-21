using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using Microsoft.Extensions.Options;
using Xunit;

namespace Dws.ScheduleTime.Tests;

/// <summary>Pins the framework log-level default and its operator override (design D1).</summary>
public sealed class LoggingSetupTests
{
    private static readonly string[] FrameworkCategories =
    [
        "Microsoft.AspNetCore.Hosting.Diagnostics",
        "Microsoft.AspNetCore.Routing.EndpointMiddleware",
        "Microsoft.AspNetCore.Http.Result.OkObjectResult",
    ];

    // Mirrors WebApplication.CreateBuilder: the "Logging" section is bound before Program.cs adds its own rules.
    private static ServiceProvider Build(string? logFormat, params (string Key, string Value)[] settings)
    {
        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(settings.Select(setting => new KeyValuePair<string, string?>(setting.Key, setting.Value)))
            .Build();
        return new ServiceCollection()
            .AddLogging(logging =>
            {
                logging.AddConfiguration(configuration.GetSection("Logging"));
                logging.AddProvider(new CapturingLoggerProvider());
                LoggingSetup.Configure(logging, configuration, logFormat);
            })
            .BuildServiceProvider();
    }

    private static ILogger Logger(ServiceProvider services, string category) =>
        services.GetRequiredService<ILoggerFactory>().CreateLogger(category);

    [Fact]
    public void FrameworkCategoriesLogWarningsButNotInformationByDefault()
    {
        using ServiceProvider services = Build(null);

        foreach (string category in FrameworkCategories)
        {
            Logger(services, category).IsEnabled(LogLevel.Information).Should().BeFalse(category);
            Logger(services, category).IsEnabled(LogLevel.Warning).Should().BeTrue(category);
        }
    }

    [Fact]
    public void OwnCategoriesKeepInformation()
    {
        using ServiceProvider services = Build(null);

        Logger(services, "Dws.ScheduleTime.ClusterWatcher").IsEnabled(LogLevel.Information).Should().BeTrue();
    }

    [Fact]
    public void OperatorConfigurationTurnsFrameworkInformationBackOn()
    {
        using ServiceProvider services = Build(null, ("Logging:LogLevel:Microsoft.AspNetCore", "Information"));

        foreach (string category in FrameworkCategories)
        {
            Logger(services, category).IsEnabled(LogLevel.Information).Should().BeTrue(category);
        }
    }

    [Fact]
    public void RegistersTheJsonFormatterInJsonMode()
    {
        using ServiceProvider services = Build("json");

        services.GetServices<ConsoleFormatter>().Should().ContainSingle(formatter => formatter is DwsJsonConsoleFormatter);
        services.GetRequiredService<IOptionsMonitor<ConsoleLoggerOptions>>().CurrentValue.FormatterName
            .Should().Be(DwsJsonConsoleFormatter.FormatterName);
    }

    [Fact]
    public void LeavesTheConsoleAloneInTextMode()
    {
        using ServiceProvider services = Build(null);

        services.GetServices<ConsoleFormatter>().Should().NotContain(formatter => formatter is DwsJsonConsoleFormatter);
    }
}
