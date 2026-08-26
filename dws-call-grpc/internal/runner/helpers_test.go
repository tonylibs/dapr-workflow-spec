package runner

import (
	"net"
	"testing"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protodesc"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/reflect/protoregistry"
	"google.golang.org/protobuf/types/descriptorpb"
)

// startHealthServer stands up a real in-process gRPC health server over
// plaintext HTTP/2 (h2c) with server reflection enabled, and returns its
// host:port address. The default ("") service reports SERVING.
func startHealthServer(t *testing.T) string {
	t.Helper()
	lis, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	srv := grpc.NewServer()
	hs := health.NewServer()
	hs.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)
	healthpb.RegisterHealthServer(srv, hs)
	reflection.Register(srv)
	go func() { _ = srv.Serve(lis) }()
	t.Cleanup(srv.Stop)
	// Give the listener a moment to accept.
	time.Sleep(50 * time.Millisecond)
	return lis.Addr().String()
}

// healthDescriptorSet builds a self-contained FileDescriptorSet for
// grpc.health.v1 from the linked generated package — no protoc required. This is
// exactly what an operator would supply via PROTO_ENDPOINT.
func healthDescriptorSet(t *testing.T) []byte {
	t.Helper()
	fd, err := protoregistry.GlobalFiles.FindFileByPath("grpc/health/v1/health.proto")
	if err != nil {
		t.Fatalf("find health.proto: %v", err)
	}
	set := &descriptorpb.FileDescriptorSet{}
	seen := map[string]bool{}
	var add func(fd protoreflect.FileDescriptor)
	add = func(fd protoreflect.FileDescriptor) {
		if seen[fd.Path()] {
			return
		}
		seen[fd.Path()] = true
		imports := fd.Imports()
		for i := 0; i < imports.Len(); i++ {
			add(imports.Get(i).FileDescriptor)
		}
		set.File = append(set.File, protodesc.ToFileDescriptorProto(fd))
	}
	add(fd)
	raw, err := proto.Marshal(set)
	if err != nil {
		t.Fatalf("marshal descriptor set: %v", err)
	}
	return raw
}
