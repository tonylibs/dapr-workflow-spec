package runner

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/dws/dws-call-grpc/internal/config"
)

// serveDescriptorSet serves the given descriptor bytes over HTTP and returns the
// URL, mirroring a config-store PROTO_ENDPOINT.
func serveDescriptorSet(t *testing.T, raw []byte) string {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(raw)
	}))
	t.Cleanup(srv.Close)
	return srv.URL
}

func TestResolveMethodFromDescriptorSet(t *testing.T) {
	raw := healthDescriptorSet(t)
	url := serveDescriptorSet(t, raw)
	sum := sha256.Sum256(raw)

	cfg := config.Config{
		Service:       "grpc.health.v1.Health",
		Method:        "Check",
		ProtoEndpoint: url,
		ProtoSHA256:   hex.EncodeToString(sum[:]),
	}
	md, err := resolveMethod(context.Background(), cfg)
	if err != nil {
		t.Fatalf("resolveMethod: %v", err)
	}
	if got := string(md.Input().FullName()); got != "grpc.health.v1.HealthCheckRequest" {
		t.Fatalf("input: got %q", got)
	}
}

func TestResolveMethodSHA256Mismatch(t *testing.T) {
	raw := healthDescriptorSet(t)
	url := serveDescriptorSet(t, raw)

	cfg := config.Config{
		Service:       "grpc.health.v1.Health",
		Method:        "Check",
		ProtoEndpoint: url,
		ProtoSHA256:   "deadbeef",
	}
	_, err := resolveMethod(context.Background(), cfg)
	if err == nil || !strings.Contains(err.Error(), "sha256 mismatch") {
		t.Fatalf("expected sha256 mismatch error, got %v", err)
	}
}

func TestResolveMethodStreamingRejected(t *testing.T) {
	raw := healthDescriptorSet(t)
	url := serveDescriptorSet(t, raw)

	// Watch is a server-streaming method — must be rejected.
	cfg := config.Config{
		Service:       "grpc.health.v1.Health",
		Method:        "Watch",
		ProtoEndpoint: url,
	}
	_, err := resolveMethod(context.Background(), cfg)
	if err == nil || !strings.Contains(err.Error(), "streaming") {
		t.Fatalf("expected streaming rejection, got %v", err)
	}
}

func TestResolveMethodUnknownMethod(t *testing.T) {
	raw := healthDescriptorSet(t)
	url := serveDescriptorSet(t, raw)

	cfg := config.Config{
		Service:       "grpc.health.v1.Health",
		Method:        "Nope",
		ProtoEndpoint: url,
	}
	_, err := resolveMethod(context.Background(), cfg)
	if err == nil || !strings.Contains(err.Error(), "not found") {
		t.Fatalf("expected not-found error, got %v", err)
	}
}

func TestResolveMethodFromReflection(t *testing.T) {
	addr := startHealthServer(t)
	cfg := config.Config{
		ServiceAddr: addr,
		Service:     "grpc.health.v1.Health",
		Method:      "Check",
	}
	md, err := resolveMethod(context.Background(), cfg)
	if err != nil {
		t.Fatalf("resolveMethod via reflection: %v", err)
	}
	if md == nil || md.Name() != "Check" {
		t.Fatalf("unexpected method: %v", md)
	}
}
