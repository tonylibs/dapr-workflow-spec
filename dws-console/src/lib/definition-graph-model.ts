import { buildGraph, Classes } from "@openworkflowspec/sdk";
import yaml from "yaml";

export type NodeKind = "task" | "container" | "start" | "end";

export interface DefinitionGraphNode {
	id: string;
	name: string;
	taskType: string;
	kind: NodeKind;
	parentId?: string;
	ariaLabel: string;
}

export interface DefinitionGraphEdge {
	id: string;
	source: string;
	target: string;
	label?: string;
}

export interface DefinitionGraph {
	nodes: DefinitionGraphNode[];
	edges: DefinitionGraphEdge[];
}

export type BuildGraphResult =
	| { ok: true; graph: DefinitionGraph }
	| { ok: false; error: string };

/**
 * Finds the node whose id is the longest segment-wise prefix of the given error JSON pointer path.
 * Returns undefined if no matching task node is found.
 */
export function findNodeForErrorPath<T extends { id: string }>(
	errorPath: string,
	nodes: T[],
): T | undefined {
	if (!errorPath) return undefined;
	const errorSegments = errorPath.split("/").filter(Boolean);
	if (errorSegments.length === 0) return undefined;

	let bestMatch: T | undefined;
	let bestLength = 0;

	for (const node of nodes) {
		const nodeSegments = node.id.split("/").filter(Boolean);
		if (nodeSegments.length === 0) continue;
		if (nodeSegments.length > errorSegments.length) continue;

		let isPrefix = true;
		for (let i = 0; i < nodeSegments.length; i++) {
			if (nodeSegments[i] !== errorSegments[i]) {
				isPrefix = false;
				break;
			}
		}

		if (isPrefix && nodeSegments.length > bestLength) {
			bestLength = nodeSegments.length;
			bestMatch = node;
		}
	}

	return bestMatch;
}

/**
 * Pure, React-free definition -> graph model function.
 * Parses YAML or JSON, applies shape guard, builds SDK graph and maps to console-owned nodes/edges.
 */
export function buildDefinitionGraph(
	text: string,
	format: "yaml" | "json" = "yaml",
): BuildGraphResult {
	let parsed: unknown;
	try {
		if (format === "json") {
			parsed = JSON.parse(text);
		} else {
			parsed = yaml.parse(text);
		}
	} catch (err) {
		return {
			ok: false,
			error: err instanceof Error ? err.message : String(err),
		};
	}

	if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
		return { ok: false, error: "Definition must be an object" };
	}
	const obj = parsed as Record<string, unknown>;
	const doc = obj.document;
	if (!doc || typeof doc !== "object" || Array.isArray(doc)) {
		return { ok: false, error: "Definition must contain a document object" };
	}
	const doList = obj.do;
	if (!Array.isArray(doList) || doList.length === 0) {
		return {
			ok: false,
			error: 'Definition must contain a non-empty "do" task list',
		};
	}

	try {
		const workflow = new Classes.Workflow(parsed);
		const sdkGraph = buildGraph(workflow);

		interface SdkNode {
			id: string;
			label?: string;
			type: string;
			taskReference?: string;
			nodes?: SdkNode[];
			edges?: SdkEdge[];
		}
		interface SdkEdge {
			id?: string;
			sourceId: string;
			targetId: string;
			label?: string;
		}

		const allNodesMap = new Map<
			string,
			{ node: SdkNode; parent: SdkNode | null }
		>();
		const allEdges: SdkEdge[] = [];

		function collectSdk(node: SdkNode, parent: SdkNode | null) {
			allNodesMap.set(node.id, { node, parent });
			if (node.edges) {
				allEdges.push(...node.edges);
			}
			if (node.nodes) {
				for (const child of node.nodes) {
					collectSdk(child, node);
				}
			}
		}
		collectSdk(sdkGraph as unknown as SdkNode, null);

		const isPort = (id: string) => {
			const item = allNodesMap.get(id);
			return item
				? item.node.type === "entry" || item.node.type === "exit"
				: false;
		};

		let workingEdges = allEdges.map((e) => ({
			sourceId: e.sourceId,
			targetId: e.targetId,
			label: e.label || "",
		}));

		// Contract entry/exit port nodes
		const portIds = Array.from(allNodesMap.keys()).filter(isPort);
		for (const portId of portIds) {
			const inEdges = workingEdges.filter((e) => e.targetId === portId);
			const outEdges = workingEdges.filter((e) => e.sourceId === portId);

			const cross: Array<{
				sourceId: string;
				targetId: string;
				label: string;
			}> = [];
			for (const inE of inEdges) {
				for (const outE of outEdges) {
					if (inE.sourceId !== outE.targetId) {
						cross.push({
							sourceId: inE.sourceId,
							targetId: outE.targetId,
							label: inE.label || outE.label || "",
						});
					}
				}
			}
			workingEdges = workingEdges
				.filter((e) => e.sourceId !== portId && e.targetId !== portId)
				.concat(cross);
		}

		const consoleNodes: DefinitionGraphNode[] = [];
		const sdkIdToConsoleId = new Map<string, string>();

		for (const [id, { node, parent }] of allNodesMap) {
			if (id === "root" || isPort(id)) continue;

			let kind: NodeKind = "task";
			if (node.type === "start") kind = "start";
			else if (node.type === "end") kind = "end";
			else if (node.nodes && node.nodes.length > 0) kind = "container";
			else if (["try", "catch", "for", "fork", "try-catch"].includes(node.type))
				kind = "container";

			const consoleId = node.taskReference || node.id;
			sdkIdToConsoleId.set(id, consoleId);

			let parentId: string | undefined;
			if (parent && parent.id !== "root" && !isPort(parent.id)) {
				parentId = parent.taskReference || parent.id;
			}

			const name =
				node.label ||
				(kind === "start" ? "Start" : kind === "end" ? "End" : node.id);
			const taskType =
				node.type ||
				(kind === "start" ? "start" : kind === "end" ? "end" : "task");

			let ariaLabel: string;
			if (kind === "start") {
				ariaLabel = "Start";
			} else if (kind === "end") {
				ariaLabel = "End";
			} else if (kind === "container") {
				ariaLabel = `${name}, ${taskType} container`;
			} else {
				ariaLabel = `${name}, ${taskType} task`;
			}

			consoleNodes.push({
				id: consoleId,
				name,
				taskType,
				kind,
				parentId,
				ariaLabel,
			});
		}

		const seenEdges = new Set<string>();
		const consoleEdges: DefinitionGraphEdge[] = [];
		for (const e of workingEdges) {
			const source = sdkIdToConsoleId.get(e.sourceId);
			const target = sdkIdToConsoleId.get(e.targetId);
			if (!source || !target || source === target) continue;
			const key = `${source}->${target}:${e.label}`;
			if (seenEdges.has(key)) continue;
			seenEdges.add(key);
			consoleEdges.push({
				id: `${source}-${target}${e.label ? `-${e.label}` : ""}`,
				source,
				target,
				label: e.label || undefined,
			});
		}

		return {
			ok: true,
			graph: {
				nodes: consoleNodes,
				edges: consoleEdges,
			},
		};
	} catch (err) {
		return {
			ok: false,
			error: err instanceof Error ? err.message : String(err),
		};
	}
}
