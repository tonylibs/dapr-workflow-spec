using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class TimeoutCallerTests
{
    private static readonly FlowInput Input = new(null, null, "root", null);
    private static readonly TimeSpan Duration = TimeSpan.FromSeconds(5);

    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public async Task ReturnsTheChildResultAndCancelsTheTimer(bool flow, bool nullResult)
    {
        TaskCompletionSource<JsonNode?> child = new();
        TaskCompletionSource timer = new();
        List<string> commands = new();
        CancellationToken timerToken = default;
        StubCaller inner = new(() =>
        {
            commands.Add("child");
            return child.Task;
        });
        TimeoutCaller caller = new(inner, "slow", Duration, (duration, token) =>
        {
            duration.Should().Be(Duration);
            timerToken = token;
            commands.Add("timer");
            return timer.Task;
        });

        Task<JsonNode?> call = Invoke(caller, flow);
        JsonNode? expected = nullResult ? null : JsonValue.Create("result");
        child.SetResult(expected);

        (await call).Should().BeSameAs(expected);
        timerToken.IsCancellationRequested.Should().BeTrue();
        commands.Should().Equal("child", "timer");
        inner.Calls.Should().Be(1);
        inner.AppId.Should().Be("child");
        inner.InstanceId.Should().Be(flow ? "root:child:0" : null);
        inner.Input.Should().BeSameAs(Input);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task PropagatesChildFailureUnchangedAndCancelsTheTimer(bool flow)
    {
        TaskCompletionSource<JsonNode?> child = new();
        CancellationToken timerToken = default;
        TimeoutCaller caller = new(new StubCaller(() => child.Task), "slow", Duration, (_, token) =>
        {
            timerToken = token;
            return new TaskCompletionSource().Task;
        });
        InvalidOperationException failure = new("task 'nested' timed out after PT1S");
        Task<JsonNode?> call = Invoke(caller, flow);

        child.SetException(failure);
        Func<Task> action = () => call;

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Should().BeSameAs(failure);
        timerToken.IsCancellationRequested.Should().BeTrue();
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task FailsWithTheTaskNameWhenTheTimerWinsWithoutCompletingTheChild(bool flow)
    {
        TaskCompletionSource<JsonNode?> child = new();
        TaskCompletionSource timer = new();
        TimeoutCaller caller = new(new StubCaller(() => child.Task), "slow", Duration, (_, _) => timer.Task);
        Task<JsonNode?> call = Invoke(caller, flow);

        timer.SetResult();
        Func<Task> action = () => call;

        await action.Should().ThrowAsync<InvalidOperationException>()
            .WithMessage("task 'slow' timed out after PT5S");
        child.Task.IsCompleted.Should().BeFalse();
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task PrefersTheCallWhenBothTasksAreAlreadyComplete(bool flow)
    {
        JsonNode result = JsonValue.Create("result");
        CancellationToken timerToken = default;
        TimeoutCaller caller = new(new StubCaller(() => Task.FromResult<JsonNode?>(result)), "slow", Duration, (_, token) =>
        {
            timerToken = token;
            return Task.CompletedTask;
        });

        (await Invoke(caller, flow)).Should().BeSameAs(result);
        timerToken.IsCancellationRequested.Should().BeTrue();
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task PropagatesSynchronousChildFailureWithoutCreatingATimer(bool flow)
    {
        InvalidOperationException failure = new("child failed");
        TimeoutCaller caller = new(new StubCaller(() => throw failure), "slow", Duration,
            (_, _) => throw new InvalidOperationException("timer must not be created"));

        Func<Task> action = () => Invoke(caller, flow);

        (await action.Should().ThrowAsync<InvalidOperationException>()).Which.Should().BeSameAs(failure);
    }

    private static Task<JsonNode?> Invoke(IChildCaller caller, bool flow) => flow
        ? caller.CallFlow("child", "root:child:0", Input)
        : caller.CallStep("child", Input);

    private sealed class StubCaller(Func<Task<JsonNode?>> invoke) : IChildCaller
    {
        public int Calls { get; private set; }
        public string? AppId { get; private set; }
        public string? InstanceId { get; private set; }
        public FlowInput? Input { get; private set; }

        public Task<JsonNode?> CallStep(string appId, FlowInput input)
        {
            Calls++;
            AppId = appId;
            Input = input;
            return invoke();
        }

        public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input)
        {
            InstanceId = instanceId;
            return CallStep(appId, input);
        }
    }
}
