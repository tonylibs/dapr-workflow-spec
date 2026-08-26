// Package config loads and validates the step configuration from the
// environment. One generic image serves every `call: grpc` step; all behavior
// is defined by the env vars parsed here.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// OutputMode controls how the upstream response is returned to the caller.
type OutputMode string

const (
	// OutputReplace responds with the decoded response verbatim.
	OutputReplace OutputMode = "replace"
	// OutputMerge shallow-merges the decoded response into the input data.
	OutputMerge OutputMode = "merge"
)

// AuthScheme determines how this step authenticates its outbound call.
type AuthScheme string

const (
	// AuthNone sends the request without runner-managed authentication.
	AuthNone AuthScheme = "none"
	// AuthBasic sends an HTTP Basic authorization metadata header.
	AuthBasic AuthScheme = "basic"
	// AuthBearer sends an HTTP Bearer authorization metadata header.
	AuthBearer AuthScheme = "bearer"
)

const (
	defaultPort    = "8080"
	defaultTask    = "call-grpc"
	defaultTimeout = 30 * time.Second
)

// Auth is the normalized, secret-backed authentication configuration for a
// step. Only none/basic/bearer are supported for gRPC: Dapr has no
// gRPC-invocation OAuth2 middleware equivalent, so oauth2 is rejected here (and
// at controller compile time) rather than silently ignored.
type Auth struct {
	Scheme   AuthScheme
	Username string
	Password string
	Token    string
}

// Config is the fully-resolved step configuration.
type Config struct {
	Port string
	Task string

	// ServiceAddr is the target gRPC server host:port.
	ServiceAddr string
	// Service is the fully-qualified protobuf service name (e.g.
	// "package.Service"); Method is the method name (e.g. "SayHello"). Together
	// they are parsed from METHOD as "package.Service/Method".
	Service string
	Method  string

	// ProtoEndpoint, when set, is the URL of a serialized
	// google.protobuf.FileDescriptorSet fetched once at boot. When empty, the
	// runner resolves the method via server reflection.
	ProtoEndpoint string
	// ProtoSHA256, when set, is the hex digest the fetched descriptor set must
	// match (set by the controller which fetched it to pin the version).
	ProtoSHA256 string

	TLS                bool
	InsecureSkipVerify bool

	Auth    Auth
	Timeout time.Duration
	Output  OutputMode
}

// Load reads configuration from the environment and validates it. It returns a
// descriptive error for any invalid or missing required value so the process
// can exit non-zero at startup.
func Load() (Config, error) {
	cfg := Config{
		Port:          getenv("PORT", defaultPort),
		Task:          getenv("TASK", defaultTask),
		ServiceAddr:   strings.TrimSpace(os.Getenv("SERVICE_ADDR")),
		ProtoEndpoint: strings.TrimSpace(os.Getenv("PROTO_ENDPOINT")),
		ProtoSHA256:   strings.ToLower(strings.TrimSpace(os.Getenv("PROTO_SHA256"))),
		Output:        OutputMode(strings.ToLower(getenv("OUTPUT", string(OutputReplace)))),
	}

	if cfg.ServiceAddr == "" {
		return Config{}, fmt.Errorf("SERVICE_ADDR is required")
	}

	service, method, err := parseMethod(os.Getenv("METHOD"))
	if err != nil {
		return Config{}, err
	}
	cfg.Service = service
	cfg.Method = method

	switch cfg.Output {
	case OutputReplace, OutputMerge:
	default:
		return Config{}, fmt.Errorf("OUTPUT must be one of replace|merge, got %q", cfg.Output)
	}

	cfg.TLS, err = parseBool("TLS", false)
	if err != nil {
		return Config{}, err
	}

	cfg.InsecureSkipVerify, err = parseBool("INSECURE_SKIP_VERIFY", false)
	if err != nil {
		return Config{}, err
	}

	cfg.Timeout, err = parseTimeout("TIMEOUT", defaultTimeout)
	if err != nil {
		return Config{}, err
	}

	cfg.Auth, err = parseAuth()
	if err != nil {
		return Config{}, err
	}

	return cfg, nil
}

// parseMethod splits "package.Service/Method" into its fully-qualified service
// name and method name. The service portion is everything before the final
// slash; the method is everything after.
func parseMethod(raw string) (service, method string, err error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", "", fmt.Errorf("METHOD is required (format package.Service/Method)")
	}
	// Tolerate a leading slash (fully-qualified "/package.Service/Method").
	raw = strings.TrimPrefix(raw, "/")
	slash := strings.LastIndex(raw, "/")
	if slash <= 0 || slash == len(raw)-1 {
		return "", "", fmt.Errorf("METHOD must be in the form package.Service/Method, got %q", raw)
	}
	service = raw[:slash]
	method = raw[slash+1:]
	if !strings.Contains(service, ".") {
		return "", "", fmt.Errorf("METHOD service must be fully-qualified (package.Service), got %q", service)
	}
	return service, method, nil
}

func parseAuth() (Auth, error) {
	auth := Auth{
		Scheme: AuthScheme(strings.ToLower(strings.TrimSpace(getenv("AUTH_SCHEME", string(AuthNone))))),
	}

	switch auth.Scheme {
	case AuthNone:
		return auth, nil
	case AuthBasic:
		auth.Username = os.Getenv("AUTH_USERNAME")
		auth.Password = os.Getenv("AUTH_PASSWORD")
		if auth.Username == "" || auth.Password == "" {
			return Auth{}, fmt.Errorf("AUTH_USERNAME and AUTH_PASSWORD are required when AUTH_SCHEME=basic")
		}
	case AuthBearer:
		auth.Token = os.Getenv("AUTH_TOKEN")
		if auth.Token == "" {
			return Auth{}, fmt.Errorf("AUTH_TOKEN is required when AUTH_SCHEME=bearer")
		}
	case "oauth2":
		return Auth{}, fmt.Errorf("AUTH_SCHEME=oauth2 is not supported for gRPC calls: Dapr has no gRPC-invocation OAuth2 middleware equivalent")
	default:
		return Auth{}, fmt.Errorf("AUTH_SCHEME must be one of none|basic|bearer, got %q", auth.Scheme)
	}

	return auth, nil
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func parseTimeout(key string, def time.Duration) (time.Duration, error) {
	raw := os.Getenv(key)
	if strings.TrimSpace(raw) == "" {
		return def, nil
	}
	d, err := time.ParseDuration(raw)
	if err != nil {
		return 0, fmt.Errorf("%s must be a Go duration (e.g. 30s, 1m): %w", key, err)
	}
	if d <= 0 {
		return 0, fmt.Errorf("%s must be positive, got %s", key, raw)
	}
	return d, nil
}

func parseBool(key string, def bool) (bool, error) {
	raw := os.Getenv(key)
	if strings.TrimSpace(raw) == "" {
		return def, nil
	}
	b, err := strconv.ParseBool(raw)
	if err != nil {
		return false, fmt.Errorf("%s must be a boolean (true/false): %w", key, err)
	}
	return b, nil
}
