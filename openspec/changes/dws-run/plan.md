# dws-run Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the missing `run` step image as a new Go component (`dws-run`, three images) and
teach `dws-controller` to compile `run.shell` / `run.script` into it while rejecting the
unsupported subtypes at compile time.

**Architecture:** One Go module under `dws-run/` mirrors `dws-call-http`'s package layout
(`internal/config`, `internal/runner`, `internal/server`) and implements the same step-service HTTP
contract. A `MODE` env var — set by each Dockerfile's final stage — selects which interpreter the
single binary execs (`sh -c`, `node -e`, `python3 -c`), so three images share one build stage and
one codebase. `dws-controller` splits `TaskKind.RUN` and `ImageCatalog.run()` three ways so a
deployed step's runtime is readable from its `dws.io/step-type` label.

**Tech Stack:** Go 1.26 (standard library only — no third-party deps, matching `dws-call-http`);
Java 25 + Quarkus + JUnit 5 + AssertJ for the controller; Docker multi-stage builds; GitHub Actions.

## Global Constraints

- Go module path: `github.com/dws/dws-run`, Go 1.26. Standard library only — do not add dependencies.
- Step-service HTTP contract is fixed: `POST /run` (body = workflow data JSON, empty body ⇒ `{}`),
  `GET /healthz`, `502` for retryable failures. Read `dws-call-http/internal/server/server.go`
  before writing `dws-run`'s server; deviate only where this plan says to.
- Images publish to `ghcr.io/tonylibs/dws-run-shell`, `ghcr.io/tonylibs/dws-run-script-js`,
  `ghcr.io/tonylibs/dws-run-script-python`.
- `RETURN` accepts exactly `stdout|stderr|code|all|none`, default `stdout`.
- `ARGUMENTS` is a JSON **object**, not an array — DSL 1.0.0 models `arguments` as a key/value map
  (`ShellArguments.getAdditionalProperties() -> Map<String,Object>`). Key order must be preserved.
- Non-zero exit is data under `RETURN=code|all`, and a `502` failure under `stdout|stderr|none`.
- `dws-orchestrator` must not be modified. An empty diff under `dws-orchestrator/` is an acceptance
  criterion.
- Every Go test runs under the race detector: `go test -race ./...`.
- Commit after every task. Do not batch.

---

## Task 1: Module scaffold and configuration

**Files:**
- Create: `dws-run/go.mod`, `dws-run/.gitignore`, `dws-run/.dockerignore`
- Create: `dws-run/internal/config/config.go`
- Test: `dws-run/internal/config/config_test.go`

**Interfaces:**
- Consumes: nothing.
- Produces: `config.Config` (fields `Mode`, `Port`, `Task`, `Command`, `Script`, `Arguments`,
  `Environment`, `Return`, `Output`, `Timeout`), `config.Load() (Config, error)`, the `Mode`,
  `ReturnMode`, `OutputMode` string types with their constants, and
  `config.Arguments` / `config.Argument` (an **ordered** name/value list, not a map).

- [ ] **Step 1: Create the module**

```bash
mkdir -p dws-run/internal/config dws-run/internal/runner dws-run/internal/server dws-run/k8s
cd dws-run
cat > go.mod <<'EOF'
module github.com/dws/dws-run

go 1.26
EOF
cp ../dws-call-http/.gitignore .gitignore
cp ../dws-call-http/.dockerignore .dockerignore
```

- [ ] **Step 2: Write the failing config test**

Create `dws-run/internal/config/config_test.go`. Note `t.Setenv` handles cleanup, so each subtest
starts from a clean environment.

```go
package config

import (
	"testing"
	"time"
)

func load(t *testing.T, env map[string]string) (Config, error) {
	t.Helper()
	for k, v := range env {
		t.Setenv(k, v)
	}
	return Load()
}

func TestDefaults(t *testing.T) {
	c, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "echo hi"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if c.Port != "8080" {
		t.Errorf("port: got %q, want 8080", c.Port)
	}
	if c.Task != "run" {
		t.Errorf("task: got %q, want run", c.Task)
	}
	if c.Return != ReturnStdout {
		t.Errorf("return: got %q, want stdout", c.Return)
	}
	if c.Output != OutputReplace {
		t.Errorf("output: got %q, want replace", c.Output)
	}
	if c.Timeout != 30*time.Second {
		t.Errorf("timeout: got %s, want 30s", c.Timeout)
	}
}

func TestRequiredPerMode(t *testing.T) {
	if _, err := load(t, map[string]string{"MODE": "shell"}); err == nil {
		t.Fatal("expected error when COMMAND is unset in shell mode")
	}
	if _, err := load(t, map[string]string{"MODE": "script-js"}); err == nil {
		t.Fatal("expected error when SCRIPT is unset in script mode")
	}
	if _, err := load(t, map[string]string{"MODE": "wat", "COMMAND": "x"}); err == nil {
		t.Fatal("expected error for unknown MODE")
	}
}

func TestReturnModes(t *testing.T) {
	for _, v := range []string{"stdout", "stderr", "code", "all", "none"} {
		if _, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "x", "RETURN": v}); err != nil {
			t.Errorf("RETURN=%s: unexpected error %v", v, err)
		}
	}
	if _, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "x", "RETURN": "exitcode"}); err == nil {
		t.Fatal("expected error for unknown RETURN")
	}
}

func TestArgumentsIsOrderedObject(t *testing.T) {
	c, err := load(t, map[string]string{
		"MODE": "shell", "COMMAND": "x",
		"ARGUMENTS": `{"env":"prod","region":"eu","count":3}`,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []string{"env", "region", "count"}
	if len(c.Arguments) != len(want) {
		t.Fatalf("arguments: got %d, want %d", len(c.Arguments), len(want))
	}
	for i, name := range want {
		if c.Arguments[i].Name != name {
			t.Errorf("arguments[%d]: got %q, want %q", i, c.Arguments[i].Name, name)
		}
	}
	if c.Arguments[2].Value != float64(3) {
		t.Errorf("count: got %#v, want 3", c.Arguments[2].Value)
	}
}

func TestArgumentsRejectsArray(t *testing.T) {
	if _, err := load(t, map[string]string{
		"MODE": "shell", "COMMAND": "x", "ARGUMENTS": `["a","b"]`,
	}); err == nil {
		t.Fatal("expected error: ARGUMENTS must be a JSON object")
	}
}

func TestEnvironmentRejectsNonStrings(t *testing.T) {
	if _, err := load(t, map[string]string{
		"MODE": "shell", "COMMAND": "x", "ENVIRONMENT": `{"PORT":8080}`,
	}); err == nil {
		t.Fatal("expected error: ENVIRONMENT must be a JSON object of strings")
	}
}

func TestOutputAndTimeoutValidation(t *testing.T) {
	if _, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "x", "OUTPUT": "append"}); err == nil {
		t.Fatal("expected error for unknown OUTPUT")
	}
	if _, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "x", "TIMEOUT": "45 seconds"}); err == nil {
		t.Fatal("expected error for unparseable TIMEOUT")
	}
	if _, err := load(t, map[string]string{"MODE": "shell", "COMMAND": "x", "TIMEOUT": "0s"}); err == nil {
		t.Fatal("expected error for non-positive TIMEOUT")
	}
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/config/`
Expected: FAIL — `undefined: Load`, `undefined: Config`.

- [ ] **Step 4: Implement the config package**

Create `dws-run/internal/config/config.go`. The ordered-arguments decoder is the only part with no
counterpart in `dws-call-http`: `encoding/json` into a `map` would lose key order, which the shell
renderer depends on, so it walks the token stream instead.

```go
// Package config loads and validates the step configuration from the
// environment. One codebase serves three images; MODE selects which
// interpreter the runner execs, and every other behavior is env-defined.
package config

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"
)

// Mode selects the interpreter this image execs. It is set by the image's
// final Docker stage, not by the workflow definition.
type Mode string

const (
	ModeShell        Mode = "shell"
	ModeScriptJS     Mode = "script-js"
	ModeScriptPython Mode = "script-python"
)

// ReturnMode mirrors DSL 1.0.0's ProcessReturnType: it selects which part of
// the subprocess result becomes the step's raw value.
type ReturnMode string

const (
	ReturnStdout ReturnMode = "stdout"
	ReturnStderr ReturnMode = "stderr"
	ReturnCode   ReturnMode = "code"
	ReturnAll    ReturnMode = "all"
	ReturnNone   ReturnMode = "none"
)

// OutputMode controls how the raw value folds into the response. Same
// semantics as dws-call-http.
type OutputMode string

const (
	OutputReplace OutputMode = "replace"
	OutputMerge   OutputMode = "merge"
)

const (
	defaultPort    = "8080"
	defaultTask    = "run"
	defaultTimeout = 30 * time.Second
)

// Argument is one entry of the DSL's `arguments` map. Order matters: the shell
// renderer emits flags in definition order, so this is a slice, not a map.
type Argument struct {
	Name  string
	Value any
}

// Config is the fully-resolved step configuration.
type Config struct {
	Mode        Mode
	Port        string
	Task        string
	Command     string
	Script      string
	Arguments   []Argument
	Environment map[string]string
	Return      ReturnMode
	Output      OutputMode
	Timeout     time.Duration
}

// Load reads configuration from the environment and validates it, returning a
// descriptive error for any invalid value so main can exit non-zero at startup
// rather than failing on first invocation.
func Load() (Config, error) {
	cfg := Config{
		Mode:   Mode(strings.ToLower(getenv("MODE", string(ModeShell)))),
		Port:   getenv("PORT", defaultPort),
		Task:   getenv("TASK", defaultTask),
		Return: ReturnMode(strings.ToLower(getenv("RETURN", string(ReturnStdout)))),
		Output: OutputMode(strings.ToLower(getenv("OUTPUT", string(OutputReplace)))),
	}

	switch cfg.Mode {
	case ModeShell:
		cfg.Command = os.Getenv("COMMAND")
		if strings.TrimSpace(cfg.Command) == "" {
			return Config{}, fmt.Errorf("COMMAND is required when MODE=shell")
		}
	case ModeScriptJS, ModeScriptPython:
		cfg.Script = os.Getenv("SCRIPT")
		if strings.TrimSpace(cfg.Script) == "" {
			return Config{}, fmt.Errorf("SCRIPT is required when MODE=%s", cfg.Mode)
		}
	default:
		return Config{}, fmt.Errorf("MODE must be one of shell|script-js|script-python, got %q", cfg.Mode)
	}

	switch cfg.Return {
	case ReturnStdout, ReturnStderr, ReturnCode, ReturnAll, ReturnNone:
	default:
		return Config{}, fmt.Errorf("RETURN must be one of stdout|stderr|code|all|none, got %q", cfg.Return)
	}

	switch cfg.Output {
	case OutputReplace, OutputMerge:
	default:
		return Config{}, fmt.Errorf("OUTPUT must be one of replace|merge, got %q", cfg.Output)
	}

	args, err := parseArguments(os.Getenv("ARGUMENTS"))
	if err != nil {
		return Config{}, err
	}
	cfg.Arguments = args

	env, err := parseStringMap("ENVIRONMENT", os.Getenv("ENVIRONMENT"))
	if err != nil {
		return Config{}, err
	}
	cfg.Environment = env

	cfg.Timeout, err = parseTimeout(os.Getenv("TIMEOUT"), defaultTimeout)
	if err != nil {
		return Config{}, err
	}

	return cfg, nil
}

// parseArguments decodes ARGUMENTS as a JSON object while preserving key
// order. encoding/json into a map would lose the order the shell renderer
// depends on, so this walks the token stream directly.
func parseArguments(raw string) ([]Argument, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}

	dec := json.NewDecoder(bytes.NewReader([]byte(raw)))
	dec.UseNumber()

	tok, err := dec.Token()
	if err != nil {
		return nil, fmt.Errorf("ARGUMENTS must be a JSON object: %w", err)
	}
	if delim, ok := tok.(json.Delim); !ok || delim != '{' {
		return nil, fmt.Errorf("ARGUMENTS must be a JSON object (a key/value map), got %s", raw)
	}

	var args []Argument
	for dec.More() {
		keyTok, err := dec.Token()
		if err != nil {
			return nil, fmt.Errorf("ARGUMENTS: %w", err)
		}
		name, ok := keyTok.(string)
		if !ok {
			return nil, fmt.Errorf("ARGUMENTS: expected a string key, got %v", keyTok)
		}
		var value any
		if err := dec.Decode(&value); err != nil {
			return nil, fmt.Errorf("ARGUMENTS[%s]: %w", name, err)
		}
		args = append(args, Argument{Name: name, Value: normalize(value)})
	}
	return args, nil
}

// normalize converts json.Number back to float64 so argument values compare
// and marshal like ordinary decoded JSON.
func normalize(v any) any {
	if n, ok := v.(json.Number); ok {
		if f, err := n.Float64(); err == nil {
			return f
		}
	}
	return v
}

func parseStringMap(key, raw string) (map[string]string, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(raw), &m); err != nil {
		return nil, fmt.Errorf("%s must be a JSON object of strings: %w", key, err)
	}
	return m, nil
}

func parseTimeout(raw string, def time.Duration) (time.Duration, error) {
	if strings.TrimSpace(raw) == "" {
		return def, nil
	}
	d, err := time.ParseDuration(raw)
	if err != nil {
		return 0, fmt.Errorf("TIMEOUT must be a Go duration (e.g. 30s, 1m): %w", err)
	}
	if d <= 0 {
		return 0, fmt.Errorf("TIMEOUT must be positive, got %s", raw)
	}
	return d, nil
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd dws-run && go test -race ./internal/config/`
Expected: PASS (all seven test functions).

- [ ] **Step 6: Commit**

```bash
git add dws-run/go.mod dws-run/.gitignore dws-run/.dockerignore dws-run/internal/config/
git commit -m "feat(dws-run): add module scaffold and env configuration"
```

---

## Task 2: Subprocess execution — spawn, stdin, capture

**Files:**
- Create: `dws-run/internal/runner/runner.go`
- Test: `dws-run/internal/runner/runner_test.go`

**Interfaces:**
- Consumes: `config.Config` from Task 1.
- Produces: `runner.Runner` with `New(config.Config) *Runner` and
  `Run(ctx context.Context, input map[string]any) (any, error)`; the error types
  `runner.ExitError{Task string; Code int; Stderr string}` and
  `runner.SpawnError{Task string; Err error}`; and the internal
  `execute(ctx, input) (result, error)` where `result` is `{Code int; Stdout, Stderr string}`.

- [ ] **Step 1: Write the failing execution test**

Create `dws-run/internal/runner/runner_test.go`. These tests shell out for real — `sh` is present
in any CI runner, and the shell-mode tests do not need Node or Python.

```go
package runner

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/dws/dws-run/internal/config"
)

func shellCfg(command string) config.Config {
	return config.Config{
		Mode:    config.ModeShell,
		Task:    "t",
		Command: command,
		Return:  config.ReturnStdout,
		Output:  config.OutputReplace,
		Timeout: 10 * time.Second,
	}
}

func TestStdinReceivesFullInput(t *testing.T) {
	r := New(shellCfg("cat"))
	out, err := r.Run(context.Background(), map[string]any{"order": map[string]any{"id": float64(7)}})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	order, ok := m["order"].(map[string]any)
	if !ok || order["id"] != float64(7) {
		t.Fatalf("input did not round-trip through stdin: %#v", m)
	}
}

func TestStdinIsClosed(t *testing.T) {
	// `cat` only exits when stdin reaches EOF; a hang here means stdin was
	// left open.
	r := New(shellCfg("cat > /dev/null; echo done"))
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	out, err := r.Run(ctx, map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "done" {
		t.Fatalf("got %#v, want \"done\"", out)
	}
}

func TestEnvironmentExtendsRatherThanReplaces(t *testing.T) {
	cfg := shellCfg(`printf '%s' "$API_TOKEN"; test -n "$PATH" || exit 9`)
	cfg.Environment = map[string]string{"API_TOKEN": "abc"}
	out, err := New(cfg).Run(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "abc" {
		t.Fatalf("got %#v, want \"abc\" (and a preserved PATH)", out)
	}
}

func TestTimeoutTerminatesSubprocess(t *testing.T) {
	cfg := shellCfg("sleep 5")
	cfg.Timeout = 100 * time.Millisecond
	start := time.Now()
	_, err := New(cfg).Run(context.Background(), map[string]any{})
	if err == nil {
		t.Fatal("expected an error when the subprocess exceeds TIMEOUT")
	}
	if elapsed := time.Since(start); elapsed > 3*time.Second {
		t.Fatalf("subprocess was not terminated promptly (took %s)", elapsed)
	}
}

func TestSpawnFailureIsSpawnError(t *testing.T) {
	cfg := shellCfg("x")
	cfg.Mode = config.ModeScriptPython
	cfg.Command = ""
	cfg.Script = "print(1)"
	r := New(cfg)
	r.interpreter = "definitely-not-an-interpreter-9f2a"
	_, err := r.Run(context.Background(), map[string]any{})
	var spawn *SpawnError
	if !errors.As(err, &spawn) {
		t.Fatalf("expected *SpawnError, got %#v", err)
	}
	if !strings.Contains(spawn.Error(), `"t"`) {
		t.Errorf("error should name the task: %v", spawn)
	}
}
```

Import `errors` alongside `context`, `strings`, `testing`, and `time` in this test file. The test
reaches into the unexported `interpreter` field, which is why `runner_test.go` is `package runner`
rather than `package runner_test`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/runner/`
Expected: FAIL — `undefined: New`, `undefined: SpawnError`.

- [ ] **Step 3: Implement spawn, stdin, and capture**

Create `dws-run/internal/runner/runner.go`. `Run` is a thin wrapper for now — Tasks 3-5 fill in
argument rendering, `RETURN` selection, and `OUTPUT` shaping.

```go
// Package runner executes the configured subprocess for a `run` step.
package runner

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"

	"github.com/dws/dws-run/internal/config"
)

// ExitError is returned when the subprocess exits non-zero and the configured
// RETURN mode does not treat the exit code as data. The server maps it to a
// 502 so the orchestrator's retry policy re-invokes the step.
type ExitError struct {
	Task   string
	Code   int
	Stderr string
}

func (e *ExitError) Error() string {
	return fmt.Sprintf("subprocess for task %q exited with code %d", e.Task, e.Code)
}

// SpawnError is returned when the subprocess never ran to completion — a
// missing interpreter, a permission error, or a timeout. Also mapped to 502,
// since these are typically transient or environmental.
type SpawnError struct {
	Task string
	Err  error
}

func (e *SpawnError) Error() string {
	return fmt.Sprintf("spawn error for task %q: %v", e.Task, e.Err)
}

func (e *SpawnError) Unwrap() error { return e.Err }

// result is the raw outcome of one subprocess invocation.
type result struct {
	Code   int
	Stdout string
	Stderr string
}

// Runner spawns and supervises the configured subprocess.
type Runner struct {
	cfg config.Config
	// interpreter is the executable to exec. Defaults from cfg.Mode; a test
	// may override it to simulate a missing interpreter.
	interpreter string
}

// New builds a Runner for the configured mode.
func New(cfg config.Config) *Runner {
	return &Runner{cfg: cfg, interpreter: interpreterFor(cfg.Mode)}
}

func interpreterFor(m config.Mode) string {
	switch m {
	case config.ModeScriptJS:
		return "node"
	case config.ModeScriptPython:
		return "python3"
	default:
		return "sh"
	}
}

// Run executes the subprocess for the given workflow data and returns the
// result shaped per RETURN and OUTPUT.
func (r *Runner) Run(ctx context.Context, input map[string]any) (any, error) {
	res, err := r.execute(ctx, input)
	if err != nil {
		return nil, err
	}
	return r.shape(input, res)
}

// execute spawns the subprocess with the workflow data on stdin and captures
// stdout, stderr, and the exit code. A non-zero exit is not an error here —
// that decision belongs to shape(), which knows the RETURN mode.
func (r *Runner) execute(ctx context.Context, input map[string]any) (result, error) {
	body, err := json.Marshal(input)
	if err != nil {
		return result{}, &SpawnError{Task: r.cfg.Task, Err: fmt.Errorf("marshal input: %w", err)}
	}

	ctx, cancel := context.WithTimeout(ctx, r.cfg.Timeout)
	defer cancel()

	args, err := r.commandArgs()
	if err != nil {
		return result{}, &SpawnError{Task: r.cfg.Task, Err: err}
	}

	cmd := exec.CommandContext(ctx, r.interpreter, args...)
	cmd.Env = r.subprocessEnv()

	var stdout, stderr bytes.Buffer
	cmd.Stdin = bytes.NewReader(body) // exec closes stdin at EOF
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	runErr := cmd.Run()
	res := result{Code: cmd.ProcessState.ExitCode(), Stdout: stdout.String(), Stderr: stderr.String()}

	if runErr != nil {
		var exitErr *exec.ExitError
		if !errors.As(runErr, &exitErr) {
			// Never started, or was killed by the timeout.
			return result{}, &SpawnError{Task: r.cfg.Task, Err: runErr}
		}
		if ctx.Err() != nil {
			return result{}, &SpawnError{Task: r.cfg.Task, Err: fmt.Errorf("timed out after %s", r.cfg.Timeout)}
		}
	}
	return res, nil
}

// subprocessEnv extends the service's own environment so PATH and interpreter
// discovery keep working; ENVIRONMENT entries win on conflict.
func (r *Runner) subprocessEnv() []string {
	env := os.Environ()
	for k, v := range r.cfg.Environment {
		env = append(env, k+"="+v)
	}
	return env
}
```

Add a temporary `commandArgs` and `shape` so the package compiles; Tasks 3-5 replace them.

```go
func (r *Runner) commandArgs() ([]string, error) {
	switch r.cfg.Mode {
	case config.ModeShell:
		return []string{"-c", r.cfg.Command}, nil
	default:
		return []string{"-e", r.cfg.Script}, nil
	}
}

func (r *Runner) shape(_ map[string]any, res result) (any, error) {
	return res.Stdout, nil
}
```

Note: `python3 -e` is not valid — Task 3 replaces this with `-c`. Leaving it wrong here is
deliberate: Task 3's test is what proves it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd dws-run && go test -race ./internal/runner/`
Expected: PASS. `TestStdinReceivesFullInput` currently returns the raw stdout string, so it will
fail on the `map[string]any` assertion — that is expected and Task 5 fixes it. If you want a green
bar now, temporarily skip that one test with `t.Skip("shaped in Task 5")` and remove the skip in
Task 5. Every other test in this file must pass.

- [ ] **Step 5: Commit**

```bash
git add dws-run/internal/runner/
git commit -m "feat(dws-run): spawn subprocess with workflow data on stdin"
```

---

## Task 3: Argument rendering per runtime

**Files:**
- Create: `dws-run/internal/runner/arguments.go`
- Modify: `dws-run/internal/runner/runner.go` (replace the temporary `commandArgs`)
- Test: `dws-run/internal/runner/arguments_test.go`

**Interfaces:**
- Consumes: `config.Argument` (Task 1), `Runner.interpreter` (Task 2).
- Produces: `shellArgv(command string, args []config.Argument) []string` and
  `scriptSource(mode config.Mode, script string, args []config.Argument) (string, error)`; the
  runner also sets `DWS_ARGUMENTS` on the subprocess environment.

- [ ] **Step 1: Write the failing argument test**

Create `dws-run/internal/runner/arguments_test.go`:

```go
package runner

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/dws/dws-run/internal/config"
)

func TestShellArgvPreservesOrderAndUsesPositionalParams(t *testing.T) {
	argv := shellArgv("deploy.sh", []config.Argument{
		{Name: "env", Value: "prod"},
		{Name: "region", Value: "eu"},
	})
	// sh -c '<command> "$@"' sh --env prod --region eu
	want := []string{"-c", `deploy.sh "$@"`, "sh", "--env", "prod", "--region", "eu"}
	if len(argv) != len(want) {
		t.Fatalf("argv: got %#v, want %#v", argv, want)
	}
	for i := range want {
		if argv[i] != want[i] {
			t.Fatalf("argv[%d]: got %q, want %q", i, argv[i], want[i])
		}
	}
}

func TestShellMetacharactersStayInsideOneArgument(t *testing.T) {
	// The trailing `#` comments out the `"$@"` that shellArgv appends after
	// the command. Without it, printf would receive those appended operands
	// too and re-apply its format string across them, corrupting the output
	// for reasons unrelated to what this test guards. The payload still
	// reaches the command only through the quoted "$2" positional parameter,
	// so a string-concatenating shellArgv still fails this test.
	cfg := shellCfg(`printf '%s' "$2" #`)
	cfg.Arguments = []config.Argument{{Name: "payload", Value: "; rm -rf /"}}
	out, err := New(cfg).Run(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "; rm -rf /" {
		t.Fatalf("got %#v — the value must reach the command as one argv entry", out)
	}
}

func TestScriptSourcePreludeBindsTypedVariables(t *testing.T) {
	args := []config.Argument{
		{Name: "count", Value: float64(3)},
		{Name: "flag", Value: true},
	}

	js, err := scriptSource(config.ModeScriptJS, "console.log(count);", args)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(js, `const count = __dwsArgs["count"];`) {
		t.Errorf("js prelude missing a const binding:\n%s", js)
	}
	if !strings.HasSuffix(js, "console.log(count);") {
		t.Errorf("user script must come after the prelude:\n%s", js)
	}

	py, err := scriptSource(config.ModeScriptPython, "print(count)", args)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.Contains(py, `count = __dws_args["count"]`) {
		t.Errorf("python prelude missing a global binding:\n%s", py)
	}
}

func TestScriptSourceRejectsInvalidIdentifiers(t *testing.T) {
	for _, name := range []string{"1foo", "has-dash", "with space", ""} {
		if _, err := scriptSource(config.ModeScriptJS, "x", []config.Argument{{Name: name}}); err == nil {
			t.Errorf("expected an error for argument name %q", name)
		}
	}
}

func TestArgumentsReachTheSubprocessEnvironment(t *testing.T) {
	cfg := shellCfg(`printf '%s' "$DWS_ARGUMENTS"`)
	cfg.Timeout = 10 * time.Second
	cfg.Arguments = []config.Argument{{Name: "a", Value: float64(1)}}
	out, err := New(cfg).Run(context.Background(), map[string]any{})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if s, _ := out.(string); !strings.Contains(s, `"a"`) {
		t.Fatalf("DWS_ARGUMENTS not visible to the subprocess: %#v", out)
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/runner/ -run 'Argument|Script|Shell'`
Expected: FAIL — `undefined: shellArgv`, `undefined: scriptSource`.

- [ ] **Step 3: Implement argument rendering**

Create `dws-run/internal/runner/arguments.go`:

```go
package runner

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/dws/dws-run/internal/config"
)

// shellArgv renders the command plus its arguments for `sh -c`. Arguments are
// passed as sh's positional parameters rather than concatenated into the
// command string, so a value containing shell metacharacters cannot alter the
// command's structure.
//
//	sh -c '<command> "$@"' sh --env prod --region eu
func shellArgv(command string, args []config.Argument) []string {
	if len(args) == 0 {
		return []string{"-c", command}
	}
	argv := []string{"-c", command + ` "$@"`, "sh"}
	for _, a := range args {
		argv = append(argv, "--"+a.Name, stringify(a.Value))
	}
	return argv
}

// stringify renders an argument value for the shell. Scalars use their natural
// text form; objects and arrays are passed as compact JSON.
func stringify(v any) string {
	switch t := v.(type) {
	case nil:
		return ""
	case string:
		return t
	case bool:
		if t {
			return "true"
		}
		return "false"
	case float64:
		if t == float64(int64(t)) {
			return fmt.Sprintf("%d", int64(t))
		}
		return fmt.Sprintf("%v", t)
	default:
		b, err := json.Marshal(t)
		if err != nil {
			return fmt.Sprintf("%v", t)
		}
		return string(b)
	}
}

// scriptSource prepends a prelude that binds each argument as an in-scope
// variable, then the author's script. Values come in through the
// DWS_ARGUMENTS environment variable rather than being interpolated into the
// source, so quoting and JSON types are both preserved exactly.
func scriptSource(mode config.Mode, script string, args []config.Argument) (string, error) {
	for _, a := range args {
		if err := validIdentifier(a.Name); err != nil {
			return "", err
		}
	}

	var b strings.Builder
	switch mode {
	case config.ModeScriptJS:
		b.WriteString(`const __dwsArgs = JSON.parse(process.env.DWS_ARGUMENTS || "{}");` + "\n")
		for _, a := range args {
			fmt.Fprintf(&b, "const %s = __dwsArgs[%q];\n", a.Name, a.Name)
		}
	case config.ModeScriptPython:
		b.WriteString("import json as __dws_json, os as __dws_os\n")
		b.WriteString(`__dws_args = __dws_json.loads(__dws_os.environ.get("DWS_ARGUMENTS", "{}"))` + "\n")
		for _, a := range args {
			fmt.Fprintf(&b, "%s = __dws_args[%q]\n", a.Name, a.Name)
		}
	default:
		return "", fmt.Errorf("scriptSource called for non-script mode %q", mode)
	}
	b.WriteString(script)
	return b.String(), nil
}

// validIdentifier rejects argument names that are valid map keys but not valid
// JS/Python identifiers. dws-controller rejects these at compile time; this is
// the defense in depth for hand-written manifests.
func validIdentifier(name string) error {
	if name == "" {
		return fmt.Errorf("argument name must not be empty")
	}
	for i, r := range name {
		isLetter := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || r == '_'
		isDigit := r >= '0' && r <= '9'
		if isLetter || (i > 0 && isDigit) {
			continue
		}
		return fmt.Errorf("argument name %q is not a valid identifier", name)
	}
	return nil
}
```

- [ ] **Step 4: Wire it into the runner**

In `dws-run/internal/runner/runner.go`, replace the temporary `commandArgs` and extend
`subprocessEnv`:

```go
func (r *Runner) commandArgs() ([]string, error) {
	if r.cfg.Mode == config.ModeShell {
		return shellArgv(r.cfg.Command, r.cfg.Arguments), nil
	}
	src, err := scriptSource(r.cfg.Mode, r.cfg.Script, r.cfg.Arguments)
	if err != nil {
		return nil, err
	}
	return []string{"-c", src}, nil // node -c is not valid; see below
}
```

`node` uses `-e`, `python3` uses `-c`. Make the flag part of the interpreter choice:

```go
func interpreterFor(m config.Mode) (string, string) {
	switch m {
	case config.ModeScriptJS:
		return "node", "-e"
	case config.ModeScriptPython:
		return "python3", "-c"
	default:
		return "sh", "-c"
	}
}
```

Store both on `Runner` (`interpreter`, `evalFlag`), set them in `New`, and have `commandArgs`
return `[]string{r.evalFlag, src}` for script modes. `shellArgv` already emits its own `-c`, so
leave the shell branch as-is.

Then add the arguments env var in `subprocessEnv`:

```go
	if len(r.cfg.Arguments) > 0 {
		obj := make(map[string]any, len(r.cfg.Arguments))
		for _, a := range r.cfg.Arguments {
			obj[a.Name] = a.Value
		}
		if b, err := json.Marshal(obj); err == nil {
			env = append(env, "DWS_ARGUMENTS="+string(b))
		}
	}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd dws-run && go test -race ./internal/runner/`
Expected: PASS for every test except the Task 5 shaping assertion noted earlier.

- [ ] **Step 6: Commit**

```bash
git add dws-run/internal/runner/
git commit -m "feat(dws-run): render arguments as shell flags and script bindings"
```

---

## Task 4: RETURN selection and exit-code semantics

**Files:**
- Create: `dws-run/internal/runner/shape.go`
- Modify: `dws-run/internal/runner/runner.go` (remove the temporary `shape`)
- Test: `dws-run/internal/runner/shape_test.go`

**Interfaces:**
- Consumes: `result` and `ExitError` (Task 2), `config.ReturnMode` (Task 1).
- Produces: `(r *Runner) selectValue(res result) (any, bool, error)` returning the raw value and
  whether it is present (`false` for `RETURN=none`), and the updated
  `(r *Runner) shape(input map[string]any, res result) (any, error)`.

- [ ] **Step 1: Write the failing RETURN test**

Create `dws-run/internal/runner/shape_test.go`:

```go
package runner

import (
	"context"
	"errors"
	"testing"

	"github.com/dws/dws-run/internal/config"
)

func runWith(t *testing.T, command string, ret config.ReturnMode) (any, error) {
	t.Helper()
	cfg := shellCfg(command)
	cfg.Return = ret
	return New(cfg).Run(context.Background(), map[string]any{})
}

func TestReturnStdout(t *testing.T) {
	out, err := runWith(t, "printf 'hello'", config.ReturnStdout)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "hello" {
		t.Fatalf("got %#v, want \"hello\"", out)
	}
}

func TestReturnStderr(t *testing.T) {
	out, err := runWith(t, "printf 'oops' >&2", config.ReturnStderr)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "oops" {
		t.Fatalf("got %#v, want \"oops\"", out)
	}
}

func TestReturnCodeIsDataOnNonZeroExit(t *testing.T) {
	out, err := runWith(t, "exit 3", config.ReturnCode)
	if err != nil {
		t.Fatalf("RETURN=code must not error on a non-zero exit: %v", err)
	}
	if out != float64(3) && out != 3 {
		t.Fatalf("got %#v, want 3", out)
	}
}

func TestReturnAllIsDataOnNonZeroExit(t *testing.T) {
	out, err := runWith(t, "printf 'out'; printf 'err' >&2; exit 1", config.ReturnAll)
	if err != nil {
		t.Fatalf("RETURN=all must not error on a non-zero exit: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	if m["code"] != 1 && m["code"] != float64(1) {
		t.Errorf("code: got %#v, want 1", m["code"])
	}
	if m["stdout"] != "out" || m["stderr"] != "err" {
		t.Errorf("stdout/stderr: got %#v / %#v", m["stdout"], m["stderr"])
	}
}

func TestReturnNoneYieldsEmptyObject(t *testing.T) {
	out, err := runWith(t, "printf 'ignored'", config.ReturnNone)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok || len(m) != 0 {
		t.Fatalf("got %#v, want an empty object", out)
	}
}

func TestNonZeroExitIsAnErrorUnderStdout(t *testing.T) {
	_, err := runWith(t, "printf 'boom' >&2; exit 2", config.ReturnStdout)
	var exitErr *ExitError
	if !errors.As(err, &exitErr) {
		t.Fatalf("expected *ExitError, got %#v", err)
	}
	if exitErr.Code != 2 {
		t.Errorf("code: got %d, want 2", exitErr.Code)
	}
	if exitErr.Stderr != "boom" {
		t.Errorf("stderr: got %q, want \"boom\"", exitErr.Stderr)
	}
	if exitErr.Task != "t" {
		t.Errorf("task: got %q, want \"t\"", exitErr.Task)
	}
}

func TestNonZeroExitIsAnErrorUnderNone(t *testing.T) {
	_, err := runWith(t, "exit 1", config.ReturnNone)
	var exitErr *ExitError
	if !errors.As(err, &exitErr) {
		t.Fatalf("expected *ExitError, got %#v", err)
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/runner/ -run Return`
Expected: FAIL — every case returns raw stdout from the Task 2 placeholder.

- [ ] **Step 3: Implement RETURN selection and exit-code semantics**

Create `dws-run/internal/runner/shape.go` (`shapeOutput` lands in Task 5; for now `shape` returns
the selected value directly):

```go
package runner

import (
	"github.com/dws/dws-run/internal/config"
)

// exitCodeIsData reports whether a non-zero exit should be returned as data
// rather than as a retryable failure. RETURN=code and RETURN=all mean the
// author explicitly asked to observe the exit code, so turning it into a 502
// would both hide the requested value and trigger pointless retries.
func (r *Runner) exitCodeIsData() bool {
	return r.cfg.Return == config.ReturnCode || r.cfg.Return == config.ReturnAll
}

// selectValue picks the raw value per RETURN. The bool reports whether a value
// is present at all — false only for RETURN=none.
func (r *Runner) selectValue(res result) (any, bool) {
	switch r.cfg.Return {
	case config.ReturnStderr:
		return res.Stderr, true
	case config.ReturnCode:
		return res.Code, true
	case config.ReturnAll:
		return map[string]any{
			"code":   res.Code,
			"stdout": res.Stdout,
			"stderr": res.Stderr,
		}, true
	case config.ReturnNone:
		return nil, false
	default: // ReturnStdout
		return res.Stdout, true
	}
}

// shape turns the subprocess result into the value returned to the caller,
// enforcing the exit-code rule first.
func (r *Runner) shape(input map[string]any, res result) (any, error) {
	if res.Code != 0 && !r.exitCodeIsData() {
		return nil, &ExitError{Task: r.cfg.Task, Code: res.Code, Stderr: res.Stderr}
	}

	value, present := r.selectValue(res)
	if !present {
		return map[string]any{}, nil
	}
	return value, nil
}
```

Delete the temporary `shape` from `runner.go`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd dws-run && go test -race ./internal/runner/`
Expected: PASS for all `Return*` and `NonZeroExit*` tests.

- [ ] **Step 5: Commit**

```bash
git add dws-run/internal/runner/
git commit -m "feat(dws-run): select result by RETURN with exit-code semantics"
```

---

## Task 5: OUTPUT shaping and JSON fallback

**Files:**
- Modify: `dws-run/internal/runner/shape.go`
- Test: `dws-run/internal/runner/shape_test.go` (append)

**Interfaces:**
- Consumes: `selectValue` (Task 4).
- Produces: the final `shape` behavior — `OUTPUT=replace` returns the value (JSON-parsed when it
  came from stdout/stderr, raw string otherwise); `OUTPUT=merge` shallow-merges an object value
  into the input and errors when the value is not an object.

- [ ] **Step 1: Write the failing shaping test**

Append to `dws-run/internal/runner/shape_test.go`:

```go
func TestReplaceParsesJSONStdout(t *testing.T) {
	out, err := runWith(t, `printf '{"id":1}'`, config.ReturnStdout)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok || m["id"] != float64(1) {
		t.Fatalf("got %#v, want the parsed object {\"id\":1}", out)
	}
}

func TestReplaceFallsBackToRawString(t *testing.T) {
	out, err := runWith(t, "printf 'deployment complete'", config.ReturnStdout)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if out != "deployment complete" {
		t.Fatalf("got %#v, want the raw string", out)
	}
}

func TestMergeFoldsObjectIntoInput(t *testing.T) {
	cfg := shellCfg(`printf '{"b":2}'`)
	cfg.Output = config.OutputMerge
	out, err := New(cfg).Run(context.Background(), map[string]any{"a": float64(1)})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m, ok := out.(map[string]any)
	if !ok {
		t.Fatalf("expected an object, got %#v", out)
	}
	if m["a"] != float64(1) || m["b"] != float64(2) {
		t.Fatalf("got %#v, want both a=1 and b=2", m)
	}
}

func TestMergeRejectsNonObjectValue(t *testing.T) {
	cfg := shellCfg("printf 'plain text'")
	cfg.Output = config.OutputMerge
	_, err := New(cfg).Run(context.Background(), map[string]any{"a": float64(1)})
	if err == nil {
		t.Fatal("expected an error when merging a non-object value")
	}
	var exitErr *ExitError
	var spawnErr *SpawnError
	if errors.As(err, &exitErr) || errors.As(err, &spawnErr) {
		t.Fatalf("merge failure must not be retryable, got %#v", err)
	}
}
```

Also remove the `t.Skip` from `TestStdinReceivesFullInput` if you added one in Task 2 — `cat`
echoes a JSON object, which now parses back into `map[string]any`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/runner/ -run 'Replace|Merge'`
Expected: FAIL — `TestReplaceParsesJSONStdout` returns the raw string; the merge tests return
stdout unchanged.

- [ ] **Step 3: Implement OUTPUT shaping**

Replace `shape` in `dws-run/internal/runner/shape.go` and add the helpers:

```go
// shape turns the subprocess result into the value returned to the caller:
// RETURN picks the raw value, then OUTPUT decides how it folds into the
// response.
func (r *Runner) shape(input map[string]any, res result) (any, error) {
	if res.Code != 0 && !r.exitCodeIsData() {
		return nil, &ExitError{Task: r.cfg.Task, Code: res.Code, Stderr: res.Stderr}
	}

	value, present := r.selectValue(res)
	if !present {
		if r.cfg.Output == config.OutputMerge {
			return cloneInput(input), nil
		}
		return map[string]any{}, nil
	}

	// Text captured from stdout/stderr is JSON if it parses, and a plain
	// string otherwise. Unlike dws-call-http, unparseable output is not an
	// error — plain text is the normal output of a shell command.
	if s, ok := value.(string); ok {
		value = parseJSONOrString(s)
	}

	if r.cfg.Output == config.OutputMerge {
		obj, ok := value.(map[string]any)
		if !ok {
			return nil, fmt.Errorf("OUTPUT=merge requires a JSON object result, got %T", value)
		}
		merged := cloneInput(input)
		for k, v := range obj {
			merged[k] = v
		}
		return merged, nil
	}
	return value, nil
}

// parseJSONOrString returns the parsed JSON value when the trimmed text is
// valid JSON, and the original string otherwise.
func parseJSONOrString(s string) any {
	trimmed := strings.TrimSpace(s)
	if trimmed == "" {
		return s
	}
	var v any
	if err := json.Unmarshal([]byte(trimmed), &v); err != nil {
		return s
	}
	return v
}

func cloneInput(input map[string]any) map[string]any {
	out := make(map[string]any, len(input))
	for k, v := range input {
		out[k] = v
	}
	return out
}
```

Add `encoding/json`, `fmt`, and `strings` to the file's imports.

- [ ] **Step 4: Run the full package test**

Run: `cd dws-run && go test -race ./...`
Expected: PASS, every test in `config` and `runner`.

- [ ] **Step 5: Commit**

```bash
git add dws-run/internal/runner/
git commit -m "feat(dws-run): shape output with JSON fallback and merge support"
```

---

## Task 6: HTTP surface

**Files:**
- Create: `dws-run/internal/server/server.go`, `dws-run/main.go`
- Test: `dws-run/internal/server/server_test.go`

**Interfaces:**
- Consumes: `config.Config`, `runner.Runner`, `runner.ExitError`, `runner.SpawnError`.
- Produces: `server.New(config.Config, *runner.Runner, *slog.Logger) *Server` and
  `(*Server).Handler() http.Handler`.

- [ ] **Step 1: Write the failing server test**

Create `dws-run/internal/server/server_test.go`:

```go
package server

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

func handler(t *testing.T, command string, ret config.ReturnMode) http.Handler {
	t.Helper()
	cfg := config.Config{
		Mode: config.ModeShell, Task: "t", Command: command,
		Return: ret, Output: config.OutputReplace, Timeout: 10 * time.Second,
	}
	return New(cfg, runner.New(cfg), slog.New(slog.DiscardHandler)).Handler()
}

func do(t *testing.T, h http.Handler, method, path, body string) (*http.Response, string) {
	t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	res := rec.Result()
	b, _ := io.ReadAll(res.Body)
	return res, string(b)
}

func TestHealthz(t *testing.T) {
	res, body := do(t, handler(t, "true", config.ReturnStdout), http.MethodGet, "/healthz", "")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200", res.StatusCode)
	}
	if !strings.Contains(body, `"task":"t"`) {
		t.Errorf("body should name the task: %s", body)
	}
}

func TestEmptyBodyIsEmptyData(t *testing.T) {
	res, body := do(t, handler(t, "cat", config.ReturnStdout), http.MethodPost, "/run", "")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200 (body: %s)", res.StatusCode, body)
	}
}

func TestMalformedBodyIs400(t *testing.T) {
	res, _ := do(t, handler(t, "cat", config.ReturnStdout), http.MethodPost, "/run", "{not json")
	if res.StatusCode != http.StatusBadRequest {
		t.Fatalf("status: got %d, want 400", res.StatusCode)
	}
}

func TestNonZeroExitIs502UnderStdout(t *testing.T) {
	h := handler(t, "printf 'boom' >&2; exit 2", config.ReturnStdout)
	res, body := do(t, h, http.MethodPost, "/run", "{}")
	if res.StatusCode != http.StatusBadGateway {
		t.Fatalf("status: got %d, want 502", res.StatusCode)
	}
	var payload map[string]any
	if err := json.Unmarshal([]byte(body), &payload); err != nil {
		t.Fatalf("body is not JSON: %s", body)
	}
	if payload["exitCode"] != float64(2) {
		t.Errorf("exitCode: got %#v, want 2", payload["exitCode"])
	}
	if payload["stderr"] != "boom" {
		t.Errorf("stderr: got %#v, want \"boom\"", payload["stderr"])
	}
	if payload["task"] != "t" {
		t.Errorf("task: got %#v, want \"t\"", payload["task"])
	}
}

func TestNonZeroExitIs200UnderReturnCode(t *testing.T) {
	h := handler(t, "exit 2", config.ReturnCode)
	res, body := do(t, h, http.MethodPost, "/run", "{}")
	if res.StatusCode != http.StatusOK {
		t.Fatalf("status: got %d, want 200 (body: %s)", res.StatusCode, body)
	}
	if strings.TrimSpace(body) != "2" {
		t.Errorf("body: got %q, want \"2\"", strings.TrimSpace(body))
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-run && go test ./internal/server/`
Expected: FAIL — `undefined: New`.

- [ ] **Step 3: Implement the server**

Create `dws-run/internal/server/server.go`. This is `dws-call-http/internal/server/server.go` with
one difference — the error mapping names `ExitError`/`SpawnError` instead of
`UpstreamError`/`TransportError`, and an exit failure reports `exitCode` and `stderr`:

```go
// Package server exposes the step HTTP surface: POST /run and GET /healthz.
package server

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
)

// Server wires the runner and config to HTTP routes.
type Server struct {
	cfg    config.Config
	runner *runner.Runner
	log    *slog.Logger
}

// New constructs a Server.
func New(cfg config.Config, r *runner.Runner, log *slog.Logger) *Server {
	return &Server{cfg: cfg, runner: r, log: log}
}

// Handler returns the routed HTTP handler.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("POST /run", s.handleRun)
	mux.HandleFunc("GET /healthz", s.handleHealthz)
	return mux
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "task": s.cfg.Task})
}

// handleRun is the step entrypoint. The request body is the current workflow
// data (a JSON object); the response is the outcome shaped per RETURN/OUTPUT.
func (s *Server) handleRun(w http.ResponseWriter, req *http.Request) {
	input, err := decodeInput(req.Body)
	if err != nil {
		s.log.Warn("invalid request body", "task", s.cfg.Task, "err", err)
		s.writeJSON(w, http.StatusBadRequest, map[string]any{
			"task":  s.cfg.Task,
			"error": "invalid JSON body: " + err.Error(),
		})
		return
	}

	out, err := s.runner.Run(req.Context(), input)
	if err != nil {
		s.writeRunError(w, err)
		return
	}

	s.writeJSON(w, http.StatusOK, out)
}

// writeRunError maps runner failures to HTTP responses. Non-zero exits (where
// RETURN does not treat the code as data) and spawn failures become 502 so the
// orchestrator retries; configuration and shaping errors become 500, since
// retrying will not help.
func (s *Server) writeRunError(w http.ResponseWriter, err error) {
	var exitErr *runner.ExitError
	if errors.As(err, &exitErr) {
		s.log.Warn("subprocess exited non-zero", "task", exitErr.Task, "code", exitErr.Code)
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"task":     exitErr.Task,
			"exitCode": exitErr.Code,
			"stderr":   exitErr.Stderr,
		})
		return
	}

	var spawnErr *runner.SpawnError
	if errors.As(err, &spawnErr) {
		s.log.Error("spawn failure", "task", spawnErr.Task, "err", spawnErr.Err)
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"task":  spawnErr.Task,
			"error": spawnErr.Error(),
		})
		return
	}

	s.log.Error("run failed", "task", s.cfg.Task, "err", err)
	s.writeJSON(w, http.StatusInternalServerError, map[string]any{
		"task":  s.cfg.Task,
		"error": err.Error(),
	})
}

// decodeInput reads the request body as a JSON object. An empty body is
// treated as empty workflow data rather than an error.
func decodeInput(body io.Reader) (map[string]any, error) {
	dec := json.NewDecoder(body)
	var input map[string]any
	if err := dec.Decode(&input); err != nil {
		if errors.Is(err, io.EOF) {
			return map[string]any{}, nil
		}
		return nil, err
	}
	if input == nil {
		input = map[string]any{}
	}
	return input, nil
}

func (s *Server) writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		s.log.Error("write response failed", "task", s.cfg.Task, "err", err)
	}
}
```

- [ ] **Step 4: Write main.go**

Create `dws-run/main.go`, mirroring `dws-call-http/main.go`:

```go
// Command dws-run is the generic, prebuilt step image for `run: shell` and
// `run: script` tasks in the DWS platform. One binary serves all three images;
// MODE selects the interpreter and every other behavior is env-configured. It
// runs as a Knative service with a Dapr sidecar and is invoked by
// dws-orchestrator via Dapr service invocation.
package main

import (
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/dws/dws-run/internal/config"
	"github.com/dws/dws-run/internal/runner"
	"github.com/dws/dws-run/internal/server"
)

const readHeaderTimeout = 10 * time.Second

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		log.Error("invalid configuration", "err", err)
		os.Exit(1)
	}

	srv := server.New(cfg, runner.New(cfg), log)

	addr := ":" + cfg.Port
	log.Info("starting dws-run",
		"addr", addr,
		"task", cfg.Task,
		"mode", cfg.Mode,
		"return", cfg.Return,
		"output", cfg.Output,
		"timeout", cfg.Timeout.String(),
	)

	httpServer := &http.Server{
		Addr:              addr,
		Handler:           srv.Handler(),
		ReadHeaderTimeout: readHeaderTimeout,
	}

	if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Error("server terminated", "err", err)
		os.Exit(1)
	}
}
```

- [ ] **Step 5: Run the full suite**

Run: `cd dws-run && go build ./... && go test -race ./...`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add dws-run/internal/server/ dws-run/main.go
git commit -m "feat(dws-run): add POST /run and GET /healthz with 502 error mapping"
```

---

## Task 7: Packaging — Makefile, Dockerfiles, manifests, README

**Files:**
- Create: `dws-run/Makefile`, `dws-run/Dockerfile.shell`, `dws-run/Dockerfile.script-js`,
  `dws-run/Dockerfile.script-python`, `dws-run/k8s/knative-service.yaml`, `dws-run/README.md`

**Interfaces:**
- Consumes: the built binary from Task 6.
- Produces: `make build|test|vet|fmt-check|lint|docker-shell|docker-script-js|docker-script-python|clean`
  and three images whose only differences are the final-stage `FROM` and the `MODE` env.

- [ ] **Step 1: Write the Makefile**

Copy `dws-call-http/Makefile` to `dws-run/Makefile`, change `BINARY := dws-run`, and replace the
single `docker` target with three:

```make
REGISTRY ?= registry.io/dws
TAG      ?= 1.0

.PHONY: docker-shell docker-script-js docker-script-python docker

## docker-shell: build the shell runtime image
docker-shell:
	docker build -f Dockerfile.shell -t $(REGISTRY)/dws-run-shell:$(TAG) .

## docker-script-js: build the JavaScript runtime image
docker-script-js:
	docker build -f Dockerfile.script-js -t $(REGISTRY)/dws-run-script-js:$(TAG) .

## docker-script-python: build the Python runtime image
docker-script-python:
	docker build -f Dockerfile.script-python -t $(REGISTRY)/dws-run-script-python:$(TAG) .

## docker: build all three images
docker: docker-shell docker-script-js docker-script-python
```

- [ ] **Step 2: Write the three Dockerfiles**

`dws-run/Dockerfile.shell` — note the base is `busybox`, not `distroless/static`, because shell
mode needs a real `sh`:

```dockerfile
# syntax=docker/dockerfile:1

# --- build stage (identical across all three dws-run images) ---
FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
ENV CGO_ENABLED=0 GOOS=linux
RUN go build -trimpath -ldflags="-s -w" -o /out/dws-run .

# --- runtime stage ---
FROM busybox:stable-glibc
WORKDIR /
COPY --from=build /out/dws-run /dws-run
ENV MODE=shell
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/dws-run"]
```

`dws-run/Dockerfile.script-js` — identical build stage, `node:24-slim` runtime, `MODE=script-js`:

```dockerfile
# syntax=docker/dockerfile:1

# --- build stage (identical across all three dws-run images) ---
FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
ENV CGO_ENABLED=0 GOOS=linux
RUN go build -trimpath -ldflags="-s -w" -o /out/dws-run .

# --- runtime stage ---
FROM node:24-slim
WORKDIR /
COPY --from=build /out/dws-run /dws-run
ENV MODE=script-js
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/dws-run"]
```

`dws-run/Dockerfile.script-python` — identical build stage, `python:3.13-slim` runtime,
`MODE=script-python`:

```dockerfile
# syntax=docker/dockerfile:1

# --- build stage (identical across all three dws-run images) ---
FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
ENV CGO_ENABLED=0 GOOS=linux
RUN go build -trimpath -ldflags="-s -w" -o /out/dws-run .

# --- runtime stage ---
FROM python:3.13-slim
WORKDIR /
COPY --from=build /out/dws-run /dws-run
ENV MODE=script-python
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/dws-run"]
```

- [ ] **Step 3: Verify the Dockerfiles differ only where they should**

```bash
cd dws-run
diff Dockerfile.shell Dockerfile.script-js
diff Dockerfile.shell Dockerfile.script-python
```

Expected: exactly two differing lines per diff — the runtime-stage `FROM` and the `ENV MODE=` line.
If anything else differs, fix it; this is an acceptance criterion.

- [ ] **Step 4: Build all three images**

```bash
cd dws-run && make docker
```

Expected: three successful builds.

- [ ] **Step 5: Write the Knative manifests**

Create `dws-run/k8s/knative-service.yaml` with three `Service` documents separated by `---`, based
on `dws-call-http/k8s/knative-service.yaml`. Each carries `dws.io/step-type` (`run-shell`,
`run-script-js`, `run-script-python`), `dws.io/task`, the scale-to-zero and Dapr annotations, the
`/healthz` readiness and liveness probes, and the same resource requests/limits. Example for the
shell variant:

```yaml
# Example Knative Service for a single `run: shell` step.
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: sync-inventory
  labels:
    dws.io/step-type: run-shell
    dws.io/task: sync-inventory
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/minScale: "0"
        dapr.io/enabled: "true"
        dapr.io/app-id: "sync-inventory"
        dapr.io/app-port: "8080"
    spec:
      containers:
        - image: registry.io/dws/dws-run-shell:1.0
          ports:
            - name: http1
              containerPort: 8080
          env:
            - name: TASK
              value: "sync-inventory"
            - name: COMMAND
              value: "./sync.sh"
            # Optional knobs (defaults shown for reference):
            # - name: ARGUMENTS
            #   value: '{"env":"prod","region":"eu"}'   # JSON object, order preserved
            # - name: ENVIRONMENT
            #   value: '{"API_TOKEN":"..."}'
            # - name: RETURN
            #   value: "stdout"        # stdout | stderr | code | all | none
            # - name: OUTPUT
            #   value: "replace"       # replace | merge
            # - name: TIMEOUT
            #   value: "30s"
          readinessProbe:
            httpGet:
              path: /healthz
              port: 8080
          livenessProbe:
            httpGet:
              path: /healthz
              port: 8080
          resources:
            requests:
              cpu: "50m"
              memory: "32Mi"
            limits:
              cpu: "250m"
              memory: "128Mi"
```

- [ ] **Step 6: Write the README**

Create `dws-run/README.md` documenting: the three images and which DSL subtype each serves; the env
contract table (`MODE`, `TASK`, `PORT`, `COMMAND`, `SCRIPT`, `ARGUMENTS`, `ENVIRONMENT`, `RETURN`,
`OUTPUT`, `TIMEOUT`); that `ARGUMENTS` is a JSON **object** whose key order is preserved; how
arguments render per runtime (shell `--key value`; script in-scope variables via a generated
prelude, which shifts the author's script line numbers); the `RETURN` → `OUTPUT` composition; the
exit-code rule; and the build/test commands.

- [ ] **Step 7: Commit**

```bash
git add dws-run/Makefile dws-run/Dockerfile.* dws-run/k8s/ dws-run/README.md
git commit -m "build(dws-run): add Makefile, three Dockerfiles, manifests, and README"
```

---

## Task 8: CI workflow

**Files:**
- Create: `.github/workflows/dws-run.yml`

**Interfaces:**
- Consumes: the Makefile targets from Task 7.
- Produces: a path-filtered workflow gating on `go vet` + `go test`, building all three images on
  PRs without pushing, and pushing to `ghcr.io/tonylibs/` only on merge to `main`.

- [ ] **Step 1: Read the existing workflow**

```bash
cat .github/workflows/dws-call-http.yml
```

Match its structure — trigger shape, permissions, `docker/login-action` usage, and the
`github.event_name != 'pull_request'` push condition. Do not invent a different pattern.

- [ ] **Step 2: Write the workflow**

Create `.github/workflows/dws-run.yml` mirroring `dws-call-http.yml`, with:
- `on.push.paths` and `on.pull_request.paths` set to `dws-run/**` and
  `.github/workflows/dws-run.yml`.
- A `test` job running `go vet ./...` and `go test -race ./...` in `dws-run/`.
- A `build` job with a matrix over the three images:

```yaml
    strategy:
      matrix:
        include:
          - dockerfile: Dockerfile.shell
            image: dws-run-shell
          - dockerfile: Dockerfile.script-js
            image: dws-run-script-js
          - dockerfile: Dockerfile.script-python
            image: dws-run-script-python
```

  building with `context: dws-run`, `file: dws-run/${{ matrix.dockerfile }}`,
  `push: ${{ github.event_name != 'pull_request' && github.ref == 'refs/heads/main' }}`, and tags
  `ghcr.io/tonylibs/${{ matrix.image }}:latest` plus the commit SHA.

- [ ] **Step 3: Verify the workflow parses**

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/dws-run.yml')); print('ok')"
```

Expected: `ok`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/dws-run.yml
git commit -m "ci(dws-run): add path-filtered test and three-image build workflow"
```

---

## Task 9: Controller — split TaskKind, ImageCatalog, and config

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/model/TaskKind.java`
- Modify: `dws-controller/src/main/java/io/dws/controller/model/ImageCatalog.java`
- Modify: `dws-controller/src/main/java/io/dws/controller/config/DwsConfig.java`
- Modify: `dws-controller/src/main/resources/application.yaml`
- Modify: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java:19-21`

**Interfaces:**
- Consumes: nothing.
- Produces: `TaskKind.RUN_SHELL`, `TaskKind.RUN_SCRIPT_JS`, `TaskKind.RUN_SCRIPT_PYTHON`;
  `ImageCatalog(String callHttp, String callOpenapi, String runShell, String runScriptJs, String
  runScriptPython, String orchestrator)`; `DwsConfig.Images.runShell()`, `.runScriptJs()`,
  `.runScriptPython()`.

This task has no new test of its own — it is a mechanical widening that the existing suite must
survive. Tasks 10 and 11 add the behavioral tests.

- [ ] **Step 1: Widen TaskKind**

```java
/** Deployable task kinds that map to a prebuilt step image. */
public enum TaskKind {
  CALL_HTTP,
  CALL_OPENAPI,
  RUN_SHELL,
  RUN_SCRIPT_JS,
  RUN_SCRIPT_PYTHON
}
```

- [ ] **Step 2: Widen ImageCatalog**

```java
public record ImageCatalog(
    String callHttp,
    String callOpenapi,
    String runShell,
    String runScriptJs,
    String runScriptPython,
    String orchestrator) {}
```

- [ ] **Step 3: Widen DwsConfig.Images**

Replace `String run();` with the three accessors and update `catalog()`:

```java
  interface Images {
    String callHttp();

    String callOpenapi();

    String runShell();

    String runScriptJs();

    String runScriptPython();

    String orchestrator();
  }

  default ImageCatalog catalog() {
    return new ImageCatalog(
        images().callHttp(),
        images().callOpenapi(),
        images().runShell(),
        images().runScriptJs(),
        images().runScriptPython(),
        images().orchestrator());
  }
```

- [ ] **Step 4: Update application.yaml**

```yaml
  images:
    call-http: ghcr.io/tonylibs/dws-call-http:latest
    call-openapi: ghcr.io/tonylibs/dws-call-openapi:latest
    run-shell: ghcr.io/tonylibs/dws-run-shell:latest
    run-script-js: ghcr.io/tonylibs/dws-run-script-js:latest
    run-script-python: ghcr.io/tonylibs/dws-run-script-python:latest
    orchestrator: ghcr.io/tonylibs/dws-orchestrator:latest
```

- [ ] **Step 5: Update the test fixture catalog**

In `WorkflowCompilerTest`, replace the `IMAGES` constant:

```java
  private static final ImageCatalog IMAGES =
      new ImageCatalog(
          "sw-call-http:1.0",
          "sw-call-openapi:1.0",
          "sw-run-shell:1.0",
          "sw-run-script-js:1.0",
          "sw-run-script-python:1.0",
          "sw-orchestrator:1.0");
```

Then grep for other construction sites and fix each:

```bash
cd dws-controller && grep -rn "new ImageCatalog(" src/
```

- [ ] **Step 6: Compile and run the suite**

Run: `cd dws-controller && ./mvnw test`
Expected: PASS. `WorkflowCompiler.runStep()` still references `images.run()` — update that call to
`images.runShell()` temporarily so the module compiles; Task 10 rewrites the method properly.

- [ ] **Step 7: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/model/TaskKind.java \
        dws-controller/src/main/java/io/dws/controller/model/ImageCatalog.java \
        dws-controller/src/main/java/io/dws/controller/config/DwsConfig.java \
        dws-controller/src/main/resources/application.yaml \
        dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java \
        dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java
git commit -m "refactor(dws-controller): split RUN task kind and image catalog three ways"
```

---

## Task 10: Controller — compile `run.shell`

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java:214-228`
- Create: `dws-controller/src/test/resources/fixtures/run-shell.yaml`
- Modify: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Consumes: `TaskKind.RUN_SHELL`, `ImageCatalog.runShell()` (Task 9).
- Produces: `runStep(String taskName, RunTask run)` handling the shell branch, plus the private
  helpers `argumentsJson(Map<String,Object>)`, `environmentJson(Map<String,Object>)`, and
  `returnValue(RunTaskConfiguration)` that Task 11 reuses.

- [ ] **Step 1: Write the failing test and fixture**

Create `dws-controller/src/test/resources/fixtures/run-shell.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: default
  name: shellflow
  version: '1.0.0'
do:
  - syncInventory:
      run:
        shell:
          command: ./sync.sh
          arguments:
            env: prod
            region: eu
          environment:
            API_TOKEN: abc
```

Add to `WorkflowCompilerTest`:

```java
  @Test
  @DisplayName("run.shell compiles to a RUN_SHELL step with ordered arguments")
  void runShellCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-shell.yaml"));

    assertThat(plan.steps()).hasSize(1);
    StepService step = plan.steps().get(0);
    assertThat(step.name()).isEqualTo("sync-inventory");
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SHELL);
    assertThat(step.image()).isEqualTo("sw-run-shell:1.0");
    assertThat(step.env())
        .containsEntry("COMMAND", "./sync.sh")
        .containsEntry("ENVIRONMENT", "{\"API_TOKEN\":\"abc\"}")
        .containsEntry("RETURN", "stdout");
    // ARGUMENTS must be a JSON object with keys in definition order.
    assertThat(step.env().get("ARGUMENTS")).isEqualTo("{\"env\":\"prod\",\"region\":\"eu\"}");
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest#runShellCompiles`
Expected: FAIL — `ARGUMENTS` and `ENVIRONMENT` are absent, `RETURN` is absent.

- [ ] **Step 3: Implement the shell branch**

Replace `runStep` in `WorkflowCompiler.java`:

```java
  private StepService runStep(String taskName, RunTask run) {
    RunTaskConfigurationUnion cfg = run.getRun();
    if (cfg == null) {
      throw new CompilationException("task '" + taskName + "': run task has no configuration");
    }

    if (cfg.getRunShell() != null) {
      RunShell runShell = cfg.getRunShell();
      Shell shell = runShell.getShell();
      Map<String, String> env = new LinkedHashMap<>();
      putIfPresent(env, "COMMAND", shell.getCommand());
      if (shell.getArguments() != null) {
        putIfPresent(env, "ARGUMENTS", toJson(shell.getArguments().getAdditionalProperties()));
      }
      if (shell.getEnvironment() != null) {
        putIfPresent(env, "ENVIRONMENT", toJson(shell.getEnvironment().getAdditionalProperties()));
      }
      env.put("RETURN", returnValue(runShell));
      return new StepService(Names.kebab(taskName), TaskKind.RUN_SHELL, images.runShell(), env);
    }

    throw new CompilationException(
        "task '" + taskName + "': unsupported run configuration (script handled in Task 11)");
  }

  /**
   * Resolves the DSL's process return type, defaulting to {@code stdout} so the deployed step's
   * behavior is explicit in its manifest rather than dependent on an image default.
   */
  private static String returnValue(RunTaskConfiguration cfg) {
    return cfg.getReturn() != null
        ? cfg.getReturn().value()
        : RunTaskConfiguration.ProcessReturnType.STDOUT.value();
  }
```

Add imports: `io.serverlessworkflow.api.types.RunShell`, `...RunScript`, `...RunTaskConfiguration`,
`...Script`, `...ScriptUnion`, `...Shell`.

Check `toJson`'s existing implementation preserves `LinkedHashMap` order — Jackson does by default
for maps, and `getAdditionalProperties()` is a `LinkedHashMap` populated in document order. If
`toJson` sorts keys (it may, given the canonicalization used for versioning), add a dedicated
`toOrderedJson` that does not.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest#runShellCompiles`
Expected: PASS.

- [ ] **Step 5: Run the whole suite**

Run: `cd dws-controller && ./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java \
        dws-controller/src/test/resources/fixtures/run-shell.yaml \
        dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "feat(dws-controller): compile run.shell to a RUN_SHELL step service"
```

---

## Task 11: Controller — compile `run.script` and reject unsupported subtypes

**Files:**
- Modify: `dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java`
- Create: `dws-controller/src/test/resources/fixtures/run-script-js.yaml`,
  `run-script-python.yaml`, `run-script-bad-language.yaml`, `run-script-source.yaml`,
  `run-container.yaml`, `run-workflow.yaml`
- Modify: `dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java`

**Interfaces:**
- Consumes: `returnValue`, `argumentsJson`, `environmentJson` (Task 10).
- Produces: the complete `runStep` covering script compilation and all five rejection paths.

- [ ] **Step 1: Write the fixtures**

`run-script-js.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: default
  name: jsflow
  version: '1.0.0'
do:
  - transformOrder:
      run:
        script:
          language: js
          code: "console.log(JSON.stringify({ok: true}));"
          arguments:
            count: 3
        return: all
```

`run-script-python.yaml` — same shape with `language: python`, `code: "print(1)"`, and no `return`.

`run-script-bad-language.yaml` — `language: ruby`, `code: "puts 1"`.

`run-script-source.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: default
  name: srcflow
  version: '1.0.0'
do:
  - remoteScript:
      run:
        script:
          language: js
          source:
            endpoint: https://example.test/script.js
```

`run-container.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: default
  name: containerflow
  version: '1.0.0'
do:
  - buildImage:
      run:
        container:
          image: alpine:3
          command: echo hi
```

`run-workflow.yaml`:

```yaml
document:
  dsl: '1.0.0'
  namespace: default
  name: subflow
  version: '1.0.0'
do:
  - callChild:
      run:
        workflow:
          namespace: default
          name: order
          version: '1.0.0'
```

- [ ] **Step 2: Write the failing tests**

Add to `WorkflowCompilerTest`:

```java
  @Test
  @DisplayName("run.script with language js compiles to a RUN_SCRIPT_JS step")
  void runScriptJsCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-script-js.yaml"));

    StepService step = plan.steps().get(0);
    assertThat(step.name()).isEqualTo("transform-order");
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SCRIPT_JS);
    assertThat(step.image()).isEqualTo("sw-run-script-js:1.0");
    assertThat(step.env())
        .containsEntry("SCRIPT", "console.log(JSON.stringify({ok: true}));")
        .containsEntry("ARGUMENTS", "{\"count\":3}")
        .containsEntry("RETURN", "all");
    assertThat(step.env()).doesNotContainKey("LANGUAGE");
  }

  @Test
  @DisplayName("run.script with language python defaults RETURN to stdout")
  void runScriptPythonCompiles() {
    DeploymentPlan plan = compiler.compile(fixture("run-script-python.yaml"));

    StepService step = plan.steps().get(0);
    assertThat(step.kind()).isEqualTo(TaskKind.RUN_SCRIPT_PYTHON);
    assertThat(step.image()).isEqualTo("sw-run-script-python:1.0");
    assertThat(step.env()).containsEntry("RETURN", "stdout");
  }

  @Test
  @DisplayName("run.script with an unsupported language is rejected")
  void runScriptUnsupportedLanguageRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-script-bad-language.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("ruby");
  }

  @Test
  @DisplayName("run.script with an external source is rejected")
  void runScriptExternalSourceRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-script-source.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("external script");
  }

  @Test
  @DisplayName("run.container is rejected with a clear message")
  void runContainerRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-container.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("container");
  }

  @Test
  @DisplayName("run.workflow is rejected with a clear message")
  void runWorkflowRejected() {
    assertThatThrownBy(() -> compiler.compile(fixture("run-workflow.yaml")))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("workflow");
  }

  @Test
  @DisplayName("an argument name that is not a valid identifier is rejected for script tasks")
  void runScriptInvalidArgumentNameRejected() {
    String yaml =
        """
        document:
          dsl: '1.0.0'
          namespace: default
          name: badargs
          version: '1.0.0'
        do:
          - transformOrder:
              run:
                script:
                  language: js
                  code: "1"
                  arguments:
                    has-dash: 1
        """;
    assertThatThrownBy(() -> compiler.compile(yaml))
        .isInstanceOf(CompilationException.class)
        .hasMessageContaining("has-dash");
  }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest`
Expected: FAIL — the script tests hit the Task 10 placeholder throw; the container and workflow
tests fail on the message assertion.

- [ ] **Step 4: Implement the script branch and the rejections**

Replace the placeholder throw at the end of `runStep`:

```java
    if (cfg.getRunScript() != null) {
      return scriptStep(taskName, cfg.getRunScript());
    }
    if (cfg.getRunContainer() != null) {
      throw new CompilationException(
          "task '" + taskName + "': run: container is not yet supported");
    }
    if (cfg.getRunWorkflow() != null) {
      throw new CompilationException(
          "task '" + taskName + "': run: workflow is not yet supported");
    }
    throw new CompilationException("task '" + taskName + "': unrecognized run configuration");
  }

  private StepService scriptStep(String taskName, RunScript runScript) {
    ScriptUnion union = runScript.getScript();
    if (union == null) {
      throw new CompilationException("task '" + taskName + "': run.script has no configuration");
    }
    if (union.getExternalScript() != null) {
      throw new CompilationException(
          "task '"
              + taskName
              + "': run.script external script sources are not supported; use inline 'code'");
    }
    InlineScript inline = union.getInlineScript();
    if (inline == null) {
      throw new CompilationException("task '" + taskName + "': run.script requires inline 'code'");
    }

    String language = inline.getLanguage() == null ? "" : inline.getLanguage().toLowerCase();
    TaskKind kind;
    String image;
    switch (language) {
      case "js" -> {
        kind = TaskKind.RUN_SCRIPT_JS;
        image = images.runScriptJs();
      }
      case "python" -> {
        kind = TaskKind.RUN_SCRIPT_PYTHON;
        image = images.runScriptPython();
      }
      default ->
          throw new CompilationException(
              "task '"
                  + taskName
                  + "': run.script language '"
                  + inline.getLanguage()
                  + "' is not supported; use 'js' or 'python'");
    }

    Map<String, String> env = new LinkedHashMap<>();
    putIfPresent(env, "SCRIPT", inline.getCode());
    if (inline.getArguments() != null) {
      Map<String, Object> args = inline.getArguments().getAdditionalProperties();
      args.keySet().forEach(name -> requireIdentifier(taskName, name));
      putIfPresent(env, "ARGUMENTS", toJson(args));
    }
    if (inline.getEnvironment() != null) {
      putIfPresent(env, "ENVIRONMENT", toJson(inline.getEnvironment().getAdditionalProperties()));
    }
    env.put("RETURN", returnValue(runScript));

    return new StepService(Names.kebab(taskName), kind, image, env);
  }

  /**
   * Script arguments become in-scope variables in the generated prelude, so a name that is a valid
   * map key but not a valid identifier would produce a syntax error inside a deployed container.
   * Reject it at post time instead.
   */
  private static void requireIdentifier(String taskName, String name) {
    if (name == null || name.isEmpty() || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new CompilationException(
          "task '" + taskName + "': argument name '" + name + "' is not a valid identifier");
    }
  }
```

Add the import for `io.serverlessworkflow.api.types.InlineScript`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd dws-controller && ./mvnw test -Dtest=WorkflowCompilerTest`
Expected: PASS, all cases.

- [ ] **Step 6: Verify no partial stack is emitted on rejection**

Confirm each rejection test's exception escapes `compile()` before any `StepService` is added —
`runStep` is called inside the task loop and throws before `steps.add(...)` completes, so no
partial plan is returned. If the loop catches exceptions anywhere, fix it so it does not.

- [ ] **Step 7: Commit**

```bash
git add dws-controller/src/main/java/io/dws/controller/compile/WorkflowCompiler.java \
        dws-controller/src/test/resources/fixtures/ \
        dws-controller/src/test/java/io/dws/controller/compile/WorkflowCompilerTest.java
git commit -m "feat(dws-controller): compile run.script and reject unsupported run subtypes"
```

---

## Task 12: Documentation and final gate

**Files:**
- Modify: `CLAUDE.md`, `README.md`

**Interfaces:**
- Consumes: everything above.
- Produces: docs that match the shipped behavior, and a green full gate.

- [ ] **Step 1: Update the root CLAUDE.md**

Add `dws-run` to the component table:

```markdown
| [`dws-run`](dws-run) | Go 1.26 | Prebuilt step images for `run: shell` and `run: script` tasks. One codebase produces three images (`dws-run-shell`, `dws-run-script-js`, `dws-run-script-python`) differing only in base layer and interpreter. |
```

Update the "Task → resource mapping" bullet so `run` names the three images, and add the component's
commands to the Commands section:

```markdown
### dws-run (Go 1.26)

```shell
cd dws-run
make build          # compile bin/dws-run
make test           # go test -race ./...
make lint           # vet + gofmt check
make docker         # build all three images
```
```

- [ ] **Step 2: Update the root README deployment diagram**

Check whether `README.md` enumerates step images; if it does, add the three `dws-run` images
alongside `dws-call-http` and `dws-call-openapi`.

```bash
grep -n "dws-call-http" README.md
```

- [ ] **Step 3: Confirm the orchestrator is untouched**

```bash
git diff --stat main -- dws-orchestrator/
```

Expected: empty output. A non-empty diff means an assumption in `design.md` (D8) was wrong — stop
and reconcile before proceeding.

- [ ] **Step 4: Run the full gate**

```bash
cd dws-run && make test && make lint
cd ../dws-controller && ./mvnw test
```

Expected: both green. Record the actual output — do not claim completion without it.

- [ ] **Step 5: Validate the change artifacts**

```bash
openspec validate --all --json
```

Expected: `"valid": true` for the `dws-run` change.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document dws-run component and run task mapping"
```
