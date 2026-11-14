# Scheduling Phase 6a — live evidence

**Status: NOT YET RUN.** This file is the procedure for tasks 9.1–9.5 of OpenSpec change
`scheduling-phase-6a-time-triggers`, with the expected result of every check. Fill in the
"Observed" column and the header below from a real run, then link this file from
[ADR 0013](../adr/0013-time-triggers-in-dws-schedule-time.md) (task 9.6).

Why it is not run yet: the agent sandbox that implemented the change cannot run any Kubernetes
node. Its kernel refuses to lower `oom_score_adj` even for root
(`echo -500 > /proc/self/oom_score_adj` → I/O error), and kubelet sets it for every pod, so kind
(also with `failCgroupV1: false` for the host's cgroup v1) fails with
`runc init: failed to update /proc/self/oom_score_adj: Permission denied`. Every unit, contract and
render gate passed there; only these cluster behaviours are unproven.

Run date: _TBD_  
Cluster: _TBD_ (kind on cgroup v2, Kubernetes ≥ 1.31)  
Dapr: _TBD_ (1.18.x, the chart's default)  
Images: _TBD_ (`dws-controller`, `dws-orchestrator`, `dws-schedule-time` from this branch)

## 0. Setup

Until the images are published, build them from the branch and load them into kind:

```bash
docker build -t dws-schedule-time:e2e dws-schedule-time
docker build -t dws-orchestrator:e2e dws-orchestrator          # per its README / Dockerfile
(cd dws-controller && ./mvnw -q package -DskipTests) && docker build -f dws-controller/src/main/docker/Dockerfile.jvm -t dws-controller:e2e dws-controller
kind load docker-image dws-schedule-time:e2e dws-orchestrator:e2e dws-controller:e2e
```

```bash
helm install dws charts/dws --namespace dws-e2e --create-namespace \
  --set controller.image.repository=dws-controller --set controller.image.tag=e2e \
  --set controller.images.orchestrator.repository=dws-orchestrator --set controller.images.orchestrator.tag=e2e \
  --set scheduleTime.image.repository=dws-schedule-time --set scheduleTime.image.tag=e2e \
  --set scheduleTime.image.pullPolicy=Never
kubectl -n dws-e2e rollout status deploy/dws-controller deploy/dws-schedule-time
kubectl -n dws-e2e port-forward svc/dws-controller 8081:80 &
```

Three definitions, each a single `set` task (no step service needed):

```bash
for spec in "tick-every|every: PT1M" "tick-cron|cron: \"*/2 * * * *\"" "tick-after|after: PT30S"; do
  name=${spec%%|*}; sched=${spec#*|}
  cat > /tmp/$name.yaml <<EOF
document: { dsl: '1.0.0', namespace: e2e, name: $name, version: '1.0.0' }
schedule: { $sched }
do:
  - stamp:
      set: { ticked: true }
EOF
  curl -fsS -X POST -H 'Content-Type: application/yaml' --data-binary @/tmp/$name.yaml http://127.0.0.1:8081/workflows
done
```

Useful probes (orchestrator Deployment `<workflow>-<version>`, app port 8080):

```bash
kubectl -n dws-e2e get cm -l dws.io/trigger=time -o custom-columns=NAME:.metadata.name,JSON:.data.trigger\\.json
kubectl -n dws-e2e logs deploy/dws-schedule-time -c schedule-time | grep -E 'dws-sched|dws-after|Fire'
# status <orchestrator-deployment> <instance-id>: through the API server's pod proxy (no tools needed in the image)
status() {
  pod=$(kubectl -n dws-e2e get pod -l "dws.io/workflow=${1%-v*}" -o jsonpath='{.items[0].metadata.name}')
  kubectl get --raw "/api/v1/namespaces/dws-e2e/pods/$pod:8080/proxy/workflows/instances/$2"
}
```

## 9.1 Fires and instance IDs

| Check | Expected | Observed |
|---|---|---|
| Trigger ConfigMaps | three `dws-trigger-<w>-<v>` with `kind` every/cron/after and `schedule` `PT1M` / `*/2 * * * *` / `PT30S` | |
| `every: PT1M` | one instance per minute, IDs `dws-sched:tick-every:<anchor + n·60s, yyyyMMddTHHmmssZ>`, first one ~1 min after registration | |
| `cron: */2` | instances at even minutes only, IDs `dws-sched:tick-cron:<yyyyMMddTHHmm00Z>` | |
| `after: PT30S` | `dws-after:tick-after:1` starts at activation; `:2` starts ≥ 30 s after `:1` completed (compare `status` timestamps), never while `:1` runs | |
| Input | every instance's input is `{}` | |

## 9.2 Takeover and deletion

| Check | Expected | Observed |
|---|---|---|
| Redeploy `tick-cron` with `cron: "1-59/2 * * * *"` | old Deployment annotated `dws.io/drain: "true"`; job `dws-sched-tick-cron` overwritten (scheduler log); next fire at an odd minute | |
| Next fire's version | runs on the **new** version's orchestrator. Both versions share Dapr app-id `tick-cron`, so check which pod logged the start; if the draining pod served it, record that here (open question from verification, see below) | |
| `DELETE /workflows/tick-cron` | trigger ConfigMap gone; job `dws-sched-tick-cron` deleted; no further `dws-sched:tick-cron:*` instances | |

## 9.3 Restart

| Check | Expected | Observed |
|---|---|---|
| `kubectl rollout restart deploy/dws-schedule-time` | no "overwrite" of unchanged jobs in the new pod's log; `tick-every` keeps its anchor (IDs stay on the same second-of-minute) | |
| `after` sequence | the new pod polls `dws-after:tick-after:<n>` and continues at `n+1`; never restarts at `:1` | |

## 9.4 Replicas and replayed bursts

| Check | Expected | Observed |
|---|---|---|
| `kubectl scale deploy/dws-schedule-time --replicas=2` | one instance per fire key (no duplicate IDs; the orchestrator answers `200` to a duplicate) | |
| Scale to 0 for ≥ 3 `every` intervals, then back to 2 | Dapr replays the missed ticks as a burst; exactly one new instance, on one fire key | |

## 9.5 Machine auth on

Repeat 9.1 after `helm upgrade ... --set auth.enabled=true --set auth.issuer=... --set auth.audience=...`
and the `kubectl rollout restart` that `NOTES.txt` prints (it now includes `dws-schedule-time`).

| Check | Expected | Observed |
|---|---|---|
| Scheduler pod | `dapr.io/api-token-secret: dws-schedule-time-dapr-api-token`, `DAPR_API_TOKEN` from that Secret | |
| Job callbacks | `POST /job/<name>` reaches the app (scheduler log shows fires) | |
| Starts | instances start; no `401` in the `daprd` logs of `dws-schedule-time` or any orchestrator | |

## Open question carried into this run

Orchestrator versions of one workflow share the Dapr app-id (the workflow name), and the trigger's
`appId` is that name. Service invocation during a rollout may therefore reach the draining version.
9.2 must record which pod served the first post-takeover fire; if it is the draining one, the spec
scenario "the next fire starts an instance on version B's orchestrator app-id" needs either a
controller change (per-version app-ids) or a relaxed scenario before archive.
