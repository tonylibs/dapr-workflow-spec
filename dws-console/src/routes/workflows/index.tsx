import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import {
	createColumnHelper,
	tableFeatures,
	useTable,
} from "@tanstack/react-table";
import { Search } from "lucide-react";
import { AppLayout } from "#/components/app-layout";
import { DataTableHead, DataTableRows } from "#/components/data-table";
import { SkeletonRows } from "#/components/skeleton";
import { Banner, EmptyState } from "#/components/states";
import { WorkflowTag } from "#/components/status";
import { useWorkflows } from "#/lib/admin-hooks";
import type { WorkflowRow } from "#/lib/mock-data";

export const Route = createFileRoute("/workflows/")({
	component: WorkflowList,
});

// Core-only table: no optional row-model features needed.
const features = tableFeatures({});
const col = createColumnHelper<typeof features, WorkflowRow>();
const columns = col.columns([
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
]);

const COLS = [44, 20, 22, 14];

function WorkflowList() {
	const navigate = useNavigate();
	const {
		rows,
		isPending,
		isError,
		refetch,
		hasNextPage,
		fetchNextPage,
		isFetchingNextPage,
	} = useWorkflows();

	const table = useTable({
		features,
		data: rows,
		columns,
	});

	return (
		<AppLayout
			active="workflows"
			crumbs={[{ label: "Workflows", heading: true }]}
			topRight={
				<span className="muted" style={{ fontSize: 12 }}>
					reading from <b style={{ color: "var(--color-text)" }}>dws-admin</b>
				</span>
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
					<div style={{ display: "flex", alignItems: "center", gap: 12 }}>
						<label className="search">
							<Search />
							<input placeholder="filter by name…" />
						</label>
						<Link to="/workflows/new" className="btn-sm primary">
							New definition
						</Link>
					</div>
				</div>

				{isError && (
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
						Could not load workflows from <code>dws-admin</code>.
					</Banner>
				)}

				{!isPending && !isError && rows.length === 0 ? (
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
							{isPending ? null : (
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
						{isPending && <SkeletonRows rows={5} cols={COLS} />}
					</div>
				)}

				{!isPending && !isError && rows.length > 0 && (
					<div className="pager">
						<span>
							Showing {rows.length}{" "}
							<span className="muted">
								{hasNextPage
									? "· more available — cursor pagination"
									: "· all loaded"}
							</span>
						</span>
						<div className="pager-actions">
							<button
								type="button"
								className="btn-sm"
								disabled={!hasNextPage || isFetchingNextPage}
								onClick={() => fetchNextPage()}
							>
								{isFetchingNextPage ? "Loading…" : "Load more"}
							</button>
						</div>
					</div>
				)}
			</div>
		</AppLayout>
	);
}
