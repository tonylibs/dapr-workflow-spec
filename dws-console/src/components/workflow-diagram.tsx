import {
	Background,
	ReactFlow,
	ReactFlowProvider,
	useReactFlow,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Maximize2, Minus, Plus } from "lucide-react";
import { useMemo } from "react";
import { Banner } from "#/components/states";
import {
	type DiagramNode,
	nodeTypes,
} from "#/components/workflow-diagram-nodes";
import type { DefinitionFormat } from "#/lib/definition-draft-store";
import { countErrorsByNode } from "#/lib/definition-graph-model";
import { useLaidOutGraph } from "#/lib/use-laid-out-graph";

type WorkflowDiagramProps = {
	definition: string;
	format: DefinitionFormat;
	/** Spec errors for exactly this draft; each badges its closest enclosing task node. */
	specErrors?: { path: string }[];
};

function DiagramControls() {
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

function WorkflowDiagramView({
	definition,
	format,
	specErrors,
}: WorkflowDiagramProps) {
	const { graph, error } = useLaidOutGraph(definition, format);

	const nodes = useMemo<DiagramNode[]>(() => {
		const laidOutNodes = graph?.nodes ?? [];
		const errorCounts = countErrorsByNode(specErrors ?? [], laidOutNodes);
		return laidOutNodes.map((node) => ({
			...node,
			data: { ...node.data, errorCount: errorCounts.get(node.id) ?? 0 },
		}));
	}, [graph, specErrors]);

	return (
		<div
			style={{
				display: "flex",
				flexDirection: "column",
				gap: 10,
				width: "100%",
				height: "100%",
				minHeight: 480,
			}}
		>
			{error && (
				<Banner variant="warn" role="alert" data-testid="stale-banner">
					<strong>
						{graph
							? "Stale diagram — showing last valid workflow"
							: "Cannot build diagram"}
					</strong>{" "}
					<code>{error}</code>
				</Banner>
			)}
			<div
				className="graph-canvas"
				style={{ flex: 1, minHeight: 420, width: "100%", padding: 0 }}
			>
				<DiagramControls />
				{graph ? (
					<ReactFlow
						nodes={nodes}
						edges={graph.edges}
						nodeTypes={nodeTypes}
						fitView
						minZoom={0.2}
						maxZoom={2}
					>
						<Background gap={22} size={1} />
					</ReactFlow>
				) : (
					<div
						className="muted"
						style={{
							height: "100%",
							display: "flex",
							alignItems: "center",
							justifyContent: "center",
							fontSize: 13,
						}}
					>
						{error
							? "Fix syntax errors to view diagram"
							: "Enter a definition to see the diagram"}
					</div>
				)}
			</div>
		</div>
	);
}

/**
 * Live, read-only control-flow diagram of a draft definition. Default-exported so the editor can
 * `lazy()` it: xyflow, elkjs, the SDK and yaml all load only through this module.
 */
export default function WorkflowDiagram(props: WorkflowDiagramProps) {
	return (
		<ReactFlowProvider>
			<WorkflowDiagramView {...props} />
		</ReactFlowProvider>
	);
}
