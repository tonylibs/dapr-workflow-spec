package expr

import (
	"fmt"
	"sort"

	"github.com/itchyny/gojq"
)

const (
	envVariable     = "$ENV"
	secretsVariable = "$secrets"
)

// blockedBuiltins are rejected by name at any arity, including a user
// definition with the same name (design D5).
var blockedBuiltins = map[string]bool{
	"env":            true,
	"input":          true,
	"inputs":         true,
	"input_filename": true,
	"halt":           true,
	"halt_error":     true,
	"debug":          true,
	"stderr":         true,
}

// PolicyError reports a blocked builtin, the $ENV variable, or a module
// directive ("import" or "include") found in the expression.
type PolicyError struct{ Builtin string }

func (e *PolicyError) Error() string { return fmt.Sprintf("blocked builtin %q", e.Builtin) }

// SecretAccessError reports a use of $secrets other than static access by a
// literal name (design D6). Form describes the offending use.
type SecretAccessError struct{ Form string }

func (e *SecretAccessError) Error() string {
	return `only static $secrets access is allowed ($secrets.<name> or $secrets["<name>"]); found ` + e.Form
}

// Check walks the whole parsed query and rejects blocked builtins, $ENV,
// module directives and any non-static $secrets access. On success it returns
// the sorted, de-duplicated secret names the expression reads (nil if none).
// The first violation found wins.
func Check(q *gojq.Query) ([]string, error) {
	c := &checker{names: map[string]bool{}}
	if err := c.query(q); err != nil {
		return nil, err
	}
	return c.secretNames(), nil
}

type checker struct {
	names map[string]bool
}

func (c *checker) secretNames() []string {
	if len(c.names) == 0 {
		return nil
	}
	names := make([]string, 0, len(c.names))
	for name := range c.names {
		names = append(names, name)
	}
	sort.Strings(names)
	return names
}

func (c *checker) query(q *gojq.Query) error {
	if q == nil {
		return nil
	}
	if len(q.Imports) > 0 {
		return importError(q.Imports[0])
	}
	for _, fd := range q.FuncDefs {
		if err := c.funcDef(fd); err != nil {
			return err
		}
	}
	if err := c.term(q.Term); err != nil {
		return err
	}
	if err := c.query(q.Left); err != nil {
		return err
	}
	if err := c.patterns(q.Patterns); err != nil {
		return err
	}
	return c.query(q.Right)
}

func (c *checker) queries(qs ...*gojq.Query) error {
	for _, q := range qs {
		if err := c.query(q); err != nil {
			return err
		}
	}
	return nil
}

func importError(imp *gojq.Import) error {
	if imp.IncludePath != "" {
		return &PolicyError{Builtin: "include"}
	}
	return &PolicyError{Builtin: "import"}
}

func (c *checker) funcDef(fd *gojq.FuncDef) error {
	if blockedBuiltins[fd.Name] {
		return &PolicyError{Builtin: fd.Name}
	}
	for _, arg := range fd.Args {
		if err := checkBinding(arg, "$secrets as a function parameter"); err != nil {
			return err
		}
	}
	return c.query(fd.Body)
}

// checkBinding rejects introducing a variable named $ENV or $secrets, whether
// by a pattern, an object pattern key or a function parameter. Non-variable
// names (no "$") are ignored.
func checkBinding(name, secretsForm string) error {
	switch name {
	case envVariable:
		return &PolicyError{Builtin: envVariable}
	case secretsVariable:
		return &SecretAccessError{Form: secretsForm}
	}
	return nil
}

func (c *checker) term(t *gojq.Term) error {
	if t == nil {
		return nil
	}
	suffixes := t.SuffixList
	if t.Func != nil {
		rest, err := c.function(t.Func, suffixes)
		if err != nil {
			return err
		}
		suffixes = rest
	}
	if err := c.termBody(t); err != nil {
		return err
	}
	for _, s := range suffixes {
		if err := c.index(s.Index); err != nil {
			return err
		}
	}
	return nil
}

// termBody visits every non-function part of a term. Each part is checked
// independently, so a term carrying more than one part is still fully walked.
func (c *checker) termBody(t *gojq.Term) error {
	parts := []func() error{
		func() error { return c.index(t.Index) },
		func() error { return c.object(t.Object) },
		func() error { return c.array(t.Array) },
		func() error { return c.unary(t.Unary) },
		func() error { return c.str(t.Str) },
		func() error { return c.ifTerm(t.If) },
		func() error { return c.try(t.Try) },
		func() error { return c.reduce(t.Reduce) },
		func() error { return c.foreach(t.Foreach) },
		func() error { return c.label(t.Label) },
		func() error { return c.query(t.Query) },
	}
	for _, part := range parts {
		if err := part(); err != nil {
			return err
		}
	}
	return nil
}

// function checks a function call or variable reference and returns the
// suffixes still to be walked: for static $secrets access the first suffix is
// the consumed name, so only the rest remain.
func (c *checker) function(f *gojq.Func, suffixes []*gojq.Suffix) ([]*gojq.Suffix, error) {
	switch {
	case f.Name == secretsVariable:
		name, err := staticSecretName(suffixes)
		if err != nil {
			return nil, err
		}
		c.names[name] = true
		return suffixes[1:], nil
	case f.Name == envVariable, blockedBuiltins[f.Name]:
		return nil, &PolicyError{Builtin: f.Name}
	}
	return suffixes, c.queries(f.Args...)
}

// staticSecretName accepts only $secrets.<name>, $secrets."<string>" and
// $secrets["<string literal>"] as the first suffix of a $secrets reference.
func staticSecretName(suffixes []*gojq.Suffix) (string, error) {
	if len(suffixes) == 0 {
		return "", &SecretAccessError{Form: "bare $secrets"}
	}
	if idx := suffixes[0].Index; idx != nil {
		switch {
		case idx.Name != "":
			return idx.Name, nil
		case idx.Str != nil && idx.Str.Queries == nil:
			return idx.Str.Str, nil
		case idx.Start != nil && idx.End == nil && !idx.IsSlice:
			if name, ok := stringLiteral(idx.Start); ok {
				return name, nil
			}
		}
	}
	return "", &SecretAccessError{Form: secretsVariable + suffixes[0].String()}
}

// stringLiteral reports whether q is exactly one plain string term with no
// interpolation, suffixes, definitions, bindings or operators.
func stringLiteral(q *gojq.Query) (string, bool) {
	if q.Term == nil || q.Left != nil || q.Right != nil ||
		len(q.Imports) > 0 || len(q.FuncDefs) > 0 || len(q.Patterns) > 0 {
		return "", false
	}
	t := q.Term
	if t.Type != gojq.TermTypeString || t.Str == nil || t.Str.Queries != nil || len(t.SuffixList) > 0 {
		return "", false
	}
	return t.Str.Str, true
}

func (c *checker) index(idx *gojq.Index) error {
	if idx == nil {
		return nil
	}
	if err := c.str(idx.Str); err != nil {
		return err
	}
	return c.queries(idx.Start, idx.End)
}

func (c *checker) str(s *gojq.String) error {
	if s == nil {
		return nil
	}
	return c.queries(s.Queries...)
}

func (c *checker) object(o *gojq.Object) error {
	if o == nil {
		return nil
	}
	for _, kv := range o.KeyVals {
		if err := c.objectKeyVal(kv); err != nil {
			return err
		}
	}
	return nil
}

// objectKeyVal treats a "$x" key as the {$x} shorthand, which reads $x.
func (c *checker) objectKeyVal(kv *gojq.ObjectKeyVal) error {
	switch kv.Key {
	case envVariable:
		return &PolicyError{Builtin: envVariable}
	case secretsVariable:
		return &SecretAccessError{Form: "{$secrets}"}
	}
	if err := c.str(kv.KeyString); err != nil {
		return err
	}
	return c.queries(kv.KeyQuery, kv.Val)
}

func (c *checker) array(a *gojq.Array) error {
	if a == nil {
		return nil
	}
	return c.query(a.Query)
}

func (c *checker) unary(u *gojq.Unary) error {
	if u == nil {
		return nil
	}
	return c.term(u.Term)
}

func (c *checker) ifTerm(i *gojq.If) error {
	if i == nil {
		return nil
	}
	for _, elif := range i.Elif {
		if err := c.queries(elif.Cond, elif.Then); err != nil {
			return err
		}
	}
	return c.queries(i.Cond, i.Then, i.Else)
}

func (c *checker) try(t *gojq.Try) error {
	if t == nil {
		return nil
	}
	return c.queries(t.Body, t.Catch)
}

func (c *checker) reduce(r *gojq.Reduce) error {
	if r == nil {
		return nil
	}
	if err := c.patterns(r.Patterns); err != nil {
		return err
	}
	return c.queries(r.Query, r.Start, r.Update)
}

func (c *checker) foreach(f *gojq.Foreach) error {
	if f == nil {
		return nil
	}
	if err := c.patterns(f.Patterns); err != nil {
		return err
	}
	return c.queries(f.Query, f.Start, f.Update, f.Extract)
}

func (c *checker) label(l *gojq.Label) error {
	if l == nil {
		return nil
	}
	return c.query(l.Body)
}

func (c *checker) patterns(ps []*gojq.Pattern) error {
	for _, p := range ps {
		if err := c.pattern(p); err != nil {
			return err
		}
	}
	return nil
}

func (c *checker) pattern(p *gojq.Pattern) error {
	if p == nil {
		return nil
	}
	if err := checkBinding(p.Name, "$secrets as a binding"); err != nil {
		return err
	}
	if err := c.patterns(p.Array); err != nil {
		return err
	}
	for _, po := range p.Object {
		if err := c.patternObject(po); err != nil {
			return err
		}
	}
	return nil
}

// patternObject treats a "$x" key ({$x} or {$x: pattern}) as binding $x.
func (c *checker) patternObject(po *gojq.PatternObject) error {
	if err := checkBinding(po.Key, "$secrets as a binding"); err != nil {
		return err
	}
	if err := c.str(po.KeyString); err != nil {
		return err
	}
	if err := c.query(po.KeyQuery); err != nil {
		return err
	}
	return c.pattern(po.Val)
}
