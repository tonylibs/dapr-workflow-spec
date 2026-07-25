## 1. dws-run scaffold

- [x] 1.1 Create `dws-run/` with `go.mod` (module `github.com/dws/dws-run`, Go 1.26), `.gitignore`,
      and `.dockerignore`, mirroring `dws-call-http`'s files.
- [ ] 1.2 Add `dws-run/Makefile` with `build`, `test` (`go test -race ./...`), `vet`, `fmt-check`,
      `lint`, `docker-shell`, `docker-script-js`, `docker-script-python`, and `clean` targets.
- [ ] 1.3 Add `dws-run/main.go` — load config, build the runner, start the server, exit non-zero on
      any configuration error.
- [x] 1.4 Validate: `cd dws-run && go build ./...`

## 2. dws-run configuration (`internal/config`)

- [x] 2.1 Define `Config` with `Task`, `Port`, `Command`, `Script`, `Arguments`, `Environment`,
      `Return`, `Output`, `Timeout`, plus a `Mode` (shell / script-js / script-python) baked in at
      build or set by the image's entrypoint.
- [x] 2.2 Parse and validate `RETURN` against exactly `stdout|stderr|code|all|none`, defaulting to
      `stdout`; reject anything else at startup with a message listing the accepted values.
- [x] 2.3 Parse `ARGUMENTS` as a JSON **object** (`map[string]any`) preserving key order; reject
      arrays and non-objects with a message stating a JSON object is required.
- [x] 2.4 Parse `ENVIRONMENT` as a JSON object of strings; reject non-string values.
- [x] 2.5 Reuse `dws-call-http`'s `OUTPUT` (`replace|merge`, default `replace`) and `TIMEOUT`
      (Go duration, positive) parsing and error style.
- [x] 2.6 Require `COMMAND` in shell mode and `SCRIPT` in script mode; fail startup otherwise.
- [x] 2.7 Write `config_test.go` covering: each `RETURN` value, unknown `RETURN`, `ARGUMENTS` as
      array (rejected), `ARGUMENTS` key order preserved, `ENVIRONMENT` non-string (rejected),
      invalid `OUTPUT`, invalid and non-positive `TIMEOUT`, and missing `COMMAND`/`SCRIPT`.
- [x] 2.8 Validate: `cd dws-run && go test -race ./internal/config/`

## 3. dws-run execution (`internal/runner`)

- [x] 3.1 Spawn the subprocess per mode: `sh -c` for shell, `node` for script-js, `python3` for
      script-python; extend (do not replace) the process environment with `ENVIRONMENT` entries.
- [x] 3.2 Write the JSON-encoded `POST /run` body to the subprocess's stdin and close stdin.
- [x] 3.3 Render shell `ARGUMENTS` as ordered `--key value` argv entries passed as positional
      parameters to `sh -c` — never string-concatenated into the command.
- [x] 3.4 Generate the script prelude that binds `ARGUMENTS` entries as in-scope variables
      (`const` for JS, module-level globals for Python) preserving JSON types.
- [x] 3.5 Capture stdout, stderr, and exit code; enforce `TIMEOUT` by terminating the subprocess.
- [x] 3.6 Implement `RETURN` selection: stdout string, stderr string, exit-code number,
      `{code, stdout, stderr}` for `all`, empty for `none`.
- [x] 3.7 Implement `OUTPUT` shaping over the selected value — `replace` verbatim, `merge`
      shallow-merged into the input, erroring when the value is not an object.
- [x] 3.8 For `RETURN=stdout|stderr` with `OUTPUT=replace`, JSON-parse the trimmed output and fall
      back to a raw JSON string when it does not parse (diverging from `dws-call-http`, which
      hard-fails).
- [x] 3.9 Define `ExitError` (non-zero exit) and `SpawnError` (interpreter missing, permission,
      timeout) as the retryable error types.
- [x] 3.10 Implement the exit-code rule: non-zero exit is data under `RETURN=code|all`, and an
      `ExitError` under `stdout|stderr|none`.
- [x] 3.11 Write `runner_test.go` covering each scenario in
      `specs/run-step-execution/spec.md`, including shell metacharacters in an argument value,
      argument order, plain-text output fallback, timeout, and both exit-code branches.
- [x] 3.12 Validate: `cd dws-run && go test -race ./internal/runner/`

## 4. dws-run HTTP surface (`internal/server`)

- [ ] 4.1 Implement `POST /run` and `GET /healthz`, mirroring `dws-call-http/internal/server/server.go`
      — empty body means `{}`, malformed JSON means `400`.
- [ ] 4.2 Map `ExitError` and `SpawnError` to `502` (with `task`, `exitCode`, `stderr` for exits)
      and everything else to `500`, so the orchestrator's retry policy engages on exactly the
      retryable cases.
- [ ] 4.3 Write `server_test.go` covering healthz, empty body, malformed body, a `200` success, a
      `502` on non-zero exit under `RETURN=stdout`, and a `200` on non-zero exit under `RETURN=code`.
- [ ] 4.4 Validate: `cd dws-run && make test && make lint`

## 5. dws-run packaging

- [ ] 5.1 Write the three Dockerfiles sharing an identical Go build stage and differing only in
      final-stage `FROM` and exec command: minimal/distroless for shell, `node:24-slim` for js,
      `python:3.13-slim` for python.
- [ ] 5.2 Confirm by diffing the three files that nothing outside the final stage differs.
- [ ] 5.3 Add `dws-run/k8s/knative-service.yaml` with one example Service per image, carrying
      `dws.io/step-type` and `dws.io/task` labels, Dapr app-id = task name, scale-to-zero
      annotations, and `/healthz` probes, mirroring `dws-call-http/k8s/knative-service.yaml`.
- [ ] 5.4 Write `dws-run/README.md` documenting the env contract, the `RETURN`/`OUTPUT`
      composition, exit-code semantics, and the argument-rendering rules per runtime.
- [ ] 5.5 Add `.github/workflows/dws-run.yml`: path-filtered on `dws-run/**`; `go vet ./... && go
      test ./...` on every push and PR; build all three images on PRs without pushing; build and
      push to `ghcr.io/tonylibs/dws-run-shell`, `dws-run-script-js`, and `dws-run-script-python`
      only on merge to `main`.
- [ ] 5.6 Validate: each of the three image builds succeeds locally.

## 6. dws-controller compilation

- [ ] 6.1 Replace `TaskKind.RUN` with `RUN_SHELL`, `RUN_SCRIPT_JS`, `RUN_SCRIPT_PYTHON`.
- [ ] 6.2 Replace `ImageCatalog.run()` with `runShell()`, `runScriptJs()`, `runScriptPython()`,
      updating the record and every construction site.
- [ ] 6.3 Replace `DwsConfig.Images.run()` with the three accessors and update `catalog()`.
- [ ] 6.4 Update `application.yaml`: `dws.images.run` becomes `run-shell`, `run-script-js`, and
      `run-script-python`, each defaulting to `ghcr.io/tonylibs/<name>:latest`.
- [ ] 6.5 Rewrite `WorkflowCompiler.runStep()` for `run.shell`: forward `command`, `arguments`
      (JSON object, key order preserved), and `environment`; select `RUN_SHELL` +
      `images.runShell()`.
- [ ] 6.6 Extend `runStep()` for `run.script`: read `language` from the `Script` base class, accept
      only `js` and `python`, require `getInlineScript()`, forward `code` to `SCRIPT` plus
      `arguments`/`environment`, and select the matching kind and image.
- [ ] 6.7 Forward `run.return` to `RETURN` using `ProcessReturnType.value()`, defaulting to
      `stdout` when unset.
- [ ] 6.8 Throw `CompilationException` with a subtype-naming message for `run.container`,
      `run.workflow`, an unsupported script `language`, and `run.script` with `source` instead of
      `code`.
- [ ] 6.9 Validate script argument names as identifiers for the target language and throw
      `CompilationException` naming the invalid argument.
- [ ] 6.10 Check whether `RunTask`'s inherited `TaskBase.getTimeout()` can be forwarded to
      `TIMEOUT` as a Go duration; forward it if so, otherwise leave `TIMEOUT` unset and record why
      in `design.md`'s Open Questions.

## 7. dws-controller tests

- [ ] 7.1 `WorkflowCompilerTest`: `run.shell` with `arguments` and `environment` — assert `COMMAND`,
      `ARGUMENTS` JSON object with keys in document order, `ENVIRONMENT`, kind `RUN_SHELL`, and the
      shell image.
- [ ] 7.2 `run.script` with `language: js`, arguments, environment, and an explicit `return` —
      assert `SCRIPT`, `RETURN`, kind `RUN_SCRIPT_JS`, and the js image.
- [ ] 7.3 `run.script` with `language: python` and no `return` — assert `RETURN` = `stdout` and kind
      `RUN_SCRIPT_PYTHON`.
- [ ] 7.4 Rejection cases, one test each: unsupported script `language`; `run.container`;
      `run.workflow`; `run.script` with `source` instead of `code`; and an invalid script argument
      name — each asserting `CompilationException` and a message naming the cause.
- [ ] 7.5 Assert no `StepService` is emitted when compilation fails on an unsupported subtype.
- [ ] 7.6 Validate: `cd dws-controller && ./mvnw test`

## 8. Cross-component verification

- [ ] 8.1 Confirm `git diff` touches no file under `dws-orchestrator/` — an empty orchestrator diff
      is an acceptance criterion, not an observation.
- [ ] 8.2 Update the root `CLAUDE.md` component table and the task-to-resource mapping so `run`
      names the three `dws-run` images alongside the existing step images.
- [ ] 8.3 Update the root `README.md` deployment diagram if it enumerates step images.
- [ ] 8.4 Full gate: `cd dws-run && make test && make lint` and
      `cd dws-controller && ./mvnw test`, both green.
