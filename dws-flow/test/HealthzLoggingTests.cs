using System.Net;
using FluentAssertions;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Xunit;

namespace Dws.Flow.Tests;

/// <summary>Sends a real GET /healthz through Kestrel with the production logging setup and checks what gets logged (design D3).</summary>
public sealed class HealthzLoggingTests
{
    private static async Task<IReadOnlyList<CapturedLog>> ProbeAsync(params (string Key, string Value)[] settings)
    {
        CapturingLoggerProvider capture = new();
        WebApplicationBuilder builder = WebApplication.CreateBuilder();
        builder.Configuration.AddInMemoryCollection(settings.Select(setting => new KeyValuePair<string, string?>(setting.Key, setting.Value)));
        builder.WebHost.UseUrls("http://127.0.0.1:0");
        builder.Logging.ClearProviders();
        builder.Logging.AddProvider(capture);
        LoggingSetup.Configure(builder.Logging, builder.Configuration, null);

        await using WebApplication app = builder.Build();
        app.MapHealthz();
        await app.StartAsync();
        string address = app.Services.GetRequiredService<IServer>().Features.GetRequiredFeature<IServerAddressesFeature>().Addresses.First();

        // Startup lines (Microsoft.Hosting.Lifetime) are not part of the probe.
        capture.Clear();
        using HttpClient client = new();
        HttpResponseMessage response = await client.GetAsync(new Uri(new Uri(address), "/healthz"));
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        // Stopping drains the request, so its "Request finished" record has been written before the assertion.
        await app.StopAsync();
        return capture.Records;
    }

    [Fact]
    public async Task HealthProbeWritesNoInformationRecord()
    {
        IReadOnlyList<CapturedLog> records = await ProbeAsync();

        // StopAsync's own "Application is shutting down" line is the host's, not the probe's.
        records.Where(record => record.Level <= LogLevel.Information && record.Category != "Microsoft.Hosting.Lifetime")
            .Should().BeEmpty();
    }

    [Fact]
    public async Task OperatorOverrideBringsTheRequestRecordsBack()
    {
        IReadOnlyList<CapturedLog> records = await ProbeAsync(("Logging:LogLevel:Microsoft.AspNetCore", "Information"));

        records.Should().Contain(record => record.Category == "Microsoft.AspNetCore.Hosting.Diagnostics");
    }
}
