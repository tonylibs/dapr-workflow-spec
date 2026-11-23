using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Console;
using Microsoft.Extensions.Options;
using Xunit;

namespace Dws.Flow.Tests;

/// <summary>Pins the framework log-level default and its operator override (design D1).</summary>
public sealed class LoggingSetupTests
{
    private const string ProjectFile = "dws-flow.csproj";

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

        Logger(services, "Dws.Flow.FlowWorkflow").IsEnabled(LogLevel.Information).Should().BeTrue();
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

    // Same rule shape as Logging:Console:LogLevel:Microsoft.AspNetCore: a provider match beats the provider-less default.
    [Fact]
    public void ProviderSpecificConfigurationTurnsFrameworkInformationBackOn()
    {
        using ServiceProvider services = Build(null, ($"Logging:{CapturingLoggerProvider.Alias}:LogLevel:Microsoft.AspNetCore", "Information"));

        foreach (string category in FrameworkCategories)
        {
            Logger(services, category).IsEnabled(LogLevel.Information).Should().BeTrue(category);
        }
    }

    [Fact]
    public void EmptyOverrideKeepsTheDefault()
    {
        using ServiceProvider services = Build(null, ("Logging:LogLevel:Microsoft.AspNetCore", ""));

        foreach (string category in FrameworkCategories)
        {
            Logger(services, category).IsEnabled(LogLevel.Information).Should().BeFalse(category);
        }
    }

    [Fact]
    public void ProgramUsesTheSharedLoggingSetupAndHealthRoute()
    {
        string program = File.ReadAllText(ProgramPath());

        program.Should().Contain("LoggingSetup.Configure(builder.Logging, builder.Configuration, Environment.GetEnvironmentVariable(\"DWS_LOG_FORMAT\"))");
        program.Should().Contain("app.MapHealthz();");
        program.Should().NotContain("MapGet(\"/healthz\"");
    }

    private static string ProgramPath()
    {
        DirectoryInfo? directory = new(AppContext.BaseDirectory);
        while (directory is not null && !File.Exists(Path.Combine(directory.FullName, ProjectFile)))
        {
            directory = directory.Parent;
        }

        return Path.Combine(directory?.FullName ?? throw new InvalidOperationException($"{ProjectFile} not found above the test output"), "src", "Program.cs");
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
