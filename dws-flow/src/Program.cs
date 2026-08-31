using Dapr.Workflow;
using Dws.Flow;

SingleNodeDefinition definition =
    new SingleNodeDefinitionLoader(Environment.GetEnvironmentVariable(SingleNodeDefinitionLoader.DefinitionPathEnvironmentVariable)).Load();
FlowDefinitionHolder.Initialize(definition);

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);
builder.Services.AddSingleton(definition);
builder.Services.AddDaprWorkflow(options => options.RegisterWorkflow<FlowWorkflow>(FlowWorkflow.Name));

WebApplication app = builder.Build();
app.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));
app.Run();
