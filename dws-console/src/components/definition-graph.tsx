import { Maximize2, Minus, Plus } from "lucide-react";
import { TaskTypeBadge } from "#/components/status";
import type { TaskType } from "#/lib/mock-data";

/**
 * Read-only workflow definition visualizer. A static, dagre-style top-down
 * layout: nodes are absolutely positioned and edges drawn in an SVG layer
 * behind them (matching the design handoff exactly). Kept dependency-free —
 * a live version would swap this for @xyflow/react with the same node/edge model.
 */

function Node({
	left,
	top,
	type,
	name,
	meta,
}: {
	left: number;
	top: number;
	type: TaskType;
	name: string;
	meta?: string;
}) {
	return (
		<div style={{ position: "absolute", left, top, width: 180, height: 66 }}>
			<div className="wf-node-card">
				<div style={{ display: "flex", alignItems: "center", gap: 6 }}>
					<TaskTypeBadge type={type} showIcon={false} />
					{meta && (
						<span className="muted" style={{ fontSize: 10.5 }}>
							{meta}
						</span>
					)}
				</div>
				<div className="name">{name}</div>
			</div>
		</div>
	);
}

export function DefinitionGraph() {
	return (
		<div className="graph-split">
			<div className="graph-canvas">
				<div className="graph-controls">
					<button type="button" className="btn-sm">
						<Minus size={14} />
					</button>
					<button type="button" className="btn-sm">
						<Plus size={14} />
					</button>
					<button type="button" className="btn-sm">
						<Maximize2 size={14} />
					</button>
				</div>

				<svg
					width="700"
					height="720"
					style={{
						position: "absolute",
						left: 28,
						top: 28,
						pointerEvents: "none",
					}}
					viewBox="0 0 700 720"
					role="img"
					aria-label="Workflow task graph edges"
				>
					<title>Workflow task graph edges</title>
					<defs>
						<marker
							id="arr"
							viewBox="0 0 10 10"
							refX="9"
							refY="5"
							markerWidth="7"
							markerHeight="7"
							orient="auto-start-reverse"
						>
							<path d="M0 0 L10 5 L0 10 z" fill="var(--color-neutral-500)" />
						</marker>
						<marker
							id="arr-red"
							viewBox="0 0 10 10"
							refX="9"
							refY="5"
							markerWidth="7"
							markerHeight="7"
							orient="auto-start-reverse"
						>
							<path d="M0 0 L10 5 L0 10 z" fill="var(--color-fail)" />
						</marker>
					</defs>
					<g fill="none" stroke="var(--color-neutral-500)" strokeWidth={2}>
						<path d="M170 44 L170 90" markerEnd="url(#arr)" />
						<path d="M170 156 L170 202" markerEnd="url(#arr)" />
						<path d="M170 268 L170 314" markerEnd="url(#arr)" />
						<path
							d="M170 380 C 170 420, 90 420, 90 460"
							markerEnd="url(#arr)"
						/>
						<path
							d="M170 380 C 170 420, 260 420, 260 460"
							markerEnd="url(#arr)"
						/>
						<path
							d="M90 520 C 90 560, 170 560, 170 590"
							markerEnd="url(#arr)"
						/>
						<path
							d="M260 520 C 260 560, 170 560, 170 590"
							markerEnd="url(#arr)"
						/>
					</g>
					<path
						d="M280 640 C 380 640, 440 640, 440 640"
						fill="none"
						stroke="var(--color-fail)"
						strokeWidth={2}
						strokeDasharray="5 4"
						markerEnd="url(#arr-red)"
					/>
					<text
						x="120"
						y="430"
						fontFamily="ui-monospace, Menlo, monospace"
						fontSize={11}
						fill="var(--color-neutral-700)"
					>
						express
					</text>
					<text
						x="270"
						y="430"
						fontFamily="ui-monospace, Menlo, monospace"
						fontSize={11}
						fill="var(--color-neutral-700)"
					>
						standard
					</text>
					<text
						x="330"
						y="632"
						fontFamily="ui-monospace, Menlo, monospace"
						fontSize={11}
						fill="var(--color-fail)"
					>
						on error
					</text>
				</svg>

				<div style={{ position: "relative", width: 700, height: 720 }}>
					{/* start */}
					<div
						style={{
							position: "absolute",
							left: 130,
							top: 10,
							width: 80,
							height: 34,
							borderRadius: 999,
							background: "var(--color-accent-2-200)",
							color: "var(--color-accent-2-800)",
							display: "flex",
							alignItems: "center",
							justifyContent: "center",
							fontSize: 12,
							fontWeight: 700,
							letterSpacing: ".06em",
						}}
					>
						START
					</div>

					<Node left={80} top={90} type="call" name="validate-payload" />
					<Node left={80} top={202} type="run" name="enrich-order" />

					{/* switch — dashed sage */}
					<div
						style={{
							position: "absolute",
							left: 80,
							top: 314,
							width: 180,
							height: 66,
						}}
					>
						<div
							style={{
								height: "100%",
								padding: "10px 14px",
								borderRadius: 18,
								background: "var(--color-accent-2-100)",
								border: "1.5px dashed var(--color-accent-2)",
								display: "flex",
								flexDirection: "column",
								gap: 4,
							}}
						>
							<div style={{ display: "flex", alignItems: "center", gap: 6 }}>
								<TaskTypeBadge type="switch" showIcon={false} />
								<span className="muted" style={{ fontSize: 10.5 }}>
									2 branches
								</span>
							</div>
							<div className="mono" style={{ fontSize: 13, fontWeight: 600 }}>
								choose-carrier
							</div>
						</div>
					</div>

					<Node left={0} top={460} type="wait" name="express-hold" meta="15m" />
					<Node left={170} top={460} type="set" name="standard-lane" />

					{/* try container */}
					<div
						style={{
							position: "absolute",
							left: 60,
							top: 580,
							width: 220,
							height: 120,
						}}
					>
						<div
							style={{
								height: "100%",
								padding: "12px 14px 10px",
								borderRadius: 20,
								background:
									"color-mix(in srgb, var(--color-accent) 8%, transparent)",
								border: "1.5px solid var(--color-accent)",
								position: "relative",
							}}
						>
							<div
								style={{
									display: "flex",
									alignItems: "center",
									gap: 6,
									marginBottom: 8,
								}}
							>
								<TaskTypeBadge type="try" showIcon={false} />
								<span className="muted" style={{ fontSize: 10.5 }}>
									retry × 3 · backoff exp
								</span>
							</div>
							<div
								style={{
									padding: "6px 10px",
									borderRadius: 12,
									background: "var(--color-bg)",
									border: "1px solid var(--color-divider)",
									display: "flex",
									alignItems: "center",
									gap: 6,
								}}
							>
								<TaskTypeBadge type="call" showIcon={false} />
								<span
									className="mono"
									style={{ fontSize: 12.5, fontWeight: 600 }}
								>
									dispatch-shipment
								</span>
							</div>
							<div
								style={{
									position: "absolute",
									bottom: -9,
									left: 14,
									fontSize: 10.5,
									color: "var(--color-accent-800)",
									background: "var(--color-bg)",
									padding: "0 6px",
								}}
							>
								on error → catch
							</div>
						</div>
					</div>

					{/* catch block */}
					<div
						style={{
							position: "absolute",
							left: 440,
							top: 596,
							width: 200,
							height: 88,
						}}
					>
						<div
							style={{
								height: "100%",
								padding: "10px 14px",
								borderRadius: 20,
								background: "var(--color-fail-bg)",
								border: "1.5px solid var(--color-fail)",
								display: "flex",
								flexDirection: "column",
								gap: 4,
							}}
						>
							<TaskTypeBadge type="catch" showIcon={false} />
							<div className="mono" style={{ fontSize: 13, fontWeight: 600 }}>
								log-and-notify
							</div>
							<div className="muted" style={{ fontSize: 11 }}>
								then → emit-failure-event
							</div>
						</div>
					</div>
				</div>
			</div>

			{/* right rail */}
			<div
				style={{
					display: "flex",
					flexDirection: "column",
					gap: 12,
					minHeight: 0,
				}}
			>
				<div className="rail-card">
					<div className="rail-title">Task types</div>
					<div
						style={{
							display: "flex",
							flexDirection: "column",
							gap: 6,
							fontSize: 12,
						}}
					>
						{(
							[
								["call", "HTTP / gRPC out"],
								["run", "step service job"],
								["switch", "conditional branch"],
								["set", "state assignment"],
								["wait", "timer"],
								["listen", "event trigger"],
								["emit", "outbound event"],
								["try", "wraps + retries"],
								["catch", "error branch"],
							] as const
						).map(([t, desc]) => (
							<div
								key={t}
								style={{ display: "flex", alignItems: "center", gap: 8 }}
							>
								<TaskTypeBadge type={t} showIcon={false} />
								<span className="muted">{desc}</span>
							</div>
						))}
					</div>
				</div>

				{/* selected node inspector */}
				<div className="rail-card selected">
					<div style={{ display: "flex", alignItems: "center", gap: 8 }}>
						<TaskTypeBadge type="try" showIcon={false} />
						<span className="muted" style={{ fontSize: 10.5 }}>
							selected
						</span>
					</div>
					<div className="mono" style={{ fontSize: 14, fontWeight: 700 }}>
						dispatch-shipment
					</div>
					<dl
						style={{
							margin: 0,
							display: "grid",
							gridTemplateColumns: "auto 1fr",
							gap: "4px 12px",
							fontSize: 11.5,
						}}
					>
						<dt className="muted">retries</dt>
						<dd style={{ margin: 0 }} className="mono">
							3
						</dd>
						<dt className="muted">backoff</dt>
						<dd style={{ margin: 0 }} className="mono">
							exponential · 5s → 10s
						</dd>
						<dt className="muted">timeout</dt>
						<dd style={{ margin: 0 }} className="mono">
							30s / attempt
						</dd>
						<dt className="muted">catches</dt>
						<dd style={{ margin: 0 }} className="mono">
							HTTP 5xx, Timeout
						</dd>
						<dt className="muted">on error</dt>
						<dd style={{ margin: 0 }} className="mono">
							→ log-and-notify
						</dd>
					</dl>
					<div style={{ marginTop: "auto", display: "flex", gap: 6 }}>
						<button
							type="button"
							className="btn-sm"
							style={{ flex: 1, justifyContent: "center" }}
						>
							View YAML
						</button>
						<button
							type="button"
							className="btn-sm"
							style={{ flex: 1, justifyContent: "center" }}
						>
							Recent runs
						</button>
					</div>
				</div>
			</div>
		</div>
	);
}
