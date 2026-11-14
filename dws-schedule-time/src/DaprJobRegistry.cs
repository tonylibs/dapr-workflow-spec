using System.Text;
using Dapr;
using Dapr.Jobs;
using Dapr.Jobs.Models;
using Dapr.Jobs.Models.Responses;
using Grpc.Core;

namespace Dws.ScheduleTime;

/// <summary>Production <see cref="IJobRegistry"/> over the Dapr .NET Jobs client; needs a sidecar and has no unit test.</summary>
public sealed class DaprJobRegistry(DaprJobsClient client) : IJobRegistry
{
    public async Task<RegisteredJob?> GetAsync(string name, CancellationToken cancellationToken)
    {
        try
        {
            DaprJobDetails details = await client.GetJobAsync(name, cancellationToken);
            return ToRegistered(name, details);
        }
        catch (Exception exception) when (HasStatus(exception, StatusCode.NotFound))
        {
            return null;
        }
    }

    public async Task<IReadOnlyList<RegisteredJob>> ListAsync(CancellationToken cancellationToken)
    {
        IReadOnlyList<DaprJobDetails> jobs = await client.ListJobsAsync(cancellationToken);
        return jobs
            .Where(details => !string.IsNullOrEmpty(details.Name))
            .Select(details => ToRegistered(details.Name ?? string.Empty, details))
            .ToList();
    }

    public async Task<bool> ScheduleAsync(JobRequest job, bool overwrite, CancellationToken cancellationToken)
    {
        DaprJobSchedule schedule = job.DueTime is { } due
            ? DaprJobSchedule.FromDateTime(due)
            : DaprJobSchedule.FromExpression(job.Expression ?? throw new ArgumentException("a job needs an expression or a due time", nameof(job)));
        JobFailurePolicyConstantOptions policy = new(JobSpecs.FailureRetryInterval) { MaxRetries = JobSpecs.FailureMaxRetries };
        try
        {
            await client.ScheduleJobAsync(
                job.Name,
                schedule,
                Encoding.UTF8.GetBytes(job.Payload),
                startingFrom: null,
                repeats: null,
                ttl: null,
                overwrite: overwrite,
                failurePolicyOptions: policy,
                cancellationToken: cancellationToken);
            return true;
        }
        catch (Exception exception) when (!overwrite && HasStatus(exception, StatusCode.AlreadyExists))
        {
            return false;
        }
    }

    public async Task DeleteAsync(string name, CancellationToken cancellationToken)
    {
        try
        {
            await client.DeleteJobAsync(name, cancellationToken);
        }
        catch (Exception exception) when (HasStatus(exception, StatusCode.NotFound))
        {
            // already gone
        }
    }

    private static RegisteredJob ToRegistered(string name, DaprJobDetails details) =>
        new(name, details.Schedule.ExpressionValue, details.Payload is { } payload ? Encoding.UTF8.GetString(payload) : null);

    private static bool HasStatus(Exception exception, StatusCode code) =>
        exception switch
        {
            RpcException rpc => rpc.StatusCode == code,
            DaprException { InnerException: RpcException inner } => inner.StatusCode == code,
            _ => false,
        };
}
