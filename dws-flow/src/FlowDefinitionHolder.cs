namespace Dws.Flow;

/// <summary>Makes the already validated definition available to Dapr's workflow instance.</summary>
public static class FlowDefinitionHolder
{
    private static SingleNodeDefinition? definition;

    private static UseResolver? resolver;

    public static void Initialize(SingleNodeDefinition loadedDefinition, UseResolver? loadedResolver = null)
    {
        definition = loadedDefinition;
        resolver = loadedResolver;
    }

    public static UseResolver UseResolver => resolver ?? UseResolver.Empty;

    public static SingleNodeDefinition Definition => definition ?? throw new InvalidOperationException("Flow definition has not been initialized");
}
