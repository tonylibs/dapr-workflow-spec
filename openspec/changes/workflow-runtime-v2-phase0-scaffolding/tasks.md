# Tasks: workflow-runtime-v2-phase0-scaffolding

Maps to Implementation Roadmap Phase 0 (`docs/roadmaps/workflow-runtime-architecture-roadmap.md`).
ADR 0001 (the design-decision half of Phase 0) is already written. Do the schema first (1), since
both runtimes' loaders validate against it, then the two runtimes in either order (2, 3).

## 1. Single-node definition JSON Schema (single-node-definition-contract)

- [x] 1.1 Write `openspec/schemas/single-node-definition.schema.json`: a `kind`-discriminated
  union (`oneOf` on `kind: "flow"` / `kind: "step"`) per `design.md` D1, with the common envelope
  (`workflow`, `version`, `nodeId`, `kind`) as shared `required` fields, `nodeId` constrained to a
  DNS-1123 label pattern.
- [x] 1.2 Add `flow` branch: `required: [scope, tasks, children]`, `scope` enum
  (`main`/`for`/`try`/`catch`/`forkBranch`), `catch` optional string.
- [x] 1.3 Add `step` branch: `required: [task]`, `functionAppId` optional string (schema can't
  express "required iff task.call or task.run is set" — document that conditional rule in the
  schema's `description` and enforce it in each runtime's loader instead, per D1.2).
- [x] 1.4 Add two or three hand-written sample definition files under
  `openspec/schemas/examples/` (one `flow` example, one `step` example for `call: http`, one
  `step` example for `set`) for both the schema's own validation tests and as the fixture files
  `dws-flow`/`dws-step`'s local `dapr run` READMEs point at.
- [x] 1.5 Validate the schema itself and all three examples with a JSON Schema validator (e.g.
  `ajv-cli` via `npx`, since Node is the only scripting runtime confirmed available in this
  environment) — confirm well-formed examples pass and a deliberately broken one (missing
  `task` in a step example) fails.

## 2. dws-step scaffold (Java/Spring, Dapr Workflow SDK)

- [x] 2.1 Create `dws-step/` mirroring `dws-orchestrator`'s Maven layout: `pom.xml` (Java 25,
  Spring Boot parent matching `dws-orchestrator`'s version, `io.dapr:dapr-sdk` +
  `io.dapr:dapr-sdk-workflows` dependencies), `mvnw`/`mvnw.cmd`/`.mvn/` wrapper (copy from
  `dws-orchestrator`, don't hand-write), `src/main/java/...`, `src/main/resources/application.yml`.
- [x] 2.2 Add a definition loader (`SingleNodeDefinitionLoader` or similar): reads the file path
  from an env var (e.g. `DWS_STEP_DEFINITION_PATH`), parses JSON, validates the `step`-shape rules
  from D1.2/spec section, and fails startup (throws during a `@PostConstruct`/`ApplicationRunner`,
  or equivalent fail-fast Spring pattern) on any violation — see
  `specs/dws-step-scaffold/spec.md`'s "Startup fails fast" requirement for exact conditions.
- [x] 2.3 Register one Dapr Workflow Activity named `Step` (constant name, per design D3) whose
  body logs the loaded task's kind and returns immediately — no real dispatch logic yet.
- [x] 2.4 Add `GET /healthz` (a plain `@RestController`, not Spring Boot Actuator's default path)
  reporting healthy once the definition loader has completed successfully at boot.
- [x] 2.5 Add a `Dockerfile` matching `dws-orchestrator`'s existing multi-stage Maven build shape.
- [x] 2.6 Write `dws-step/README.md`: local dev section with the `dapr run` invocation (app-id,
  `--app-port`, Dapr HTTP/gRPC ports) against one of task 1.4's sample files, mirroring
  `dws-orchestrator/README.md`'s existing local-dev section.
- [x] 2.7 Add unit tests for the definition loader (valid flow-shape file rejected; valid step file
  with `call` accepted with `functionAppId` required; valid step file with `set` accepted without
  `functionAppId`; malformed JSON rejected; missing file rejected).
- [x] 2.8 Validation: `cd dws-step && ./mvnw verify` — passed locally using the Maven wrapper and
  its Java 25 runtime; all five loader tests pass.

## 3. dws-flow scaffold (.NET, Dapr Workflow SDK)

- [x] 3.1 Create `dws-flow/` as a new .NET project (`dws-flow.csproj`, worker-service style
  `Program.cs`), with `Dapr.Workflow` and `Dapr.Client` package references.
- [x] 3.2 Add a definition loader mirroring 2.2's shape and validation rules, for the `flow`
  branch of the contract (`DWS_FLOW_DEFINITION_PATH` env var; fail-fast on missing file, invalid
  JSON, wrong `kind`, or missing `scope`/`tasks`/`children`).
- [x] 3.3 Register one Dapr Workflow type named `Flow` (constant name, per design D2) whose body
  logs the loaded scope and task count and returns immediately.
- [x] 3.4 Add `GET /healthz` reporting healthy once the definition loader has completed
  successfully at boot.
- [x] 3.5 Add a `Dockerfile` (multi-stage `dotnet publish` build), following the shape of the
  existing Go/Node components' Dockerfiles as the closest available multi-stage-build reference in
  this repo (no existing .NET Dockerfile to copy from — this is the repo's first .NET component).
- [x] 3.6 Write `dws-flow/README.md`: local dev section with the `dapr run` invocation against one
  of task 1.4's sample files, same shape as 2.6.
- [x] 3.7 Add unit tests for the definition loader (same coverage as 2.7, for the `flow` shape).
- [x] 3.8 Validation: the host has no .NET SDK, but the .NET 10 SDK container successfully builds
  the production image and runs all five `dws-flow.Tests` loader tests.

## 4. Follow-up (not part of this change)

- [ ] 4.1 Path-filtered CI workflows for `dws-flow`/`dws-step` (`.github/workflows/dws-flow.yml`,
  `dws-step.yml`) — deferred per the proposal's Impact section. This is the first point either
  scaffold gets a real, environment-independent build gate; strongly recommended as the immediate
  next task after this change merges, precisely because local verification was incomplete here.
