using Dapr.Workflow;
using Dws.Flow;

SingleNodeDefinition definition =
    new SingleNodeDefinitionLoader(Environment.GetEnvironmentVariable(SingleNodeDefinitionLoader.DefinitionPathEnvironmentVariable)).Load();
UseResolver useResolver = await UseResolverLoader.Load(
    definition,
    Environment.GetEnvironmentVariable(UseResolverLoader.StoreEnvironmentVariable),
    new DaprUseStore());
FlowDefinitionHolder.Initialize(definition, useResolver);

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton(definition);
builder.Services.AddSingleton(useResolver);
LoggingSetup.Configure(builder.Logging, builder.Configuration, Environment.GetEnvironmentVariable("DWS_LOG_FORMAT"));

builder.Services.AddDaprWorkflow(options => options.RegisterWorkflow<FlowWorkflow>(FlowWorkflow.Name));

WebApplication app = builder.Build();
app.MapHealthz();
app.Run();
