package conformance

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

// defaultCorpus is conformance/expressions/ relative to this package.
const defaultCorpus = "../../../conformance/expressions"

// corpusEnv points the runner at another copy of the corpus.
const corpusEnv = "DWS_CONFORMANCE_EXPRESSIONS"

// TestExpressionCorpus runs the shared expression corpus through the same
// compile-and-evaluate code that serves POST /run, in UTC. It fails on any
// fail or stale-divergence verdict.
func TestExpressionCorpus(t *testing.T) {
	t.Setenv("TZ", "UTC")
	previousLocal := time.Local
	time.Local = time.UTC
	t.Cleanup(func() { time.Local = previousLocal })

	dir := os.Getenv(corpusEnv)
	if dir == "" {
		dir = defaultCorpus
	}

	report, err := Run(dir, filepath.Join(dir, "divergences", "dws-jq.yaml"))
	if err != nil {
		t.Fatalf("corpus %s is malformed: %v", dir, err)
	}

	for _, res := range report.Results {
		t.Logf("%s  %s  %s", res.Verdict, res.Test, res.Reason)
	}
	totals := report.Totals()
	t.Logf("totals: %d cases, %d pass, %d fail, %d known divergence, %d stale divergence",
		len(report.Results), totals[VerdictPass], totals[VerdictFail],
		totals[VerdictKnownDivergence], totals[VerdictStaleDivergence])
	if !report.OK() {
		t.Fatal("expression corpus: every case must pass or be a still-diverging listed case")
	}
}
