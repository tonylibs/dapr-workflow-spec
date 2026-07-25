package runner

import (
	"context"
	"os"
	"path/filepath"
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

// TestShellArgumentValueIsNeverExecuted demonstrates the actual danger the
// metacharacter guard exists for, without the `#` workaround the assertion
// above needs: a naive string-concatenating shellArgv would let this payload
// break out and genuinely run `touch` on the filesystem. The command here
// (`echo`) tolerates the shellArgv-appended `"$@"` operands instead of
// re-applying a format string over them (unlike printf), so it doesn't need
// a `#` to stay clean — this test asserts on the filesystem, not stdout.
func TestShellArgumentValueIsNeverExecuted(t *testing.T) {
	dir := t.TempDir()
	marker := filepath.Join(dir, "pwned")

	cfg := shellCfg(`echo "$2"`)
	cfg.Arguments = []config.Argument{{Name: "payload", Value: "; touch " + marker}}
	if _, err := New(cfg).Run(context.Background(), map[string]any{}); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if _, err := os.Stat(marker); !os.IsNotExist(err) {
		t.Fatalf("marker file exists — the argument value was executed as shell code, not passed as data (stat err: %v)", err)
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

func TestScriptSourceRejectsReservedWordsPerLanguage(t *testing.T) {
	// A name that's a charset-valid identifier can still be unusable as a
	// binding in the target language: `const const = ...;` and
	// `class = ...` are both SyntaxErrors. Reserved-ness is per language, so
	// a name invalid in one script mode may be perfectly fine in the other.
	jsReserved := []string{"const", "class", "for", "import", "with", "in", "let", "function", "return"}
	for _, name := range jsReserved {
		if _, err := scriptSource(config.ModeScriptJS, "x", []config.Argument{{Name: name}}); err == nil {
			t.Errorf("expected scriptSource(js) to reject reserved word %q", name)
		}
		// Not necessarily reserved in Python — only assert the ones that
		// genuinely aren't Python keywords stay accepted there.
	}
	if _, err := scriptSource(config.ModeScriptPython, "x", []config.Argument{{Name: "class"}}); err == nil {
		t.Errorf(`expected scriptSource(python) to reject reserved word "class"`)
	}

	pyReserved := []string{"def", "class", "import", "from", "with", "in", "True", "False", "None", "lambda", "pass", "global", "nonlocal"}
	for _, name := range pyReserved {
		if _, err := scriptSource(config.ModeScriptPython, "x", []config.Argument{{Name: name}}); err == nil {
			t.Errorf("expected scriptSource(python) to reject reserved word %q", name)
		}
	}

	// A word reserved in JS but not in Python (and vice versa) must still be
	// usable where it isn't reserved.
	if _, err := scriptSource(config.ModeScriptPython, "class", []config.Argument{{Name: "const"}}); err != nil {
		t.Errorf(`"const" is not a Python keyword, expected it to be accepted: %v`, err)
	}
	if _, err := scriptSource(config.ModeScriptJS, "None", []config.Argument{{Name: "None"}}); err != nil {
		t.Errorf(`"None" is not a JS reserved word, expected it to be accepted: %v`, err)
	}
}

func TestScriptSourceRejectsInternalPreludeNames(t *testing.T) {
	for _, mode := range []config.Mode{config.ModeScriptJS, config.ModeScriptPython} {
		for _, name := range []string{"__dwsArgs", "__dws_args", "__dws_json", "__dws_os"} {
			if _, err := scriptSource(mode, "x", []config.Argument{{Name: name}}); err == nil {
				t.Errorf("expected scriptSource(%s) to reject internal name %q", mode, name)
			}
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
