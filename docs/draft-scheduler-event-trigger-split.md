# DRAFT: split Phase 6 triggers into separate `dws-*` components

Status: **draft idea, nothing decided.** Written 2026-11-04. Nothing here is in the Phase 6 prompt yet.
Related: Phase 6 page on Notion (current prompt covers a controller-only design), `claude/handoff-6-scheduling-should-add.md` (project doc).

## 1. Idea

Move scheduling out of `dws-controller` into its own deployable components. Each one ends the same way: it
starts a new workflow instance through `workflowClient`. The controller keeps compiling and deploying, and no
longer has to fire anything.

## 2. How OWS groups the triggers

The spec puts all four under `schedule`. The word "listener" in the spec means the `listen` **task**, which is
not a trigger.

| OWS | Kind | Fires when | Component |
|---|---|---|---|
| `schedule.every` | Time | Fixed interval; may overlap a still-running run | Scheduler |
| `schedule.cron` | Time | Cron match (spec example is 5-field: `0 0 * * *`) | Scheduler |
| `schedule.after` | Time + completion | Delay after the previous run completes | Scheduler, plus a completion signal |
| `schedule.on` | Event | Events match the consumption strategy (`one` / `any` / `all`, `until`, `correlate`) | Event trigger |
| `listen` task | Not a trigger | Inside a running instance | Stays in the workflow runtime (Flow controller, ADR 0006) |

Spec facts that shape the design:
- `schedule.on` input is an **array of the triggering events**, read as `$workflow.input[0]`. `read` is `data`
  (default), `envelope` or `raw`, and is valid only with `on`.
- `schedule.on` only decides *when an instance is created*. Its faults and timeouts are invisible to the instance.
- At least one of `every`, `cron`, `after`, `on` must be set.
- A duration is an object (days to milliseconds), an ISO-8601 string, or a runtime expression that yields one.

## 3. Proposed shape

```
controller ──deploys──► definition + trigger config   (cluster = source of truth)
                                   │ watched by
              ┌────────────────────┴───────────────────┐
        dws-scheduler                            dws-event-trigger
    every / cron / after                         on: one / any / all
        Dapr Jobs API                       pub/sub + matching + state
              │                                        │
              └────► start(workflow, input, instanceId) ◄────┘
                                  │ workflowClient
                                  ▼
              workflow's orchestrator (v1)  /  dws-flow main (v2)
```

## 4. Suggestion

| Point | Suggestion |
|---|---|
| Components | Two: `dws-scheduler` (time) and `dws-event-trigger` (`schedule.on`). Separate failure and scaling, and they use different Dapr mechanisms. |
| Name | Do not use `dws-listener`: it collides with the `listen` task. |
| Shared contract | One start contract for both: workflow, input, deterministic instance ID. Defined in 6a. |
| Phases | **6a** `dws-scheduler` (`every`, `cron`, `after`) + start contract + cross-app start spike + ADR. **6b** `dws-event-trigger` (`on`), built on 6a's contract. |
| Mechanism | Dapr Jobs API for the scheduler. A cron binding fires in every replica, so a replicated component would start duplicates. |
| Registering | The scheduler watches the cluster for deployed definitions and registers its own jobs. This also gives rebuild after restart, version takeover and cleanup on delete. |
| `after` | The scheduler polls instance status through `workflowClient`. It must not depend on lifecycle events (Core does not publish them when the Events layer is off). |
| Event state | Keep state for `all`, `any` + `until` and `correlate` in a Dapr state store. Core already requires one (ADR 0007). Ship `one` and plain `any` first if this is too much for 6b. |
| Layer | Core. Two new Deployments in the chart, per ADR 0007 ("triggers are Core"). |

## 5. Things that need a decision

| # | Decision | Options | My lean | Status |
|---|---|---|---|---|
| D1 | **How a trigger component starts an instance of another app's workflow** | (a) `workflowClient` straight to the target app, if Dapr 1.18 allows it, perhaps governed by `WorkflowAccessPolicy`; (b) call the orchestrator's existing start endpoint (`POST /workflows/{name}/instances`) through Dapr service invocation | Spike first; fall back to (b) | **Open, biggest risk** |
| D2 | Jobs API or cron binding | Jobs API / cron binding | Jobs API | Open (ADR in 6a) |
| D3 | Who registers jobs | Scheduler watches the cluster / controller pushes to the scheduler | Scheduler watches. The Jobs callback goes to the app that created the job, so the scheduler must be the creator. | Open, needs Dapr check |
| D4 | `after` completion signal | Poll status / orchestrator reports completion / lifecycle events | Poll status | Open |
| D5 | State for `all`, `until`, `correlate` | Dapr state store / an aggregator workflow that waits for external events / defer and ship `one` and plain `any` | Defer first, state store later | Open |
| D6 | One or two components | Two (`dws-scheduler`, `dws-event-trigger`) / one `dws-trigger` with two modes | Two | Open |
| D7 | Language and stack | Java (same stack as the orchestrator, can reuse expression code) / .NET (like `dws-flow`) / Go | Java | Open; ties to the Flow expression spike (2.0c) because `with` filters allow regular expressions and runtime expressions |
| D8 | Target runtime | v1 orchestrator only / v1 and v2 | v1 first, keep the contract runtime-neutral | Open |
| D9 | Instance ID format for duplicate-start safety | Per fire (schedule time) and per event set (hash of event IDs) | Deterministic, one format shared by both components | Open |
| D10 | Event matching scope | Which pub/sub topics are subscribed; how `emit` publishes (v1 maps `emit`/`listen` to topics only, with no event-type matching). An empty `any` matches every incoming event. | Scope to the subscribed topics | Open |
| D11 | Auth on | The bearer middleware conflicts with `/dapr/subscribe` (see `dws-auth.md`). Job callbacks and subscriptions must be proven live with auth on. | Live check required | Open |
| D12 | Timing rules | First fire time, missed fires during downtime, overlap for `every`, cron timezone, minimum interval, a runtime-expression duration with no instance to evaluate it against | Decide in the ADR | Open (already in the prompt) |
| D13 | Failure behaviour | A fire while the target is not ready: retry for how long, or drop | Decide in the ADR | Open |
| D14 | Packaging work | CI, release-please component, image keys, chart values and RBAC for two new components (like the `dws-step` / `dws-flow` CI handoff) | Own handoff before or with 6a | Open |

## 6. What changes in the current Phase 6 prompt

- The "controller-only" framing goes away. The controller deploys the definition; the trigger components fire.
- Split into two prompts: 6a (time triggers, contract, spike, ADR) and 6b (event trigger).
- Moves into 6a: `every` / `cron` / `after` behaviour, takeover, duplicate safety, rebuild from the cluster,
  validation at submit, the cross-app start spike.
- Moves into 6b: `on`, `read`, `until`, `correlate`, the input array, topic scoping.
- Validation at submit stays in the controller (it rejects bad `schedule` blocks and `read` without `on`).
- The new-component packaging (D14) probably needs its own handoff, as `dws-step` / `dws-flow` did.

## 7. Not verified

- Whether Dapr 1.18 lets an app start a workflow hosted by another app ID (D1).
- Jobs API behaviour: callback goes to the creating app, delivery guarantees, cron field format (Dapr's may differ
  from the spec's 5-field form).
- Whether the event trigger's `with` matching can reuse any existing expression code.
