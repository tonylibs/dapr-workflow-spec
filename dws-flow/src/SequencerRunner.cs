using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>Runs a <c>main</c>/<c>do</c> node's task list in source order, dispatching each task to its
/// Step or Flow child and resolving <c>then</c> between tasks exactly as v1's interpreter does.</summary>
public static class SequencerRunner
{
    private const int MaxSteps = 10_000;

    public static async Task<JsonNode?> Run(FlowInput input, SingleNodeDefinition definition, IChildCaller caller)
    {
        List<(string Name, JsonObject Body)> items = TaskItems(definition.Tasks);
        if (items.Count == 0)
        {
            return input.Data;
        }

        Dictionary<string, int> indexByName = new();
        for (int i = 0; i < items.Count; i++)
        {
            indexByName[items[i].Name] = i;
        }

        JsonNode? data = input.Data;
        int pc = 0;
        for (int steps = 0; pc >= 0 && pc < items.Count; steps++)
        {
            if (steps > MaxSteps)
            {
                throw new InvalidOperationException("workflow exceeded 10000 steps; check for a definition loop");
            }

            (string name, JsonObject body) = items[pc];
            data = await DispatchTask(input, definition, caller, name, body, data);

            ThenOutcome outcome = ThenResolver.Next(body["then"], pc, indexByName);
            if (outcome.Kind is ThenKind.End or ThenKind.Exit)
            {
                return data;
            }

            pc = outcome.Pc;
        }

        return data;
    }

    private static Task<JsonNode?> DispatchTask(
        FlowInput input,
        SingleNodeDefinition definition,
        IChildCaller caller,
        string name,
        JsonObject body,
        JsonNode? data)
    {
        string appId = ChildAppId(definition, name);
        FlowInput childInput = input with { Data = data };

        Task<JsonNode?> call = ChildClassifier.Classify(body) == ChildKind.Step
            ? caller.CallStep(appId, childInput)
            : caller.CallFlow(appId, InstanceIds.For(input.RootInstanceId!, appId, input.IterationIndex ?? InstanceIds.DefaultIteration), childInput);

        TimeSpan? timeout = TaskTimeout.Parse(body["timeout"]);
        return timeout is null ? call : AwaitWithTimeout(caller, timeout.Value, call, name);
    }

    private static async Task<JsonNode?> AwaitWithTimeout(IChildCaller caller, TimeSpan timeout, Task<JsonNode?> call, string name)
    {
        (bool timedOut, JsonNode? result) = await caller.WithTimeout(timeout, call);
        if (timedOut)
        {
            throw new InvalidOperationException($"task '{name}' timed out after {TaskTimeout.Format(timeout)}");
        }

        return result;
    }

    private static string ChildAppId(SingleNodeDefinition definition, string name)
    {
        if (definition.Children[name] is JsonValue value && value.TryGetValue<string>(out string? appId))
        {
            return appId;
        }

        throw new InvalidOperationException($"flow references task '{name}', which has no app ID declared in children");
    }

    private static List<(string Name, JsonObject Body)> TaskItems(JsonArray tasks)
    {
        List<(string Name, JsonObject Body)> items = new();
        foreach (JsonNode? task in tasks)
        {
            JsonObject taskObject = (JsonObject)task!;
            KeyValuePair<string, JsonNode?> entry = taskObject.Single();
            items.Add((entry.Key, (JsonObject)entry.Value!));
        }

        return items;
    }
}
