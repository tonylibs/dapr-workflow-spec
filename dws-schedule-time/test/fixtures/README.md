# Trigger fixtures (controller → scheduler contract)

`trigger-<kind>.json` are the exact `trigger.json` values `dws-controller` renders into a scheduled
version's trigger ConfigMap, one per kind (`every`, `cron`, `after`). The scheduler's
`TriggerConfig` parser test reads them. They are **controller output, not hand-written**:
`dws-controller`'s `SchedulerContractTest` fails when they drift from what the controller renders.

After an intended `trigger.json` change, regenerate them from `dws-controller/` and update the
scheduler's parser in the same change:

```sh
./mvnw test -Dtest=SchedulerContractTest -Ddws.writeTriggerFixtures=true
```

The rest of the contract, pinned by the same test:

- trigger ConfigMaps carry the standard `dws.io/workflow`, `dws.io/version`, `dws.io/managed-by`
  labels plus `dws.io/trigger=time`, and are immutable;
- `kind` is `every`, `cron` or `after`; `schedule` is a canonical ISO-8601 duration
  (`java.time.Duration#toString`, e.g. `PT1H30M`, `PT24H` for a day) or a single-space-separated
  standard 5-field cron;
- a superseded orchestrator Deployment carries the **annotation** (not a label)
  `dws.io/drain: "true"`; the active version is the one whose Deployment lacks it.
