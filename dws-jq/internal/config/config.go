// Package config loads and validates the dws-jq configuration from the
// environment. One generic image serves every jq expression site; the
// expression and its variables are fixed at deployment by the env vars parsed
// here.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"regexp"
	"strings"
	"time"
)

const (
	defaultPort         = "8080"
	defaultSite         = "dws-jq"
	defaultTimeout      = 5 * time.Second
	defaultDaprHTTPPort = "3500"
)

// Names lists every environment variable Load reads. TestConfigContract pins
// it against the fixtures under testdata/contract, so adding a name here
// without a fixture (or the reverse) fails the package tests.
var Names = []string{
	"JQ_EXPRESSION",
	"JQ_VARIABLES",
	"JQ_TIMEOUT",
	"JQ_SECRET_STORE",
	"JQ_SITE",
	"PORT",
	"DAPR_HTTP_PORT",
	"DAPR_API_TOKEN",
	"DWS_LOG_FORMAT",
}

var (
	variableName      = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)
	reservedVariables = map[string]bool{"secrets": true, "ENV": true, "__loc__": true}
)

// Config is the fully-resolved configuration.
type Config struct {
	// Expression is JQ_EXPRESSION exactly as configured; it is not unwrapped.
	Expression string
	// Variables is JQ_VARIABLES in declaration order, never nil.
	Variables []string
	// Timeout is the per-evaluation limit (JQ_TIMEOUT).
	Timeout time.Duration
	// SecretStore is JQ_SECRET_STORE, empty when unset. Whether it is needed
	// depends on the expression, so Load does not check it.
	SecretStore  string
	Site         string
	Port         string
	DaprHTTPPort string
	DaprAPIToken string
	// LogFormat is DWS_LOG_FORMAT, raw.
	LogFormat string
}

// FieldError reports an invalid environment variable.
type FieldError struct {
	Name   string
	Reason string
}

func (e *FieldError) Error() string { return e.Name + ": " + e.Reason }

// Load reads configuration from the environment and validates it. Invalid
// input yields a *FieldError so the process can log it and exit non-zero.
func Load() (Config, error) {
	cfg := Config{
		Expression:   os.Getenv("JQ_EXPRESSION"),
		SecretStore:  os.Getenv("JQ_SECRET_STORE"),
		Site:         getenv("JQ_SITE", defaultSite),
		Port:         getenv("PORT", defaultPort),
		DaprHTTPPort: getenv("DAPR_HTTP_PORT", defaultDaprHTTPPort),
		DaprAPIToken: os.Getenv("DAPR_API_TOKEN"),
		LogFormat:    os.Getenv("DWS_LOG_FORMAT"),
	}

	if strings.TrimSpace(cfg.Expression) == "" {
		return Config{}, &FieldError{Name: "JQ_EXPRESSION", Reason: "is required"}
	}

	variables, err := parseVariables("JQ_VARIABLES")
	if err != nil {
		return Config{}, err
	}
	cfg.Variables = variables

	cfg.Timeout, err = parseTimeout("JQ_TIMEOUT", defaultTimeout)
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

func parseVariables(key string) ([]string, error) {
	raw := os.Getenv(key)
	if strings.TrimSpace(raw) == "" {
		return []string{}, nil
	}
	var names []string
	if err := json.Unmarshal([]byte(raw), &names); err != nil {
		return nil, &FieldError{Name: key, Reason: fmt.Sprintf("must be a JSON array of strings: %v", err)}
	}
	if names == nil {
		return nil, &FieldError{Name: key, Reason: "must be a JSON array of strings, got null"}
	}

	seen := make(map[string]bool, len(names))
	for _, name := range names {
		switch {
		case !variableName.MatchString(name):
			return nil, &FieldError{Name: key, Reason: fmt.Sprintf("variable name %q must match %s", name, variableName)}
		case reservedVariables[name]:
			return nil, &FieldError{Name: key, Reason: fmt.Sprintf("variable name %q is reserved", name)}
		case seen[name]:
			return nil, &FieldError{Name: key, Reason: fmt.Sprintf("variable name %q is duplicated", name)}
		}
		seen[name] = true
	}
	return names, nil
}

func parseTimeout(key string, def time.Duration) (time.Duration, error) {
	raw := os.Getenv(key)
	if strings.TrimSpace(raw) == "" {
		return def, nil
	}
	d, err := time.ParseDuration(raw)
	if err != nil {
		return 0, &FieldError{Name: key, Reason: fmt.Sprintf("must be a Go duration (e.g. 5s, 250ms): %v", err)}
	}
	if d <= 0 {
		return 0, &FieldError{Name: key, Reason: fmt.Sprintf("must be positive, got %s", raw)}
	}
	return d, nil
}
