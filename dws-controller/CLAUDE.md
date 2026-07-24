# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

The DWS controller. Accepts Serverless Workflow DSL 1.0 definitions, compiles them, and deploys
one stack per definition: an immutable definition ConfigMap + Dapr configuration Component, one
Knative Service per I/O task (from prebuilt images), and a dedicated orchestrator Deployment.
Package root is `io.dws.controller`. The sibling projects in the parent directory
(`dws-orchestrator`, `dws-call-http`, `dws-call-openapi`) build the prebuilt images this
controller deploys; they are separate Maven projects, not modules of this one.

There is **no persistence layer**. Every GET answers from live cluster state selected by the
`dws.io/*` labels — the cluster is the source of truth.

## Commands

Windows: use `mvnw.cmd`; POSIX shells (Git Bash): use `./mvnw`.

```shell
# Dev mode (live coding, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run unit tests only
./mvnw test

# Run a single test class
./mvnw test -Dtest=WorkflowCompilerTest

# Run a single test method
./mvnw test -Dtest=WorkflowCompilerTest#versionIsStable

# Package (produces target/quarkus-app/quarkus-run.jar)
./mvnw package

# Package + run integration tests (QuarkusIntegrationTest, packaged mode)
./mvnw verify

# Über-jar
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Native executable (requires GraalVM)
./mvnw package -Dnative

# Native executable via container build (no local GraalVM needed)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

`skipITs` is `true` by default in `pom.xml` — integration tests (`*IT.java`, e.g. `GreetingResourceIT`) only run under `./mvnw verify`, and only actually execute against a native binary when the `native` Maven profile is active (`-Dnative`).

## Architecture

### Package layout (`io.dws.controller`)

| Package | Role |
|---------|------|
| `api` | JAX-RS resource (`/workflows`) + exception mappers |
| `compile` | **Pure** compile pass — no Kubernetes calls. `WorkflowCompiler` parses via `WorkflowReader` and walks `document`+`do` into a `DeploymentPlan`. `SpecDigest` computes the content-addressed version; `Names` holds the naming rules. |
| `k8s` | Apply pass. `StackSynthesizer` renders the plan (cdk8s for Knative/Dapr, fabric8 models for ConfigMap/Deployment); `StackApplier` mutates + rolls out + GCs; `StackReader` answers all GETs from the cluster. |
| `model` | Records: `DeploymentPlan`, `StepService`, `OrchestratorSpec`, status projections. |
| `config` | `DwsConfig` `@ConfigMapping` over `dws.*` in `application.yaml`. |

### Key invariants

- **Version = `<workflow>@v<sha256-8>`** of the *canonicalized* spec (parsed, key-sorted, re-serialized
  as JSON). Same content → same version → idempotent no-op. The ConfigMap stores the text *verbatim*;
  only the hash uses the canonical form.
- **Definition ConfigMaps are immutable**, so they are created only when absent. Everything else is
  applied with `createOr(NonDeletingOperation::update)`.
- **Knative Service names are NOT version-suffixed** — the name is the Dapr `app-id` the orchestrator
  invokes, so it must be stable. A new version *updates* the same object and re-labels it to the new
  version; that is what makes label-scoped GC of the old version safe. Steps dropped in the new version
  keep the old label and get collected.
- **Task mapping**: `call http`/`call openapi`/`run` → a `StepService`; `emit`/`listen` → a topic binding
  only; `switch`/`set`/`wait`/`for`/`try`/`raise` deploy nothing.
- **Rollout**: the controller only annotates the superseded orchestrator `dws.io/drain=true`. Draining is
  the orchestrator's concern; the controller collects that version once its Deployment reports
  `status.replicas == 0` (a null status means "not reported yet", never "drained").

### Framework

- **Framework**: Quarkus 3.37.4, Java 25 (`maven.compiler.release=25`), packaging type `quarkus`.
- **Serverless Workflow SDK**: `io.serverlessworkflow:serverlessworkflow-api` 7.26.0.Final. Note the model
  shape: `call http`/`call openapi` are *not* direct on `Task` — they hang off `Task.getCallTask()`
  (`CallTask` is a oneOf of `CallHTTP`/`CallOpenAPI`/`CallGRPC`/...).
- **cdk8s needs Node.js at runtime**: jsii executes its bundled JavaScript in a Node child process, so
  synthesis fails without `node` on PATH. `src/main/docker/Dockerfile.jvm` installs it for that reason —
  don't drop that layer.
- **Web layer**: `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS style resources, e.g. `@Path`/`@GET` in `GreetingResource.java`; Jackson for JSON).
- **DI**: `quarkus-arc` (CDI-based dependency injection) — use constructor injection per this repo's Java coding conventions.
- **Kubernetes**: `quarkus-kubernetes-client` is on the classpath — expect this project to interact with a Kubernetes API (likely in service of Dapr workflow orchestration, per the repo name) rather than being a plain REST CRUD app.
- **Testing**: `quarkus-junit` (JUnit 5 + `@QuarkusTest`), AssertJ, `rest-assured` for HTTP-level assertions, and `quarkus-test-kubernetes-client` (`@WithKubernetesTestServer`, CRUD mock) for the apply pass. `WorkflowCompilerTest` is a plain JUnit test — the compiler is deliberately framework-free. Note rest-assured cannot encode `application/yaml` out of the box; `WorkflowResourceTest` registers an `EncoderConfig` for it.
- **Native profile**: activated via `-Dnative`, flips `quarkus.native.enabled=true`, disables the jar build, and enables `skipITs=false` so failsafe actually runs.
- **cdk8s**: `org.cdk8s:cdk8s` (Maven) is used to define/synthesize Kubernetes manifests from Java code, not just talk to the API via `quarkus-kubernetes-client`.
- **Knative Serving constructs**: generated (not hand-written) under `src/main/java/imports/dev/knative/...` via `cdk8s import`, from the CRD bundle pinned at `k8s/serving-crds.yaml` (Knative `knative-v1.22.1`). Covers `serving.knative.dev` (`Service`, `Route`, `Configuration`, `Revision`, `DomainMapping`) plus the internal `networking`/`autoscaling`/`caching` CRDs Knative Serving depends on. Companion jsii metadata lives in `src/main/resources/imports/...` — required at runtime by cdk8s/jsii, don't delete it.
- **Dapr constructs**: generated under `src/main/java/imports/io/dapr/...` via `cdk8s import`, from the CRD bundle pinned at `k8s/dapr-crds.yaml` (Dapr `v1.18.2`, concatenation of `charts/dapr/crds/*.yaml` from the dapr/dapr repo). Covers `dapr.io` — `Component`, `Configuration`, `HttpEndpoint`, `McpServer`, `Resiliency`, `Subscription` (plus `SubscriptionV2Alpha1` for the `v2alpha1` version), `WorkflowAccessPolicy`. Same jsii-metadata caveat as Knative applies (`src/main/resources/imports/io/dapr/...`). Note `io.dapr.Configuration` and `dev.knative.serving.Configuration` are distinct generated classes in different packages — import the right one.
  - Regenerate after bumping either CRD bundle: `npx cdk8s-cli import` (config in `cdk8s.yaml`, lists both `k8s/serving-crds.yaml` and `k8s/dapr-crds.yaml`; requires Node/npm on PATH — no other project files depend on Node).
  - Treat `src/main/java/imports/` and `src/main/resources/imports/` as generated code: don't hand-edit, re-run import instead.

## Known issues

- `pom.xml` sets `maven.compiler.release=25`; the environment's default JDK may be older (e.g. GraalVM 21), which fails `./mvnw compile` with "release version 25 not supported". This is a pre-existing project/environment mismatch, not something recent changes caused — resolve by pointing `JAVA_HOME` at a JDK 25, or ask before lowering the pom's release version (it's a project-wide decision).
