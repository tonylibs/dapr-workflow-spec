# Scheduling Phase 6a — live evidence

**Status: tasks 9.1–9.5 passed on `docker-desktop` (2026-11-14).** The initial run below
found replay and machine-auth failures. After the follow-up fixes, a rebuilt scheduler passed
9.4 and 9.5; see [the rerun](#follow-up-rerun--94-and-95-passed). Dapr 1.18.7 remains unverified;
the live cluster runs 1.18.2. This evidence does not replace the implementation merge/archive gates.

Change: `scheduling-phase-6a-time-triggers` · Source: `f4ed1bce72bc6b4ed2eb2078a9f4a01e62f1f8c8`
(branch `DuongVu98/scheduler-verification`).
Cluster: `docker-desktop`, three Ready kind nodes, Kubernetes v1.34.3, containerd 2.2.0.
Dapr control plane and injected sidecars: **1.18.2**, externally managed, not the chart's
1.18.7 pin. Helm: 4.3.0. SDK: Dapr .NET 1.18.10, .NET 10.

## Setup and boundaries

Installed the repository chart in namespace `dws-scheduling-6a`, release `dws`, with
`dapr.enabled=false`, `admin.enabled=false`, `postgresql.enabled=false`, the branch-built
images below, and an isolated Redis 7.4 pod. The chart's Redis-backed state Component reports
ETag, transactional and TTL support. No existing platform installation was upgraded.
The scheduler served `/healthz` with HTTP 200; its named test-project gate passed **208/208**.

| Image tag | Image ID |
|---|---|
| `dws-controller:scheduling-6a` | `sha256:9d97c7043a34d37049989ad41135db5fe955950379c731cc17101597de7575cb` |
| `dws-orchestrator:scheduling-6a` | `sha256:7a6eca187f4c9b8a9c4f81cec288cc621b0c577362dee83cee93781a6f8891cc` |
| `dws-schedule-time:scheduling-6a` | `sha256:eba6449d309798599a3bcc77bc89719489a4abe250fffa4fa2fc1199ff16ba19` |

The environment had no Docker or Helm executable initially. Helm was downloaded to `/tmp`;
a temporary Docker-in-Docker builder and registry in the test namespace built the images.
Temporary node-local helpers pulled and tagged them into each node's containerd image store.
Orchestrator and scheduler used their repository Dockerfiles. Controller compilation used a
temporary multistage Dockerfile with JDK 25, Maven 3.9.16 and Node 24, then the repository's UBI
25 runtime recipe. The Maven ZIP matched its pinned SHA-256; the wrapper had incorrectly checked
the tarball against that ZIP checksum when `unzip` was absent. No checksum check was disabled.

**Controller prerequisite:** the unmodified runtime rejected submissions with HTTP 500 because
jsii's package cache security check rejects the UBI home directory's group-writable mode 0770.
The test controller Deployment was given `JSII_RUNTIME_PACKAGE_CACHE=disabled`. Therefore these
results verify scheduling with that workaround; they do not prove the default controller image
can submit workflows. No application source was changed for this run.

The auth run used release `dws-6a-auth` in namespace `dws-scheduling-6a-auth`, the same images,
`auth.enabled=true`, bundled Dex with a cluster-reachable issuer, and the test Redis through its
namespace-qualified Service. Its scheduler has its own app-id and token Secret. Fixture
submissions used the controller app port through port-forwarding; browser/JWT access was not tested.
A unique release name avoided an existing `dws-dex` ClusterRole owned by a different release.

## 9.1 — fires, IDs and completion-relative delay: passed

Deployed three definitions at 18:40:34 UTC: `verify-every` (`every: PT1M`), `verify-cron`
(`cron: "*/2 * * * *"`), and `verify-after` (`after: PT30S`). The first two contain a `set`
task; `verify-after` waits ten seconds before `set`. The output marker uses the jq expression
`'"A"'`, not bare `A`. Earlier disposable `tick-*` fixtures had an invalid marker; those failed
runs were deleted and are not used as passing evidence.

All three immutable trigger ConfigMaps had the expected workflow/version/app-id/kind/schedule
fields. All sampled completed instances had input `{}` and output `{"version":"A","ticked":true}`.
Timestamps below come from Dapr's workflow metadata API, not the local observation clock.

| Instance ID | Created UTC | Completed UTC |
|---|---|---|
| `dws-sched:verify-every:20261114T184134Z` | 18:41:35.136807662 | 18:41:35.569163701 |
| `dws-sched:verify-cron:20261114T184200Z` | 18:42:00.200317786 | 18:42:00.695272704 |
| `dws-after:verify-after:1` | 18:40:52.328009868 | 18:41:02.834090178 |
| `dws-after:verify-after:2` | 18:41:35.388058819 | 18:41:46.308885163 |

The `every` anchor was `2026-11-14T18:40:34.180Z`; its first instance key is anchor + 60 s,
with delivery about one second later. Cron fired at an even UTC minute. The gap between
`after:1` completion and `after:2` creation was **32.554 seconds**, exceeding the requested 30 s.
The immediate activation start initially saw a target-not-ready HTTP 500; the poll recovered it.

## 9.2 — takeover and deletion: passed for the observed fire

At 18:42:39 UTC redeployed `verify-cron` from `v93fb64b1` to `v09baa58f`, changing the cron to
`1-59/2 * * * *` and the output marker to `B`. The old Deployment acquired the **annotation**
`dws.io/drain: "true"`; the Dapr job changed to `0 1-59/2 * * * *`, version `v09baa58f`.

`dws-sched:verify-cron:20261114T184300Z` started at 18:43:00.124048983 and completed at
18:43:00.458128743 with `{"version":"B","ticked":true}`. Both old and new Deployments still
existed when inspected at 18:43:24. The observed next fire therefore executed the new definition.
This single observation does not establish version affinity for every possible rollout: versions
still share a Dapr app-id.

Deleted the workflow through `DELETE /workflows/verify-cron`. At 18:43:36 the SDK Jobs list
contained only the every and after jobs; `dws-sched-verify-cron` was absent. No ConfigMap with
`dws.io/workflow=verify-cron` remained.

## 9.3 — scheduler restart: passed

Restarted the scheduler at approximately 18:41:56. SDK Jobs snapshots taken before and after
compared equal for both recurring jobs, including their payload anchors:

- every: `2026-11-14T18:40:34.180Z`;
- cron: `2026-11-14T18:40:34.322Z`.

The replacement pod logged no registration of unchanged jobs. The next every instance retained
key `20261114T184234Z` (created 18:42:34.214225206). The durable after state progressed to
sequence 3, which started at 18:42:20.734828104 and completed at 18:42:31.012326712; it did not
reset to sequence 1.

## 9.4 — two replicas passed; downtime replay failed

Both scheduler replicas were Ready by 18:43:23. Instance
`dws-sched:verify-every:20261114T184334Z` started at 18:43:35.049768173 and completed at
18:43:35.268771069. Its Redis start claim was `started`, version 2 (claim creation followed by
completion), with one instance recorded for that fire key. The after sequence continued to 5.

Scaled the scheduler to zero at 18:44:01 and waited for both pods to disappear. Restored two
replicas at **18:48:01**, after four missed ticks (18:44:34 through 18:47:34).
The timestamped app logs show one canceled callback (499) and **16 callback responses of 500**
at 18:48:04, all in the pod `dws-schedule-time-86f4679795-hbzsm`. Initial cluster lists completed
at 18:48:05.293481041 and 18:48:05.578830023. No missed-tick instance was created; querying the
expected recovery-slot ID `dws-sched:verify-every:20261114T184734Z` returned 404.

The next ordinary tick, `dws-sched:verify-every:20261114T184834Z`, started at
18:48:35.394292398 and completed at 18:48:35.779869443. This was the only new every claim in the
recovery snapshot (claim version 2), but it was **not** the required replayed-burst instance.
The after sequence resumed at 6. Thus no duplicates were observed, but the exactly-one replay
assertion did not pass and **9.4 remains open**.

`ScheduledJobHandler` returns retry while `cluster.Synced` is false. In this run the entire
replayed retry burst finished before synchronization, despite the configured retry policy.
Before rerunning, address callback admission during initial synchronization (or wait for the
initial snapshot before processing callbacks), and verify the actual Dapr replay/retry timing.
The evidence does not establish that a synchronized replay race is deduplicated correctly.

## 9.5 — machine auth: failed, task remains open

Deployed `auth-every` (`PT1M`), `auth-cron` (`*/2 * * * *`) and `auth-after` (`PT30S`) at
18:44:47–48 UTC in the auth-enabled release. The scheduler's pod annotation named
`dws-6a-auth-schedule-time-dapr-api-token`; `DAPR_API_TOKEN` used a `secretKeyRef` to that
Secret's `token` key. The Secret held a 48-character token (its value was never recorded).
Sidecar logs confirmed HTTP and gRPC token authentication enabled. Jobs registered successfully
and real callbacks reached `/job/*`.

All three workflow starts failed:

```text
First after start failed for workflow auth-after instance dws-after:auth-after:1: HTTP 401
Fire refused for workflow auth-every fire key dws-sched:auth-every:20261114T184547Z: HTTP 401
Fire refused for workflow auth-cron fire key dws-sched:auth-cron:20261114T184600Z: HTTP 401
```

At 18:45:39 an explicit status request through that scheduler's sidecar returned 401 with
`invalid api token` without the header, and reached the orchestrator (404, instance never started)
with the correct header. These deliberate diagnostic requests followed the real failures above.
The source sidecar's default log level did not emit a per-request 401 line; absence of such a log
line is not evidence that the start succeeded.

**Cause:** `Program.cs` creates the invocation client with `DaprClient.CreateInvokeHttpClient()`.
In [Dapr .NET SDK 1.18.10's factory](https://github.com/dapr/dotnet-sdk/blob/v1.18.10/src/Dapr.Client/DaprClient.cs#L76),
the optional `daprApiToken` argument defaults to null and is assigned to the invocation handler.
The live token-free request reproduces the observed failure. Jobs and state calls use different
SDK clients and do receive the configured token.

**Required follow-up:** explicitly supply the token at the `Program.cs` composition root, add a
regression test covering the actual invocation-client token wiring, rebuild the scheduler image,
and repeat all three auth-enabled schedules. Current unit tests pass while this live path fails.
The apply skill requires pausing on blockers; no scheduler source fix was made in this verification run.

## Follow-up fixes (after the initial run)

The three blockers above were fixed on `feat/time-scheduler`. The rerun below verifies the
scheduler fixes with a rebuilt image; it reuses the cached controller image with the same cache
workaround, so it does not verify the controller Dockerfile fix.

| Finding | Fix | Covered offline by |
|---|---|---|
| 9.5: starts get 401, `CreateInvokeHttpClient()` sends no token | `Program.cs` passes `DAPR_API_TOKEN` through `OrchestratorInvokeStarter.CreateInvokeClient` | `SendsTheDaprApiTokenThroughTheRealInvokeClient` (real SDK client, socket-level fake sidecar), `ProgramHandsTheDaprApiTokenToTheInvokeClient` |
| 9.4: replayed ticks and their immediate retries all arrive before the first cluster list and are answered retry | `JobDispatcher` holds each callback until the list completes (up to 30 s) and keys it by its delivery time | `HoldsACallbackThatArrivesBeforeTheFirstList...`, `CollapsesAReplayedBurstHeldDuringTheFirstListOntoOneInstance`, `KeysTheFireByTheDeliveryTimeNotByWhenTheListCompleted` |
| Controller image answers `POST /workflows` with 500 (jsii cache in a 0770 home) | `Dockerfile.jvm` sets `JSII_RUNTIME_PACKAGE_CACHE=disabled` | `RuntimeImageTest`; reproduced with a 0770 `HOME` (34/70 `StackSynthesizerTest` errors without it, none with it) |

The replay fix assumes Dapr's job callback waits at least as long as the first cluster list takes
(1–2 s in this run). The rerun should confirm one instance for the replayed slot and no `499`
(callback cancelled by the sidecar) while the scheduler holds a callback.

## Follow-up rerun — 9.4 and 9.5 passed

Source: `e7d2904d507161dc05b6a135830394ab22a15695`, branch `test/phase-6-verification`.
Cluster: `docker-desktop`, Kubernetes 1.34.3, Dapr 1.18.2. Installed release `dws-rerun` in
isolated namespace `dws-scheduling-rerun`, with the same external-Dapr/Redis setup and cached
controller/orchestrator images as the initial run. The controller again used
`JSII_RUNTIME_PACKAGE_CACHE=disabled`. No application source was changed during verification.

Built the scheduler from this checkout using its repository Dockerfile:
`dws-schedule-time:rerun`, image ID
`sha256:cb08c7c29f4ef013b84c10997c0914815efdded9e81a8076c2a3489a94ba58b3`.
The named test-project gate passed **219/219**, zero failures or skips, in the .NET 10 SDK container.
The chart used `auth.enabled=true`, bundled Dex with cluster-reachable issuer
`http://dws-rerun-dex.dws-scheduling-rerun.svc.cluster.local:5556/dex`, and
`scheduleTime.replicaCount=2`. Workflow submissions used the controller app port via
port-forwarding; JWT/browser access was outside this check.

### 9.5 — all three authenticated schedules passed

Submitted `rerun-every` (`every: PT1M`), `rerun-cron` (`cron: "*/2 * * * *"`) and
`rerun-after` (`after: PT30S`, with a ten-second wait before the stamp task). All completed
with input `{}` and output `{"version":"rerun","ticked":true}`.

| Instance ID | Created UTC | Completed UTC |
|---|---|---|
| `dws-sched:rerun-every:20261114T195224Z` | 19:52:24.635909299 | 19:52:25.065808880 |
| `dws-sched:rerun-cron:20261114T195200Z` | 19:52:02.904923896 | 19:52:03.449909064 |
| `dws-after:rerun-after:1` | 19:51:24.009720172 | 19:51:37.399200008 |
| `dws-after:rerun-after:2` | 19:52:21.112037598 | 19:53:15.401348799 |

The every job anchor was `19:51:24.512Z`, so its first key is anchor + 60 s. Cron's key
matches the even UTC minute. The second after instance started **43.713 seconds** after the first
completed, exceeding the 30-second delay. Completion was checked through Dapr workflow metadata;
the second after instance completed during scheduler downtime.

Both scheduler replicas referenced `dws-rerun-schedule-time-dapr-api-token` in their annotation
and received `DAPR_API_TOKEN` through the chart's Secret reference. Sidecar startup logs confirmed
HTTP/gRPC token authentication enabled. The scheduler and all three target sidecars' logs showed
no 401/invalid-token/Unauthorized errors in the inspected windows. Actual completed instances
and successful callbacks establish that starts worked; this conclusion is not based only on log
absence. Compiled orchestrator pods have no API-token annotation in this chart configuration;
this verifies the scheduler's authenticated local-sidecar invocation path, not an extra target
API-token requirement. No token value was recorded.

### 9.4 — two replicas and downtime replay passed

Before downtime, both replicas were Ready, every/cron each had one instance, and all four sampled
instance claims were `started` with Redis version 2 (claim creation followed by successful start).
The target sidecars each logged exactly one `starting new` entry per sampled instance ID.

Scaled the scheduler to zero at 19:53:04 UTC; both pods were gone by 19:53:07. After a 190-second
sleep with no scheduler pods, the restore-to-two operation was recorded at **19:56:28 UTC**.
Four every ticks (19:53:24 through 19:56:24) and two cron ticks (19:54 and 19:56) were due while
the scheduler was unavailable. The next ordinary every tick was not due until 19:57:24.

On recovery, four successful every callbacks reached both replicas, and two successful cron
callbacks reached one replica. They produced exactly these two completed instances:

| Replayed instance ID | Created UTC | Completed UTC | New starts / claim version |
|---|---|---|---|
| `dws-sched:rerun-every:20261114T195624Z` | 19:56:30.500940871 | 19:56:30.814392187 | 1 / 2 |
| `dws-sched:rerun-cron:20261114T195600Z` | 19:56:30.512536456 | 19:56:30.855975028 | 1 / 2 |

The recovery snapshot at 19:56:46–48, before the next ordinary every tick, contained only one
new every claim and one new cron claim. Both were `started`. Full target-sidecar logs contained
exactly one `starting new` entry for each ID, including the earlier instances, so completed
instances were not restarted by duplicate deliveries. This verifies the replay burst rather than
only the next ordinary tick.

**Cancellation caveat:** the stronger follow-up expectation of *no* 499 did not hold. One every,
one cron and one after-poll callback were canceled at 19:56:29.818, before initial lists completed
at 19:56:30.265 and 19:56:30.360. Subsequent deliveries succeeded: 21 callback responses were 200
and none were 500 in the recovery snapshot. The successful every/cron callbacks held across
initial synchronization took about 770 ms. No replay instance was lost or duplicated in this run,
but it does not prove cancellation-free admission under every startup timing.

Selected metadata, claims, pod/image identities, callback responses, initial-list timestamps and
individual start log entries are retained in
[scheduling-phase6a-rerun-observations.json](scheduling-phase6a-rerun-observations.json).
To reproduce, install the chart settings above, submit the initial-run fixture with the rerun
names/marker, inspect all three schedules, scale the scheduler `2 → 0`, wait for both pods to
disappear and several intervals to elapse, then restore `0 → 2`. Capture claims and target
start logs before the next ordinary tick. Use port-forwarding to sidecar `:3500` for workflow
metadata: these sidecars' HTTP APIs listen on localhost, so the Kubernetes pod proxy cannot
reach them at the pod IP. Use the SDK Jobs list probe for registration/cleanup as in the first run.

## Reproduction and retained evidence

Use the image build and chart configuration above, then submit this definition with the appropriate
name and schedule (add `wait: PT10S` before `stamp` for the after workflow):

```yaml
document: { dsl: '1.0.0', namespace: e2e, name: verify-every, version: '1.0.0' }
schedule: { every: PT1M }
do:
  - stamp:
      set: { version: '"A"', ticked: true }
```

Query instance metadata through the orchestrator sidecar at
`GET /v1.0/workflows/dapr/<instance-id>`; it includes input, output, creation and completion times.
The app's `GET /workflows/instances/<id>` omits those timestamps. Inspect jobs using the SDK's
`ListJobsAsync`: Dapr 1.18.2's HTTP GetJob endpoint cannot JSON-encode the SDK's custom protobuf
payload type (`dapr.io/schedule/jobpayload`) and returns 500, although the stored job is valid.
The read-only SDK probe was executed in the scheduler container with its existing environment.

Selected metadata and job observations are retained in
[scheduling-phase6a-observations.json](scheduling-phase6a-observations.json). Temporary build logs,
full observations and helper scripts were kept under `/tmp/scheduling-6a` during the session;
they are not durable repository evidence. Dapr 1.18.7 and all-rollout version affinity remain
unverified, independently of the auth blocker.

## Cleanup

Deleted all verification workflows through their controller APIs (HTTP 204). Both namespaces'
SDK Jobs lists were then empty (`[]`), so no verification jobs were left behind. The temporary
Helm releases were uninstalled and their namespaces were submitted for deletion. Node-local
build-image cache entries were retained.

The follow-up rerun also deleted all three workflows through the controller (HTTP 204 each).
The SDK Jobs list was empty at 19:57:35 UTC. Uninstalled `dws-rerun` and deleted the isolated
`dws-scheduling-rerun` namespace, including its Redis, builder, registry and node-image helpers.
Existing Dapr/Knative installations were left in place; node image caches were retained.
