package config

import "fmt"

// InvalidIdentifierError indicates an argument name cannot be bound as an
// identifier in the target script language. It is a distinct type (rather
// than a plain error) so callers can tell "this config is permanently
// broken" apart from transient spawn failures: Load returns it to fail the
// process at startup, and if a caller keeps a defense-in-depth check at
// request time, it must not be wrapped in something that maps to a
// retryable response — retrying can never fix a bad argument name.
type InvalidIdentifierError struct {
	Name   string
	Reason string
}

func (e *InvalidIdentifierError) Error() string {
	return fmt.Sprintf("argument name %q %s", e.Name, e.Reason)
}

// reservedInternalNames are the identifiers the generated script prelude
// itself declares (see runner.scriptSource). An argument sharing one of
// these names would redeclare it — a SyntaxError in both target languages —
// so these are rejected regardless of mode.
var reservedInternalNames = map[string]bool{
	"__dwsArgs":  true,
	"__dws_args": true,
	"__dws_json": true,
	"__dws_os":   true,
}

// jsReservedWords are ECMAScript keywords and reserved words. Binding one as
// `const <word> = ...;` is a SyntaxError, so these are rejected for
// ModeScriptJS.
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
// ModeScriptPython.
var pythonReservedWords = map[string]bool{
	"False": true, "None": true, "True": true, "and": true, "as": true,
	"assert": true, "async": true, "await": true, "break": true, "class": true,
	"continue": true, "def": true, "del": true, "elif": true, "else": true,
	"except": true, "finally": true, "for": true, "from": true, "global": true,
	"if": true, "import": true, "in": true, "is": true, "lambda": true,
	"nonlocal": true, "not": true, "or": true, "pass": true, "raise": true,
	"return": true, "try": true, "while": true, "with": true, "yield": true,
}

// ValidIdentifier rejects argument names that are valid map keys but not
// valid bindable identifiers for the target script runtime: names that
// aren't JS/Python identifiers at all, names that collide with the
// prelude's own internal variables, and names that are reserved keywords in
// the target language (a name invalid in one script language may be
// perfectly valid in the other, e.g. `const` is a JS keyword but a fine
// Python identifier; `None`/`def` are Python keywords but fine JS
// identifiers).
//
// Only ModeScriptJS and ModeScriptPython bind arguments as identifiers;
// ModeShell passes them as `--name value` flags, which has no such
// constraint, so callers should only invoke this for script modes.
func ValidIdentifier(mode Mode, name string) error {
	if name == "" {
		return &InvalidIdentifierError{Name: name, Reason: "must not be empty"}
	}
	for i, r := range name {
		isLetter := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || r == '_'
		isDigit := r >= '0' && r <= '9'
		if isLetter || (i > 0 && isDigit) {
			continue
		}
		return &InvalidIdentifierError{Name: name, Reason: "is not a valid identifier"}
	}
	if reservedInternalNames[name] {
		return &InvalidIdentifierError{
			Name:   name,
			Reason: "collides with an identifier the generated prelude uses internally",
		}
	}
	switch mode {
	case ModeScriptJS:
		if jsReservedWords[name] {
			return &InvalidIdentifierError{Name: name, Reason: "is a reserved JavaScript keyword"}
		}
	case ModeScriptPython:
		if pythonReservedWords[name] {
			return &InvalidIdentifierError{Name: name, Reason: "is a reserved Python keyword"}
		}
	}
	return nil
}
