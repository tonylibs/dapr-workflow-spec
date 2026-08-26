package runner

import (
	"context"
	"errors"
	"reflect"
	"testing"

	"github.com/dws/dws-call-grpc/internal/config"
)

// newHealthRunner builds a Runner for grpc.health.v1.Health/Check against a live
// server, resolving the descriptor from a bundled FileDescriptorSet (the
// deterministic path).
func newHealthRunner(t *testing.T, output config.OutputMode) *Runner {
	t.Helper()
	addr := startHealthServer(t)
	url := serveDescriptorSet(t, healthDescriptorSet(t))
	cfg := config.Config{
		Task:          "health-check",
		ServiceAddr:   addr,
		Service:       "grpc.health.v1.Health",
		Method:        "Check",
		ProtoEndpoint: url,
		Output:        output,
		Auth:          config.Auth{Scheme: config.AuthNone},
		Timeout:       5 * 1e9, // 5s
	}
	r, err := New(context.Background(), cfg)
	if err != nil {
		t.Fatalf("runner.New: %v", err)
	}
	return r
}

func TestRunReplace(t *testing.T) {
	r := newHealthRunner(t, config.OutputReplace)
	got, err := r.Run(context.Background(), map[string]any{"service": ""})
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	want := map[string]any{"status": "SERVING"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("replace output: got %#v, want %#v", got, want)
	}
}

func TestRunMerge(t *testing.T) {
	r := newHealthRunner(t, config.OutputMerge)
	got, err := r.Run(context.Background(), map[string]any{"service": "", "keep": "x"})
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	want := map[string]any{"service": "", "keep": "x", "status": "SERVING"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("merge output: got %#v, want %#v", got, want)
	}
}

func TestRunUpstreamStatusIsRetryable(t *testing.T) {
	r := newHealthRunner(t, config.OutputReplace)
	// The health server returns NotFound for an unregistered service.
	_, err := r.Run(context.Background(), map[string]any{"service": "does.not.exist"})
	if err == nil {
		t.Fatal("expected an upstream error")
	}
	var upstream *UpstreamError
	if !errors.As(err, &upstream) {
		t.Fatalf("expected *UpstreamError, got %T: %v", err, err)
	}
}

func TestRunTransportFailureIsRetryable(t *testing.T) {
	// Resolve the descriptor from the bundled set (no dial), then point the call
	// at a closed port so the invocation dial fails.
	url := serveDescriptorSet(t, healthDescriptorSet(t))
	cfg := config.Config{
		Task:          "health-check",
		ServiceAddr:   "127.0.0.1:1", // nothing listening
		Service:       "grpc.health.v1.Health",
		Method:        "Check",
		ProtoEndpoint: url,
		Output:        config.OutputReplace,
		Auth:          config.Auth{Scheme: config.AuthNone},
		Timeout:       2 * 1e9,
	}
	r, err := New(context.Background(), cfg)
	if err != nil {
		t.Fatalf("runner.New: %v", err)
	}
	_, err = r.Run(context.Background(), map[string]any{"service": ""})
	if err == nil {
		t.Fatal("expected a transport/upstream error")
	}
	var upstream *UpstreamError
	var transport *TransportError
	if !errors.As(err, &upstream) && !errors.As(err, &transport) {
		t.Fatalf("expected a retryable runner error, got %T: %v", err, err)
	}
}
