using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Forwards child calls to a wrapped caller so subclasses can add invocation behavior.</summary>
public abstract class ChildCallerDecorator : IChildCaller
{
    protected ChildCallerDecorator(IChildCaller caller)
    {
        Delegate = caller;
    }

    protected IChildCaller Delegate { get; }

    public virtual Task<JsonNode?> CallStep(string appId, FlowInput input) =>
        Delegate.CallStep(appId, input);

    public virtual Task<JsonNode?> CallFlow(string appId, string instanceId, FlowInput input) =>
        Delegate.CallFlow(appId, instanceId, input);
}
