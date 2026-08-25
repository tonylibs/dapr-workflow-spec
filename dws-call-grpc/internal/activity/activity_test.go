package activity

import (
	"context"
	"errors"
	"reflect"
	"strings"
	"testing"

	"github.com/dws/dws-call-grpc/internal/runner"
)

// fakeRunner is a StepRunner whose behavior is fixed per test case. It records
// the input it received so empty-input handling can be asserted.
type fakeRunner struct {
	out      any
	err      error
	gotInput map[string]any
}

func (f *fakeRunner) Run(_ context.Context, input map[string]any) (any, error) {
	f.gotInput = input
	return f.out, f.err
}

func TestRun(t *testing.T) {
	tests := []struct {
		name     string
		input    map[string]any
		out      any
		err      error
		want     any
		wantErr  string // substring the failure message must contain ("" => no error)
		wantData map[string]any
	}{
		{
			name:     "nil input is empty workflow data",
			input:    nil,
			out:      map[string]any{"ok": true},
			want:     map[string]any{"ok": true},
			wantData: map[string]any{},
		},
		{
			name:  "success returns shaped output",
			input: map[string]any{"name": "ada"},
			out:   map[string]any{"message": "hi ada"},
			want:  map[string]any{"message": "hi ada"},
		},
		{
			name:  "nil output leaves data unchanged",
			input: map[string]any{"name": "ada"},
			out:   nil,
			want:  map[string]any{"name": "ada"},
		},
		{
			name:    "upstream error is retryable upstream failure",
			input:   map[string]any{},
			err:     &runner.UpstreamError{Task: "greet-user", Code: "unavailable", Message: "down"},
			wantErr: "step 'greet-user' upstream failure:",
		},
		{
			name:    "transport error is retryable upstream failure",
			input:   map[string]any{},
			err:     &runner.TransportError{Task: "greet-user", Err: errors.New("connection refused")},
			wantErr: "step 'greet-user' upstream failure:",
		},
		{
			name:    "other error is non-retryable config failure",
			input:   map[string]any{},
			err:     errors.New("decode response: unexpected EOF"),
			wantErr: "step 'greet-user' config failure:",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			fr := &fakeRunner{out: tt.out, err: tt.err}
			got, err := Run(context.Background(), fr, "greet-user", tt.input)

			if tt.wantErr != "" {
				if err == nil {
					t.Fatalf("expected error containing %q, got nil", tt.wantErr)
				}
				if !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("error %q does not contain %q", err.Error(), tt.wantErr)
				}
				return
			}

			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Fatalf("output: got %#v, want %#v", got, tt.want)
			}
			if tt.wantData != nil && !reflect.DeepEqual(fr.gotInput, tt.wantData) {
				t.Fatalf("runner input: got %#v, want %#v", fr.gotInput, tt.wantData)
			}
		})
	}
}

// TestRunUpstreamFailureDetail asserts the underlying detail is carried in the
// upstream-failure message so the orchestrator can surface it.
func TestRunUpstreamFailureDetail(t *testing.T) {
	fr := &fakeRunner{err: &runner.UpstreamError{Task: "greet-user", Code: "unavailable", Message: "down"}}
	_, err := Run(context.Background(), fr, "greet-user", map[string]any{})
	if err == nil {
		t.Fatal("expected error")
	}
	want := "step 'greet-user' upstream failure: upstream call for task \"greet-user\" returned gRPC status unavailable: down"
	if err.Error() != want {
		t.Fatalf("message:\n got %q\nwant %q", err.Error(), want)
	}
}
