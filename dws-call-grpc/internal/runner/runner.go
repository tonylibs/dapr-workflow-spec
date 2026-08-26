// Package runner executes the configured outbound gRPC call for a step.
package runner

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"

	"connectrpc.com/connect"
	"golang.org/x/net/http2"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/types/dynamicpb"

	"github.com/dws/dws-call-grpc/internal/config"
)

// UpstreamError is returned when the gRPC call completes with a non-OK status or
// a transport failure. Both are retryable: the activity maps this to the
// "upstream failure" marker so the orchestrator's retry policy re-invokes.
type UpstreamError struct {
	Task    string
	Code    string
	Message string
}

func (e *UpstreamError) Error() string {
	return fmt.Sprintf("upstream call for task %q returned gRPC status %s: %s", e.Task, e.Code, e.Message)
}

// TransportError is returned when the request never produced a response
// (connection refused, DNS failure). It is also mapped to the retryable
// "upstream failure" marker.
type TransportError struct {
	Task string
	Err  error
}

func (e *TransportError) Error() string {
	return fmt.Sprintf("transport error for task %q: %v", e.Task, e.Err)
}

func (e *TransportError) Unwrap() error { return e.Err }

// Runner performs the configured gRPC call against a runtime-selected method.
type Runner struct {
	cfg    config.Config
	method protoreflect.MethodDescriptor
	client *connect.Client[dynamicpb.Message, dynamicpb.Message]
}

// New resolves the target method descriptor (bundled FileDescriptorSet or
// reflection) and builds a connect-go dynamic client over h2c or TLS. A
// resolution or configuration failure returns an error so the process exits
// non-zero at startup rather than serving a misconfigured step.
func New(ctx context.Context, cfg config.Config) (*Runner, error) {
	md, err := resolveMethod(ctx, cfg)
	if err != nil {
		return nil, err
	}

	scheme := "http"
	if cfg.TLS {
		scheme = "https"
	}
	url := fmt.Sprintf("%s://%s/%s/%s", scheme, cfg.ServiceAddr, cfg.Service, cfg.Method)

	client := connect.NewClient[dynamicpb.Message, dynamicpb.Message](
		httpClient(cfg),
		url,
		connect.WithGRPC(),
		connect.WithSchema(md),
		connect.WithResponseInitializer(func(_ connect.Spec, message any) error {
			m, ok := message.(*dynamicpb.Message)
			if !ok {
				return fmt.Errorf("unexpected response message type %T", message)
			}
			*m = *dynamicpb.NewMessage(md.Output())
			return nil
		}),
	)

	return &Runner{cfg: cfg, method: md, client: client}, nil
}

// httpClient builds the transport the connect client speaks over: HTTP/2
// cleartext (h2c) by default, or standard TLS HTTP/2 when TLS is enabled.
func httpClient(cfg config.Config) *http.Client {
	if !cfg.TLS {
		return &http.Client{
			Transport: &http2.Transport{
				AllowHTTP: true,
				DialTLSContext: func(ctx context.Context, network, addr string, _ *tls.Config) (net.Conn, error) {
					var d net.Dialer
					return d.DialContext(ctx, network, addr)
				},
			},
		}
	}
	return &http.Client{
		Transport: &http2.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: cfg.InsecureSkipVerify},
		},
	}
}

// Run builds the request message from the input workflow data, invokes the
// unary method, and returns the result shaped per the configured OUTPUT mode.
func (r *Runner) Run(ctx context.Context, input map[string]any) (any, error) {
	ctx, cancel := context.WithTimeout(ctx, r.cfg.Timeout)
	defer cancel()

	reqMsg, err := r.buildRequest(input)
	if err != nil {
		return nil, err
	}

	req := connect.NewRequest(reqMsg)
	if err := r.applyAuth(req.Header()); err != nil {
		return nil, err
	}

	resp, err := r.client.CallUnary(ctx, req)
	if err != nil {
		return nil, r.classifyCallError(err)
	}

	return r.shapeOutput(input, resp.Msg)
}

// buildRequest decodes the input workflow data into the request message via
// protobuf JSON. Unknown fields are discarded so a shared workflow-data document
// carrying fields beyond the request message flows through cleanly.
func (r *Runner) buildRequest(input map[string]any) (*dynamicpb.Message, error) {
	msg := dynamicpb.NewMessage(r.method.Input())
	if len(input) == 0 {
		return msg, nil
	}
	raw, err := json.Marshal(input)
	if err != nil {
		return nil, fmt.Errorf("marshal input workflow data: %w", err)
	}
	if err := (protojson.UnmarshalOptions{DiscardUnknown: true}).Unmarshal(raw, msg); err != nil {
		return nil, fmt.Errorf("map input workflow data onto request message %s: %w", r.method.Input().FullName(), err)
	}
	return msg, nil
}

func (r *Runner) applyAuth(h http.Header) error {
	switch r.cfg.Auth.Scheme {
	case config.AuthNone:
	case config.AuthBasic:
		credential := base64.StdEncoding.EncodeToString([]byte(r.cfg.Auth.Username + ":" + r.cfg.Auth.Password))
		h.Set("Authorization", "Basic "+credential)
	case config.AuthBearer:
		h.Set("Authorization", "Bearer "+r.cfg.Auth.Token)
	default:
		return fmt.Errorf("unsupported auth scheme %q", r.cfg.Auth.Scheme)
	}
	return nil
}

// classifyCallError maps a connect call error to a retryable runner error. gRPC
// status failures and transport failures both become upstream failures.
func (r *Runner) classifyCallError(err error) error {
	var connectErr *connect.Error
	if errors.As(err, &connectErr) {
		return &UpstreamError{Task: r.cfg.Task, Code: connectErr.Code().String(), Message: connectErr.Message()}
	}
	return &TransportError{Task: r.cfg.Task, Err: err}
}

// shapeOutput turns the response message into the value returned to the caller
// according to the OUTPUT mode, identical to the shared step contract.
func (r *Runner) shapeOutput(input map[string]any, resp *dynamicpb.Message) (any, error) {
	raw, err := protojson.Marshal(resp)
	if err != nil {
		return nil, fmt.Errorf("encode response message: %w", err)
	}

	if r.cfg.Output == config.OutputMerge {
		upstream := map[string]any{}
		if err := json.Unmarshal(raw, &upstream); err != nil {
			return nil, fmt.Errorf("decode response for merge (expected JSON object): %w", err)
		}
		merged := make(map[string]any, len(input)+len(upstream))
		for k, v := range input {
			merged[k] = v
		}
		for k, v := range upstream {
			merged[k] = v
		}
		return merged, nil
	}

	var upstream any
	if err := json.Unmarshal(raw, &upstream); err != nil {
		return nil, fmt.Errorf("decode response: %w", err)
	}
	return upstream, nil
}
