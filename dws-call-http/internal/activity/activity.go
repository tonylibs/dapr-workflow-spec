// Package activity adapts the step runner to a Dapr Workflow activity worker.
// Each deployed step registers a single canonical activity named Run whose
// handler wraps runner.Run, preserving the OUTPUT shaping and the
// upstream-vs-config failure distinction the HTTP POST /run handler carried.
package activity

import (
	"context"
	"errors"
	"fmt"

	"github.com/dapr/durabletask-go/workflow"

	"github.com/dws/dws-call-http/internal/runner"
)

// Name is the canonical activity name every deployed step registers. Multi-app
// dispatch is disambiguated by Dapr app-id, so the name is stable across steps.
const Name = "Run"

// StepRunner is the step-execution surface the activity wraps. *runner.Runner
// satisfies it; tests supply a fake.
type StepRunner interface {
	Run(ctx context.Context, input map[string]any) (any, error)
}

// Run executes the step for the given workflow-data input and returns the value
// the orchestrator applies to the workflow data document. A nil input is treated
// as empty workflow data ({}); a nil runner result leaves the data unchanged
// (the input is returned). Runner failures are mapped to the two activity
// failure-message forms the Java orchestrator classifies on: upstream/transport
// faults are retryable ("upstream failure"), everything else is not ("config
// failure").
func Run(ctx context.Context, r StepRunner, task string, input map[string]any) (any, error) {
	if input == nil {
		input = map[string]any{}
	}

	out, err := r.Run(ctx, input)
	if err != nil {
		return nil, classify(task, err)
	}
	if out == nil {
		return input, nil
	}
	return out, nil
}

// classify maps a runner error to the activity failure whose message the
// orchestrator reads. Upstream and transport faults become the retryable
// "upstream failure" marker (the activity-path equivalent of the old HTTP 502);
// all other errors (config, shaping, decode) become the non-retryable "config
// failure" marker.
func classify(task string, err error) error {
	var upstream *runner.UpstreamError
	var transport *runner.TransportError
	if errors.As(err, &upstream) || errors.As(err, &transport) {
		return fmt.Errorf("step '%s' upstream failure: %s", task, err.Error())
	}
	return fmt.Errorf("step '%s' config failure: %s", task, err.Error())
}

// Handler adapts Run to a Dapr Workflow activity. The activity input is the
// current workflow data; an absent or empty input decodes to nil, which Run
// treats as empty workflow data ({}).
func Handler(r StepRunner, task string) workflow.Activity {
	return func(actx workflow.ActivityContext) (any, error) {
		var input map[string]any
		if err := actx.GetInput(&input); err != nil {
			return nil, fmt.Errorf("step '%s' config failure: decode activity input: %s", task, err.Error())
		}
		return Run(actx.Context(), r, task, input)
	}
}
