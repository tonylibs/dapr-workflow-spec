package conformance

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// realSchema is the corpus schema the self-tests copy into each temporary corpus.
const realSchema = "../../../conformance/expressions/case.schema.json"

const noDivergences = "runtime: dws-jq (gojq)\ndivergences: []\n"

// fixture is one case file of a temporary corpus, keyed by its path under cases/.
type fixture map[string]string

// newCorpus writes a corpus (schema, cases, divergence list) under t.TempDir()
// and returns its directory and divergence file.
func newCorpus(t *testing.T, cases fixture, divergences string) (string, string) {
	t.Helper()
	dir := t.TempDir()
	schema, err := os.ReadFile(realSchema)
	if err != nil {
		t.Fatalf("read schema: %v", err)
	}
	writeFile(t, filepath.Join(dir, "case.schema.json"), string(schema))
	if err := os.MkdirAll(filepath.Join(dir, "cases"), 0o755); err != nil {
		t.Fatalf("mkdir cases: %v", err)
	}
	for rel, body := range cases {
		writeFile(t, filepath.Join(dir, "cases", filepath.FromSlash(rel)), body)
	}
	divergenceFile := filepath.Join(dir, "divergences", "dws-jq.yaml")
	writeFile(t, divergenceFile, divergences)
	return dir, divergenceFile
}

func writeFile(t *testing.T, path, body string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}

// caseYAML renders a case file with the fields every case needs.
func caseYAML(name, test, area, expression, input, expect string) string {
	return "name: " + name + "\n" +
		"test: " + test + "\n" +
		"area: " + area + "\n" +
		"summary: 'self-test case'\n" +
		"source: 'runner self-test'\n" +
		"expression: '" + strings.ReplaceAll(expression, "'", "''") + "'\n" +
		"input: " + input + "\n" +
		expect
}

func mustRun(t *testing.T, dir, divergenceFile string) Report {
	t.Helper()
	report, err := Run(dir, divergenceFile)
	if err != nil {
		t.Fatalf("Run: unexpected error: %v", err)
	}
	return report
}

func onlyResult(t *testing.T, report Report) CaseResult {
	t.Helper()
	if len(report.Results) != 1 {
		t.Fatalf("got %d results, want 1: %+v", len(report.Results), report.Results)
	}
	return report.Results[0]
}

func TestRun_structuralErrors(t *testing.T) {
	tests := []struct {
		name    string
		cases   fixture
		div     string
		wantErr string
	}{
		{
			name: "nameMismatch",
			cases: fixture{"loops/x.yaml": caseYAML("loops/y", "givenA_whenB_thenC", "loops", ".", "null",
				"expect: { result: null, truthy: false }\n")},
			div:     noDivergences,
			wantErr: "name",
		},
		{
			name:    "yamlNotAMapping",
			cases:   fixture{"loops/x.yaml": "- just\n- a list\n"},
			div:     noDivergences,
			wantErr: "loops/x.yaml",
		},
		{
			name: "divergenceUnknownCase",
			cases: fixture{"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", ".", "null",
				"expect: { result: null, truthy: false }\n")},
			div:     "runtime: dws-jq (gojq)\ndivergences:\n  - { case: loops/nope, reason: r, followUp: EX-1 }\n",
			wantErr: "loops/nope",
		},
		{
			name: "divergenceMissingFollowUp",
			cases: fixture{"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", ".", "null",
				"expect: { result: null, truthy: false }\n")},
			div:     "runtime: dws-jq (gojq)\ndivergences:\n  - { case: loops/x, reason: r }\n",
			wantErr: "followUp",
		},
		{
			name:    "noCases",
			cases:   fixture{},
			div:     noDivergences,
			wantErr: "no cases",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir, div := newCorpus(t, tt.cases, tt.div)

			_, err := Run(dir, div)

			assertErrorMentions(t, err, tt.wantErr)
		})
	}
}

func assertErrorMentions(t *testing.T, err error, want string) {
	t.Helper()
	if err == nil {
		t.Fatal("Run: want a structural error, got nil")
	}
	if !strings.Contains(err.Error(), want) {
		t.Fatalf("Run error %q does not mention %q", err, want)
	}
}

func TestRun_areaMismatch(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"catch/x.yaml": caseYAML("catch/x", "givenA_whenB_thenC", "loops", ".", "null",
			"expect: { result: null, truthy: false }\n"),
	}, noDivergences)

	_, err := Run(dir, div)

	assertErrorMentions(t, err, "area")
}

func TestRun_duplicateTest(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"loops/a.yaml": caseYAML("loops/a", "givenA_whenB_thenC", "loops", ".", "null",
			"expect: { result: null, truthy: false }\n"),
		"catch/b.yaml": caseYAML("catch/b", "givenA_whenB_thenC", "catch", ".", "null",
			"expect: { result: null, truthy: false }\n"),
	}, noDivergences)

	_, err := Run(dir, div)

	assertErrorMentions(t, err, "givenA_whenB_thenC")
}

func TestRun_schemaInvalid(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", ".", "null", ""),
	}, noDivergences)

	_, err := Run(dir, div)

	assertErrorMentions(t, err, "loops/x.yaml")
}

func TestRun_staleDivergence(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", "1 + 1", "null",
			"expect: { result: 2, truthy: true }\n"),
	}, "runtime: dws-jq (gojq)\ndivergences:\n  - { case: loops/x, reason: r, followUp: EX-1 }\n")

	report := mustRun(t, dir, div)

	got := onlyResult(t, report)
	if got.Verdict != VerdictStaleDivergence {
		t.Fatalf("verdict = %q, want %q (reason %q)", got.Verdict, VerdictStaleDivergence, got.Reason)
	}
	if !strings.Contains(got.Reason, "loops/x") {
		t.Errorf("reason %q does not name the case", got.Reason)
	}
	if report.OK() {
		t.Error("OK() = true, want false on a stale divergence")
	}
}

func TestRun_knownDivergence(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", "1 + 1", "null",
			"expect: { result: 3, truthy: true }\n"),
	}, "runtime: dws-jq (gojq)\ndivergences:\n  - { case: loops/x, reason: arithmetic, followUp: EX-1 }\n")

	report := mustRun(t, dir, div)

	got := onlyResult(t, report)
	if got.Verdict != VerdictKnownDivergence {
		t.Fatalf("verdict = %q, want %q (reason %q)", got.Verdict, VerdictKnownDivergence, got.Reason)
	}
	if !strings.Contains(got.Reason, "arithmetic") || !strings.Contains(got.Reason, "EX-1") {
		t.Errorf("reason %q does not carry the divergence reason and follow-up", got.Reason)
	}
	if !report.OK() {
		t.Error("OK() = false, want true when the only non-pass is a known divergence")
	}
}

func TestRun_numbersByValue(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", "10.0", "null",
			"expect: { result: 10, truthy: true }\n"),
	}, noDivergences)

	report := mustRun(t, dir, div)

	if got := onlyResult(t, report); got.Verdict != VerdictPass {
		t.Fatalf("verdict = %q, want pass (reason %q)", got.Verdict, got.Reason)
	}
	if !report.OK() {
		t.Error("OK() = false, want true")
	}
}

func TestRun_faultPhase(t *testing.T) {
	dir, div := newCorpus(t, fixture{
		// keys_unsorted does not exist in gojq: a compile failure, not an evaluate one.
		"loops/x.yaml": caseYAML("loops/x", "givenA_whenB_thenC", "loops", "keys_unsorted", "{}",
			"expect:\n  fault: { phase: evaluate, detailContains: [] }\n"),
	}, noDivergences)

	report := mustRun(t, dir, div)

	got := onlyResult(t, report)
	if got.Verdict != VerdictFail {
		t.Fatalf("verdict = %q, want fail (reason %q)", got.Verdict, got.Reason)
	}
	if !strings.Contains(got.Reason, "compile") {
		t.Errorf("reason %q does not say the fault was a compile fault", got.Reason)
	}
	if report.OK() {
		t.Error("OK() = true, want false on a fail")
	}
}

// TestRun_verdicts covers the outcome comparison: results, truthiness, fault
// phases and fault details, each as one case in its own corpus.
func TestRun_verdicts(t *testing.T) {
	tests := []struct {
		name       string
		expression string
		input      string
		extra      string
		expect     string
		want       Verdict
		reasonHas  string
	}{
		{"resultMatches", ".a", `{"a":[1,"x",null]}`, "", "expect: { result: [1,\"x\",null], truthy: true }\n", VerdictPass, ""},
		{"resultDiffers", ".a", `{"a":1}`, "", "expect: { result: 2, truthy: true }\n", VerdictFail, "result"},
		{"truthyDiffers", "false", "null", "", "expect: { result: false, truthy: true }\n", VerdictFail, "truthy"},
		{"noOutputIsNull", "empty", "null", "", "expect: { result: null, truthy: false }\n", VerdictPass, ""},
		{"keyOrderIgnored", `{"b":1,"a":2}`, "null", "", "expect: { result: {\"a\":2,\"b\":1}, truthy: true }\n", VerdictPass, ""},
		{"bigIntegerExact", "9007199254740993", "null", "", "expect: { result: 9007199254740992, truthy: true }\n", VerdictFail, "result"},
		{"bigIntegerInput", ".", "9007199254740993", "", "expect: { result: 9007199254740993, truthy: true }\n", VerdictPass, ""},
		{"floatByValue", "0.5 + 0.25", "null", "", "expect: { result: 0.75, truthy: true }\n", VerdictPass, ""},
		{"variablesBound", "$a + $b", "null", "variables: {\"a\":1,\"b\":2}\n", "expect: { result: 3, truthy: true }\n", VerdictPass, ""},
		{"compileFaultMatches", "keys_unsorted", "{}", "", "expect:\n  fault: { phase: compile, detailContains: [\"keys_unsorted\"] }\n", VerdictPass, ""},
		{"policyFaultIsCompile", "env", "null", "", "expect:\n  fault: { phase: compile, detailContains: [\"env\"] }\n", VerdictPass, ""},
		{"evaluateFaultMatches", ".a + 1", `{"a":"x"}`, "", "expect:\n  fault: { phase: evaluate, detailContains: [] }\n", VerdictPass, ""},
		{"evaluateFaultIsNotCompile", ".a + 1", `{"a":"x"}`, "", "expect:\n  fault: { phase: compile, detailContains: [] }\n", VerdictFail, "evaluate"},
		{"detailMissing", "keys_unsorted", "{}", "", "expect:\n  fault: { phase: compile, detailContains: [\"nope\"] }\n", VerdictFail, "nope"},
		{"faultExpectedResultGot", "1", "null", "", "expect:\n  fault: { phase: evaluate, detailContains: [] }\n", VerdictFail, "fault"},
		{"resultExpectedFaultGot", ".a + 1", `{"a":"x"}`, "", "expect: { result: 1, truthy: true }\n", VerdictFail, "evaluate"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body := caseYAML("loops/x", "givenA_whenB_thenC", "loops", tt.expression, tt.input, tt.extra+tt.expect)
			dir, div := newCorpus(t, fixture{"loops/x.yaml": body}, noDivergences)

			got := onlyResult(t, mustRun(t, dir, div))

			if got.Verdict != tt.want {
				t.Fatalf("verdict = %q, want %q (reason %q)", got.Verdict, tt.want, got.Reason)
			}
			if !strings.Contains(got.Reason, tt.reasonHas) {
				t.Errorf("reason %q does not contain %q", got.Reason, tt.reasonHas)
			}
			if got.Name != "loops/x" || got.Test != "givenA_whenB_thenC" {
				t.Errorf("result names %q/%q, want loops/x/givenA_whenB_thenC", got.Name, got.Test)
			}
		})
	}
}

func TestReport_OK(t *testing.T) {
	tests := []struct {
		name     string
		verdicts []Verdict
		want     bool
	}{
		{"empty", nil, true},
		{"passAndKnown", []Verdict{VerdictPass, VerdictKnownDivergence}, true},
		{"fail", []Verdict{VerdictPass, VerdictFail}, false},
		{"stale", []Verdict{VerdictStaleDivergence}, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var r Report
			for _, v := range tt.verdicts {
				r.Results = append(r.Results, CaseResult{Verdict: v})
			}
			if got := r.OK(); got != tt.want {
				t.Fatalf("OK() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestReport_Totals(t *testing.T) {
	r := Report{Results: []CaseResult{
		{Verdict: VerdictPass}, {Verdict: VerdictPass}, {Verdict: VerdictFail}, {Verdict: VerdictKnownDivergence},
	}}

	got := r.Totals()

	if got[VerdictPass] != 2 || got[VerdictFail] != 1 || got[VerdictKnownDivergence] != 1 || got[VerdictStaleDivergence] != 0 {
		t.Fatalf("Totals() = %v", got)
	}
}
