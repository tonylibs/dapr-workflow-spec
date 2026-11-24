package expr

import (
	"context"
	"errors"
	"reflect"
	"strings"
	"testing"
	"time"
)

func TestCompile(t *testing.T) {
	t.Run("wrapped expression compiles and is reported as configured", func(t *testing.T) {
		p, err := Compile("${ .a }", nil)
		if err != nil {
			t.Fatalf("Compile() error = %v", err)
		}
		if got := p.Expression(); got != "${ .a }" {
			t.Errorf("Expression() = %q, want %q", got, "${ .a }")
		}
	})

	t.Run("declared variables and secret names are reported", func(t *testing.T) {
		p, err := Compile(`$item + ($secrets.b | length) + ($secrets.a | length)`, []string{"item"})
		if err != nil {
			t.Fatalf("Compile() error = %v", err)
		}
		if got, want := p.Variables(), []string{"item"}; !reflect.DeepEqual(got, want) {
			t.Errorf("Variables() = %v, want %v", got, want)
		}
		if got, want := p.SecretNames(), []string{"a", "b"}; !reflect.DeepEqual(got, want) {
			t.Errorf("SecretNames() = %v, want %v", got, want)
		}
	})

	t.Run("variables are defensively copied", func(t *testing.T) {
		vars := []string{"item"}
		p, err := Compile(`$item`, vars)
		if err != nil {
			t.Fatalf("Compile() error = %v", err)
		}
		vars[0] = "changed"
		p.Variables()[0] = "changed"
		if got := p.Variables(); got[0] != "item" {
			t.Errorf("Variables() = %v, want [item]", got)
		}
	})

	t.Run("an undeclared variable names the variable", func(t *testing.T) {
		_, err := Compile(`$item < 3`, nil)
		var ce *CompileError
		if !errors.As(err, &ce) {
			t.Fatalf("Compile() error = %v, want *CompileError", err)
		}
		if !strings.Contains(err.Error(), "$item") {
			t.Errorf("error %q does not mention $item", err)
		}
	})

	t.Run("a declared variable compiles", func(t *testing.T) {
		if _, err := Compile(`$item < 3`, []string{"item"}); err != nil {
			t.Fatalf("Compile() error = %v", err)
		}
	})

	t.Run("policy errors stay findable", func(t *testing.T) {
		_, err := Compile(`env`, nil)
		var pe *PolicyError
		if !errors.As(err, &pe) || pe.Builtin != "env" {
			t.Fatalf("Compile() error = %v, want *PolicyError for env", err)
		}
	})

	t.Run("secret access errors stay findable", func(t *testing.T) {
		_, err := Compile(`$secrets`, nil)
		var se *SecretAccessError
		if !errors.As(err, &se) {
			t.Fatalf("Compile() error = %v, want *SecretAccessError", err)
		}
	})
}

func TestCompile_compileErrors(t *testing.T) {
	tests := []struct {
		name string
		expr string
	}{
		{name: "parse error", expr: `.a |`},
		{name: "two wrapped expressions", expr: `${ .a } + ${ .b }`},
		{name: "keys_unsorted is unsupported", expr: `keys_unsorted`},
		{name: "$__loc__ is unsupported", expr: `$__loc__`},
		{name: "input_line_number is unsupported", expr: `input_line_number`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := Compile(tt.expr, nil)
			var ce *CompileError
			if !errors.As(err, &ce) {
				t.Fatalf("Compile(%q) error = %v, want *CompileError", tt.expr, err)
			}
			if ce.Reason == "" {
				t.Errorf("Compile(%q) CompileError.Reason is empty", tt.expr)
			}
		})
	}
}

func TestEvaluate(t *testing.T) {
	tests := []struct {
		name       string
		expr       string
		variables  []string
		in         Input
		wantValue  any
		wantTruthy bool
	}{
		{name: "first output is used", expr: `.[]`, in: Input{Data: []any{0, 1}}, wantValue: 0, wantTruthy: true},
		{name: "empty has no value", expr: `empty`, wantValue: nil, wantTruthy: false},
		{name: "false is falsy", expr: `false`, wantValue: false, wantTruthy: false},
		{name: "null is falsy", expr: `null`, wantValue: nil, wantTruthy: false},
		{name: "zero is truthy", expr: `0`, wantValue: 0, wantTruthy: true},
		{name: "empty string is truthy", expr: `""`, wantValue: "", wantTruthy: true},
		{name: "empty array is truthy", expr: `[]`, wantValue: []any{}, wantTruthy: true},
		{
			name: "absent declared variable is null", expr: `$item == null`, variables: []string{"item"},
			wantValue: true, wantTruthy: true,
		},
		{
			name: "declared variable is bound", expr: `$item + 1`, variables: []string{"item"},
			in: Input{Variables: map[string]any{"item": 2}}, wantValue: 3, wantTruthy: true,
		},
		{
			name: "variables bind by name not position", expr: `[$b, $a]`, variables: []string{"a", "b"},
			in:        Input{Variables: map[string]any{"a": 1, "b": 2}},
			wantValue: []any{2, 1}, wantTruthy: true,
		},
		{
			name: "secret is readable", expr: `$secrets.a | length`,
			in: Input{Secrets: map[string]any{"a": "abcd"}}, wantValue: 4, wantTruthy: true,
		},
		{
			name: "absent secret reads as null when Secrets is nil", expr: `$secrets.a == null`,
			wantValue: true, wantTruthy: true,
		},
		{
			name: "object construction", expr: `{ "id": ([ .id ] | .[0]), "source": "api" }`,
			in:        Input{Data: map[string]any{"id": 7}},
			wantValue: map[string]any{"id": 7, "source": "api"}, wantTruthy: true,
		},
		{
			name: "wrapped expression is unwrapped", expr: `${ .a }`,
			in: Input{Data: map[string]any{"a": "x"}}, wantValue: "x", wantTruthy: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			p, err := Compile(tt.expr, tt.variables)
			if err != nil {
				t.Fatalf("Compile(%q) error = %v", tt.expr, err)
			}
			got, err := p.Evaluate(context.Background(), time.Second, tt.in)
			if err != nil {
				t.Fatalf("Evaluate() error = %v", err)
			}
			if !reflect.DeepEqual(got.Value, tt.wantValue) {
				t.Errorf("Value = %#v, want %#v", got.Value, tt.wantValue)
			}
			if got.Truthy != tt.wantTruthy {
				t.Errorf("Truthy = %v, want %v", got.Truthy, tt.wantTruthy)
			}
		})
	}
}

func TestEvaluate_errors(t *testing.T) {
	tests := []struct {
		name       string
		expr       string
		in         Input
		timeout    time.Duration
		wantDetail string // exact when exact is true, substring otherwise
		exact      bool
	}{
		{name: "type error", expr: `.a + 1`, in: Input{Data: map[string]any{"a": "x"}}, timeout: time.Second},
		{name: "error builtin", expr: `error("boom")`, timeout: time.Second, wantDetail: "boom"},
		{
			name: "timeout", expr: `last(range(1; infinite))`, timeout: 50 * time.Millisecond,
			wantDetail: "evaluation exceeded the 50ms time limit", exact: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			p, err := Compile(tt.expr, nil)
			if err != nil {
				t.Fatalf("Compile(%q) error = %v", tt.expr, err)
			}
			start := time.Now()
			_, err = p.Evaluate(context.Background(), tt.timeout, tt.in)
			if elapsed := time.Since(start); elapsed > time.Second {
				t.Errorf("Evaluate() took %v, want well under 1s", elapsed)
			}
			var ee *EvaluationError
			if !errors.As(err, &ee) {
				t.Fatalf("Evaluate() error = %v, want *EvaluationError", err)
			}
			if ee.Detail == "" {
				t.Fatal("EvaluationError.Detail is empty")
			}
			if tt.exact && ee.Detail != tt.wantDetail {
				t.Errorf("Detail = %q, want %q", ee.Detail, tt.wantDetail)
			}
			if !tt.exact && !strings.Contains(ee.Detail, tt.wantDetail) {
				t.Errorf("Detail = %q, want it to contain %q", ee.Detail, tt.wantDetail)
			}
		})
	}
}

func TestEvaluate_cancelledContext(t *testing.T) {
	p, err := Compile(`last(range(1; infinite))`, nil)
	if err != nil {
		t.Fatalf("Compile() error = %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err = p.Evaluate(ctx, time.Minute, Input{})
	var ee *EvaluationError
	if !errors.As(err, &ee) {
		t.Fatalf("Evaluate() error = %v, want *EvaluationError", err)
	}
}

func TestEvaluate_reusableAcrossCalls(t *testing.T) {
	p, err := Compile(`.a`, nil)
	if err != nil {
		t.Fatalf("Compile() error = %v", err)
	}
	for _, want := range []int{1, 2} {
		got, err := p.Evaluate(context.Background(), time.Second, Input{Data: map[string]any{"a": want}})
		if err != nil {
			t.Fatalf("Evaluate() error = %v", err)
		}
		if got.Value != want {
			t.Errorf("Value = %v, want %v", got.Value, want)
		}
	}
}
