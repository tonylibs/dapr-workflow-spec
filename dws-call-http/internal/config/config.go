// Package config loads and validates the step configuration from the
// environment. One generic image serves every `call: http` step; all behavior
// is defined by the env vars parsed here.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// BodyMode controls how the outbound request body is built.
type BodyMode string

const (
	// BodyPassthrough forwards the input workflow data as the request body.
	BodyPassthrough BodyMode = "passthrough"
	// BodyNone sends no request body.
	BodyNone BodyMode = "none"
	// BodyTemplate renders BODY_TEMPLATE with {placeholders} as the body.
	BodyTemplate BodyMode = "template"
)

// OutputMode controls how the upstream response is returned to the caller.
type OutputMode string

const (
	// OutputReplace responds with the upstream body verbatim.
	OutputReplace OutputMode = "replace"
	// OutputMerge shallow-merges the upstream object into the input data.
	OutputMerge OutputMode = "merge"
)

const (
	defaultPort    = "8080"
	defaultMethod  = "POST"
	defaultTask    = "call-http"
	defaultTimeout = 30 * time.Second
)

// Config is the fully-resolved step configuration.
type Config struct {
	Port               string
	Task               string
	Endpoint           string
	Method             string
	Headers            map[string]string
	Query              map[string]string
	BodyMode           BodyMode
	BodyTemplate       string
	Output             OutputMode
	Timeout            time.Duration
	InsecureSkipVerify bool
}

// Load reads configuration from the environment and validates it. It returns a
// descriptive error for any invalid or missing required value so the process
// can exit non-zero at startup.
func Load() (Config, error) {
	cfg := Config{
		Port:     getenv("PORT", defaultPort),
		Task:     getenv("TASK", defaultTask),
		Endpoint: os.Getenv("ENDPOINT"),
		Method:   strings.ToUpper(getenv("METHOD", defaultMethod)),
		BodyMode: BodyMode(strings.ToLower(getenv("BODY_MODE", string(BodyPassthrough)))),
		Output:   OutputMode(strings.ToLower(getenv("OUTPUT", string(OutputReplace)))),
	}

	if strings.TrimSpace(cfg.Endpoint) == "" {
		return Config{}, fmt.Errorf("ENDPOINT is required")
	}

	headers, err := parseJSONMap("HEADERS")
	if err != nil {
		return Config{}, err
	}
	cfg.Headers = headers

	query, err := parseJSONMap("QUERY")
	if err != nil {
		return Config{}, err
	}
	cfg.Query = query

	switch cfg.BodyMode {
	case BodyPassthrough, BodyNone, BodyTemplate:
	default:
		return Config{}, fmt.Errorf("BODY_MODE must be one of passthrough|none|template, got %q", cfg.BodyMode)
	}

	cfg.BodyTemplate = os.Getenv("BODY_TEMPLATE")
	if cfg.BodyMode == BodyTemplate && strings.TrimSpace(cfg.BodyTemplate) == "" {
		return Config{}, fmt.Errorf("BODY_TEMPLATE is required when BODY_MODE=template")
	}

	switch cfg.Output {
	case OutputReplace, OutputMerge:
	default:
		return Config{}, fmt.Errorf("OUTPUT must be one of replace|merge, got %q", cfg.Output)
	}

	cfg.Timeout, err = parseTimeout("TIMEOUT", defaultTimeout)
	if err != nil {
		return Config{}, err
	}

	cfg.InsecureSkipVerify, err = parseBool("INSECURE_SKIP_VERIFY", false)
	if err != nil {
		return Config{}, err
	}

	return cfg, nil
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func parseJSONMap(key string) (map[string]string, error) {
	raw := os.Getenv(key)
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(raw), &m); err != nil {
		return nil, fmt.Errorf("%s must be a JSON object of strings: %w", key, err)
	}
	return m, nil
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
