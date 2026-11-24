package expr

import (
	"errors"
	"reflect"
	"testing"

	"github.com/itchyny/gojq"
)

func mustParse(t *testing.T, src string) *gojq.Query {
	t.Helper()
	q, err := gojq.Parse(src)
	if err != nil {
		t.Fatalf("gojq.Parse(%q) error = %v", src, err)
	}
	return q
}

func TestCheck_blocked(t *testing.T) {
	tests := []struct {
		src     string
		builtin string
	}{
		{src: `env.HOME`, builtin: "env"},
		{src: `$ENV.HOME`, builtin: "$ENV"},
		{src: `{$ENV}`, builtin: "$ENV"},
		{src: `. as [$ENV] | 1`, builtin: "$ENV"},
		{src: `. as {$ENV} | 1`, builtin: "$ENV"},
		{src: `def f($ENV): 1; f(.)`, builtin: "$ENV"},
		{src: `input`, builtin: "input"},
		{src: `[inputs]`, builtin: "inputs"},
		{src: `input_filename`, builtin: "input_filename"},
		{src: `halt`, builtin: "halt"},
		{src: `"x" | halt_error`, builtin: "halt_error"},
		{src: `halt_error(1)`, builtin: "halt_error"},
		{src: `debug`, builtin: "debug"},
		{src: `debug("m")`, builtin: "debug"},
		{src: `stderr`, builtin: "stderr"},
		{src: `import "a" as s; .`, builtin: "import"},
		{src: `include "h"; .`, builtin: "include"},
		// Nested positions.
		{src: `def f: debug; f`, builtin: "debug"},
		{src: `reduce .[] as $x (0; . + ($x | env | length))`, builtin: "env"},
		{src: `foreach .[] as $x (0; input; .)`, builtin: "input"},
		{src: `try halt catch .`, builtin: "halt"},
		{src: `if .a then stderr else . end`, builtin: "stderr"},
		{src: `{a: (. | debug)}`, builtin: "debug"},
		{src: `"\(env)"`, builtin: "env"},
		{src: `label $out | inputs`, builtin: "inputs"},
		{src: `.[] | select(. == (input))`, builtin: "input"},
		// Name-based: a user definition of a blocked name is rejected too.
		{src: `def debug: .; debug`, builtin: "debug"},
	}
	for _, tt := range tests {
		t.Run(tt.src, func(t *testing.T) {
			names, err := Check(mustParse(t, tt.src))
			var policy *PolicyError
			if !errors.As(err, &policy) {
				t.Fatalf("Check(%q) error = %v, want *PolicyError", tt.src, err)
			}
			if policy.Builtin != tt.builtin {
				t.Errorf("Check(%q) Builtin = %q, want %q", tt.src, policy.Builtin, tt.builtin)
			}
			if names != nil {
				t.Errorf("Check(%q) names = %#v, want nil", tt.src, names)
			}
		})
	}
}

func TestPolicyError_message(t *testing.T) {
	err := &PolicyError{Builtin: "env"}
	if got, want := err.Error(), `blocked builtin "env"`; got != want {
		t.Errorf("Error() = %q, want %q", got, want)
	}
}

func TestCheck_allowed(t *testing.T) {
	tests := []string{
		`.env`,
		`{env: 1}`,
		`{debug: .x}`,
		`"input"`,
		`.["halt"]`,
		`now`,
		`0 | todate`,
		`0 | gmtime | mktime`,
		`0 | strftime("%Y")`,
		`0 | localtime`,
	}
	for _, src := range tests {
		t.Run(src, func(t *testing.T) {
			names, err := Check(mustParse(t, src))
			if err != nil {
				t.Fatalf("Check(%q) error = %v, want nil", src, err)
			}
			if len(names) != 0 {
				t.Errorf("Check(%q) names = %#v, want none", src, names)
			}
		})
	}
}

func TestCheck_secrets(t *testing.T) {
	allowed := []struct {
		src  string
		want []string
	}{
		{src: `$secrets.a`, want: []string{"a"}},
		{src: `$secrets."a"`, want: []string{"a"}},
		{src: `$secrets["api-token"]`, want: []string{"api-token"}},
		{src: `$secrets["a"].b`, want: []string{"a"}},
		{src: `$secrets.b + $secrets.a + $secrets.a`, want: []string{"a", "b"}},
	}
	for _, tt := range allowed {
		t.Run("allowed/"+tt.src, func(t *testing.T) {
			names, err := Check(mustParse(t, tt.src))
			if err != nil {
				t.Fatalf("Check(%q) error = %v, want nil", tt.src, err)
			}
			if !reflect.DeepEqual(names, tt.want) {
				t.Errorf("Check(%q) names = %#v, want %#v", tt.src, names, tt.want)
			}
		})
	}

	rejected := []struct {
		src  string
		form string
	}{
		{src: `$secrets`, form: "bare $secrets"},
		{src: `$secrets | keys`, form: "bare $secrets"},
		{src: `$secrets as $s | $s.a`, form: "bare $secrets"},
		{src: `$secrets[.k]`, form: "$secrets[.k]"},
		{src: `$secrets[$k]`, form: "$secrets[$k]"},
		{src: `$secrets["a" + .x]`, form: `$secrets["a" + .x]`},
		{src: `$secrets."\(.x)"`, form: `$secrets."\(.x)"`},
		{src: `$secrets[]`, form: "$secrets[]"},
		{src: `$secrets[0:1]`, form: "$secrets[0:1]"},
		{src: `{$secrets}`, form: "{$secrets}"},
		// {$secrets: v} uses the whole of $secrets as the key.
		{src: `{$secrets: 1}`, form: "{$secrets}"},
		{src: `. as $secrets | 1`, form: "$secrets as a binding"},
		{src: `. as {$secrets} | 1`, form: "$secrets as a binding"},
		{src: `. as {a: $secrets} | 1`, form: "$secrets as a binding"},
		{src: `. as [$a] ?// $secrets | 1`, form: "$secrets as a binding"},
		{src: `reduce .[] as $secrets (0; .)`, form: "$secrets as a binding"},
		{src: `def f($secrets): 1; f(.)`, form: "$secrets as a function parameter"},
	}
	for _, tt := range rejected {
		t.Run("rejected/"+tt.src, func(t *testing.T) {
			names, err := Check(mustParse(t, tt.src))
			var secret *SecretAccessError
			if !errors.As(err, &secret) {
				t.Fatalf("Check(%q) error = %v, want *SecretAccessError", tt.src, err)
			}
			if secret.Form != tt.form {
				t.Errorf("Check(%q) Form = %q, want %q", tt.src, secret.Form, tt.form)
			}
			if names != nil {
				t.Errorf("Check(%q) names = %#v, want nil", tt.src, names)
			}
		})
	}
}

func TestSecretAccessError_message(t *testing.T) {
	err := &SecretAccessError{Form: "$secrets[]"}
	want := `only static $secrets access is allowed ($secrets.<name> or $secrets["<name>"]); found $secrets[]`
	if got := err.Error(); got != want {
		t.Errorf("Error() = %q, want %q", got, want)
	}
}
