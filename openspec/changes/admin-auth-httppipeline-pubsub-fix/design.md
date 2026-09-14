# Design

Full rationale lives in `docs/roadmaps/dws-auth.md` "Decision (2026-09-13)". This file records
the cross-component contract detail an implementer needs.

## The asymmetry, and why it is not an oversight

Dapr's `middleware.http.bearer` can be wired into a `Configuration` in one of two pipelines:

| Pipeline | What it gates |
|---|---|
| `spec.appHttpPipeline` | **daprd → app** direction: every inbound sidecar→app call, including Dapr's own internal `GET /dapr/subscribe` discovery and pub/sub delivery. |
| `spec.httpPipeline` | The sidecar's **own Dapr HTTP API** (`/v1.0/invoke/...`, state, pub/sub publish, etc.) — the surface external callers (APISIX, the browser via the gateway, and an app's own outbound calls) actually hit. |

- **`dws-admin` moves to `httpPipeline`.** Browser reads/writes/SSE arrive as Dapr service
  invocations against the admin sidecar's HTTP API — that leg is on `httpPipeline`, so it stays
  gated. Meanwhile daprd's internal `/dapr/subscribe` + `dws.events` delivery travel the app
  channel (`appHttpPipeline`), which is now un-gated — so the subscription registers and events
  flow. This is the fix.
- **`dws-controller` stays on `appHttpPipeline`.** `dws-admin` reaches the controller by Dapr
  **service invocation**, which arrives at the controller sidecar over internal gRPC and **never
  traverses `httpPipeline`**. Dapr's docs put the receiving side's middleware on
  `appHttpPipeline` for exactly this case. Moving the controller handler to `httpPipeline` would
  render clean, lint green, and silently delete the Phase 2 gate — only a no-token invocation
  would ever reveal it. So it does not move.

Because the two `auth-configuration.yaml` files now diverge on purpose, their header comments are
rewritten to state why, and a `helm template` test pins each placement so a future "make them
match again" refactor fails CI instead of reopening the hole.

## Controller event publishing — checked, not assumed

The roadmap decision flagged one risk: if `dws-controller` published events over HTTP, moving its
handler would 401 that background path. It does **not** apply here for two reasons: (1) the
controller handler is not moving, and (2) `events/DaprClientProducer.java` builds
`new DaprClientBuilder().build()`, whose Java-SDK default transport is gRPC — HTTP middleware does
not gate gRPC regardless of pipeline. Verified by reading the producer, not inferred from the SDK
default alone.

## The relay's forwarded token becomes load-bearing

`dws-admin/src/controller-relay/controller-relay.service.ts` already forwards the incoming
`Authorization` header verbatim on its outbound `/v1.0/invoke/<controller>/method/workflows`
call. Before this change that token only had to satisfy the controller's `appHttpPipeline` gate.
After this change the admin sidecar's **own** `httpPipeline` also bearer-checks that outbound
invoke leg, so the same forwarded token now clears two gates. It is therefore load-bearing and
must not be dropped — the relay is left exactly as-is.

## Accepted risk and its owner

Once the admin gate leaves the app channel, `POST /dapr/events/dws` on `dws-admin`'s app port is
reachable unauthenticated by any pod-network peer — an event-injection path into the read model,
same class as the §4 pod-IP:8080 controller bypass. This is accepted for this change and closed
by roadmap **Phase 10** (Dapr API token + App API token), not here.

## Operational note

daprd reads its `Configuration` only at startup. A bare `helm upgrade` therefore leaves a
half-state (new manifest, old in-memory pipeline). The admin and controller pods must be restarted
after upgrade for the placement change to take effect — this is called out in the verification
steps and the proposal's Impact section.
