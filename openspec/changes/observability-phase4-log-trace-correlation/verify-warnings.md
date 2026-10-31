# Verification warnings (to review later)

Raised by `/opsx:verify observability-phase4-log-trace-correlation` on 2026-10-31 against HEAD
`474e48fe`. Not blockers for archive on their own; the CRITICAL items of that run were the
unfinished task group 9 (live evidence), handled separately.

The run's two suggestions are tracked as issues:
[#178](https://github.com/tonylibs/dapr-workflow-spec/issues/178) (dws-step timestamp drops
milliseconds on exact seconds) and
[#179](https://github.com/tonylibs/dapr-workflow-spec/issues/179) (align dws-step's formatter with
dws-orchestrator's).

## W1. Collector receiver is only statically verified

The `filelog` receiver in `charts/dws/tests/fixtures/otel-collector-dev.yaml` (operators
`dws_json` → `dws_trace` → `dws_message`/`dws_logger` → `remove` steps) is guarded by a render-test
leg in `charts/dws/tests/observability-render-test.sh` that asserts the exclusions and operators are
*present*. Nothing automated proves they *behave*:

- a JSON line becomes an OTLP record with body = `message`, severity = `severity`, and the record's
  TraceId/SpanId set from `trace_id`/`span_id`
  (spec `helm-log-format`, "JSON line becomes a trace-correlated record");
- a non-JSON line passes through with its raw body and no trace context ("Non-JSON line is kept");
- the record is retrievable from Loki by trace ID ("Logs are retrievable by trace ID").

Also open from `design.md` D6 / Open Questions: the Loki version and OTLP endpoint form
(`http://loki:3100/otlp`, fixture line ~136) are pinned without a cluster check.

**What to do:** the task 9.1–9.3 live run is the check. Record one JSON line and one non-JSON line in
`docs/roadmaps/observability-phase4-evidence.md` with the record they produced. If a cheaper
regression guard is wanted later, run `otelcol-contrib` against the extracted `config.yaml` with a
`filelog` input file and a `file` exporter in CI and diff the output.

**Status (live run 2026-10-31): settled for this commit.** All three scenarios were observed and are
recorded in the evidence file's "Collector receiver behaviour" section. Loki 3.7.9 accepted
`otlphttp` at `/otlp`. A CI guard is still absent, so a later edit to the operators would go unnoticed
until the next live run.

## W2. "Nothing else on stdout" has known gaps outside the logging systems

Spec `structured-log-correlation`, "JSON mode writes one object per line", says nothing else shall
be written to stdout *by the logging system*. That holds, but `design.md` D7 records three sources of
non-JSON stdout that a `jq -e .` over `kubectl logs` would trip on:

- `dws-controller` under `quarkus:dev` stays text (the `@QuarkusMain` `ControllerMain` does not run
  there) — dev only;
- `dws-controller`: a failed Quarkus start can append Quarkus's own text fallback;
- `dws-orchestrator` / `dws-step`: JVM-level `WARNING: sun.misc.Unsafe ...` lines from netty, printed
  by the JVM, not by Logback.

The reference collector forwards non-JSON lines with a raw body, so nothing is lost; the cost is
untidy records and a `jq` pipeline that must tolerate them (`jq -R 'fromjson? // .'`).

**What to do:** check during the live run whether the `sun.misc.Unsafe` lines appear in the
orchestrator pod; if so, list them as a deviation in the evidence doc, and consider either
`--sun-misc-unsafe-memory-access=allow` in the image's JVM flags or a note in `docs/log-format.md`
that JVM/stderr output is outside the contract.

**Status (live run 2026-10-31):** the netty lines do appear in the orchestrator, along with more than
D7 lists. The controller has the `run-java.sh` launcher lines (`INFO exec -a "java" …`) and both Java
images have the JVM's `Picked up JAVA_TOOL_OPTIONS` and CDS warning plus the OTel Java agent's own
logger. **Every one of them is on stderr**; stdout carried JSON only. So the contract holds as
written, and only a whole-stream `kubectl logs | jq -e .` fails. Suggested follow-up: the
`docs/log-format.md` note ("the contract covers stdout; stderr carries JVM, launcher and agent
output and is forwarded raw"), which is cheaper and more complete than a JVM flag.

## W3. The collector fixture only reads one node's logs

`otel-collector-dev.yaml` runs the collector as a single-replica `Deployment`, and the `filelog`
receiver reads the host's `/var/log/pods` through a `hostPath`, so it sees only pods on the node it
was scheduled to. On a multi-node cluster most pods' logs never reach Loki. The live run had to
apply it as a `DaemonSet`. The chart README already says the collector must be "an agent or
DaemonSet-style collector", so only the fixture contradicts it.

**What to do:** make the fixture's collector a `DaemonSet` (drop `replicas: 1`). The render test does
not pin the workload kind, so it should not need a change.

## Defects found during the live run (not log-format)

Recorded in the evidence file, "Defects found on the way"; none has an issue yet:

1. `dapr.enabled=false` leaves the Redis Components pointing at a bundled Redis that is not
   installed (`Secret "dws-redis" not found`); the documented answer is `redis.external.*`, but the
   preflight does not say so.
2. The controller image fails `POST /workflows` with a jsii package-cache permission error on
   `/home/default` (mode 0770); `JSII_RUNTIME_PACKAGE_CACHE=disabled` works around it.
3. Re-posting a deployed workflow fails: the controller's `PUT` on the step KService drops the
   immutable `serving.knative.dev/creator` annotation.
