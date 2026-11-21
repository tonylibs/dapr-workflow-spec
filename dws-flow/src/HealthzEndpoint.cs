namespace Dws.Flow;

/// <summary>Maps the liveness/readiness route the chart probes, shared by Program.cs and its logging test.</summary>
public static class HealthzEndpoint
{
    public static RouteHandlerBuilder MapHealthz(this IEndpointRouteBuilder endpoints) =>
        endpoints.MapGet("/healthz", () => Results.Ok(new { status = "ok" }));
}
