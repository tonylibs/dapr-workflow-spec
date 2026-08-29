namespace Dws.Flow;

/// <summary>Makes the already validated definition available to Dapr's workflow instance.</summary>
public static class FlowDefinitionHolder
{
    private static SingleNodeDefinition? definition;

    public static void Initialize(SingleNodeDefinition loadedDefinition)
    {
        definition = loadedDefinition;
    }

    public static SingleNodeDefinition Definition => definition ?? throw new InvalidOperationException("Flow definition has not been initialized");
}
