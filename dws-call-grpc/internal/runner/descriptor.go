package runner

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	reflectpb "google.golang.org/grpc/reflection/grpc_reflection_v1"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/types/descriptorpb"

	"github.com/dws/dws-call-grpc/internal/config"
	"github.com/jhump/protoreflect/grpcreflect"
)

// resolveMethod resolves the descriptor for cfg.Service/cfg.Method from the
// configured descriptor source: a bundled FileDescriptorSet fetched from
// PROTO_ENDPOINT (hash-pinned when PROTO_SHA256 is set), or server reflection
// against the target when PROTO_ENDPOINT is empty. Streaming methods are
// rejected — this runner invokes unary methods only.
func resolveMethod(ctx context.Context, cfg config.Config) (protoreflect.MethodDescriptor, error) {
	var md protoreflect.MethodDescriptor
	var err error
	if cfg.ProtoEndpoint != "" {
		md, err = resolveFromDescriptorSet(ctx, cfg)
	} else {
		md, err = resolveFromReflection(ctx, cfg)
	}
	if err != nil {
		return nil, err
	}
	if md.IsStreamingClient() || md.IsStreamingServer() {
		return nil, fmt.Errorf("method %s/%s is a streaming method; only unary methods are supported",
			cfg.Service, cfg.Method)
	}
	return md, nil
}

// resolveFromDescriptorSet fetches a serialized FileDescriptorSet, verifies it
// against PROTO_SHA256 when set, parses it, and resolves the method.
func resolveFromDescriptorSet(ctx context.Context, cfg config.Config) (protoreflect.MethodDescriptor, error) {
	raw, err := fetch(ctx, cfg.ProtoEndpoint)
	if err != nil {
		return nil, fmt.Errorf("fetch descriptor set from %s: %w", cfg.ProtoEndpoint, err)
	}
	if cfg.ProtoSHA256 != "" {
		sum := hex.EncodeToString(sha256Sum(raw))
		if sum != cfg.ProtoSHA256 {
			return nil, fmt.Errorf("descriptor set sha256 mismatch: got %s, want %s", sum, cfg.ProtoSHA256)
		}
	}
	var set descriptorpb.FileDescriptorSet
	if err := proto.Unmarshal(raw, &set); err != nil {
		return nil, fmt.Errorf("parse FileDescriptorSet (expected a serialized descriptor set, not .proto source): %w", err)
	}
	files, err := protodesc.NewFiles(&set)
	if err != nil {
		return nil, fmt.Errorf("build descriptor registry: %w", err)
	}
	desc, err := files.FindDescriptorByName(protoreflect.FullName(cfg.Service))
	if err != nil {
		return nil, fmt.Errorf("service %q not found in descriptor set: %w", cfg.Service, err)
	}
	sd, ok := desc.(protoreflect.ServiceDescriptor)
	if !ok {
		return nil, fmt.Errorf("%q is not a service", cfg.Service)
	}
	return methodByName(sd, cfg)
}

// resolveFromReflection resolves the method via the target's gRPC server
// reflection service.
func resolveFromReflection(ctx context.Context, cfg config.Config) (protoreflect.MethodDescriptor, error) {
	conn, err := grpc.NewClient(cfg.ServiceAddr, grpc.WithTransportCredentials(reflectionCreds(cfg)))
	if err != nil {
		return nil, fmt.Errorf("dial %s for reflection: %w", cfg.ServiceAddr, err)
	}
	defer conn.Close()

	client := grpcreflect.NewClientV1(ctx, reflectpb.NewServerReflectionClient(conn))
	defer client.Reset()

	svc, err := client.ResolveService(cfg.Service)
	if err != nil {
		return nil, fmt.Errorf("resolve service %q via reflection: %w", cfg.Service, err)
	}
	return methodByName(svc.UnwrapService(), cfg)
}

func methodByName(sd protoreflect.ServiceDescriptor, cfg config.Config) (protoreflect.MethodDescriptor, error) {
	md := sd.Methods().ByName(protoreflect.Name(cfg.Method))
	if md == nil {
		return nil, fmt.Errorf("method %q not found on service %q", cfg.Method, cfg.Service)
	}
	return md, nil
}

func reflectionCreds(cfg config.Config) credentials.TransportCredentials {
	if !cfg.TLS {
		return insecure.NewCredentials()
	}
	return credentials.NewTLS(&tls.Config{InsecureSkipVerify: cfg.InsecureSkipVerify})
}

// fetch GETs the descriptor bytes. The descriptor endpoint is an ordinary
// HTTP(S) config-store URL, distinct from the gRPC target.
func fetch(ctx context.Context, url string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("unexpected status %d", resp.StatusCode)
	}
	return io.ReadAll(resp.Body)
}

func sha256Sum(b []byte) []byte {
	sum := sha256.Sum256(b)
	return sum[:]
}
