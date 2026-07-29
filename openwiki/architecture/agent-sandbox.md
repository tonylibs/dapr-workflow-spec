---
type: Development Environment
title: Cluster-hosted agent sandbox
description: Template and CI-validated container image for long-lived DWS development sessions on an existing OpenSandbox Kubernetes control plane.
tags: [dws, development, kubernetes, agent-sandbox, ci]
---

# Cluster-hosted agent sandbox

`agent-sandbox/` provides templates for long-lived Claude or other agent development sessions running as a Kubernetes `Sandbox` resource. It is a contributor environment, not part of the DWS workflow runtime: the controller and orchestrator deployment lifecycle remains documented in [deployed workflow lifecycle](deployed-workflow.md). Unlike `scripts/start-kind-cluster.sh`, which supports ephemeral local `CLAUDE_CODE_REMOTE` test runs, this setup expects an already-provisioned OpenSandbox control plane and persistent build caches.

## Image and session template

The shared image is built from `agent-sandbox/Dockerfile`. It assembles the same toolchain families used by the independently built DWS components: JDK 25 for the controller and orchestrator, Go 1.26 for `dws-call-http` and `dws-run`, and Node 24 with pnpm 11.10.0 for `dws-call-openapi`. It also includes `git`, `gh`, `jq`, `make`, and the C toolchain needed by Go race tests. A Dockerfile smoke test checks the expected tool versions during image build.

`agent-sandbox/sandbox.yaml` is intentionally a skeleton for a single session. It mounts the repository at `/workspace` and connects Maven, Go module, and pnpm-store cache volumes. `agent-sandbox/cache-pvcs.yaml` defines the corresponding PVC templates (5 GiB Maven, 5 GiB Go, and 2 GiB pnpm), but leaves the storage class as a cluster-specific placeholder.

```mermaid
flowchart TD
  Image["agent-sandbox Docker image"] --> Session["Sandbox session pod"]
  Repo["Repository workspace"] --> Session
  Maven["Maven cache PVC"] --> Session
  GoCache["Go module cache PVC"] --> Session
  Pnpm["pnpm store PVC"] --> Session
  Session --> Gates["DWS component build and test gates"]
  CI["Agent sandbox CI workflow"] --> Image
  CI --> Gates
```

This diagram shows the sandbox image, session mounts, and CI validation sharing the same component toolchains.

## CI validation and publishing

The [agent-sandbox CI workflow](../../.github/workflows/agent-sandbox.yml) runs on pull requests and pushes to `main` when sandbox files change, and can also be dispatched manually. It builds the image before running the repository's real component gates inside that image:

- `dws-controller` and `dws-orchestrator`: `./mvnw -B -ntp verify`
- `dws-call-http` and `dws-run`: `make vet && make test`
- `dws-call-openapi`: `pnpm install --frozen-lockfile && pnpm lint && pnpm test && pnpm build`

The workflow validates image builds for pull requests but publishes to `ghcr.io/<repository owner>/dws-agent-sandbox` only for `main` pushes. Thus it depends on the component verification conventions summarized in the [quickstart](../quickstart.md), while proving that the sandbox can execute them rather than merely exposing the executables.

## Cluster prerequisites and boundaries

Before applying the template, confirm the installed `sandboxes.agents.x-k8s.io` CRD version. The checked-in manifest currently uses `agents.x-k8s.io/v1beta1`, while the repository notes that agent-sandbox v0.4.x serves only `v1alpha1` and has no conversion webhook. Also resolve the image registry/tag, namespace and service-account RBAC, storage class, an available gVisor or Kata runtime class if required, and the way the empty workspace is populated (for example, a git-clone init container or a persistent repository volume).

The image deliberately does not include `kubectl`, the Dapr CLI, or in-session image-building tools. Add them only when a live-cluster workflow requires them. Source: `agent-sandbox/README.md`, `agent-sandbox/sandbox.yaml`, and `agent-sandbox/cache-pvcs.yaml`.

## Change and verification guide

- Change the assembled toolchain in `agent-sandbox/Dockerfile` only when a component's supported runtime or package-manager version changes; keep the smoke test aligned.
- Change cache paths only as a coordinated edit across the Dockerfile, `sandbox.yaml`, and `cache-pvcs.yaml`.
- Change the CI contract in `.github/workflows/agent-sandbox.yml` when a component's CI gate changes, then validate the workflow by building the image and running its affected gate in that image.
- Treat CRD version, storage class, runtime class, RBAC, registry, and workspace hydration as deployment-specific configuration; do not represent template TODOs as defaults.
