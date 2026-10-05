using System.Text.Json.Nodes;

namespace Dws.Flow;

/// <summary>What a resolved <c>then</c> directive means for the Sequencer's program counter.</summary>
public enum ThenKind
{
    Continue,
    End,
    Exit,
}

/// <summary>The result of resolving one task's <c>then</c> directive.</summary>
public readonly record struct ThenOutcome(ThenKind Kind, int Pc)
{
    public static ThenOutcome Continue(int pc) => new(ThenKind.Continue, pc);

    public static readonly ThenOutcome End = new(ThenKind.End, -1);

    public static readonly ThenOutcome Exit = new(ThenKind.Exit, -1);
}

/// <summary>Resolves a task's <c>then</c> directive exactly as v1's <c>InterpreterWorkflow.advance</c> does.</summary>
public static class ThenResolver
{
    public static ThenOutcome Next(JsonNode? then, int pc, IReadOnlyDictionary<string, int> indexByName)
    {
        string? directive = then is JsonValue value && value.TryGetValue<string>(out string? text) ? text : null;

        if (string.IsNullOrEmpty(directive) || directive == "continue")
        {
            return ThenOutcome.Continue(pc + 1);
        }

        if (directive == "end")
        {
            return ThenOutcome.End;
        }

        if (directive == "exit")
        {
            return ThenOutcome.Exit;
        }

        if (!indexByName.TryGetValue(directive, out int target))
        {
            throw new InvalidOperationException($"flow references task '{directive}', which is not declared in this task scope");
        }

        return ThenOutcome.Continue(target);
    }
}
