# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Fresh Quarkus scaffold (`quarkus create` default output). Only code present: `GreetingResource` (`GET /hello`) and its tests. Package root is `org.tony`. No Dapr workflow code exists yet despite the repo name — treat this as a greenfield Quarkus + Kubernetes-client project.

## Commands

Windows: use `mvnw.cmd`; POSIX shells (Git Bash): use `./mvnw`.

```shell
# Dev mode (live coding, Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Run unit tests only
./mvnw test

# Run a single test class
./mvnw test -Dtest=GreetingResourceTest

# Run a single test method
./mvnw test -Dtest=GreetingResourceTest#testHelloEndpoint

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

- **Framework**: Quarkus 3.37.4, Java 25 (`maven.compiler.release=25`), packaging type `quarkus`.
- **Web layer**: `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS style resources, e.g. `@Path`/`@GET` in `GreetingResource.java`; Jackson for JSON).
- **DI**: `quarkus-arc` (CDI-based dependency injection) — use constructor injection per this repo's Java coding conventions.
- **Kubernetes**: `quarkus-kubernetes-client` is on the classpath — expect this project to interact with a Kubernetes API (likely in service of Dapr workflow orchestration, per the repo name) rather than being a plain REST CRUD app.
- **Testing**: `quarkus-junit` (JUnit 5 + `@QuarkusTest`) and `rest-assured` for HTTP-level assertions. Integration tests extend the corresponding `@QuarkusTest` class and are re-annotated `@QuarkusIntegrationTest` to run the same assertions against the packaged/native artifact (see `GreetingResourceIT` extending `GreetingResourceTest`).
- **Native profile**: activated via `-Dnative`, flips `quarkus.native.enabled=true`, disables the jar build, and enables `skipITs=false` so failsafe actually runs.
- **cdk8s**: `org.cdk8s:cdk8s` (Maven) is used to define/synthesize Kubernetes manifests from Java code, not just talk to the API via `quarkus-kubernetes-client`.
- **Knative Serving constructs**: generated (not hand-written) under `src/main/java/imports/dev/knative/...` via `cdk8s import`, from the CRD bundle pinned at `k8s/serving-crds.yaml` (Knative `knative-v1.22.1`). Covers `serving.knative.dev` (`Service`, `Route`, `Configuration`, `Revision`, `DomainMapping`) plus the internal `networking`/`autoscaling`/`caching` CRDs Knative Serving depends on. Companion jsii metadata lives in `src/main/resources/imports/...` — required at runtime by cdk8s/jsii, don't delete it.
- **Dapr constructs**: generated under `src/main/java/imports/io/dapr/...` via `cdk8s import`, from the CRD bundle pinned at `k8s/dapr-crds.yaml` (Dapr `v1.18.2`, concatenation of `charts/dapr/crds/*.yaml` from the dapr/dapr repo). Covers `dapr.io` — `Component`, `Configuration`, `HttpEndpoint`, `McpServer`, `Resiliency`, `Subscription` (plus `SubscriptionV2Alpha1` for the `v2alpha1` version), `WorkflowAccessPolicy`. Same jsii-metadata caveat as Knative applies (`src/main/resources/imports/io/dapr/...`). Note `io.dapr.Configuration` and `dev.knative.serving.Configuration` are distinct generated classes in different packages — import the right one.
  - Regenerate after bumping either CRD bundle: `npx cdk8s-cli import` (config in `cdk8s.yaml`, lists both `k8s/serving-crds.yaml` and `k8s/dapr-crds.yaml`; requires Node/npm on PATH — no other project files depend on Node).
  - Treat `src/main/java/imports/` and `src/main/resources/imports/` as generated code: don't hand-edit, re-run import instead.

## Known issues

- `pom.xml` sets `maven.compiler.release=25`; the environment's default JDK may be older (e.g. GraalVM 21), which fails `./mvnw compile` with "release version 25 not supported". This is a pre-existing project/environment mismatch, not something recent changes caused — resolve by pointing `JAVA_HOME` at a JDK 25, or ask before lowering the pom's release version (it's a project-wide decision).
