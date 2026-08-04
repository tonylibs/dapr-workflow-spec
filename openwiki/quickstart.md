---
type: Repository Guide
title: DWS OpenWiki quickstart
description: Entry point for the DWS config-driven Kubernetes workflow platform, its deployed runtime, and lifecycle-event integration.
tags: [dws, dapr, kubernetes, workflows]
---

# DWS OpenWiki quickstart

DWS (`dapr-workflow-spec`) is a config-driven workflow platform for Kubernetes. Clients submit Open Workflow Specification DSL 1.0 YAML or JSON to a controller; the controller deploys Dapr-backed resources and a generic orchestrator interprets the definition at runtime. No per-workflow application code is generated.

## Start here

- [Deployed workflow architecture](architecture/deployed-workflow.md) explains how a definition becomes an immutable, versioned deployment and how the orchestrator executes its tasks.
- [Lifecycle events](integrations/lifecycle-events.md) explains the shared Dapr pub/sub stream that observes controller deployment and orchestrator execution lifecycles.
- [Administrative read model](integrations/admin-read-model.md) explains how `dws-admin` turns that stream into a durable Postgres query view and read-only APIs.
- [OWS DSL feature roadmap](architecture/roadmap.md) tracks DSL 1.0 task-type and cross-cutting feature coverage against the current implementation, phased into build order.
- [Cluster-hosted agent sandbox](architecture/agent-sandbox.md) explains the CI-validated development image and Kubernetes session templates for persistent agent work.

The repository has four independently built components; run builds and tests from each component directory rather than from the repository root:

| Component | Responsibility | Primary verification |
|---|---|---|
| `dws-controller` | Quarkus API that validates/compiles definitions and applies Kubernetes resources | `./mvnw test`; `./mvnw verify` |
| `dws-orchestrator` | Spring Boot Dapr Workflow interpreter for one pinned definition per pod | `./mvnw verify` |
| `dws-call-http` | Go step image for `call: http` tasks | `make test` |
| `dws-call-openapi` | TypeScript/Fastify step image for `call: openapi` tasks | `pnpm lint && pnpm test && pnpm build` |
| `dws-run` | Go step images for `run: shell` and inline JavaScript/Python `run: script` tasks | `make lint && make test` |
| `dws-admin` | NestJS/Postgres projection and query API for lifecycle events | `pnpm db:migrate && pnpm lint && pnpm test && pnpm build` |

## Change guide

- For definition-to-resource behavior, begin with `dws-controller/src/main/java/io/dws/controller/compile/` and `k8s/`; preserve content-addressed versioning and stable task-derived app IDs described in the [deployed workflow](architecture/deployed-workflow.md).
- For interpreter semantics, begin with `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`; task execution also produces the [orchestrator lifecycle events](integrations/lifecycle-events.md#orchestrator-events).
- For cross-component telemetry, treat `docs/events.md` as the source contract; changes also affect the [administrative read model](integrations/admin-read-model.md) that consumes this stream.

The GitHub Actions workflow at `.github/workflows/openwiki-update.yml` refreshes this generated documentation on every merge to `main` (or on manual dispatch).

## Backlog

- **Step-runner internals** — `dws-call-http/`, `dws-call-openapi/`, and `dws-run/`: the platform-level task-to-step-service contract is documented, but individual runner request/response and configuration details remain deferred until a dedicated runtime page is needed.
- **Administrative console** — `dws-console/`: the committed TanStack Start application is an unconnected starter scaffold; document it when it acquires DWS-facing routes or calls the `dws-admin` read API.
