package runner

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/dws/dws-run/internal/config"
)

// shellArgv renders the command plus its arguments for `sh -c`. Arguments are
// passed as sh's positional parameters rather than concatenated into the
// command string, so a value containing shell metacharacters cannot alter the
// command's structure.
//
//	sh -c '<command> "$@"' sh --env prod --region eu
func shellArgv(command string, args []config.Argument) []string {
	if len(args) == 0 {
		return []string{"-c", command}
	}
	argv := []string{"-c", command + ` "$@"`, "sh"}
	for _, a := range args {
		argv = append(argv, "--"+a.Name, stringify(a.Value))
	}
	return argv
}

// stringify renders an argument value for the shell. Scalars use their natural
// text form; objects and arrays are passed as compact JSON.
func stringify(v any) string {
	switch t := v.(type) {
	case nil:
		return ""
	case string:
		return t
	case bool:
		if t {
			return "true"
		}
		return "false"
	case json.Number:
		// json.Number's underlying string is the original decoded text, so
		// rendering it directly preserves values that lose precision (or
		// blow up into 1e+20-style notation) as float64 — large integers
		// like order IDs, Snowflake IDs, and ns timestamps.
		return t.String()
	case float64:
		if t == float64(int64(t)) {
			return fmt.Sprintf("%d", int64(t))
		}
		return fmt.Sprintf("%v", t)
	default:
		b, err := json.Marshal(t)
		if err != nil {
			return fmt.Sprintf("%v", t)
		}
		return string(b)
	}
}

// scriptSource prepends a prelude that binds each argument as an in-scope
// variable, then the author's script. Values come in through the
// DWS_ARGUMENTS environment variable rather than being interpolated into the
// source, so quoting and JSON types are both preserved exactly.
func scriptSource(mode config.Mode, script string, args []config.Argument) (string, error) {
	// config.Load already rejects a bad argument name at startup (see
	// run-step-configuration/spec.md: invalid config must fail at startup,
	// not at first invocation). This call is defense in depth for the rare
	// caller that builds a Runner from a Config that bypassed Load — it
	// should never fire in the deployed image, but if it does, the caller
	// (runner.execute) must not wrap it in something that maps to a
	// retryable response.
	for _, a := range args {
		if err := config.ValidIdentifier(mode, a.Name); err != nil {
			return "", err
		}
	}

	var b strings.Builder
	switch mode {
	case config.ModeScriptJS:
		b.WriteString(`const __dwsArgs = JSON.parse(process.env.DWS_ARGUMENTS || "{}");` + "\n")
		for _, a := range args {
			fmt.Fprintf(&b, "const %s = __dwsArgs[%q];\n", a.Name, a.Name)
		}
	case config.ModeScriptPython:
		b.WriteString("import json as __dws_json, os as __dws_os\n")
		b.WriteString(`__dws_args = __dws_json.loads(__dws_os.environ.get("DWS_ARGUMENTS", "{}"))` + "\n")
		for _, a := range args {
			fmt.Fprintf(&b, "%s = __dws_args[%q]\n", a.Name, a.Name)
		}
	default:
		return "", fmt.Errorf("scriptSource called for non-script mode %q", mode)
	}
	b.WriteString(script)
	return b.String(), nil
}
