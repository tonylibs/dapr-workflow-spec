package config

import (
	"encoding/json"
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
	if c.Arguments[2].Value != json.Number("3") {
		t.Errorf("count: got %#v, want json.Number(3)", c.Arguments[2].Value)
	}
}

// TestLargeIntegerArgumentsSurviveExactly guards against a regression where
// normalize() converted json.Number to float64, which has only 53 bits of
// integer precision. That silently corrupted values beyond 2^53 (order IDs,
// Snowflake IDs, ns timestamps) — and did so only for top-level argument
// values, since nested numbers were never normalized, producing two different
// renderings of the identical value in the same payload.
func TestLargeIntegerArgumentsSurviveExactly(t *testing.T) {
	const big = "12345678901234567890"
	c, err := load(t, map[string]string{
		"MODE": "shell", "COMMAND": "x",
		"ARGUMENTS": `{"orderId":` + big + `,"nested":{"orderId":` + big + `}}`,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var top any
	for _, a := range c.Arguments {
		if a.Name == "orderId" {
			top = a.Value
		}
	}
	n, ok := top.(json.Number)
	if !ok {
		t.Fatalf("top-level orderId: got %#v (%T), want json.Number", top, top)
	}
	if n.String() != big {
		t.Errorf("top-level orderId: got %s, want %s", n.String(), big)
	}

	var nested any
	for _, a := range c.Arguments {
		if a.Name == "nested" {
			nested = a.Value
		}
	}
	nestedMap, ok := nested.(map[string]any)
	if !ok {
		t.Fatalf("nested: got %#v (%T), want map[string]any", nested, nested)
	}
	nestedNum, ok := nestedMap["orderId"].(json.Number)
	if !ok {
		t.Fatalf("nested orderId: got %#v (%T), want json.Number", nestedMap["orderId"], nestedMap["orderId"])
	}
	if nestedNum.String() != big {
		t.Errorf("nested orderId: got %s, want %s", nestedNum.String(), big)
	}
	if n.String() != nestedNum.String() {
		t.Errorf("top-level and nested renderings diverge: %s vs %s", n.String(), nestedNum.String())
	}
}

// TestLoadRejectsBadArgumentNamesAtStartup guards run-step-configuration/
// spec.md's requirement that invalid config fail at startup rather than at
// first invocation: a bad argument name must not make it past Load, or the
// process starts successfully and the failure only surfaces (as a retryable
// 502, forever) on the first /run request.
func TestLoadRejectsBadArgumentNamesAtStartup(t *testing.T) {
	cases := []struct {
		name string
		mode string
		arg  string
	}{
		{"JS-reserved word under script-js", "script-js", `{"const":1}`},
		{"Python-reserved word under script-python", "script-python", `{"def":1}`},
		{"internal prelude name", "script-js", `{"__dwsArgs":1}`},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := load(t, map[string]string{
				"MODE": tc.mode, "SCRIPT": "x", "ARGUMENTS": tc.arg,
			})
			if err == nil {
				t.Fatalf("expected Load to reject %s under MODE=%s", tc.arg, tc.mode)
			}
		})
	}
}

// TestLoadAcceptsLanguageSpecificallyLegalNames confirms the flip side of the
// above: a name reserved in one script language but not the other must still
// be accepted where it's legal (e.g. "None" is a Python keyword but a fine JS
// identifier; "const" is a JS keyword but a fine Python identifier).
func TestLoadAcceptsLanguageSpecificallyLegalNames(t *testing.T) {
	if _, err := load(t, map[string]string{
		"MODE": "script-js", "SCRIPT": "x", "ARGUMENTS": `{"None":1}`,
	}); err != nil {
		t.Errorf(`"None" is not a JS reserved word, expected Load to accept it: %v`, err)
	}
	if _, err := load(t, map[string]string{
		"MODE": "script-python", "SCRIPT": "x", "ARGUMENTS": `{"const":1}`,
	}); err != nil {
		t.Errorf(`"const" is not a Python keyword, expected Load to accept it: %v`, err)
	}
}

// TestLoadDoesNotValidateIdentifiersForShellMode confirms shell arguments
// (rendered as `--name value` flags, not bound as language identifiers) are
// exempt from identifier validation.
func TestLoadDoesNotValidateIdentifiersForShellMode(t *testing.T) {
	if _, err := load(t, map[string]string{
		"MODE": "shell", "COMMAND": "x", "ARGUMENTS": `{"const":1,"has-dash":2}`,
	}); err != nil {
		t.Errorf("shell mode should not validate argument names as identifiers: %v", err)
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
