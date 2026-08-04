import { createFileRoute, Link } from "@tanstack/react-router";
import { Check, X } from "lucide-react";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import { EmptyState, StateSwitch, type ViewState } from "#/components/states";
import {
	InstanceStatusBadge,
	TaskStatusBadge,
	TaskTypeBadge,
} from "#/components/status";
import {
	getInstanceDetail,
	statusClass,
	type TaskEvent,
} from "#/lib/mock-data";

export const Route = createFileRoute("/instances/$id")({
	component: InstanceDetail,
});

function InstanceDetail() {
	const { id } = Route.useParams();
	const [state, setState] = useState<ViewState>("data");
	const [expanded, setExpanded] = useState<Set<string>>(
		new Set(["dispatch-shipment"]),
	);

	const detail = getInstanceDetail(id);
	const notFound = state === "error" || !detail;

	const crumbs = [
		{ label: "Instances", to: "/instances" },
		{ label: id, mono: true },
	];
	const topRight = (
		<>
			<button type="button" className="btn-sm">
				Copy id
			</button>
			<button type="button" className="btn-sm">
				Open in logs ↗
			</button>
			<StateSwitch value={state} onChange={setState} />
		</>
	);

	if (notFound) {
		return (
			<AppLayout active="instances" crumbs={crumbs} topRight={topRight}>
				<div className="pane">
					<EmptyState title={`No instance with id “${id}”`}>
						The read model has no record for that id. It may have been pruned,
						or the id is mistyped.
					</EmptyState>
					<div style={{ textAlign: "center" }}>
						<Link to="/instances" className="btn-sm">
							← Back to instances
						</Link>
					</div>
				</div>
			</AppLayout>
		);
	}

	if (state === "loading") {
		return (
			<AppLayout active="instances" crumbs={crumbs} topRight={topRight}>
				<div className="pane" style={{ gap: 20 }}>
					<div className="id-header">
						<div>
							<Skeleton width={280} height={22} />
							<div className="id-meta" style={{ marginTop: 18 }}>
								{Array.from({ length: 8 }, (_, i) => (
									// biome-ignore lint/suspicious/noArrayIndexKey: fixed-count skeleton fields, no stable id
									<div key={`meta-skel-${i}`}>
										<Skeleton width={70} height={10} />
										<Skeleton width={110} style={{ marginTop: 6 }} />
									</div>
								))}
							</div>
						</div>
					</div>
					<div className="tbl-wrap">
						<SkeletonRows rows={6} cols={[34, 14, 20, 18, 14]} />
					</div>
				</div>
			</AppLayout>
		);
	}

	const d = detail;
	const started = d.started ?? "in progress";
	const ended = d.ended ?? (d.status === "RUNNING" ? "in progress" : "pending");

	const toggle = (name: string) =>
		setExpanded((prev) => {
			const next = new Set(prev);
			next.has(name) ? next.delete(name) : next.add(name);
			return next;
		});

	return (
		<AppLayout active="instances" crumbs={crumbs} topRight={topRight}>
			<div className="pane" style={{ gap: 20 }}>
				{/* header card */}
				<div className="id-header">
					<div>
						<div className="id-title">
							<h3>Instance</h3>
							<span className="mono muted" style={{ fontSize: 14 }}>
								{d.id}
							</span>
							<InstanceStatusBadge
								status={d.status}
								style={{ marginLeft: 6 }}
							/>
						</div>
						<dl className="id-meta">
							<div>
								<dt>Workflow</dt>
								<dd>{d.workflow}</dd>
							</div>
							<div>
								<dt>Version</dt>
								<dd>{d.version}</dd>
							</div>
							<div>
								<dt>Orchestrator app</dt>
								<dd>{d.orchestrator}</dd>
							</div>
							<div>
								<dt>Duration</dt>
								<dd>{d.duration}</dd>
							</div>
							<div>
								<dt>Started</dt>
								<dd>{started}</dd>
							</div>
							<div>
								<dt>Ended</dt>
								<dd>{ended}</dd>
							</div>
							<div>
								<dt>Tasks</dt>
								<dd>
									{d.taskCount} ·{" "}
									<span
										className="muted"
										style={{ fontFamily: "var(--font-body)" }}
									>
										{d.failedCount} failed
									</span>
								</dd>
							</div>
							<div>
								<dt>Retries</dt>
								<dd>{d.retries}</dd>
							</div>
						</dl>
					</div>
					<div className="id-actions">
						<button type="button" className="btn-sm">
							Refresh
						</button>
					</div>
				</div>

				{/* task timeline */}
				<div>
					<div
						style={{
							display: "flex",
							alignItems: "baseline",
							justifyContent: "space-between",
							marginBottom: 10,
						}}
					>
						<h3 style={{ fontFamily: "var(--font-heading)", fontSize: 20 }}>
							Task timeline
						</h3>
						<span className="muted" style={{ fontSize: 12 }}>
							chronological · click a task to expand
						</span>
					</div>

					{state === "empty" || d.tasks.length === 0 ? (
						<div className="tbl-wrap">
							<EmptyState title="Task events not yet reported">
								The read model is eventually consistent — task events for a
								fresh instance can lag a moment behind the run.
							</EmptyState>
						</div>
					) : (
						<div className="tbl-wrap">
							<table className="task-tbl">
								<thead>
									<tr>
										<th style={{ width: "34%" }}>Task</th>
										<th style={{ width: "14%" }}>Type</th>
										<th style={{ width: "20%" }}>Status</th>
										<th style={{ width: "18%" }}>When</th>
										<th style={{ width: "14%" }}>Duration</th>
									</tr>
								</thead>
								<tbody>
									{d.tasks.map((t) => (
										<TaskRow
											key={t.name}
											task={t}
											expanded={expanded.has(t.name)}
											onToggle={() => toggle(t.name)}
										/>
									))}
								</tbody>
							</table>
						</div>
					)}
				</div>
			</div>
		</AppLayout>
	);
}

function TaskRow({
	task,
	expanded,
	onToggle,
}: {
	task: TaskEvent;
	expanded: boolean;
	onToggle: () => void;
}) {
	const isExpandable = !!task.attemptHistory;
	return (
		<>
			<tr
				className={
					isExpandable ? `expandable${expanded ? " expanded" : ""}` : undefined
				}
				onClick={isExpandable ? onToggle : undefined}
			>
				<td>
					<span
						style={{ display: "inline-flex", alignItems: "center", gap: 8 }}
					>
						{isExpandable && (
							<span className="disclosure">{expanded ? "▾" : "▸"}</span>
						)}
						<span
							className="mono"
							style={task.indent ? { paddingLeft: 16 } : undefined}
						>
							{task.indent ? "↳ " : ""}
							{task.name}
						</span>
						{task.attempts != null && (
							<span className="attempts-tag">
								{task.attempts} attempt{task.attempts === 1 ? "" : "s"}
							</span>
						)}
					</span>
				</td>
				<td>
					<TaskTypeBadge type={task.type} />
				</td>
				<td>
					<TaskStatusBadge status={task.status} label={task.statusLabel} />
				</td>
				<td className="mono muted">{task.when}</td>
				<td className="mono muted">{task.duration}</td>
			</tr>

			{isExpandable && expanded && (
				<tr>
					<td colSpan={5} className="mini-cell">
						<div className="mini-wrap">
							<p className="mini-title">Attempt history · {task.retryPolicy}</p>
							<div className="mini">
								{(task.attemptHistory ?? []).map((a) => (
									<div
										key={`${a.label}-${a.time}`}
										className={`mini-node ${statusClass(a.status)}${a.kind === "attempt" && a.status === "failed" ? " err" : ""}`}
									>
										<span
											className={`marker${a.kind === "attempt" ? " filled" : ""}`}
										>
											{a.kind === "attempt" &&
												(a.status === "failed" ? <X /> : <Check />)}
										</span>
										<span
											className={`lbl${a.kind === "backoff" ? " muted" : ""}`}
											style={
												a.kind === "backoff" ? { fontWeight: 500 } : undefined
											}
										>
											{a.label}
										</span>
										<span
											className={`detail${a.kind === "backoff" ? " muted" : ""}`}
										>
											{a.detail}
										</span>
										<span className="t">{a.time}</span>
									</div>
								))}
								{task.caughtError && (
									<div className="mini-err-panel">
										<b>caught by:</b> catch →{" "}
										<span style={{ color: "var(--color-accent)" }}>
											{task.caughtBy}
										</span>
										<br />
										{task.caughtError}
									</div>
								)}
							</div>
						</div>
					</td>
				</tr>
			)}
		</>
	);
}
