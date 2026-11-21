using Microsoft.Extensions.Logging;

namespace Dws.Flow.Tests;

internal sealed record CapturedLog(LogLevel Level, string Category, string Message);

/// <summary>Records every log entry that passes the factory's filters, across all categories.</summary>
internal sealed class CapturingLoggerProvider : ILoggerProvider
{
    private readonly object gate = new();
    private readonly List<CapturedLog> records = [];

    public IReadOnlyList<CapturedLog> Records
    {
        get
        {
            lock (gate)
            {
                return records.ToList();
            }
        }
    }

    public void Clear()
    {
        lock (gate)
        {
            records.Clear();
        }
    }

    public ILogger CreateLogger(string categoryName) => new CategoryLogger(this, categoryName);

    public void Dispose()
    {
    }

    private void Add(CapturedLog record)
    {
        lock (gate)
        {
            records.Add(record);
        }
    }

    private sealed class CategoryLogger(CapturingLoggerProvider owner, string category) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state)
            where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => logLevel != LogLevel.None;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter) =>
            owner.Add(new CapturedLog(logLevel, category, formatter(state, exception)));
    }
}
