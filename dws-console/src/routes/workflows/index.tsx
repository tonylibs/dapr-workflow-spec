import { createFileRoute, useNavigate } from "@tanstack/react-router";
import {
	createColumnHelper,
	getCoreRowModel,
	useReactTable,
} from "@tanstack/react-table";
import { Search } from "lucide-react";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DataTableHead, DataTableRows } from "#/components/data-table";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import {
	Banner,
	EmptyState,
	StateSwitch,
	type ViewState,
} from "#/components/states";
import { WorkflowTag } from "#/components/status";
import { type WorkflowRow, workflows } from "#/lib/mock-data";

export const Route = createFileRoute("/workflows/")({
	component: WorkflowList,
});

const col = createColumnHelper<WorkflowRow>();
const columns = [
	col.accessor("name", {
		header: "Name",
		meta: { width: "44%", cellClass: "mono" },
	}),
	col.accessor("latestVersion", {
		header: "Latest version",
		meta: { width: "20%", cellClass: "mono" },
	}),
	col.accessor("status", {
		header: "Status",
		cell: (c) => <WorkflowTag status={c.getValue()} />,
		meta: { width: "22%" },
	}),
	col.accessor("updated", {
		header: "Updated",
		meta: { width: "14%", cellClass: "muted" },
	}),
];

const COLS = [44, 20, 22, 14];

function WorkflowList() {
	const navigate = useNavigate();
	const [state, setState] = useState<ViewState>("data");

	const table = useReactTable({
		data: workflows,
		columns,
		getCoreRowModel: getCoreRowModel(),
	});

	return (
		<AppLayout
			active="workflows"
			crumbs={[{ label: "Workflows", heading: true }]}
			topRight={
				<>
					<StateSwitch value={state} onChange={setState} />
					<span className="muted" style={{ fontSize: 12 }}>
						cluster: <b style={{ color: "var(--color-text)" }}>prod-eu</b>
					</span>
				</>
			}
		>
			<div className="pane">
				<div
					style={{
						display: "flex",
						justifyContent: "space-between",
						alignItems: "baseline",
					}}
				>
					<div>
						<h2 className="pane-title">Workflows</h2>
						<p className="pane-lede">
							One row per workflow name — its latest version and status.
						</p>
					</div>
					<label className="search">
						<Search />
						<input placeholder="filter by name…" />
					</label>
				</div>

				{state === "error" && (
					<Banner
						action={
							<button type="button" className="btn-sm">
								Reset filters
							</button>
						}
					>
						Filter rejected — the request returned <b>400 Bad Request</b>.
					</Banner>
				)}

				{state === "empty" ? (
					<div className="tbl-wrap">
						<EmptyState title="No workflows deployed yet">
							Deploy a definition with <code>POST /workflows</code> and it will
							show up here with its latest version and status.
						</EmptyState>
					</div>
				) : (
					<div className="tbl-wrap">
						<table className="tbl">
							<DataTableHead table={table} />
							{state === "loading" ? null : (
								<DataTableRows
									table={table}
									onRowClick={(w) =>
										navigate({
											to: "/workflows/$name",
											params: { name: w.name },
										})
									}
								/>
							)}
						</table>
						{state === "loading" && <SkeletonRows rows={5} cols={COLS} />}
					</div>
				)}

				{state === "data" && (
					<div className="pager">
						<span>
							Showing {workflows.length} of {workflows.length} ·{" "}
							<span className="muted">
								cursor pagination — API drives “Load more”
							</span>
						</span>
						<div className="pager-actions">
							<button type="button" className="btn-sm" disabled>
								Load more
							</button>
						</div>
					</div>
				)}
				{state === "loading" && (
					<div className="pager">
						<Skeleton width={180} />
					</div>
				)}
			</div>
		</AppLayout>
	);
}
