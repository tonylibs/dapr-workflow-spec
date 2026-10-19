using System.Text.Json.Nodes;
using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class ChildCallerDecoratorTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task ForwardsArgumentsAndReturnsTheDelegatesTask(bool flow)
    {
        FlowInput input = new(JsonValue.Create("data"), null, "root", "2");
        RecordingCaller inner = new();
        IChildCaller caller = new ForwardingCaller(inner);

        Task<JsonNode?> call = flow
            ? caller.CallFlow("child", "root:child:2", input)
            : caller.CallStep("child", input);

        call.Should().BeSameAs(inner.Result);
        (await call).Should().BeSameAs(inner.Data);
        inner.AppId.Should().Be("child");
        inner.Input.Should().BeSameAs(input);
        inner.InstanceId.Should().Be(flow ? "root:child:2" : null);
        inner.Calls.Should().Be(1);
    }

    private sealed class ForwardingCaller(IChildCaller caller) : ChildCallerDecorator(caller);

    private sealed class RecordingCaller : IChildCaller
    {
        public JsonNode Data { get; } = JsonValue.Create("result");
        public Task<JsonNode?> Result { get; }
        public string? AppId { get; private set; }
        public string? InstanceId { get; private set; }
        public FlowInput? Input { get; private set; }
        public int Calls { get; private set; }

        public RecordingCaller() => Result = Task.FromResult<JsonNode?>(Data);

        public Task<JsonNode?> CallStep(string appId, FlowInput input)
        {
            AppId = appId;
            Input = input;
            Calls++;
            return Result;
        }

        public Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input)
        {
            InstanceId = instanceId;
            return CallStep(appId, input);
        }
    }
}
