// Package config loads and validates the step configuration from the
// environment. One codebase serves three images; MODE selects which
// interpreter the runner execs, and every other behavior is env-defined.
package config

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"time"
)

// Mode selects the interpreter this image execs. It is set by the image's
// final Docker stage, not by the workflow definition.
type Mode string

const (
	ModeShell        Mode = "shell"
	ModeScriptJS     Mode = "script-js"
	ModeScriptPython Mode = "script-python"
)

// ReturnMode mirrors DSL 1.0.0's ProcessReturnType: it selects which part of
// the subprocess result becomes the step's raw value.
type ReturnMode string

const (
	ReturnStdout ReturnMode = "stdout"
	ReturnStderr ReturnMode = "stderr"
	ReturnCode   ReturnMode = "code"
	ReturnAll    ReturnMode = "all"
	ReturnNone   ReturnMode = "none"
)

// OutputMode controls how the raw value folds into the response. Same
// semantics as dws-call-http.
type OutputMode string

const (
	OutputReplace OutputMode = "replace"
	OutputMerge   OutputMode = "merge"
)

const (
	defaultPort    = "8080"
	defaultTask    = "run"
	defaultTimeout = 30 * time.Second
)

// Argument is one entry of the DSL's `arguments` map. Order matters: the shell
// renderer emits flags in definition order, so this is a slice, not a map.
type Argument struct {
	Name  string
	Value any
}

// Config is the fully-resolved step configuration.
type Config struct {
	Mode        Mode
	Port        string
	Task        string
	Command     string
	Script      string
	Arguments   []Argument
	Environment map[string]string
	Return      ReturnMode
	Output      OutputMode
	Timeout     time.Duration
}

// Load reads configuration from the environment and validates it, returning a
// descriptive error for any invalid value so main can exit non-zero at startup
// rather than failing on first invocation.
func Load() (Config, error) {
	cfg := Config{
		Mode:   Mode(strings.ToLower(getenv("MODE", string(ModeShell)))),
		Port:   getenv("PORT", defaultPort),
		Task:   getenv("TASK", defaultTask),
		Return: ReturnMode(strings.ToLower(getenv("RETURN", string(ReturnStdout)))),
		Output: OutputMode(strings.ToLower(getenv("OUTPUT", string(OutputReplace)))),
	}

	switch cfg.Mode {
	case ModeShell:
		cfg.Command = os.Getenv("COMMAND")
		if strings.TrimSpace(cfg.Command) == "" {
			return Config{}, fmt.Errorf("COMMAND is required when MODE=shell")
		}
	case ModeScriptJS, ModeScriptPython:
		cfg.Script = os.Getenv("SCRIPT")
		if strings.TrimSpace(cfg.Script) == "" {
			return Config{}, fmt.Errorf("SCRIPT is required when MODE=%s", cfg.Mode)
		}
	default:
		return Config{}, fmt.Errorf("MODE must be one of shell|script-js|script-python, got %q", cfg.Mode)
	}

	switch cfg.Return {
	case ReturnStdout, ReturnStderr, ReturnCode, ReturnAll, ReturnNone:
	default:
		return Config{}, fmt.Errorf("RETURN must be one of stdout|stderr|code|all|none, got %q", cfg.Return)
	}

	switch cfg.Output {
	case OutputReplace, OutputMerge:
	default:
		return Config{}, fmt.Errorf("OUTPUT must be one of replace|merge, got %q", cfg.Output)
	}

	args, err := parseArguments(os.Getenv("ARGUMENTS"))
	if err != nil {
		return Config{}, err
	}
	cfg.Arguments = args

	env, err := parseStringMap("ENVIRONMENT", os.Getenv("ENVIRONMENT"))
	if err != nil {
		return Config{}, err
	}
	cfg.Environment = env

	cfg.Timeout, err = parseTimeout(os.Getenv("TIMEOUT"), defaultTimeout)
	if err != nil {
		return Config{}, err
	}

	return cfg, nil
}

// parseArguments decodes ARGUMENTS as a JSON object while preserving key
// order. encoding/json into a map would lose the order the shell renderer
// depends on, so this walks the token stream directly.
func parseArguments(raw string) ([]Argument, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}

	dec := json.NewDecoder(bytes.NewReader([]byte(raw)))
	dec.UseNumber()

	tok, err := dec.Token()
	if err != nil {
		return nil, fmt.Errorf("ARGUMENTS must be a JSON object: %w", err)
	}
	if delim, ok := tok.(json.Delim); !ok || delim != '{' {
		return nil, fmt.Errorf("ARGUMENTS must be a JSON object (a key/value map), got %s", raw)
	}

	var args []Argument
	for dec.More() {
		keyTok, err := dec.Token()
		if err != nil {
			return nil, fmt.Errorf("ARGUMENTS: %w", err)
		}
		name, ok := keyTok.(string)
		if !ok {
			return nil, fmt.Errorf("ARGUMENTS: expected a string key, got %v", keyTok)
		}
		var value any
		if err := dec.Decode(&value); err != nil {
			return nil, fmt.Errorf("ARGUMENTS[%s]: %w", name, err)
		}
		args = append(args, Argument{Name: name, Value: value})
	}
	return args, nil
}

func parseStringMap(key, raw string) (map[string]string, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}
	var m map[string]string
	if err := json.Unmarshal([]byte(raw), &m); err != nil {
		return nil, fmt.Errorf("%s must be a JSON object of strings: %w", key, err)
	}
	return m, nil
}

func parseTimeout(raw string, def time.Duration) (time.Duration, error) {
	if strings.TrimSpace(raw) == "" {
		return def, nil
	}
	d, err := time.ParseDuration(raw)
	if err != nil {
		return 0, fmt.Errorf("TIMEOUT must be a Go duration (e.g. 30s, 1m): %w", err)
	}
	if d <= 0 {
		return 0, fmt.Errorf("TIMEOUT must be positive, got %s", raw)
	}
	return d, nil
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
