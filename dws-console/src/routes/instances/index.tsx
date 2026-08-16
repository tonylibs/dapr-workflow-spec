import { createFileRoute, useNavigate } from "@tanstack/react-router";
import {
	createColumnHelper,
	tableFeatures,
	useTable,
} from "@tanstack/react-table";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DataTableHead, DataTableRows } from "#/components/data-table";
import { SkeletonRows } from "#/components/skeleton";
import { Banner, EmptyState } from "#/components/states";
import { InstanceStatusBadge } from "#/components/status";
import {
	useInstanceListLiveUpdates,
	useInstances,
	useWorkflowNames,
} from "#/lib/admin-hooks";
import {
	INSTANCE_STATUSES,
	type InstanceRow,
	type InstanceStatus,
} from "#/lib/mock-data";

export const Route = createFileRoute("/instances/")({
	component: InstanceList,
});

// Core-only table: filtering happens server-side (see `useInstances`), so no
// client-side filter feature is needed — it could only ever filter the pages
// already fetched.
const features = tableFeatures({});
const col = createColumnHelper<typeof features, InstanceRow>();
const columns = col.columns([
	col.accessor("id", {
		header: "Instance ID",
		meta: { width: "22%", cellClass: "mono" },
	}),
	col.accessor("workflow", {
		header: "Workflow",
		meta: { width: "18%", cellClass: "mono" },
	}),
	col.accessor("version", {
		header: "Version",
		meta: { width: "10%", cellClass: "mono" },
	}),
	col.accessor("status", {
		header: "Status",
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
	const [workflow, setWorkflow] = useState<string | null>(null);
	const [status, setStatus] = useState<InstanceStatus | null>(null);

	// `GET /instances` takes one status, so the chips are a single choice
	// rather than the multi-select the mock allowed.
	const filters = {
		workflow: workflow ?? undefined,
		status: status ?? undefined,
	};

	const {
		rows,
		isPending,
		isError,
		error,
		refetch,
		hasNextPage,
		fetchNextPage,
		isFetchingNextPage,
	} = useInstances(filters);

	// Patches the status of rows already loaded as instances finish. Rows the
	// operator has not paged to are left alone — see `applyStatusDelta`.
	useInstanceListLiveUpdates(filters);

	// Every workflow name, not just the first page — otherwise the filter
	// silently cannot reach workflows past page 1.
	const { data: workflowNames = [] } = useWorkflowNames();

	const clear = () => {
		setWorkflow(null);
		setStatus(null);
	};

	const cycleWorkflow = () => {
		const names = workflowNames;
		if (names.length === 0) return;
		if (workflow === null) return setWorkflow(names[0]);
		const i = names.indexOf(workflow);
		setWorkflow(i + 1 >= names.length ? null : names[i + 1]);
	};

	const table = useTable({
		features,
		data: rows,
		columns,
	});

	const filterCount = (workflow ? 1 : 0) + (status ? 1 : 0);

	return (
		<AppLayout
			active="instances"
			crumbs={[{ label: "Instances", heading: true }]}
			topRight={
				<span className="muted" style={{ fontSize: 12 }}>
					most recent first
				</span>
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
							className={`fchip${status === s ? " active" : ""}`}
							onClick={() => setStatus(status === s ? null : s)}
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
						{error?.name === "ApiError" ? (
							<>
								<code>dws-admin</code> rejected the request — {error.message}
							</>
						) : (
							<>
								Could not load instances from <code>dws-admin</code>.
							</>
						)}
					</Banner>
				)}

				{!isPending && !isError && rows.length === 0 ? (
					<div className="tbl-wrap">
						{filterCount === 0 ? (
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
							{isPending ? null : (
								<DataTableRows
									table={table}
									onRowClick={(r) =>
										navigate({ to: "/instances/$id", params: { id: r.id } })
									}
								/>
							)}
						</table>
						{isPending && <SkeletonRows rows={6} cols={COLS} />}
					</div>
				)}

				{!isPending && !isError && rows.length > 0 && (
					<div className="pager">
						<span>
							{rows.length} shown{" "}
							<span className="muted">
								{hasNextPage
									? "· more available — cursor pagination"
									: "· all loaded"}
							</span>
						</span>
						<div className="pager-actions">
							<button
								type="button"
								className="btn-sm primary"
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
