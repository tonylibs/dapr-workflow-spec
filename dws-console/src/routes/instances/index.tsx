import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import {
	Banner,
	EmptyState,
	StateSwitch,
	type ViewState,
} from "#/components/states";
import { InstanceStatusBadge } from "#/components/status";
import {
	INSTANCE_STATUSES,
	type InstanceStatus,
	instances,
	workflows,
} from "#/lib/mock-data";

export const Route = createFileRoute("/instances/")({
	component: InstanceList,
});

const COLS = [22, 18, 10, 16, 17, 17];

function InstanceList() {
	const navigate = useNavigate();
	const [state, setState] = useState<ViewState>("data");
	const [workflow, setWorkflow] = useState<string | null>("workflow-a");
	const [statuses, setStatuses] = useState<Set<InstanceStatus>>(
		new Set(["RUNNING", "FAILED"]),
	);

	const toggleStatus = (s: InstanceStatus) =>
		setStatuses((prev) => {
			const next = new Set(prev);
			next.has(s) ? next.delete(s) : next.add(s);
			return next;
		});

	const clear = () => {
		setWorkflow(null);
		setStatuses(new Set());
	};

	const cycleWorkflow = () => {
		const names = workflows.map((w) => w.name);
		if (workflow === null) return setWorkflow(names[0]);
		const i = names.indexOf(workflow);
		setWorkflow(i + 1 >= names.length ? null : names[i + 1]);
	};

	const filterCount = (workflow ? 1 : 0) + statuses.size;
	const rows = instances.filter(
		(r) =>
			(!workflow || r.workflow === workflow) &&
			(statuses.size === 0 || statuses.has(r.status)),
	);

	return (
		<AppLayout
			active="instances"
			crumbs={[{ label: "Instances", heading: true }]}
			topRight={
				<>
					<StateSwitch value={state} onChange={setState} />
					<span className="muted" style={{ fontSize: 12 }}>
						most recent first
					</span>
				</>
			}
		>
			<div className="pane" style={{ gap: 16 }}>
				<div>
					<h2 className="pane-title">Instances</h2>
					<p className="pane-lede">
						Runtime executions across all workflow definitions.
					</p>
				</div>

				<div className="filters">
					<button type="button" className="wf-select" onClick={cycleWorkflow}>
						<span className="muted">workflow</span>
						<span className="mono" style={{ fontWeight: 600 }}>
							{workflow ?? "all"}
						</span>
						<span className="caret">▾</span>
					</button>
					<span className="muted" style={{ fontSize: 12 }}>
						·
					</span>
					<span className="muted" style={{ fontSize: 12 }}>
						status:
					</span>
					{INSTANCE_STATUSES.map((s) => (
						<button
							key={s}
							type="button"
							className={`fchip${statuses.has(s) ? " active" : ""}`}
							onClick={() => toggleStatus(s)}
						>
							{s}
						</button>
					))}
					<span className="muted" style={{ fontSize: 12, marginLeft: "auto" }}>
						{filterCount} filter{filterCount === 1 ? "" : "s"} ·{" "}
						<button
							type="button"
							onClick={clear}
							style={{
								color: "var(--color-accent)",
								background: "none",
								border: "none",
								cursor: "pointer",
								font: "inherit",
								padding: 0,
							}}
						>
							clear
						</button>
					</span>
				</div>

				{state === "error" && (
					<Banner variant="warn">
						<code>limit</code> out of range — showing default 50.
					</Banner>
				)}

				{state === "empty" || (state === "data" && rows.length === 0) ? (
					<div className="tbl-wrap">
						{state === "empty" ? (
							<EmptyState title="No instances yet">
								Once a deployed workflow runs, its executions appear here — most
								recent first.
							</EmptyState>
						) : (
							<EmptyState title="No instances match these filters">
								<button
									type="button"
									onClick={clear}
									className="btn-sm"
									style={{ marginTop: 8 }}
								>
									Reset filters
								</button>
							</EmptyState>
						)}
					</div>
				) : (
					<div className="tbl-wrap">
						{state === "loading" ? (
							<>
								<table className="tbl">
									<thead>
										<Head />
									</thead>
								</table>
								<SkeletonRows rows={6} cols={COLS} />
							</>
						) : (
							<table className="tbl">
								<thead>
									<Head />
								</thead>
								<tbody>
									{rows.map((r) => (
										<tr
											key={r.id}
											className="clickable"
											onClick={() =>
												navigate({ to: "/instances/$id", params: { id: r.id } })
											}
										>
											<td className="mono">{r.id}</td>
											<td className="mono">{r.workflow}</td>
											<td className="mono">{r.version}</td>
											<td>
												<InstanceStatusBadge status={r.status} />
											</td>
											<td className="muted">{r.started}</td>
											<td className="muted">
												{r.ended ?? <em>in progress</em>}
											</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>
				)}

				{state === "data" && rows.length > 0 && (
					<div className="pager">
						<span>
							{rows.length} shown ·{" "}
							<span className="muted">
								more available — cursor: <span className="mono">…d3fe</span>
							</span>
						</span>
						<div className="pager-actions">
							<button type="button" className="btn-sm primary">
								Load more
							</button>
						</div>
					</div>
				)}
				{state === "loading" && (
					<div className="pager">
						<Skeleton width={140} />
						<div className="pager-actions">
							<Skeleton width={92} height={30} style={{ borderRadius: 999 }} />
						</div>
					</div>
				)}
			</div>
		</AppLayout>
	);
}

function Head() {
	const labels = [
		"Instance ID",
		"Workflow",
		"Version",
		"Status",
		"Started",
		"Ended",
	];
	return (
		<tr>
			{labels.map((l, i) => (
				<th key={l} style={{ width: `${COLS[i]}%` }}>
					{l}
				</th>
			))}
		</tr>
	);
}
