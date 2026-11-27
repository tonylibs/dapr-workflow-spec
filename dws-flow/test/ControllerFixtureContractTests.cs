using Dws.Flow;
using FluentAssertions;
using System.Text.Json.Nodes;
using Xunit;

namespace Dws.Flow.Tests;

/// <summary>Runs every Flow node pinned by dws-controller's v2 golden fixtures through this package's loader and classifier.</summary>
public sealed class ControllerFixtureContractTests
{
    private const string FixtureRoot = "dws-controller/src/test/resources/v2";

    public static TheoryData<string> FlowFixtures()
    {
        string root = FixtureDirectory();
        TheoryData<string> fixtures = new();
        foreach (string path in Directory.EnumerateFiles(root, "*.json", SearchOption.AllDirectories).Order(StringComparer.Ordinal))
        {
            if (Path.GetFileName(Path.GetDirectoryName(path)) == "nodes" && Kind(path) == "flow")
            {
                fixtures.Add(Path.GetRelativePath(root, path).Replace('\\', '/'));
            }
        }

        return fixtures;
    }

    [Theory]
    [MemberData(nameof(FlowFixtures))]
    public void AcceptsEveryControllerFlowFixture(string fixture)
    {
        string path = FixturePath(fixture);

        SingleNodeDefinition definition = new SingleNodeDefinitionLoader(path).Load();

        definition.NodeId.Should().Be(Path.GetFileNameWithoutExtension(path));
    }

    [Theory]
    [MemberData(nameof(FlowFixtures))]
    public void ClassifiesEveryChildAsTheKindTheControllerCompiled(string fixture)
    {
        string path = FixturePath(fixture);
        SingleNodeDefinition definition = new SingleNodeDefinitionLoader(path).Load();

        foreach (JsonNode? task in definition.Tasks)
        {
            (string name, JsonNode? body) = task!.AsObject().Single();
            string appId = definition.Children[name]!.GetValue<string>();
            ChildKind expected = Kind(SiblingNode(path, appId)) == "flow" ? ChildKind.Flow : ChildKind.Step;

            ChildClassifier.Classify(body!.AsObject()).Should().Be(expected, $"task '{name}' compiles to node '{appId}'");
        }
    }

    [Theory]
    [MemberData(nameof(FlowFixtures))]
    public void ReferencesOnlyADataflowCompanionThatIsAStep(string fixture)
    {
        string path = FixturePath(fixture);
        SingleNodeDefinition definition = new SingleNodeDefinitionLoader(path).Load();

        if (definition.Dataflow is string companion)
        {
            Kind(SiblingNode(path, companion)).Should().Be("step");
        }
    }

    private static string FixturePath(string fixture) => Path.Combine(FixtureDirectory(), fixture);

    private static string SiblingNode(string path, string appId) =>
        Path.Combine(Path.GetDirectoryName(path) ?? throw new InvalidOperationException($"no directory for '{path}'"), $"{appId}.json");

    private static string Kind(string path) =>
        JsonNode.Parse(File.ReadAllText(path))?["kind"]?.GetValue<string>()
            ?? throw new InvalidOperationException($"fixture '{path}' has no kind");

    private static string FixtureDirectory()
    {
        for (DirectoryInfo? directory = new(AppContext.BaseDirectory); directory is not null; directory = directory.Parent)
        {
            string candidate = Path.Combine(directory.FullName, FixtureRoot);
            if (Directory.Exists(candidate))
            {
                return candidate;
            }
        }

        throw new DirectoryNotFoundException($"'{FixtureRoot}' not found above '{AppContext.BaseDirectory}'");
    }
}
