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

The repository has four independently built components; run builds and tests from each component directory rather than from the repository root:

| Component | Responsibility | Primary verification |
|---|---|---|
| `dws-controller` | Quarkus API that validates/compiles definitions and applies Kubernetes resources | `./mvnw test`; `./mvnw verify` |
| `dws-orchestrator` | Spring Boot Dapr Workflow interpreter for one pinned definition per pod | `./mvnw verify` |
| `dws-call-http` | Go step image for `call: http` tasks | `make test` |
| `dws-call-openapi` | TypeScript/Fastify step image for `call: openapi` tasks | `pnpm lint && pnpm test && pnpm build` |

## Change guide

- For definition-to-resource behavior, begin with `dws-controller/src/main/java/io/dws/controller/compile/` and `k8s/`; preserve content-addressed versioning and stable task-derived app IDs described in the [deployed workflow](architecture/deployed-workflow.md).
- For interpreter semantics, begin with `dws-orchestrator/src/main/java/io/dws/orchestrator/workflow/InterpreterWorkflow.java`; task execution also produces the [orchestrator lifecycle events](integrations/lifecycle-events.md#orchestrator-events).
- For cross-component telemetry, treat `docs/events.md` as the source contract and verify controller plus orchestrator tests when changing event types or payloads.

The scheduled GitHub Actions workflow at `.github/workflows/openwiki-update.yml` refreshes this generated documentation.

## Backlog

- **Step-runner internals** — `dws-call-http/` and `dws-call-openapi/`: deferred because the current cross-component change is lifecycle-event publishing; document their HTTP contract when their implementation changes or a dedicated runtime page is needed.
