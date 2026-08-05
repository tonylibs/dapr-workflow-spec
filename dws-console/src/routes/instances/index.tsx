import { createFileRoute, useNavigate } from "@tanstack/react-router";
import {
	type ColumnFiltersState,
	columnFilteringFeature,
	createColumnHelper,
	createFilteredRowModel,
	filterFn_equalsString,
	tableFeatures,
	useTable,
} from "@tanstack/react-table";
import { useMemo, useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DataTableHead, DataTableRows } from "#/components/data-table";
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
	type InstanceRow,
	type InstanceStatus,
	instances,
	workflows,
} from "#/lib/mock-data";

export const Route = createFileRoute("/instances/")({
	component: InstanceList,
});

// Column filtering feature + the built-in `equalsString` filterFn the
// workflow column references by name.
const features = tableFeatures({
	columnFilteringFeature,
	filteredRowModel: createFilteredRowModel(),
	filterFns: { equalsString: filterFn_equalsString },
});
const col = createColumnHelper<typeof features, InstanceRow>();
const columns = col.columns([
	col.accessor("id", {
		header: "Instance ID",
		meta: { width: "22%", cellClass: "mono" },
	}),
	col.accessor("workflow", {
		header: "Workflow",
		filterFn: "equalsString",
		meta: { width: "18%", cellClass: "mono" },
	}),
	col.accessor("version", {
		header: "Version",
		meta: { width: "10%", cellClass: "mono" },
	}),
	col.accessor("status", {
		header: "Status",
		// multi-select: keep the row if its status is one of the picked chips
		filterFn: (row, id, picked: InstanceStatus[]) =>
			picked.length === 0 || picked.includes(row.getValue(id)),
		cell: (c) => <InstanceStatusBadge status={c.getValue()} />,
		meta: { width: "16%" },
	}),
	col.accessor("started", {
		header: "Started",
		meta: { width: "17%", cellClass: "muted" },
	}),
	col.accessor("ended", {
		header: "Ended",
		cell: (c) => c.getValue() ?? <em>in progress</em>,
		meta: { width: "17%", cellClass: "muted" },
	}),
]);

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

	// Drive TanStack's column filters from the chip/select UI state.
	const columnFilters = useMemo<ColumnFiltersState>(() => {
		const f: ColumnFiltersState = [];
		if (workflow) f.push({ id: "workflow", value: workflow });
		if (statuses.size > 0)
			f.push({ id: "status", value: Array.from(statuses) });
		return f;
	}, [workflow, statuses]);

	const table = useTable({
		features,
		data: instances,
		columns,
		state: { columnFilters },
	});

	const rows = table.getRowModel().rows;
	const filterCount = (workflow ? 1 : 0) + statuses.size;

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
						<table className="tbl">
							<DataTableHead table={table} />
							{state === "loading" ? null : (
								<DataTableRows
									table={table}
									onRowClick={(r) =>
										navigate({ to: "/instances/$id", params: { id: r.id } })
									}
								/>
							)}
						</table>
						{state === "loading" && <SkeletonRows rows={6} cols={COLS} />}
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
