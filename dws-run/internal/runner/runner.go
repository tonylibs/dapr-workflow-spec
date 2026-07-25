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
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/dws/dws-run/internal/config"
)

// killDrainDelay bounds how long Wait() will wait for stdout/stderr capture
// to drain after the process group has been killed. Without it, a subprocess
// that forks a further child inheriting the output pipes (e.g. `sh -c`
// spawning a grandchild) can leave that pipe's write end open after the
// direct child is gone, and Wait would otherwise block until the orphan
// exits on its own.
const killDrainDelay = 2 * time.Second

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
	// evalFlag is the interpreter flag that introduces an inline source
	// string (e.g. "-c" for sh/python3, "-e" for node).
	evalFlag string
}

// New builds a Runner for the configured mode.
func New(cfg config.Config) *Runner {
	interpreter, evalFlag := interpreterFor(cfg.Mode)
	return &Runner{cfg: cfg, interpreter: interpreter, evalFlag: evalFlag}
}

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

	// Run the subprocess in its own process group so a timeout or context
	// cancellation can kill it and any children it spawned, rather than just
	// the immediate child (which may leave grandchildren running and holding
	// the output pipes open).
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	// killedByUs records whether our own Cancel hook actually fired. cmd.Run()
	// alone can't tell "we killed it" apart from "it happened to exit
	// non-zero right around when the deadline elapsed" — both produce a real
	// *exec.ExitError. Cancel runs on a goroutine managed by os/exec, so this
	// must be set race-safely.
	var killedByUs atomic.Bool
	cmd.Cancel = func() error {
		killedByUs.Store(true)
		return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	}
	cmd.WaitDelay = killDrainDelay

	var stdout, stderr bytes.Buffer
	cmd.Stdin = bytes.NewReader(body) // exec closes stdin at EOF
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	runErr := cmd.Run()
	res := result{
		Code: cmd.ProcessState.ExitCode(),
		// Trim only the trailing newline(s) shell commands almost always
		// emit (e.g. `echo`) — not leading whitespace or interior blank
		// lines, and not with TrimSpace, since a script that deliberately
		// emits trailing spaces should keep them. Applied identically to
		// stdout and stderr so RETURN=all and ExitError.Stderr don't end up
		// with one trimmed and the other not.
		Stdout: strings.TrimRight(stdout.String(), "\n"),
		Stderr: strings.TrimRight(stderr.String(), "\n"),
	}

	if runErr != nil {
		var exitErr *exec.ExitError
		if !errors.As(runErr, &exitErr) {
			// Never started at all.
			return result{}, &SpawnError{Task: r.cfg.Task, Err: runErr}
		}
		if killedByUs.Load() {
			// Our Cancel hook fired, so this *exec.ExitError reflects the
			// SIGKILL we sent, not a genuine exit. Distinguish why: our own
			// TIMEOUT deadline elapsed, versus the caller's context being
			// canceled for some unrelated reason (e.g. an upstream
			// disconnect once this is wired into a server).
			if errors.Is(ctx.Err(), context.DeadlineExceeded) {
				return result{}, &SpawnError{Task: r.cfg.Task, Err: fmt.Errorf("timed out after %s", r.cfg.Timeout)}
			}
			return result{}, &SpawnError{Task: r.cfg.Task, Err: errors.New("canceled before completion")}
		}
		// killedByUs was never set: this *exec.ExitError is a genuine
		// non-zero exit, not something we caused. Fall through and return
		// the populated result with its real code and captured output —
		// shape() (Task 4) decides whether that's a failure.
	}
	return res, nil
}

// subprocessEnv extends the service's own environment so PATH and interpreter
// discovery keep working; ENVIRONMENT entries win on conflict. Arguments (if
// any) are also exposed as DWS_ARGUMENTS, a JSON object, so script preludes
// can bind them without interpolating values into generated source.
func (r *Runner) subprocessEnv() []string {
	env := os.Environ()
	for k, v := range r.cfg.Environment {
		env = append(env, k+"="+v)
	}
	if len(r.cfg.Arguments) > 0 {
		obj := make(map[string]any, len(r.cfg.Arguments))
		for _, a := range r.cfg.Arguments {
			obj[a.Name] = a.Value
		}
		if b, err := json.Marshal(obj); err == nil {
			env = append(env, "DWS_ARGUMENTS="+string(b))
		}
	}
	return env
}

// commandArgs builds the interpreter arguments for the configured mode: shell
// arguments are rendered as sh positional parameters (shellArgv), script
// arguments are bound via a generated prelude (scriptSource) and run with the
// interpreter's own eval flag (evalFlag, from interpreterFor).
func (r *Runner) commandArgs() ([]string, error) {
	if r.cfg.Mode == config.ModeShell {
		return shellArgv(r.cfg.Command, r.cfg.Arguments), nil
	}
	src, err := scriptSource(r.cfg.Mode, r.cfg.Script, r.cfg.Arguments)
	if err != nil {
		return nil, err
	}
	return []string{r.evalFlag, src}, nil
}

// shape is a placeholder: Task 4 adds RETURN selection and exit-code
// semantics, Task 5 adds OUTPUT shaping.
func (r *Runner) shape(_ map[string]any, res result) (any, error) {
	return res.Stdout, nil
}
