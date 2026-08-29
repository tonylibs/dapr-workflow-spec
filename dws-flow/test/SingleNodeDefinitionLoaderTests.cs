using Dws.Flow;
using FluentAssertions;
using Xunit;

namespace Dws.Flow.Tests;

public sealed class SingleNodeDefinitionLoaderTests : IDisposable
{
    private readonly string temporaryDirectory = Path.Combine(Path.GetTempPath(), $"dws-flow-tests-{Guid.NewGuid()}");

    public SingleNodeDefinitionLoaderTests()
    {
        Directory.CreateDirectory(temporaryDirectory);
    }

    [Fact]
    public void RejectsValidStepShape()
    {
        Action action = () => Load("step.json", StepDefinition());

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("kind");
    }

    [Fact]
    public void AcceptsFlowShapeWithScopeTasksAndChildren()
    {
        SingleNodeDefinition definition = Load("flow.json", FlowDefinition());

        definition.Scope.Should().Be("main");
        definition.Tasks.Should().BeEmpty();
        definition.Children.Count.Should().Be(0);
    }

    [Fact]
    public void RejectsMalformedJson()
    {
        Action action = () => Load("malformed.json", "{not json");

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("failed to load");
    }

    [Fact]
    public void RejectsFlowMissingRequiredShapeFields()
    {
        Action action = () => Load("missing-tasks.json", """
            {"workflow":"order","version":"order@v1","nodeId":"order-main","kind":"flow","scope":"main","children":{}}
            """);

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("tasks");
    }

    [Fact]
    public void RejectsMissingFile()
    {
        Action action = () => new SingleNodeDefinitionLoader(Path.Combine(temporaryDirectory, "missing.json")).Load();

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("failed to load");
    }

    public void Dispose()
    {
        Directory.Delete(temporaryDirectory, recursive: true);
    }

    private SingleNodeDefinition Load(string name, string content)
    {
        string path = Path.Combine(temporaryDirectory, name);
        File.WriteAllText(path, content);
        return new SingleNodeDefinitionLoader(path).Load();
    }

    private static string FlowDefinition() => """
        {"workflow":"order","version":"order@v1","nodeId":"order-main","kind":"flow",
         "scope":"main","tasks":[],"children":{}}
        """;

    private static string StepDefinition() => """
        {"workflow":"order","version":"order@v1","nodeId":"validate-order","kind":"step",
         "task":{"set":{"valid":true}}}
        """;
}
