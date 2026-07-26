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
	for _, a := range args {
		if err := validIdentifier(mode, a.Name); err != nil {
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

// reservedInternalNames are the identifiers the generated prelude itself
// declares (see scriptSource). An argument sharing one of these names would
// redeclare it — a SyntaxError in both target languages — so these are
// rejected regardless of mode.
var reservedInternalNames = map[string]bool{
	"__dwsArgs":  true,
	"__dws_args": true,
	"__dws_json": true,
	"__dws_os":   true,
}

// jsReservedWords are ECMAScript keywords and reserved words. Binding one as
// `const <word> = ...;` is a SyntaxError, so these are rejected for
// config.ModeScriptJS.
var jsReservedWords = map[string]bool{
	"break": true, "case": true, "catch": true, "class": true, "const": true,
	"continue": true, "debugger": true, "default": true, "delete": true, "do": true,
	"else": true, "enum": true, "export": true, "extends": true, "false": true,
	"finally": true, "for": true, "function": true, "if": true, "implements": true,
	"import": true, "in": true, "instanceof": true, "interface": true, "let": true,
	"new": true, "null": true, "package": true, "private": true, "protected": true,
	"public": true, "return": true, "static": true, "super": true, "switch": true,
	"this": true, "throw": true, "true": true, "try": true, "typeof": true,
	"var": true, "void": true, "while": true, "with": true, "yield": true,
	"await": true,
}

// pythonReservedWords are Python's reserved keywords (keyword.kwlist).
// Binding one as `<word> = ...` is a SyntaxError, so these are rejected for
// config.ModeScriptPython.
var pythonReservedWords = map[string]bool{
	"False": true, "None": true, "True": true, "and": true, "as": true,
	"assert": true, "async": true, "await": true, "break": true, "class": true,
	"continue": true, "def": true, "del": true, "elif": true, "else": true,
	"except": true, "finally": true, "for": true, "from": true, "global": true,
	"if": true, "import": true, "in": true, "is": true, "lambda": true,
	"nonlocal": true, "not": true, "or": true, "pass": true, "raise": true,
	"return": true, "try": true, "while": true, "with": true, "yield": true,
}

// validIdentifier rejects argument names that are valid map keys but not
// valid bindable identifiers for the target runtime: names that aren't
// JS/Python identifiers at all, names that collide with the prelude's own
// internal variables, and names that are reserved keywords in the target
// language (a name invalid in one script language may be perfectly valid in
// the other, e.g. `const` is a JS keyword but a fine Python identifier;
// `None`/`def` are Python keywords but fine JS identifiers). dws-controller
// rejects these at compile time; this is the defense in depth for
// hand-written manifests.
func validIdentifier(mode config.Mode, name string) error {
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
	if reservedInternalNames[name] {
		return fmt.Errorf("argument name %q collides with an identifier the generated prelude uses internally", name)
	}
	switch mode {
	case config.ModeScriptJS:
		if jsReservedWords[name] {
			return fmt.Errorf("argument name %q is a reserved JavaScript keyword", name)
		}
	case config.ModeScriptPython:
		if pythonReservedWords[name] {
			return fmt.Errorf("argument name %q is a reserved Python keyword", name)
		}
	}
	return nil
}
