import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { AppLayout } from "#/components/app-layout";
import { DefinitionGraph } from "#/components/definition-graph";
import { Skeleton, SkeletonRows } from "#/components/skeleton";
import { EmptyState, StateSwitch, type ViewState } from "#/components/states";
import { DeploymentTag, WorkflowTag } from "#/components/status";
import { getWorkflowDetail } from "#/lib/mock-data";

export const Route = createFileRoute("/workflows/$name")({
	component: WorkflowDetail,
});

type Tab = "versions" | "deployments" | "definition";

function WorkflowDetail() {
	const { name } = Route.useParams();
	const [state, setState] = useState<ViewState>("data");
	const [tab, setTab] = useState<Tab>("versions");

	const detail = getWorkflowDetail(name);
	const notFound = state === "error" || !detail;

	const crumbs = [
		{ label: "Workflows", to: "/workflows" },
		{ label: name, mono: true },
	];
	const topRight = <StateSwitch value={state} onChange={setState} />;

	if (notFound) {
		return (
			<AppLayout active="workflows" crumbs={crumbs} topRight={topRight}>
				<div className="pane">
					<EmptyState title={`No workflow named “${name}”`}>
						The controller has no definition under that name. It may have been
						drained, or the name is mistyped.
					</EmptyState>
					<div style={{ textAlign: "center" }}>
						<Link to="/workflows" className="btn-sm">
							← Back to workflows
						</Link>
					</div>
				</div>
			</AppLayout>
		);
	}

	if (state === "loading") {
		return (
			<AppLayout active="workflows" crumbs={crumbs} topRight={topRight}>
				<div className="pane" style={{ gap: 16 }}>
					<Skeleton width={260} height={26} />
					<div className="tabs">
						<Skeleton width={120} height={16} style={{ margin: "10px 16px" }} />
						<Skeleton width={110} height={16} style={{ margin: "10px 16px" }} />
					</div>
					<div className="tbl-wrap">
						<SkeletonRows rows={3} cols={[14, 26, 32, 28]} />
					</div>
					<div className="dep-grid">
						<div className="dep-card">
							<Skeleton width="60%" height={18} />
							<Skeleton width="90%" style={{ marginTop: 12 }} />
							<Skeleton width="80%" style={{ marginTop: 8 }} />
						</div>
						<div className="dep-card">
							<Skeleton width="60%" height={18} />
							<Skeleton width="90%" style={{ marginTop: 12 }} />
							<Skeleton width="80%" style={{ marginTop: 8 }} />
						</div>
					</div>
				</div>
			</AppLayout>
		);
	}

	const d = detail;

	return (
		<AppLayout
			active="workflows"
			crumbs={
				tab === "definition"
					? [...crumbs, { label: "definition", heading: true }]
					: crumbs
			}
			topRight={
				<>
					{tab === "definition" ? (
						<>
							<span
								className="muted"
								style={{ fontSize: 11.5, paddingRight: 6 }}
							>
								6 tasks · 1 switch · 1 try/catch · depth 4
							</span>
							<button type="button" className="btn-sm">
								Copy YAML
							</button>
							<button type="button" className="btn-sm">
								Download DSL
							</button>
						</>
					) : (
						<Link to="/instances" className="btn-sm">
							View instances →
						</Link>
					)}
					{topRight}
				</>
			}
		>
			<div className="pane" style={{ gap: 16 }}>
				<div style={{ display: "flex", alignItems: "baseline", gap: 14 }}>
					<h2
						className="mono"
						style={{
							fontFamily: "ui-monospace, Menlo, monospace",
							fontSize: 26,
						}}
					>
						{d.name}
					</h2>
					<WorkflowTag status={d.status} />
					{tab === "definition" ? (
						<>
							<span className="muted" style={{ fontSize: 12.5 }}>
								viewing{" "}
								<b className="mono" style={{ color: "var(--color-text)" }}>
									{d.latestVersion}
								</b>{" "}
								· DSL 1.0
							</span>
							<div className="wf-select" style={{ marginLeft: "auto" }}>
								<span className="muted">version</span>
								<span className="mono" style={{ fontWeight: 600 }}>
									{d.latestVersion}
								</span>
								<span style={{ opacity: 0.5 }}>▾</span>
							</div>
						</>
					) : (
						<span className="muted" style={{ fontSize: 12.5 }}>
							latest:{" "}
							<b className="mono" style={{ color: "var(--color-text)" }}>
								{d.latestVersion}
							</b>{" "}
							· {d.versions.length} versions
						</span>
					)}
				</div>

				<div className="tabs">
					<button
						type="button"
						className={`tab${tab === "versions" ? " active" : ""}`}
						onClick={() => setTab("versions")}
					>
						Version history
					</button>
					<button
						type="button"
						className={`tab${tab === "deployments" ? " active" : ""}`}
						onClick={() => setTab("deployments")}
					>
						Deployments
					</button>
					<button
						type="button"
						className={`tab${tab === "definition" ? " active" : ""}`}
						onClick={() => setTab("definition")}
					>
						Definition
					</button>
				</div>

				{tab === "versions" && (
					<div className="tbl-wrap">
						<table className="tbl">
							<thead>
								<tr>
									<th style={{ width: "14%" }}>Version</th>
									<th style={{ width: "26%" }}>Status</th>
									<th style={{ width: "32%" }}>Created</th>
									<th>Note</th>
								</tr>
							</thead>
							<tbody>
								{d.versions.map((v) => (
									<tr key={v.version}>
										<td className="mono">{v.version}</td>
										<td>
											<WorkflowTag status={v.status} />
										</td>
										<td className="muted">{v.created}</td>
										<td className="muted">{v.note}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				)}

				{tab === "deployments" &&
					(d.deployments.length === 0 ? (
						<div className="tbl-wrap">
							<EmptyState title="No deployments recorded." />
						</div>
					) : (
						<div className="dep-grid">
							{d.deployments.map((dep) => (
								<div
									key={dep.version}
									className={`dep-card${dep.status === "DRAINED" ? " drained" : ""}`}
								>
									<div className="dep-card-head">
										<span className="dep-card-ver mono">{dep.version}</span>
										<DeploymentTag status={dep.status} />
									</div>
									<dl>
										<dt>orchestrator</dt>
										<dd className="mono">{dep.orchestrator}</dd>
										<dt>step services</dt>
										<dd className="mono">{dep.stepServices.join(", ")}</dd>
										<dt>drained-at</dt>
										<dd className={dep.drainedAt ? "mono" : "muted"}>
											{dep.drainedAt ?? "—"}
										</dd>
									</dl>
								</div>
							))}
						</div>
					))}

				{tab === "definition" && <DefinitionGraph />}
			</div>
		</AppLayout>
	);
}
