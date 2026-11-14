using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace Dws.ScheduleTime.Tests;

public class StartClaimTests
{
    private const string Id = "dws-sched:order-sync:20261106T000000Z";

    private readonly ManualTime time = new(new DateTimeOffset(2026, 11, 6, 0, 0, 5, TimeSpan.Zero));
    private readonly InMemoryVersionedStore store = new();
    private readonly RecordingStarter starter = new();

    private StartClaim NewClaim() => new(store, starter, time, TimeSpan.FromHours(24), NullLogger<StartClaim>.Instance);

    private static string ClaimJson(string state, DateTimeOffset at) => $$"""{"state":"{{state}}","at":"{{at.UtcDateTime:yyyy-MM-dd'T'HH:mm:ss.fff'Z'}}"}""";

    [Fact]
    public async Task StartsWithAnEmptyObjectAndMarksTheClaimStarted()
    {
        StartResult result = await NewClaim().StartAsync("app", "order-sync", Id, default);

        result.Outcome.Should().Be(StartOutcome.Started);
        starter.Starts.Should().ContainSingle().Which.Should().Be(new StartCall("app", "order-sync", Id, "{}"));
        store.Peek("claim:" + Id).Should().Contain("\"state\":\"started\"");
    }

    [Fact]
    public async Task SucceedsWithoutStartingWhenTheClaimIsAlreadyStarted()
    {
        store.Put("claim:" + Id, ClaimJson("started", time.GetUtcNow()));

        StartResult result = await NewClaim().StartAsync("app", "order-sync", Id, default);

        result.Outcome.Should().Be(StartOutcome.Started);
        starter.Starts.Should().BeEmpty();
    }

    [Fact]
    public async Task AsksForARetryWhenAFreshClaimIsHeld()
    {
        store.Put("claim:" + Id, ClaimJson("claiming", time.GetUtcNow() - TimeSpan.FromSeconds(29)));

        StartResult result = await NewClaim().StartAsync("app", "order-sync", Id, default);

        result.Outcome.Should().Be(StartOutcome.Retryable);
        starter.Starts.Should().BeEmpty();
    }

    [Fact]
    public async Task TakesOverAStaleClaimAndStarts()
    {
        store.Put("claim:" + Id, ClaimJson("claiming", time.GetUtcNow() - TimeSpan.FromSeconds(30)));

        StartResult result = await NewClaim().StartAsync("app", "order-sync", Id, default);

        result.Outcome.Should().Be(StartOutcome.Started);
        starter.Starts.Should().ContainSingle();
        store.Peek("claim:" + Id).Should().Contain("\"state\":\"started\"");
    }

    [Fact]
    public async Task TreatsAnUnreadableClaimAsStale()
    {
        store.Put("claim:" + Id, "garbage");

        (await NewClaim().StartAsync("app", "order-sync", Id, default)).Outcome.Should().Be(StartOutcome.Started);
    }

    [Fact]
    public async Task ReleasesTheClaimAfterARetryableFailureSoTheRetryStartsAtOnce()
    {
        starter.Enqueue(new StartResult(StartOutcome.Retryable, "HTTP 503"));
        StartClaim claim = NewClaim();

        StartResult first = await claim.StartAsync("app", "order-sync", Id, default);
        StartResult second = await claim.StartAsync("app", "order-sync", Id, default);

        first.Outcome.Should().Be(StartOutcome.Retryable);
        second.Outcome.Should().Be(StartOutcome.Started);
        starter.Starts.Should().HaveCount(2);
    }

    [Fact]
    public async Task ReleasesTheClaimAfterANonRetryableFailure()
    {
        starter.Enqueue(new StartResult(StartOutcome.NonRetryable, "HTTP 400"));

        StartResult result = await NewClaim().StartAsync("app", "order-sync", Id, default);

        result.Outcome.Should().Be(StartOutcome.NonRetryable);
        store.Peek("claim:" + Id).Should().BeNull();
    }

    [Fact]
    public async Task StartsExactlyOnceForTwoConcurrentCallersWithTheSameId()
    {
        StartClaim first = NewClaim();
        StartClaim second = NewClaim();

        StartResult[] results = await Task.WhenAll(
            first.StartAsync("app", "order-sync", Id, default),
            second.StartAsync("app", "order-sync", Id, default));

        starter.Starts.Should().ContainSingle();
        results.Count(result => result.Outcome == StartOutcome.Started).Should().BeGreaterThanOrEqualTo(1);
        results.Should().OnlyContain(result => result.Outcome != StartOutcome.NonRetryable);
    }

    [Fact]
    public async Task ReportsWhetherAClaimRecordsACompletedStart()
    {
        StartClaim claim = NewClaim();
        (await claim.IsStartedAsync(Id, default)).Should().BeFalse();

        await claim.StartAsync("app", "order-sync", Id, default);

        (await claim.IsStartedAsync(Id, default)).Should().BeTrue();
    }
}
