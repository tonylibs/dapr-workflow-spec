---
type: Administrative Console
title: DWS administrative console
description: DWS Console is a TanStack Start user-interface mockup for browsing workflow catalog and execution views using static data shaped after the dws-admin read API.
tags: [dws, console, tanstack, admin, user-interface]
---

# DWS administrative console

`dws-console` is the in-repository administrative-console mockup. It presents workflow and instance views that are intended to surface the durable query projection described in the [administrative read model](admin-read-model.md). It currently uses static in-process data rather than a network client, so it neither queries nor controls a DWS cluster.

## Current surface

The file-based TanStack routes redirect `/` to `/workflows` and provide:

- a workflow catalog with a name filter, workflow/version status, empty/loading/error presentations, and a workflow-detail route;
- a workflow detail view with version history, deployment cards, and a definition graph;
- an instance list with workflow and status filters; and
- an instance detail view with execution metadata, task status, and expandable attempt/backoff history.

Those views make the deployment and execution facts from [deployed workflow architecture](../architecture/deployed-workflow.md) navigable for an operator. `src/components/app-layout.tsx` supplies the shared catalog/system navigation; the `Controller` item is only a non-linking status label in the current mockup.

## Data and integration boundary

`src/lib/mock-data.ts` defines the console's workflow, deployment, instance, task, and attempt shapes. Its source comment states that these are static mock data mirroring the `dws-admin` read API shapes and should be replaced by TanStack Query calls to `GET /workflows`, `GET /instances`, and related endpoints when the console goes live. The actual endpoint set, pagination behavior, and read-only boundary are canonical in the [administrative read model](admin-read-model.md#query-surface-and-runtime-boundary).

The console's simulated filters, status switches, pagination language, copy/download buttons, and log link are presentation-only. They must not be treated as wired requests or operations until an implementation adds a client and endpoint configuration.

## Development and change guide

The app is a private ESM package using TanStack Start/Router/Query, React, Tailwind, and Biome. From `dws-console/`, use `npm run dev` (port 3000) for development; `npm run lint`, `npm run typecheck`, and `npm run build` check the current scripts. `vite.config.ts` configures the TanStack Start, React, Tailwind, and TanStack devtools plugins.

When connecting the console:

- Keep read/query behavior aligned with the [administrative read model](admin-read-model.md), rather than inferring controller state directly.
- Replace the static data behind the relevant routes with explicit query calls and preserve loading, empty, error, not-found, and pagination states.
- Treat any future controller action, authentication, log integration, or API configuration as a separate feature: none is implemented in the inspected console source.
- Regenerate the route tree with `npm run generate-routes` when adding file-based routes.

Primary source anchors: `dws-console/src/routes/`, `dws-console/src/lib/mock-data.ts`, `dws-console/src/components/app-layout.tsx`, `dws-console/package.json`, and `dws-console/vite.config.ts`.
