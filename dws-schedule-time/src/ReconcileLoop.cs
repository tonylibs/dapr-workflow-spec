using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Dws.ScheduleTime;

/// <summary>Reconciles jobs on every cluster change and on a periodic resync; a failed pass is logged and retried, never fatal.</summary>
public sealed class ReconcileLoop(
    ClusterSnapshot snapshot, ChangeSignal signal, Reconciler reconciler, TimeProvider time, ILogger<ReconcileLoop> logger) : BackgroundService
{
    private static readonly TimeSpan RetryDelay = TimeSpan.FromSeconds(5);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await signal.WaitAsync(ScheduleTimeOptions.Resync, time, stoppingToken);
                if (!snapshot.Synced)
                {
                    continue;
                }

                ActiveTriggerSet resolved = snapshot.Resolved;
                foreach (string error in resolved.Errors)
                {
                    logger.LogWarning("{Error}", error);
                }

                await reconciler.ReconcileAsync(resolved.Triggers, stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception exception) when (!stoppingToken.IsCancellationRequested)
            {
                // Dapr or the state store may be unavailable at boot; keep the host up and try again.
                logger.LogError(exception, "Reconcile failed; retrying in {Delay}", RetryDelay);
                signal.Notify();
                await DelayAsync(stoppingToken);
            }
        }
    }

    private async Task DelayAsync(CancellationToken cancellationToken)
    {
        try
        {
            await Task.Delay(RetryDelay, time, cancellationToken);
        }
        catch (OperationCanceledException)
        {
            // shutting down
        }
    }
}
