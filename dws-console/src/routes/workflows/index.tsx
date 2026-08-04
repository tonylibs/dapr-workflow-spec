import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { Search } from "lucide-react";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import {
	Banner,
	EmptyState,
	StateSwitch,
	type ViewState,
} from "#/components/states";
import { WorkflowTag } from "#/components/status";
import { workflows } from "#/lib/mock-data";

export const Route = createFileRoute("/workflows/")({
	component: WorkflowList,
});

const COLS = [44, 20, 22, 14];

function WorkflowList() {
	const navigate = useNavigate();
	const [state, setState] = useState<ViewState>("data");

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
						{state === "loading" ? (
							<>
								<table className="tbl">
									<thead>
										<Head />
									</thead>
								</table>
								<SkeletonRows rows={5} cols={COLS} />
							</>
						) : (
							<table className="tbl">
								<thead>
									<Head />
								</thead>
								<tbody>
									{workflows.map((w) => (
										<tr
											key={w.name}
											className="clickable"
											onClick={() =>
												navigate({
													to: "/workflows/$name",
													params: { name: w.name },
												})
											}
										>
											<td className="mono">{w.name}</td>
											<td className="mono">{w.latestVersion}</td>
											<td>
												<WorkflowTag status={w.status} />
											</td>
											<td className="muted">{w.updated}</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
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

function Head() {
	return (
		<tr>
			<th style={{ width: `${COLS[0]}%` }}>Name</th>
			<th style={{ width: `${COLS[1]}%` }}>Latest version</th>
			<th style={{ width: `${COLS[2]}%` }}>Status</th>
			<th style={{ width: `${COLS[3]}%` }}>Updated</th>
		</tr>
	);
}
