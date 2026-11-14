using System.Threading.Channels;

namespace Dws.ScheduleTime;

/// <summary>Coalesces "something changed" notifications into at most one pending wake-up for the reconcile loop.</summary>
public sealed class ChangeSignal
{
    private readonly Channel<bool> channel = Channel.CreateBounded<bool>(new BoundedChannelOptions(1) { FullMode = BoundedChannelFullMode.DropWrite });

    public void Notify() => channel.Writer.TryWrite(true);

    /// <summary>Waits for a notification or for the resync interval, whichever comes first.</summary>
    public async Task WaitAsync(TimeSpan resync, TimeProvider time, CancellationToken cancellationToken)
    {
        using CancellationTokenSource stop = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        Task changed = channel.Reader.WaitToReadAsync(stop.Token).AsTask();
        Task elapsed = Task.Delay(resync, time, stop.Token);
        await Task.WhenAny(changed, elapsed);
        await stop.CancelAsync();
        cancellationToken.ThrowIfCancellationRequested();
        channel.Reader.TryRead(out _);
    }
}
