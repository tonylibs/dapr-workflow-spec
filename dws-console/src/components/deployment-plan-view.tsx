import type { DeploymentPlan } from "#/lib/admin-client";

/**
 * Renders what a definition would deploy. Note what is deliberately absent: the
 * DSL's control-flow tasks (`switch`/`set`/`wait`/`try`/`for`/`fork`) never
 * appear in a DeploymentPlan — it is the deployable-resource view, not the task
 * graph. `specText` is not re-rendered; the operator is looking at it.
 *
 * Raw `<table className="tbl">` rather than `data-table.tsx`: those helpers
 * render a TanStack table instance, and a plan is a fixed, three-column,
 * non-interactive listing with no sorting, filtering, or row model to own. The
 * console's own table styling still applies, and `<caption>`/`<th scope>` name
 * each grid for a screen reader that meets it without the surrounding prose.
 */
export function DeploymentPlanView({ plan }: { plan: DeploymentPlan }) {
	return (
		<div className="pane" style={{ gap: 12 }}>
			<div>
				<h3 className="pane-title">Would deploy {plan.version}</h3>
				<p className="pane-lede">
					Nothing was applied. {plan.steps.length} step
					{plan.steps.length === 1 ? "" : "s"}, {plan.bindings.length} binding
					{plan.bindings.length === 1 ? "" : "s"}.
				</p>
			</div>

			{plan.steps.length > 0 && (
				<div className="tbl-wrap">
					<table className="tbl">
						<caption className="muted" style={captionStyle}>
							Step services
						</caption>
						<thead>
							<tr>
								<th scope="col">Task</th>
								<th scope="col">Kind</th>
								<th scope="col">Image</th>
							</tr>
						</thead>
						<tbody>
							{plan.steps.map((step) => (
								<tr key={step.name}>
									<td>{step.name}</td>
									<td>{step.kind}</td>
									<td>{step.image}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}

			{plan.bindings.length > 0 && (
				<div className="tbl-wrap">
					<table className="tbl">
						<caption className="muted" style={captionStyle}>
							Topic bindings
						</caption>
						<thead>
							<tr>
								<th scope="col">Task</th>
								<th scope="col">Direction</th>
								<th scope="col">Topic</th>
							</tr>
						</thead>
						<tbody>
							{plan.bindings.map((binding) => (
								<tr key={`${binding.task}:${binding.topic}`}>
									<td>{binding.task}</td>
									<td>{binding.direction}</td>
									<td>{binding.topic}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}

			<p className="muted">
				Orchestrator {plan.orchestrator.name} · {plan.orchestrator.image}
			</p>
		</div>
	);
}

/**
 * A caption is centered by default and would read as a stray heading above the
 * grid; left-aligning it on the same gutter as the first column keeps it
 * attached to the table it names.
 */
const captionStyle = {
	textAlign: "left",
	padding: "10px 12px 0",
	fontSize: 12,
} as const;
