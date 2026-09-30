import type { Edge, Node } from "@xyflow/react";
import ELK, { type ElkNode } from "elkjs/lib/elk.bundled.js";
import type {
	DefinitionGraph,
	DefinitionGraphNode,
	NodeKind,
} from "./definition-graph-model";

export type LaidOutNode = Node<
	DefinitionGraphNode,
	"taskNode" | "containerNode" | "startEndNode"
>;

export type LaidOutGraph = {
	nodes: LaidOutNode[];
	edges: Edge[];
};

const NODE_TYPE = {
	task: "taskNode",
	container: "containerNode",
	start: "startEndNode",
	end: "startEndNode",
} as const satisfies Record<NodeKind, LaidOutNode["type"]>;

const TASK_SIZE = { width: 200, height: 68 };
const TERMINAL_SIZE = { width: 120, height: 44 };

const ROOT_LAYOUT_OPTIONS = {
	"elk.algorithm": "layered",
	"elk.direction": "DOWN",
	"elk.hierarchyHandling": "INCLUDE_CHILDREN",
	"elk.spacing.nodeNode": "35",
	"elk.layered.spacing.nodeNodeBetweenLayers": "45",
	"elk.padding": "[top=30,left=30,bottom=30,right=30]",
};

const CONTAINER_LAYOUT_OPTIONS = {
	"elk.algorithm": "layered",
	"elk.direction": "DOWN",
	"elk.padding": "[top=38,left=20,bottom=20,right=20]",
	"elk.spacing.nodeNode": "25",
	"elk.layered.spacing.nodeNodeBetweenLayers": "35",
};

// elk.bundled.js on the main thread: the classic elk-worker.min.js cannot be emitted as an ESM
// worker under Vite 8 / TanStack Start (design D5). This module only loads in the lazy,
// client-only diagram chunk, and layouts of tens of nodes finish in milliseconds.
const elk = new ELK();

/**
 * ELK's JSON importer treats an explicit `undefined` property (for example `width: undefined` on
 * a container) as a null size and throws, so nodes are built without absent keys.
 */
function toElkNode(node: DefinitionGraphNode): ElkNode {
	switch (node.kind) {
		case "container":
			return {
				id: node.id,
				layoutOptions: CONTAINER_LAYOUT_OPTIONS,
				children: [],
			};
		case "task":
			return { id: node.id, ...TASK_SIZE };
		default:
			return { id: node.id, ...TERMINAL_SIZE };
	}
}

/** Lays the graph out top-down with ELK; containers are sized from their children. */
export async function layoutWorkflowGraph(
	graph: DefinitionGraph,
): Promise<LaidOutGraph> {
	const elkNodes = new Map(graph.nodes.map((n) => [n.id, toElkNode(n)]));
	const rootChildren: ElkNode[] = [];
	for (const { id, parentId } of graph.nodes) {
		const elkNode = elkNodes.get(id);
		const parent = parentId ? elkNodes.get(parentId) : undefined;
		if (elkNode) (parent?.children ?? rootChildren).push(elkNode);
	}

	const laidOut = await elk.layout({
		id: "root",
		layoutOptions: ROOT_LAYOUT_OPTIONS,
		children: rootChildren,
		edges: graph.edges.map((e) => ({
			id: e.id,
			sources: [e.source],
			targets: [e.target],
			labels: e.label ? [{ text: e.label }] : [],
		})),
	});

	const nodesById = new Map(graph.nodes.map((n) => [n.id, n]));
	const nodes: LaidOutNode[] = [];
	// Parents are pushed before their children, as xyflow requires.
	const collect = (elkNode: ElkNode, parentId?: string) => {
		const data = nodesById.get(elkNode.id);
		if (data) {
			nodes.push({
				id: data.id,
				type: NODE_TYPE[data.kind],
				position: { x: elkNode.x ?? 0, y: elkNode.y ?? 0 },
				data,
				ariaLabel: accessibleName(data),
				style: { width: elkNode.width, height: elkNode.height },
				parentId,
				extent: parentId ? "parent" : undefined,
			});
		}
		for (const child of elkNode.children ?? []) collect(child, data?.id);
	};
	collect(laidOut);

	return {
		nodes,
		edges: graph.edges.map((e) => ({
			id: e.id,
			source: e.source,
			target: e.target,
			label: e.label,
			type: "smoothstep",
		})),
	};
}

/** Screen-reader name of a node: "<name>, <type> task" (containers say "container"). */
function accessibleName({ name, taskType, kind }: DefinitionGraphNode): string {
	if (kind === "start") return "Start";
	if (kind === "end") return "End";
	return `${name}, ${taskType} ${kind === "container" ? "container" : "task"}`;
}
