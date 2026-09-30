import {
	buildGraph,
	Classes,
	type Graph,
	type GraphEdge,
	type GraphNode,
	GraphNodeType,
} from "@openworkflowspec/sdk";
import yaml from "yaml";
import type { DefinitionFormat } from "#/lib/definition-draft-store";

/**
 * Pure, React-free draft text -> graph model. Nodes are keyed by the SDK's JSON-pointer
 * `taskReference` (for example `/do/1/approve`). Everything the console renders or badges is
 * derived from these console-owned types, so an SDK graph-shape change only touches
 * `toDefinitionGraph`.
 */

export type NodeKind = "task" | "container" | "start" | "end";

export type DefinitionGraphNode = {
	id: string;
	name: string;
	taskType: string;
	kind: NodeKind;
	parentId?: string;
};

export type DefinitionGraphEdge = {
	id: string;
	source: string;
	target: string;
	label?: string;
};

export type DefinitionGraph = {
	nodes: DefinitionGraphNode[];
	edges: DefinitionGraphEdge[];
};

export type BuildGraphResult =
	| { ok: true; graph: DefinitionGraph }
	| { ok: false; error: string };

const segments = (pointer: string) => pointer.split("/").filter(Boolean);

/**
 * Finds the node whose id is the longest segment-wise prefix of an error's JSON pointer path,
 * or undefined when no node encloses it (for example a `/document/...` error).
 */
export function findNodeForErrorPath<T extends { id: string }>(
	errorPath: string,
	nodes: readonly T[],
): T | undefined {
	const errorSegments = segments(errorPath);
	let bestMatch: T | undefined;
	let bestLength = 0;

	for (const node of nodes) {
		const nodeSegments = segments(node.id);
		if (
			nodeSegments.length > bestLength &&
			nodeSegments.length <= errorSegments.length &&
			nodeSegments.every((segment, i) => segment === errorSegments[i])
		) {
			bestMatch = node;
			bestLength = nodeSegments.length;
		}
	}

	return bestMatch;
}

/** Counts spec errors per node id, using the longest-prefix rule; unmatched errors count nowhere. */
export function countErrorsByNode(
	errors: readonly { path: string }[],
	nodes: readonly { id: string }[],
): Map<string, number> {
	const counts = new Map<string, number>();
	for (const error of errors) {
		const node = findNodeForErrorPath(error.path, nodes);
		if (node) counts.set(node.id, (counts.get(node.id) ?? 0) + 1);
	}
	return counts;
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** The minimum shape `buildGraph` can cope with; returns a readable reason when it is missing. */
function shapeError(parsed: unknown): string | undefined {
	if (!isRecord(parsed)) return "Definition must be an object";
	if (!isRecord(parsed.document)) {
		return "Definition must contain a document object";
	}
	if (!Array.isArray(parsed.do) || parsed.do.length === 0) {
		return 'Definition must contain a non-empty "do" task list';
	}
	return undefined;
}

type SdkNode = Graph | GraphNode;
type WorkingEdge = Pick<GraphEdge, "sourceId" | "targetId"> & { label: string };

const isPort = (node: SdkNode) =>
	node.type === GraphNodeType.Entry || node.type === GraphNodeType.Exit;

const consoleId = (node: SdkNode) => node.taskReference || node.id;

/** Flattens the SDK's nested graph into id -> node (with its parent) plus every edge. */
function flattenSdkGraph(root: Graph) {
	const nodes = new Map<string, { node: SdkNode; parent?: SdkNode }>();
	const edges: GraphEdge[] = [];

	const visit = (node: SdkNode, parent?: SdkNode) => {
		nodes.set(node.id, { node, parent });
		if (!("nodes" in node)) return;
		edges.push(...(node.edges ?? []));
		for (const child of node.nodes) visit(child, node);
	};
	visit(root);

	return { nodes, edges };
}

/** Bridges every edge that passes through an entry/exit port, then drops the port edges. */
function contractPorts(edges: WorkingEdge[], portIds: string[]): WorkingEdge[] {
	let result = edges;
	for (const portId of portIds) {
		const incoming = result.filter((edge) => edge.targetId === portId);
		const outgoing = result.filter((edge) => edge.sourceId === portId);
		const bridges = incoming.flatMap((inEdge) =>
			outgoing
				.filter((outEdge) => inEdge.sourceId !== outEdge.targetId)
				.map((outEdge) => ({
					sourceId: inEdge.sourceId,
					targetId: outEdge.targetId,
					label: inEdge.label || outEdge.label,
				})),
		);
		result = result
			.filter((edge) => edge.sourceId !== portId && edge.targetId !== portId)
			.concat(bridges);
	}
	return result;
}

/** Label on the edge from a try body into its catch body. */
export const CATCH_EDGE_LABEL = "on error";

const exitPortOf = (container: SdkNode) =>
	"nodes" in container
		? container.nodes.find((child) => child.type === GraphNodeType.Exit)
		: undefined;

const entryPortOf = (container: SdkNode) =>
	"nodes" in container
		? container.nodes.find((child) => child.type === GraphNodeType.Entry)
		: undefined;

/**
 * The SDK chains a try-catch as `try exit -> catch entry -> catch exit`, which reads as "the catch
 * body always runs". At runtime the catch body runs only after a handled failure, and a successful
 * try body continues straight past the try-catch. Label the try -> catch edge as the error path and
 * add the success edge from the try body's exit to the try-catch's exit.
 */
function splitTryCatchPaths(
	edges: WorkingEdge[],
	sdkNodes: Iterable<SdkNode>,
): WorkingEdge[] {
	const result = [...edges];
	for (const node of sdkNodes) {
		if (node.type !== GraphNodeType.TryCatch || !("nodes" in node)) continue;
		const tryBody = node.nodes.find(
			(child) => child.type === GraphNodeType.Try,
		);
		const catchBody = node.nodes.find(
			(child) => child.type === GraphNodeType.Catch,
		);
		const tryExit = tryBody && exitPortOf(tryBody);
		const catchEntry = catchBody && entryPortOf(catchBody);
		const outerExit = exitPortOf(node);
		if (!tryExit || !catchEntry || !outerExit) continue;

		for (const [i, edge] of result.entries()) {
			if (edge.sourceId === tryExit.id && edge.targetId === catchEntry.id) {
				result[i] = { ...edge, label: CATCH_EDGE_LABEL };
			}
		}
		const hasSuccessEdge = result.some(
			(edge) => edge.sourceId === tryExit.id && edge.targetId === outerExit.id,
		);
		if (!hasSuccessEdge) {
			result.push({ sourceId: tryExit.id, targetId: outerExit.id, label: "" });
		}
	}
	return result;
}

function nodeKind(node: SdkNode): NodeKind {
	if (node.type === GraphNodeType.Start) return "start";
	if (node.type === GraphNodeType.End) return "end";
	return "nodes" in node && node.nodes.length > 0 ? "container" : "task";
}

function displayName(node: SdkNode, kind: NodeKind): string {
	if (node.label) return node.label;
	if (kind === "start") return "Start";
	if (kind === "end") return "End";
	return node.id;
}

function toDefinitionGraph(root: Graph): DefinitionGraph {
	const { nodes: sdkNodes, edges: sdkEdges } = flattenSdkGraph(root);

	const nodes: DefinitionGraphNode[] = [];
	const idBySdkId = new Map<string, string>();
	for (const [sdkId, { node, parent }] of sdkNodes) {
		if (node.type === GraphNodeType.Root || isPort(node)) continue;

		const kind = nodeKind(node);
		const id = consoleId(node);
		idBySdkId.set(sdkId, id);
		nodes.push({
			id,
			name: displayName(node, kind),
			taskType: node.type,
			kind,
			parentId:
				parent && parent.type !== GraphNodeType.Root
					? consoleId(parent)
					: undefined,
		});
	}

	const portIds = [...sdkNodes.values()]
		.filter(({ node }) => isPort(node))
		.map(({ node }) => node.id);
	const edges: DefinitionGraphEdge[] = [];
	const seen = new Set<string>();
	const workingEdges = splitTryCatchPaths(
		sdkEdges.map((edge) => ({
			sourceId: edge.sourceId,
			targetId: edge.targetId,
			label: edge.label ?? "",
		})),
		[...sdkNodes.values()].map(({ node }) => node),
	);
	for (const edge of contractPorts(workingEdges, portIds)) {
		const source = idBySdkId.get(edge.sourceId);
		const target = idBySdkId.get(edge.targetId);
		if (!source || !target || source === target) continue;

		const key = `${source}->${target}:${edge.label}`;
		if (seen.has(key)) continue;
		seen.add(key);
		edges.push({
			id: `${source}-${target}${edge.label ? `-${edge.label}` : ""}`,
			source,
			target,
			label: edge.label || undefined,
		});
	}

	return { nodes, edges };
}

/**
 * Parses YAML or JSON, applies the shape guard, and maps the SDK graph to console nodes/edges.
 *
 * Uses `new Classes.Workflow(parsed)` and never `deserialize()`: deserialization validates against
 * DSL 1.0.3 and rejects shapes DWS deploys. Any throw (parse error, malformed shape the guard
 * missed, SDK failure) becomes `{ ok: false }`.
 */
export function buildDefinitionGraph(
	text: string,
	format: DefinitionFormat,
): BuildGraphResult {
	try {
		const parsed = format === "json" ? JSON.parse(text) : yaml.parse(text);
		const invalid = shapeError(parsed);
		if (invalid) return { ok: false, error: invalid };
		return {
			ok: true,
			graph: toDefinitionGraph(buildGraph(new Classes.Workflow(parsed))),
		};
	} catch (error) {
		return {
			ok: false,
			error: error instanceof Error ? error.message : String(error),
		};
	}
}
