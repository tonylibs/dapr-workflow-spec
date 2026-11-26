// Package conformance runs the shared expression corpus
// (conformance/expressions/, ADR 0014 Decision 5) through the expr package,
// the same compile-and-evaluate code that serves POST /run.
package conformance

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/santhosh-tekuri/jsonschema/v6"
	"gopkg.in/yaml.v3"

	"github.com/dws/dws-jq/internal/expr"
)

// Verdict is the outcome of one case.
type Verdict string

const (
	VerdictPass            Verdict = "pass"
	VerdictFail            Verdict = "fail"
	VerdictKnownDivergence Verdict = "known divergence"
	VerdictStaleDivergence Verdict = "stale divergence"
)

// evaluationTimeout matches the service's default JQ_TIMEOUT.
const evaluationTimeout = 5 * time.Second

// CaseResult is the verdict of one case. Reason is empty on pass.
type CaseResult struct {
	Name, Test string
	Verdict    Verdict
	Reason     string
}

// Report holds one result per case, sorted by case name.
type Report struct{ Results []CaseResult }

// OK reports whether the run is green: no fail and no stale divergence.
func (r Report) OK() bool {
	for _, res := range r.Results {
		if res.Verdict == VerdictFail || res.Verdict == VerdictStaleDivergence {
			return false
		}
	}
	return true
}

// Totals counts the results per verdict.
func (r Report) Totals() map[Verdict]int {
	totals := map[Verdict]int{}
	for _, res := range r.Results {
		totals[res.Verdict]++
	}
	return totals
}

// Run loads every cases/**/*.yaml under corpusDir, validates it against
// case.schema.json, checks names, areas and test uniqueness, then evaluates
// each case and applies the divergence list. An error means the corpus or the
// divergence list is malformed; no case is evaluated in that case.
func Run(corpusDir, divergenceFile string) (Report, error) {
	cases, err := loadCases(corpusDir)
	if err != nil {
		return Report{}, err
	}
	divergences, err := loadDivergences(divergenceFile, cases)
	if err != nil {
		return Report{}, err
	}
	results := make([]CaseResult, 0, len(cases))
	for _, c := range cases {
		mismatch := evaluate(c)
		results = append(results, verdict(c, mismatch, divergences))
	}
	return Report{Results: results}, nil
}

// verdict combines a case's mismatch ("" when it matches) with the divergence list.
func verdict(c testCase, mismatch string, divergences map[string]divergence) CaseResult {
	res := CaseResult{Name: c.Name, Test: c.Test}
	d, listed := divergences[c.Name]
	switch {
	case !listed && mismatch == "":
		res.Verdict = VerdictPass
	case !listed:
		res.Verdict, res.Reason = VerdictFail, c.Name+": "+mismatch
	case mismatch == "":
		res.Verdict = VerdictStaleDivergence
		res.Reason = c.Name + ": listed in the divergence list but now passes; remove the entry"
	default:
		res.Verdict = VerdictKnownDivergence
		res.Reason = fmt.Sprintf("%s: %s (followUp %s); %s", c.Name, d.Reason, d.FollowUp, mismatch)
	}
	return res
}

// testCase is one decoded case file. Raw JSON fields are decoded the way
// POST /run decodes its body, through expr.DecodeJSON.
type testCase struct {
	Name       string                     `json:"name"`
	Test       string                     `json:"test"`
	Area       string                     `json:"area"`
	Expression string                     `json:"expression"`
	Input      json.RawMessage            `json:"input"`
	Variables  map[string]json.RawMessage `json:"variables"`
	Expect     struct {
		Result json.RawMessage `json:"result"`
		Truthy *bool           `json:"truthy"`
		Fault  *fault          `json:"fault"`
	} `json:"expect"`
}

type fault struct {
	Phase          string   `json:"phase"`
	DetailContains []string `json:"detailContains"`
}

const (
	phaseCompile  = "compile"
	phaseEvaluate = "evaluate"
)

func loadCases(corpusDir string) ([]testCase, error) {
	schema, err := jsonschema.NewCompiler().Compile(filepath.Join(corpusDir, "case.schema.json"))
	if err != nil {
		return nil, fmt.Errorf("compile case schema: %w", err)
	}
	casesDir := filepath.Join(corpusDir, "cases")
	var cases []testCase
	err = filepath.WalkDir(casesDir, func(p string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil || d.IsDir() {
			return walkErr
		}
		rel, err := filepath.Rel(casesDir, p)
		if err != nil {
			return err
		}
		// Every file under cases/ is a case: a .yml or stray file would
		// otherwise be skipped and its case silently never run.
		if filepath.Ext(p) != ".yaml" {
			return fmt.Errorf("%s: every file under cases/ must be a .yaml case file", filepath.ToSlash(rel))
		}
		c, err := loadCase(p, filepath.ToSlash(rel), schema)
		if err != nil {
			return err
		}
		cases = append(cases, c)
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("load corpus %s: %w", corpusDir, err)
	}
	if len(cases) == 0 {
		return nil, fmt.Errorf("load corpus %s: no cases under %s", corpusDir, casesDir)
	}
	if err := checkUniqueTests(cases); err != nil {
		return nil, err
	}
	sort.Slice(cases, func(i, j int) bool { return cases[i].Name < cases[j].Name })
	return cases, nil
}

// loadCase reads one case file (rel is its slash path under cases/), converts
// the YAML to JSON, validates it and checks its name and area against the path.
func loadCase(file, rel string, schema *jsonschema.Schema) (testCase, error) {
	doc, err := yamlFileToJSON(file)
	if err != nil {
		return testCase{}, fmt.Errorf("%s: %w", rel, err)
	}
	inst, err := jsonschema.UnmarshalJSON(bytes.NewReader(doc))
	if err != nil {
		return testCase{}, fmt.Errorf("%s: %w", rel, err)
	}
	if err := schema.Validate(inst); err != nil {
		return testCase{}, fmt.Errorf("%s: does not match case.schema.json: %w", rel, err)
	}
	var c testCase
	if err := json.Unmarshal(doc, &c); err != nil {
		return testCase{}, fmt.Errorf("%s: %w", rel, err)
	}
	dir, file := path.Split(rel)
	wantName := strings.TrimSuffix(dir, "/") + "/" + strings.TrimSuffix(file, ".yaml")
	if c.Name != wantName {
		return testCase{}, fmt.Errorf("%s: name %q does not match its path, want %q", rel, c.Name, wantName)
	}
	if c.Area+"/" != dir {
		return testCase{}, fmt.Errorf("%s: area %q does not match its directory %q", rel, c.Area, strings.TrimSuffix(dir, "/"))
	}
	return c, nil
}

func checkUniqueTests(cases []testCase) error {
	seen := map[string]string{}
	for _, c := range cases {
		if other, dup := seen[c.Test]; dup {
			return fmt.Errorf("test %q is used by both %s and %s", c.Test, other, c.Name)
		}
		seen[c.Test] = c.Name
	}
	return nil
}

// yamlFileToJSON decodes one YAML document and re-encodes it as JSON.
func yamlFileToJSON(file string) ([]byte, error) {
	raw, err := os.ReadFile(file)
	if err != nil {
		return nil, err
	}
	var v any
	if err := yaml.Unmarshal(raw, &v); err != nil {
		return nil, fmt.Errorf("parse YAML: %w", err)
	}
	if err := checkJSONCompatible(v); err != nil {
		return nil, err
	}
	doc, err := json.Marshal(v)
	if err != nil {
		return nil, fmt.Errorf("convert YAML to JSON: %w", err)
	}
	return doc, nil
}

// checkJSONCompatible rejects YAML values with no JSON form (non-string keys,
// timestamps, binary), which json.Marshal would otherwise reject or reshape.
func checkJSONCompatible(v any) error {
	switch v := v.(type) {
	case nil, bool, string, int, int64, uint64, float64:
		return nil
	case map[string]any:
		for _, item := range v {
			if err := checkJSONCompatible(item); err != nil {
				return err
			}
		}
		return nil
	case []any:
		for _, item := range v {
			if err := checkJSONCompatible(item); err != nil {
				return err
			}
		}
		return nil
	}
	return fmt.Errorf("YAML value of type %T has no JSON form", v)
}

type divergence struct {
	Case     string `yaml:"case"`
	Reason   string `yaml:"reason"`
	FollowUp string `yaml:"followUp"`
}

// loadDivergences reads the engine's divergence list. Every entry must name a
// case of the corpus once, with a reason and a follow-up.
func loadDivergences(file string, cases []testCase) (map[string]divergence, error) {
	raw, err := os.ReadFile(file)
	if err != nil {
		return nil, fmt.Errorf("read divergence list: %w", err)
	}
	var list struct {
		Runtime     string       `yaml:"runtime"`
		Divergences []divergence `yaml:"divergences"`
	}
	dec := yaml.NewDecoder(bytes.NewReader(raw))
	dec.KnownFields(true)
	if err := dec.Decode(&list); err != nil {
		return nil, fmt.Errorf("%s: %w", file, err)
	}
	known := map[string]bool{}
	for _, c := range cases {
		known[c.Name] = true
	}
	out := map[string]divergence{}
	for i, d := range list.Divergences {
		switch {
		case d.Case == "" || d.Reason == "" || d.FollowUp == "":
			return nil, fmt.Errorf("%s: divergence %d needs case, reason and followUp", file, i)
		case !known[d.Case]:
			return nil, fmt.Errorf("%s: divergence for unknown case %q", file, d.Case)
		}
		if _, dup := out[d.Case]; dup {
			return nil, fmt.Errorf("%s: case %q is listed twice", file, d.Case)
		}
		out[d.Case] = d
	}
	return out, nil
}

// evaluate runs one case the way POST /run does and returns "" when the
// outcome matches the expectation, or a description of the difference.
func evaluate(c testCase) string {
	in, names, err := caseInput(c)
	if err != nil {
		return "cannot decode the case values: " + err.Error()
	}
	program, err := expr.Compile(c.Expression, names)
	if err != nil {
		return compareFault(c, phaseCompile, err.Error())
	}
	res, err := program.Evaluate(context.Background(), evaluationTimeout, in)
	var evalErr *expr.EvaluationError
	switch {
	case errors.As(err, &evalErr):
		return compareFault(c, phaseEvaluate, evalErr.Detail)
	case err != nil:
		return "unexpected evaluation error: " + err.Error()
	}
	return compareResult(c, res)
}

// caseInput decodes input and variables with expr.DecodeJSON, as /run does,
// and returns the variable names sorted.
func caseInput(c testCase) (expr.Input, []string, error) {
	data, err := decodeValue(c.Input)
	if err != nil {
		return expr.Input{}, nil, fmt.Errorf("input: %w", err)
	}
	names := make([]string, 0, len(c.Variables))
	vars := make(map[string]any, len(c.Variables))
	for name, raw := range c.Variables {
		v, err := decodeValue(raw)
		if err != nil {
			return expr.Input{}, nil, fmt.Errorf("variable %q: %w", name, err)
		}
		names = append(names, name)
		vars[name] = v
	}
	sort.Strings(names)
	return expr.Input{Data: data, Variables: vars}, names, nil
}

func compareFault(c testCase, phase, detail string) string {
	want := c.Expect.Fault
	if want == nil {
		return fmt.Sprintf("expected result %s, got %s fault: %s", c.Expect.Result, phase, detail)
	}
	if want.Phase != phase {
		return fmt.Sprintf("expected %s fault, got %s fault: %s", want.Phase, phase, detail)
	}
	for _, part := range want.DetailContains {
		if !strings.Contains(detail, part) {
			return fmt.Sprintf("%s fault detail %q does not contain %q", phase, detail, part)
		}
	}
	return ""
}

func compareResult(c testCase, res expr.Result) string {
	encoded, err := expr.Encode(res.Value)
	if err != nil {
		return "cannot encode the result: " + err.Error()
	}
	if c.Expect.Fault != nil {
		return fmt.Sprintf("expected %s fault, got result %s", c.Expect.Fault.Phase, encoded)
	}
	equal, err := jsonEqual(c.Expect.Result, encoded)
	if err != nil {
		return "cannot compare the result: " + err.Error()
	}
	if !equal {
		return fmt.Sprintf("expected result %s, got %s", c.Expect.Result, encoded)
	}
	if want := *c.Expect.Truthy; want != res.Truthy {
		return fmt.Sprintf("expected truthy %t, got %t", want, res.Truthy)
	}
	return ""
}

// decodeValue decodes one case value with expr.DecodeJSON, the decoder POST
// /run uses. A JSON null may reach here as an empty document.
func decodeValue(raw json.RawMessage) (any, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	return expr.DecodeJSON(raw)
}
