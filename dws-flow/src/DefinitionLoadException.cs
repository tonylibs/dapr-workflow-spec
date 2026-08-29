namespace Dws.Flow;

/// <summary>Raised when the pod's pinned single-node definition cannot be loaded or is invalid.</summary>
public sealed class DefinitionLoadException : Exception
{
    public DefinitionLoadException(string message) : base(message)
    {
    }

    public DefinitionLoadException(string message, Exception innerException) : base(message, innerException)
    {
    }
}
