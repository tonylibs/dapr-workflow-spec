# Data-flow conformance corpus

One language-neutral set of data-flow cases that every runtime must pass:
[ADR 0012](../../docs/adr/0012-data-flow-in-v2-node-handlers.md) Decision 10, roadmap item 2.0b.
The three runtimes are v1 (`dws-orchestrator`), the v2 Step handlers (2h) and the v2 Flow companion
path (3k). v1 runs the whole corpus on every build, so each expected result has been checked
against a real runtime. Where v1 disagrees with an expectation, the disagreement is listed as a
known divergence.

```
conformance/data-flow/
├── case.schema.json      # JSON Schema 2020-12 for one case file
├── cases/<area>/*.yaml   # the corpus, one case per file
└── divergences/v1.yaml   # v1's known divergences, each with its follow-up task
```

## Case format

A case is plain YAML data, validated by [`case.schema.json`](case.schema.json).

| Field | Required | Meaning |
|---|---|---|
| `name` | yes | `<area>/<file name>`, unique; divergence lists refer to it |
| `test` | yes | the test name a runner reports, `givenX_whenY_thenZ`, unique |
| `area` | yes | the directory: `stages`, `no-data-flow`, `context`, `structural`, `expressions`, `faults`, `unsupported`, `builtins` |
| `summary` | yes | what the case proves, one sentence |
| `source` | yes | where the expected result comes from: OWS 1.0.0, `openspec/specs/workflow-data-flow/spec.md`, or an ADR 0012 decision |
| `engine` | no | what today's jq engine does with the expression, when that matters |
| `uses` | yes | every task kind in the workflow, sorted (`for`, `fork`, `raise`, `set`, `try`) |
| `workflow` | yes | an OWS DSL 1.0.0 document |
| `input` | yes | the instance input |
| `expect` | yes | either `{output, context}` or `{fault}` |

`expect.output` is the instance's completion output. `expect.context` is the workflow context when
the top-level `do` list ends. The context is never part of the output. Numbers compare by value,
so `10` and `10.0` are equal.

`expect.fault` means the instance fails with a data-flow fault:

| Field | Meaning |
|---|---|
| `task` | the task the fault names |
| `phase` | `input`, `output` or `export` |
| `location` | for a schema failure, the JSON Pointer of the offending instance location (`""` is the root) |
| `detailContains` | strings the fault detail must contain, such as the unsupported form or the blocked builtin |

```yaml
name: faults/output-schema
test: givenNonConformingOutput_whenTaskCompletes_thenFaultNamesOutputPhaseAndNestedLocation
area: faults
summary: A non-conforming output faults the task in the output phase, naming a nested offending location.
source: workflow-data-flow spec, scenario "Validation failure names the offending field".
uses: [set]
workflow:
  document: { dsl: '1.0.0', namespace: conformance, name: output-schema-fault, version: '1.0.0' }
  do:
    - pack:
        set:
          items: '${ .items }'
        output:
          schema:
            format: json
            document: { type: object, properties: { items: { type: array, items: { properties: { qty: { minimum: 1 } } } } } }
input:
  items: [{ qty: 1 }, { qty: 0 }]
expect:
  fault: { task: pack, phase: output, location: /items/1/qty }
```

## Authoring rules

- **Expectations come from the specs, not from a runtime.** The sources are OWS 1.0.0, the
  `workflow-data-flow` capability spec and ADR 0012. If a runtime disagrees, list a divergence for
  that runtime. Never edit the expectation.
- **Stage order is v1's**, which ADR 0012 adopts: `input.from`, `input.schema`, body, `output.as`,
  `output.schema`, `export.as`, `export.schema`. OWS 1.0.0 (dsl.md "Data Flow", step 3) validates
  `input.schema` against the *raw* input before `input.from`. `stages/order-input-schema-after-from`
  pins the adopted order.
- **Context rules are ADR 0012 Decision 5.** The context starts as `{}` and each task's export
  replaces it for the next task. It threads through `for` iterations and out of the loop. A `try`
  body or `catch.do` returns its context. Each `fork` branch gets a snapshot, and the fork returns
  the incoming context.
- **Runtime-neutral.** Use only the pure task kinds `set`, `raise`, `for`, `fork` and `try`. No
  case calls a service, names a class or activity, or depends on an endpoint. A schema
  `resource.endpoint` is data that must never be fetched.
- **`set` bodies are neutral too.** OWS 1.0.0 does not say whether `set` merges its mapping into
  the input or replaces it (v1 merges). Every body therefore either sets every key of its input,
  or is followed by an `output.as` that builds the whole document, so both readings give the same
  result. Expression values are always `${ }`-wrapped.
- **Be honest about the engine.** If the jq engine in use does not implement a builtin, say so in
  `engine`. A case passes only on the behavior the spec asks for, never on what the engine happens
  to do.
- Every inline schema states `format: json`, which OWS 1.0.0 requires. Every runtime expression is
  single-quoted, because an unquoted `${ { ... } }` is a YAML flow mapping.

## Running a runtime against the corpus

A runner, in any language:

1. Loads every `cases/**/*.yaml`, validates it against `case.schema.json`, and checks that `name`
   and `area` match the path, that `test` is unique, and that `uses` lists the task kinds the
   workflow contains. It reports each case under its `test` name.
2. Starts the workflow with `input` and observes either the completion output plus the context the
   top-level `do` list ends with, or a failure.
3. Maps a failure to the neutral fault `(task, phase, detail)`, plus the schema locations if the
   runtime reports them. A failure that is not a data-flow fault does not match an expected fault.
4. Compares the result with `expect`. Each case gets exactly one verdict:
   - **pass**
   - **fail**
   - **known divergence**: the case is listed in `divergences/<runtime>.yaml` and still differs.
   - **stale divergence**: the case is listed but now passes. This must fail the run, so the
     entry is removed in the same change that fixes it.
5. Prints one line per case and the totals. The run is green when every case that is not listed
   passes, and every listed case still diverges.

The divergence list is per runtime:

```yaml
runtime: v1 (dws-orchestrator)
divergences:
  - case: builtins/clock-local-time
    reason: one line
    followUp: '5.2'      # 5.1 = v1 conformance and schema dialect, 5.2 = jq builtin policy in v1
```

How each runtime plugs in:

| Runtime | How it runs a case |
|---|---|
| v1 | Interprets the document directly. See below. |
| 2h, Step handlers | A `set` or `raise` task with data flow is one Step call that runs the whole pipeline (Decision 2). The runner threads `data` and `context` between Step calls as the Flow would (Decision 4 envelope), until 3k exists. Cases whose `uses` include `for`, `fork` or `try` need the Flow. |
| 3k, Flow and companion | The Flow runs the document. A `for`, `fork` or `try` that declares data flow calls its companion `<task>.dataflow` before and after its body (Decision 3). Every case applies. |

## v1

```sh
cd dws-orchestrator
./mvnw test -Dtest=DataFlowConformanceIntegrationTest
```

`DataFlowConformanceIntegrationTest` runs inside `./mvnw verify`. It drives v1's real interpreter
and its in-process activities against a mocked workflow context. It reads the context through the
top-level scope result, and reads faults from the exception message, which is all that crosses the
activity boundary. Set `-Ddws.conformance.dataflow=<dir>` to run another copy of the corpus.

v1 renders a data-flow fault as `task '<task>' <phase> data flow failed: <detail>`. For a schema
failure, `<detail>` is `schema validation failed: <pointer>: <message>[; ...]`.

## jq engine notes

v1 and `dws-step` both use jackson-jq 1.2.0 with the jq 1.6 builtin set.

- It implements none of the builtins ADR 0012 Decision 9 blocks (`env`, `$ENV`, `input`,
  `inputs`, `input_filename`, `halt`, `halt_error`, `debug`, `stderr`), so they already fail to
  compile.
- `import` and `include` parse, but fail with "module not found" because no module loader is
  configured.
- v1's fault detail names the builtin only because it echoes the expression. The engine's own
  reason, for example "Function env/0 does not exist", does not reach the fault.
- Of the clock functions, only `now`, `todateiso8601` and `fromdateiso8601` exist. jq 1.6's
  `todate`, `fromdate`, `gmtime`, `mktime`, `strftime`, `strptime`, `localtime`, `strflocaltime`
  and `dateadd` are missing, even though Decision 9 allows them (see
  `builtins/clock-local-time`).
