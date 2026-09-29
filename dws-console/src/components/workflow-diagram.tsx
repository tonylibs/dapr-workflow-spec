import {
	Background,
	type Edge,
	Handle,
	type Node,
	type NodeProps,
	Position,
	ReactFlow,
	ReactFlowProvider,
	useReactFlow,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Maximize2, Minus, Plus } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { TaskTypeBadge } from "#/components/status";
import {
	buildDefinitionGraph,
	findNodeForErrorPath,
} from "#/lib/definition-graph-model";
import type { TaskType } from "#/lib/mock-data";
import {
	type LaidOutGraph,
	type LaidOutNodeData,
	layoutWorkflowGraph,
} from "#/lib/workflow-layout";

export interface WorkflowDiagramProps {
	definition: string;
	format: "yaml" | "json";
	specErrors?: Array<{ path: string; message?: string }>;
}

function TaskNodeComponent({ data }: NodeProps) {
	const nodeData = data as unknown as LaidOutNodeData;
	return (
		<div
			className="wf-node-card"
			style={{
				width: nodeData.width ?? 200,
				height: nodeData.height ?? 68,
				cursor: "default",
				position: "relative",
			}}
		>
			<Handle
				type="target"
				position={Position.Top}
				style={{ visibility: "hidden" }}
			/>
			<div style={{ display: "flex", alignItems: "center", gap: 6 }}>
				<TaskTypeBadge type={nodeData.taskType as TaskType} showIcon={false} />
				{nodeData.errorCount !== undefined && nodeData.errorCount > 0 && (
					<span
						data-testid={`error-badge-${nodeData.id}`}
						style={{
							marginLeft: "auto",
							background: "var(--color-fail, #dc2626)",
							color: "#ffffff",
							borderRadius: "999px",
							padding: "1px 6px",
							fontSize: "11px",
							fontWeight: 700,
						}}
					>
						{nodeData.errorCount} error{nodeData.errorCount > 1 ? "s" : ""}
					</span>
				)}
			</div>
			<div
				className="name"
				style={{
					overflow: "hidden",
					textOverflow: "ellipsis",
					whiteSpace: "nowrap",
				}}
			>
				{nodeData.name}
			</div>
			<Handle
				type="source"
				position={Position.Bottom}
				style={{ visibility: "hidden" }}
			/>
		</div>
	);
}

function ContainerNodeComponent({ data }: NodeProps) {
	const nodeData = data as unknown as LaidOutNodeData;
	return (
		<div
			style={{
				width: nodeData.width,
				height: nodeData.height,
				borderRadius: 18,
				border: "1.5px dashed var(--color-divider)",
				backgroundColor:
					"color-mix(in srgb, var(--color-surface) 40%, transparent)",
				padding: "10px 14px",
				boxSizing: "border-box",
				position: "relative",
			}}
		>
			<Handle
				type="target"
				position={Position.Top}
				style={{ visibility: "hidden" }}
			/>
			<div
				style={{
					display: "flex",
					alignItems: "center",
					gap: 6,
					fontSize: "11px",
					fontWeight: 700,
					textTransform: "uppercase",
					letterSpacing: "0.05em",
					color: "color-mix(in srgb, var(--color-text) 60%, transparent)",
				}}
			>
				<TaskTypeBadge type={nodeData.taskType as TaskType} showIcon={false} />
				<span>{nodeData.name}</span>
			</div>
			<Handle
				type="source"
				position={Position.Bottom}
				style={{ visibility: "hidden" }}
			/>
		</div>
	);
}

function StartEndNodeComponent({ data }: NodeProps) {
	const nodeData = data as unknown as LaidOutNodeData;
	const isStart = nodeData.kind === "start";
	return (
		<div
			style={{
				width: nodeData.width ?? 120,
				height: nodeData.height ?? 44,
				borderRadius: 999,
				border: "1.5px solid var(--color-divider)",
				backgroundColor: "var(--color-surface)",
				display: "flex",
				alignItems: "center",
				justifyContent: "center",
				fontSize: "12px",
				fontWeight: 600,
				letterSpacing: "0.05em",
				textTransform: "uppercase",
			}}
		>
			{isStart ? (
				<Handle
					type="source"
					position={Position.Bottom}
					style={{ visibility: "hidden" }}
				/>
			) : (
				<Handle
					type="target"
					position={Position.Top}
					style={{ visibility: "hidden" }}
				/>
			)}
			{nodeData.name}
		</div>
	);
}

const nodeTypes = {
	taskNode: TaskNodeComponent,
	containerNode: ContainerNodeComponent,
	startEndNode: StartEndNodeComponent,
};

function DiagramCanvasControls() {
	const { zoomIn, zoomOut, fitView } = useReactFlow();

	return (
		<div
			className="graph-controls"
			role="toolbar"
			aria-label="Diagram zoom controls"
		>
			<button
				type="button"
				className="btn-sm"
				aria-label="Zoom out"
				onClick={() => zoomOut()}
			>
				<Minus size={14} />
			</button>
			<button
				type="button"
				className="btn-sm"
				aria-label="Zoom in"
				onClick={() => zoomIn()}
			>
				<Plus size={14} />
			</button>
			<button
				type="button"
				className="btn-sm"
				aria-label="Fit view"
				onClick={() => fitView()}
			>
				<Maximize2 size={14} />
			</button>
		</div>
	);
}

export function WorkflowDiagramInner({
	definition,
	format,
	specErrors,
}: WorkflowDiagramProps) {
	const [debouncedInput, setDebouncedInput] = useState({ definition, format });
	const [lastGoodGraph, setLastGoodGraph] = useState<
		LaidOutGraph | undefined
	>();
	const [errorMessage, setErrorMessage] = useState<string | undefined>();
	const [isStale, setIsStale] = useState(false);
	const seqRef = useRef(0);

	// 300 ms debounce for text changes
	useEffect(() => {
		const timer = setTimeout(() => {
			setDebouncedInput({ definition, format });
		}, 300);
		return () => clearTimeout(timer);
	}, [definition, format]);

	// Compute error mapping for current spec errors
	const errorsByNodeId = useMemo(() => {
		const map = new Map<string, number>();
		if (!specErrors || specErrors.length === 0 || !lastGoodGraph) return map;

		for (const err of specErrors) {
			const match = findNodeForErrorPath(err.path, lastGoodGraph.nodes);
			if (match) {
				map.set(match.id, (map.get(match.id) ?? 0) + 1);
			}
		}
		return map;
	}, [specErrors, lastGoodGraph]);

	// Build and layout graph whenever debounced input changes
	useEffect(() => {
		if (!debouncedInput.definition.trim()) {
			setLastGoodGraph(undefined);
			setErrorMessage(undefined);
			setIsStale(false);
			return;
		}

		seqRef.current += 1;
		const currentSeq = seqRef.current;

		const buildResult = buildDefinitionGraph(
			debouncedInput.definition,
			debouncedInput.format,
		);

		if (!buildResult.ok) {
			setIsStale(true);
			setErrorMessage(buildResult.error);
			return;
		}

		// Graph build succeeded
		void layoutWorkflowGraph(buildResult.graph).then((laidOut) => {
			// Discard stale result if a newer request began
			if (seqRef.current !== currentSeq) return;

			setLastGoodGraph(laidOut);
			setErrorMessage(undefined);
			setIsStale(false);
		});
	}, [debouncedInput]);

	// Apply updated error counts to nodes
	const nodes = useMemo(() => {
		if (!lastGoodGraph) return [];
		return lastGoodGraph.nodes.map((node) => {
			const errorCount = errorsByNodeId.get(node.id) ?? 0;
			return {
				...node,
				data: {
					...node.data,
					errorCount,
				},
			};
		});
	}, [lastGoodGraph, errorsByNodeId]);

	const edges = lastGoodGraph?.edges ?? [];

	return (
		<div
			className="workflow-diagram-container"
			style={{
				display: "flex",
				flexDirection: "column",
				gap: "10px",
				width: "100%",
				height: "100%",
				minHeight: "480px",
			}}
		>
			{/* Stale / Error Banner */}
			{isStale && errorMessage && (
				<div
					role="alert"
					data-testid="stale-banner"
					style={{
						padding: "10px 14px",
						borderRadius: "10px",
						backgroundColor:
							"color-mix(in srgb, var(--color-warn, #eab308) 15%, transparent)",
						borderLeft: "4px solid var(--color-warn, #eab308)",
						fontSize: "12.5px",
					}}
				>
					<div style={{ fontWeight: 600, marginBottom: "2px" }}>
						{lastGoodGraph
							? "Stale diagram — showing last valid workflow"
							: "Cannot build diagram"}
					</div>
					<div
						style={{
							fontFamily: "ui-monospace, Menlo, monospace",
							fontSize: "11.5px",
							opacity: 0.85,
						}}
					>
						{errorMessage}
					</div>
				</div>
			)}

			{/* Canvas */}
			<div
				className="graph-canvas"
				style={{
					flex: 1,
					minHeight: "420px",
					width: "100%",
					position: "relative",
					padding: 0,
				}}
			>
				<DiagramCanvasControls />
				{nodes.length > 0 ? (
					<ReactFlow
						nodes={nodes as unknown as Node[]}
						edges={edges as unknown as Edge[]}
						nodeTypes={nodeTypes}
						fitView
						minZoom={0.2}
						maxZoom={2}
					>
						<Background gap={22} size={1} />
					</ReactFlow>
				) : (
					<div
						style={{
							height: "100%",
							display: "flex",
							alignItems: "center",
							justifyContent: "center",
							color: "color-mix(in srgb, var(--color-text) 50%, transparent)",
							fontSize: "13px",
						}}
					>
						{errorMessage
							? "Fix syntax errors to view diagram"
							: "Enter a definition to see the diagram"}
					</div>
				)}
			</div>
		</div>
	);
}

export default function WorkflowDiagram(props: WorkflowDiagramProps) {
	return (
		<ReactFlowProvider>
			<WorkflowDiagramInner {...props} />
		</ReactFlowProvider>
	);
}
