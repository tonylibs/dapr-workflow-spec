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
	for _, a := range args {
		if err := validIdentifier(a.Name); err != nil {
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

// validIdentifier rejects argument names that are valid map keys but not valid
// JS/Python identifiers. dws-controller rejects these at compile time; this is
// the defense in depth for hand-written manifests.
func validIdentifier(name string) error {
	if name == "" {
		return fmt.Errorf("argument name must not be empty")
	}
	for i, r := range name {
		isLetter := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || r == '_'
		isDigit := r >= '0' && r <= '9'
		if isLetter || (i > 0 && isDigit) {
			continue
		}
		return fmt.Errorf("argument name %q is not a valid identifier", name)
	}
	return nil
}
