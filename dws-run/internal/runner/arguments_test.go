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
	// shellArgv appends a literal `"$@"` after the configured command (see
	// TestShellArgvPreservesOrderAndUsesPositionalParams) so that a command
	// which is itself a script/program can forward the flags to its own
	// argument parsing. A trailing `#` here comments out that appended
	// suffix so this one-liner isn't also handed to printf as extra operands
	// (which would make printf re-apply its format string) — it does not
	// weaken what's under test: the payload still reaches the command only
	// through the quoted "$2" positional parameter.
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
