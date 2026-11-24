package expr

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"time"

	"github.com/itchyny/gojq"
)

// CompileError reports an expression that does not parse or compile: bad
// syntax, an undeclared variable, or a builtin gojq does not support. Policy
// and secret-access violations are returned as *PolicyError and
// *SecretAccessError instead.
type CompileError struct{ Reason string }

func (e *CompileError) Error() string { return e.Reason }

// EvaluationError reports a failed evaluation: a jq runtime error, an
// `error(...)` call, or the time limit being hit.
type EvaluationError struct{ Detail string }

func (e *EvaluationError) Error() string { return e.Detail }

// Input is what one evaluation sees: the `.` value, the declared `$name`
// variables and the `$secrets` object. All values must be in gojq's normalized
// form (see DecodeJSON). A declared variable absent from Variables is null, and
// nil Secrets means {}.
type Input struct {
	Data      any
	Variables map[string]any
	Secrets   map[string]any
}

// Result is the first output of an evaluation. Value is nil when the
// expression produced no output; Truthy follows jq (only null, false and no
// output are falsy).
type Result struct {
	Value  any
	Truthy bool
}

// Program is a compiled, immutable jq expression. It is safe for concurrent
// use.
type Program struct {
	expression string
	variables  []string
	secrets    []string
	code       *gojq.Code
}

// Compile parses expression (unwrapping a single ${ … }), rejects anything the
// policy forbids, and compiles it with the declared variables (names without
// "$") plus $secrets.
func Compile(expression string, variables []string) (*Program, error) {
	query, err := gojq.Parse(Unwrap(expression))
	if err != nil {
		return nil, &CompileError{Reason: err.Error()}
	}
	secretNames, err := Check(query)
	if err != nil {
		return nil, err
	}
	declared := make([]string, 0, len(variables)+1)
	for _, name := range variables {
		declared = append(declared, "$"+name)
	}
	declared = append(declared, secretsVariable)
	code, err := gojq.Compile(query, gojq.WithVariables(declared))
	if err != nil {
		return nil, &CompileError{Reason: err.Error()}
	}
	return &Program{
		expression: expression,
		variables:  slices.Clone(variables),
		secrets:    secretNames,
		code:       code,
	}, nil
}

// Expression returns the expression as configured, not unwrapped.
func (p *Program) Expression() string { return p.expression }

// Variables returns the declared variable names, without "$".
func (p *Program) Variables() []string { return slices.Clone(p.variables) }

// SecretNames returns the sorted secret names the expression reads.
func (p *Program) SecretNames() []string { return slices.Clone(p.secrets) }

// Evaluate runs the program on in and returns its first output. The timeout
// bounds the whole evaluation; gojq checks the context while it runs, so a
// runaway expression stops rather than leaking. Errors are *EvaluationError.
func (p *Program) Evaluate(ctx context.Context, timeout time.Duration, in Input) (Result, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	values := make([]any, 0, len(p.variables)+1)
	for _, name := range p.variables {
		values = append(values, in.Variables[name])
	}
	secrets := in.Secrets
	if secrets == nil {
		secrets = map[string]any{}
	}
	values = append(values, secrets)

	v, ok := p.code.RunWithContext(ctx, in.Data, values...).Next()
	if !ok {
		return Result{}, nil
	}
	if err, isErr := v.(error); isErr {
		return Result{}, &EvaluationError{Detail: evaluationDetail(ctx, err, timeout)}
	}
	return Result{Value: v, Truthy: v != nil && v != false}, nil
}

func evaluationDetail(ctx context.Context, err error, timeout time.Duration) string {
	switch {
	case errors.Is(err, context.DeadlineExceeded), errors.Is(ctx.Err(), context.DeadlineExceeded):
		return fmt.Sprintf("evaluation exceeded the %s time limit", timeout)
	case errors.Is(err, context.Canceled), errors.Is(ctx.Err(), context.Canceled):
		return "evaluation was cancelled"
	}
	return err.Error()
}
