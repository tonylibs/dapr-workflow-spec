import ELKBundled from "elkjs/lib/elk.bundled.js";
import type { ElkExtendedEdge, ElkNode } from "elkjs/lib/elk-api";
import type {
	DefinitionGraph,
	DefinitionGraphNode,
} from "./definition-graph-model";

export interface LaidOutNodeData extends DefinitionGraphNode {
	errorCount?: number;
	width?: number;
	height?: number;
}

export interface LaidOutNode {
	id: string;
	type: "taskNode" | "containerNode" | "startEndNode";
	position: { x: number; y: number };
	data: LaidOutNodeData;
	parentId?: string;
	extent?: "parent";
	style?: { width: number; height: number };
	ariaLabel?: string;
}

export interface LaidOutEdge {
	id: string;
	source: string;
	target: string;
	label?: string;
	type?: string;
}

export interface LaidOutGraph {
	nodes: LaidOutNode[];
	edges: LaidOutEdge[];
	width: number;
	height: number;
}

type ElkInstance = {
	layout(graph: ElkNode): Promise<ElkNode>;
};

let elkInstance: ElkInstance | null = null;

/**
 * Returns the singleton ELK instance using elk.bundled.js.
 * Under Vite 8 / TanStack Start, the classic elk-worker.min.js cannot be emitted as an ESM worker,
 * so elk.bundled.js runs on the main thread inside the lazy diagram chunk. Layout of typical
 * workflow graphs (tens of nodes) completes in milliseconds.
 */
export function getElk(): ElkInstance {
	if (!elkInstance) {
		elkInstance = new (ELKBundled as unknown as { new (): ElkInstance })();
	}
	return elkInstance;
}

export async function layoutWorkflowGraph(
	graph: DefinitionGraph,
	errorsByNodeId?: Map<string, number>,
): Promise<LaidOutGraph> {
	const nodeMap = new Map<string, DefinitionGraphNode>();
	for (const n of graph.nodes) {
		nodeMap.set(n.id, n);
	}

	// Build tree of ELK nodes
	const elkNodesById = new Map<string, ElkNode>();

	for (const n of graph.nodes) {
		const isStartEnd = n.kind === "start" || n.kind === "end";
		const isContainer = n.kind === "container";

		const elkNode: ElkNode = {
			id: n.id,
			width: isStartEnd ? 120 : isContainer ? undefined : 200,
			height: isStartEnd ? 44 : isContainer ? undefined : 68,
			layoutOptions: isContainer
				? {
						"elk.algorithm": "layered",
						"elk.direction": "DOWN",
						"elk.padding": "[top=38,left=20,bottom=20,right=20]",
						"elk.spacing.nodeNode": "25",
						"elk.layered.spacing.nodeNodeBetweenLayers": "35",
					}
				: undefined,
			children: isContainer ? [] : undefined,
		};
		elkNodesById.set(n.id, elkNode);
	}

	const rootChildren: ElkNode[] = [];

	for (const n of graph.nodes) {
		const elkNode = elkNodesById.get(n.id);
		if (!elkNode) continue;
		if (n.parentId && elkNodesById.has(n.parentId)) {
			const parentElk = elkNodesById.get(n.parentId);
			if (parentElk) {
				if (!parentElk.children) parentElk.children = [];
				parentElk.children.push(elkNode);
			}
		} else {
			rootChildren.push(elkNode);
		}
	}

	const elkEdges: ElkExtendedEdge[] = graph.edges.map((e) => ({
		id: e.id,
		sources: [e.source],
		targets: [e.target],
		labels: e.label ? [{ text: e.label }] : undefined,
	}));

	const rootElkGraph: ElkNode = {
		id: "root",
		layoutOptions: {
			"elk.algorithm": "layered",
			"elk.direction": "DOWN",
			"elk.hierarchyHandling": "INCLUDE_CHILDREN",
			"elk.spacing.nodeNode": "35",
			"elk.layered.spacing.nodeNodeBetweenLayers": "45",
			"elk.padding": "[top=30,left=30,bottom=30,right=30]",
		},
		children: rootChildren,
		edges: elkEdges,
	};

	const laidOutRoot = await getElk().layout(rootElkGraph);

	const flatLaidOutNodes: LaidOutNode[] = [];

	function collectLaidOut(elkNode: ElkNode, parentNodeId?: string) {
		if (elkNode.id !== "root") {
			const original = nodeMap.get(elkNode.id);
			if (original) {
				let nodeType: "taskNode" | "containerNode" | "startEndNode" =
					"taskNode";
				if (original.kind === "container") {
					nodeType = "containerNode";
				} else if (original.kind === "start" || original.kind === "end") {
					nodeType = "startEndNode";
				}

				const width =
					elkNode.width ?? (nodeType === "startEndNode" ? 120 : 200);
				const height =
					elkNode.height ?? (nodeType === "startEndNode" ? 44 : 68);
				const errorCount = errorsByNodeId?.get(original.id) ?? 0;

				flatLaidOutNodes.push({
					id: original.id,
					type: nodeType,
					position: {
						x: elkNode.x ?? 0,
						y: elkNode.y ?? 0,
					},
					data: {
						...original,
						errorCount,
						width,
						height,
					},
					parentId: parentNodeId,
					extent: parentNodeId ? "parent" : undefined,
					style: {
						width,
						height,
					},
					ariaLabel: original.ariaLabel,
				});
			}
		}

		if (elkNode.children) {
			for (const child of elkNode.children) {
				collectLaidOut(child, elkNode.id === "root" ? undefined : elkNode.id);
			}
		}
	}

	collectLaidOut(laidOutRoot);

	const laidOutEdges: LaidOutEdge[] = graph.edges.map((e) => ({
		id: e.id,
		source: e.source,
		target: e.target,
		label: e.label,
		type: "smoothstep",
	}));

	return {
		nodes: flatLaidOutNodes,
		edges: laidOutEdges,
		width: laidOutRoot.width ?? 600,
		height: laidOutRoot.height ?? 400,
	};
}
