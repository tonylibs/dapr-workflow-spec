using Dws.Flow;
using FluentAssertions;
using System.Text.Json.Nodes;
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

    [Theory]
    [InlineData("\"PT5M\"", "")]
    [InlineData("""{"seconds":10}""", ""","dataflow":"wait-dataflow" """)]
    public void LoadsWaitDurationVerbatim(string wait, string fields)
    {
        SingleNodeDefinition definition = Load("wait.json",
            ScopedDefinition("wait", fields: $""","wait":{wait}{fields}"""));

        definition.Scope.Should().Be("wait");
        definition.Tasks.Should().BeEmpty();
        definition.Children.Count.Should().Be(0);
        JsonNode.DeepEquals(definition.Wait, JsonNode.Parse(wait)).Should().BeTrue();
        definition.Dataflow.Should().Be(fields.Length == 0 ? null : "wait-dataflow");
    }

    [Theory]
    [InlineData(null, null, null)]
    [InlineData("\"PT1H\"", "data", "listen-dataflow")]
    [InlineData("""{"after":{"seconds":5}}""", "envelope", null)]
    public void LoadsListenConfigurationVerbatim(string? timeout, string? read, string? dataflow)
    {
        const string to = """{"one":{"with":{"type":"example.approved"}}}""";
        string fields = $""","to":{to}""";
        if (timeout is not null)
        {
            fields += $""","timeout":{timeout}""";
        }
        if (read is not null)
        {
            fields += $""","read":"{read}" """;
        }
        if (dataflow is not null)
        {
            fields += $""","dataflow":"{dataflow}" """;
        }

        SingleNodeDefinition definition = Load("listen.json", ScopedDefinition("listen", fields: fields));

        definition.Scope.Should().Be("listen");
        definition.Tasks.Should().BeEmpty();
        definition.Children.Count.Should().Be(0);
        JsonNode.DeepEquals(definition.To, JsonNode.Parse(to)).Should().BeTrue();
        JsonNode.DeepEquals(definition.Timeout, timeout is null ? null : JsonNode.Parse(timeout)).Should().BeTrue();
        definition.Read.Should().Be(read);
        definition.Dataflow.Should().Be(dataflow);
    }

    [Theory]
    [InlineData("do")]
    [InlineData("for")]
    [InlineData("try-catch")]
    [InlineData("fork")]
    public void LoadsDataflowReferencesOnExistingStructuralScopes(string scope)
    {
        SingleNodeDefinition definition = Load("flow.json",
            ScopedDefinition(scope, fields: ""","dataflow":"reserve-items-dataflow" """));

        definition.Dataflow.Should().Be("reserve-items-dataflow");
    }

    [Theory]
    [InlineData("wait", "", "wait")]
    [InlineData("wait", ""","wait":false""", "wait")]
    [InlineData("wait", ""","wait":null""", "wait")]
    [InlineData("listen", "", "to")]
    [InlineData("listen", ""","to":[]""", "to")]
    [InlineData("listen", ""","to":{},"timeout":42""", "timeout")]
    [InlineData("listen", ""","to":{},"timeout":null""", "timeout")]
    [InlineData("listen", ""","to":{},"read":"" """, "read")]
    [InlineData("listen", ""","to":{},"read":null""", "read")]
    [InlineData("listen", ""","to":{},"wait":"PT5M" """, "wait")]
    [InlineData("wait", ""","wait":"PT5M","to":{}""", "to")]
    [InlineData("do", ""","wait":"PT5M" """, "wait")]
    [InlineData("for", ""","to":{}""", "to")]
    [InlineData("main", ""","timeout":null""", "timeout")]
    [InlineData("fork", ""","read":"data" """, "read")]
    [InlineData("main", ""","dataflow":"reserve-items-dataflow" """, "dataflow")]
    [InlineData("do", ""","dataflow":null""", "dataflow")]
    [InlineData("do", ""","dataflow":"" """, "dataflow")]
    [InlineData("do", ""","dataflow":"Invalid" """, "dataflow")]
    [InlineData("do", ""","dataflow":"invalid\n" """, "dataflow")]
    public void RejectsInvalidScopeFields(string scope, string fields, string expectedField)
    {
        Action action = () => Load("invalid.json", ScopedDefinition(scope, fields: fields));

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain(expectedField);
    }

    [Fact]
    public void RejectsDataflowReferenceLongerThanDns1123LabelLimit()
    {
        string reference = new('a', 64);
        Action action = () => Load("invalid.json",
            ScopedDefinition("do", fields: $""","dataflow":"{reference}" """));

        action.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("DNS-1123");
    }

    [Theory]
    [InlineData("wait", ""","wait":"PT5M" """)]
    [InlineData("listen", ""","to":{}""")]
    public void RejectsTasksAndChildrenOnLeafControllers(string scope, string fields)
    {
        Action withTasks = () => Load("tasks.json",
            ScopedDefinition(scope, tasks: """[{"nested":{"set":{}}}]""", fields: fields));
        Action withChildren = () => Load("children.json",
            ScopedDefinition(scope, children: """{"nested":"nested-node"}""", fields: fields));

        withTasks.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("tasks");
        withChildren.Should().Throw<DefinitionLoadException>().Which.Message.Should().Contain("children");
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

    private static string ScopedDefinition(string scope, string tasks = "[]", string children = "{}", string fields = "") => $$"""
        {"workflow":"order","version":"order@v1","nodeId":"order-node","kind":"flow",
         "scope":"{{scope}}","tasks":{{tasks}},"children":{{children}}{{fields}}}
        """;

    private static string StepDefinition() => """
        {"workflow":"order","version":"order@v1","nodeId":"validate-order","kind":"step",
         "task":{"set":{"valid":true}}}
        """;
}
