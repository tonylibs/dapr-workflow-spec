package io.dws.step.config;

/** Makes the already validated definition available to Dapr's reflectively created activity. */
public final class StepDefinitionHolder {

  private static volatile SingleNodeDefinition definition;

  private StepDefinitionHolder() {}

  public static void initialize(SingleNodeDefinition loadedDefinition) {
    definition = loadedDefinition;
  }

  public static SingleNodeDefinition definition() {
    if (definition == null) {
      throw new IllegalStateException("Step definition has not been initialized");
    }
    return definition;
  }
}
