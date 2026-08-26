package runner

import (
	"context"
	"reflect"
	"testing"

	"github.com/dws/dws-call-grpc/internal/config"
)

// TestIntegrationReflectionEndToEnd exercises the full runner against a real
// gRPC server over h2c using the server-reflection descriptor source (no bundled
// descriptor set). This is the end-to-end counterpart to the bundled-descriptor
// runner tests.
func TestIntegrationReflectionEndToEnd(t *testing.T) {
	addr := startHealthServer(t)
	cfg := config.Config{
		Task:        "health-check",
		ServiceAddr: addr,
		Service:     "grpc.health.v1.Health",
		Method:      "Check",
		Output:      config.OutputReplace,
		Auth:        config.Auth{Scheme: config.AuthNone},
		Timeout:     5 * 1e9,
	}
	r, err := New(context.Background(), cfg)
	if err != nil {
		t.Fatalf("runner.New (reflection): %v", err)
	}
	got, err := r.Run(context.Background(), map[string]any{"service": ""})
	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	want := map[string]any{"status": "SERVING"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("reflection end-to-end output: got %#v, want %#v", got, want)
	}
}
