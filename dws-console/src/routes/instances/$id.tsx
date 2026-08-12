import { createFileRoute, Link } from "@tanstack/react-router";
import {
	createColumnHelper,
	createExpandedRowModel,
	type ExpandedState,
	flexRender,
	rowExpandingFeature,
	tableFeatures,
	useTable,
} from "@tanstack/react-table";
import { Check, X } from "lucide-react";
import { Fragment, useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DataTableHead } from "#/components/data-table";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import { Banner, EmptyState } from "#/components/states";
import {
	InstanceStatusBadge,
	TaskStatusBadge,
	TaskTypeBadge,
} from "#/components/status";
import { ApiError } from "#/lib/admin-client";
import { useInstanceDetail } from "#/lib/admin-hooks";
import { statusClass, type TaskEvent } from "#/lib/mock-data";

export const Route = createFileRoute("/instances/$id")({
	component: InstanceDetail,
});

// Row-expanding feature drives the inline attempt/backoff mini-timeline. The
// read API carries no attempt history today, so rows stay collapsed until it
// does — see `getRowCanExpand` below.
const features = tableFeatures({
	rowExpandingFeature,
	expandedRowModel: createExpandedRowModel(),
});
const tcol = createColumnHelper<typeof features, TaskEvent>();
const taskColumns = tcol.columns([
	tcol.accessor("name", {
		header: "Task",
		meta: { width: "34%" },
		cell: ({ row }) => {
			const t = row.original;
			return (
				<span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
					{row.getCanExpand() && (
						<span className="disclosure">
							{row.getIsExpanded() ? "▾" : "▸"}
						</span>
					)}
					<span
						className="mono"
						style={t.indent ? { paddingLeft: 16 } : undefined}
					>
						{t.indent ? "↳ " : ""}
						{t.name}
					</span>
					{t.attempts != null && (
						<span className="attempts-tag">
							{t.attempts} attempt{t.attempts === 1 ? "" : "s"}
						</span>
					)}
				</span>
			);
		},
	}),
	tcol.accessor("type", {
		header: "Type",
		meta: { width: "14%" },
		cell: (c) => <TaskTypeBadge type={c.getValue()} />,
	}),
	tcol.accessor("status", {
		header: "Status",
		meta: { width: "20%" },
		cell: ({ row }) => (
			<TaskStatusBadge
				status={row.original.status}
				label={row.original.statusLabel}
			/>
		),
	}),
	tcol.accessor("when", {
		header: "When",
		meta: { width: "18%", cellClass: "mono muted" },
	}),
	tcol.accessor("duration", {
		header: "Duration",
		meta: { width: "14%", cellClass: "mono muted" },
	}),
]);

function InstanceDetail() {
	const { id } = Route.useParams();
	const [expanded, setExpanded] = useState<ExpandedState>({});

	const { data: detail, isPending, error, refetch } = useInstanceDetail(id);

	// A 404 means the read model has no record for this id — distinct from
	// dws-admin being unreachable.
	const notFound = error instanceof ApiError && error.status === 404;

	const table = useTable({
		features,
		data: detail?.tasks ?? [],
		columns: taskColumns,
		state: { expanded },
		onExpandedChange: setExpanded,
		getRowId: (t) => t.name,
		getRowCanExpand: (row) => !!row.original.attemptHistory,
	});

	const crumbs = [
		{ label: "Instances", to: "/instances" },
		{ label: id, mono: true },
	];

	if (notFound) {
		return (
			<AppLayout active="instances" crumbs={crumbs}>
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

	if (error) {
		return (
			<AppLayout active="instances" crumbs={crumbs}>
				<div className="pane">
					<Banner
						action={
							<button
								type="button"
								className="btn-sm"
								onClick={() => refetch()}
							>
								Retry
							</button>
						}
					>
						Could not load instance <code>{id}</code> from{" "}
						<code>dws-admin</code>.
					</Banner>
				</div>
			</AppLayout>
		);
	}

	if (isPending || !detail) {
		return (
			<AppLayout active="instances" crumbs={crumbs}>
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

	return (
		<AppLayout
			active="instances"
			crumbs={crumbs}
			topRight={
				<button type="button" className="btn-sm" onClick={() => refetch()}>
					Refresh
				</button>
			}
		>
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
							chronological · one row per task
						</span>
					</div>

					{d.tasks.length === 0 ? (
						<div className="tbl-wrap">
							<EmptyState title="Task events not yet reported">
								The read model is eventually consistent — task events for a
								fresh instance can lag a moment behind the run.
							</EmptyState>
						</div>
					) : (
						<div className="tbl-wrap">
							<table className="task-tbl">
								<DataTableHead table={table} />
								<tbody>
									{table.getRowModel().rows.map((row) => {
										const expandable = row.getCanExpand();
										const open = row.getIsExpanded();
										return (
											<Fragment key={row.id}>
												<tr
													className={
														expandable
															? `expandable${open ? " expanded" : ""}`
															: undefined
													}
													onClick={
														expandable
															? row.getToggleExpandedHandler()
															: undefined
													}
												>
													{row.getAllCells().map((cell) => (
														<td
															key={cell.id}
															className={cell.column.columnDef.meta?.cellClass}
														>
															{flexRender(
																cell.column.columnDef.cell,
																cell.getContext(),
															)}
														</td>
													))}
												</tr>
												{expandable && open && (
													<MiniTimeline task={row.original} />
												)}
											</Fragment>
										);
									})}
								</tbody>
							</table>
						</div>
					)}
				</div>
			</div>
		</AppLayout>
	);
}

/**
 * Inline attempt/backoff mini-timeline for an expanded task.
 *
 * Only rendered when a task carries `attemptHistory`, which the read API does
 * not populate today — kept so the view is ready the moment `dws-admin` starts
 * recording attempt-level events.
 */
function MiniTimeline({ task }: { task: TaskEvent }) {
	return (
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
									style={a.kind === "backoff" ? { fontWeight: 500 } : undefined}
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
	);
}
