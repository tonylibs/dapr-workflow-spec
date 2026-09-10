# Files

- [DWS administrative read model](admin-read-model.md) - DWS admin subscribes to lifecycle events and projects workflow definitions, deployments, instances, and task events into an idempotent Postgres query model.
- [DWS lifecycle events](lifecycle-events.md) - Shared Dapr pub/sub contract for advisory controller deployment and orchestrator instance/task lifecycle events.
- [OpenAPI step runner](openapi-step-runner.md) - Runtime and configuration contract for dws-call-openapi, the generic Fastify service that executes a pinned OpenAPI operation for DWS call openapi tasks.
