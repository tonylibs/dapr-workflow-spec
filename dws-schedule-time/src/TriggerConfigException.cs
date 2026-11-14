namespace Dws.ScheduleTime;

/// <summary>Signals a trigger.json document that the scheduler cannot act on.</summary>
public sealed class TriggerConfigException : Exception
{
    public TriggerConfigException(string message)
        : base(message)
    {
    }

    public TriggerConfigException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
