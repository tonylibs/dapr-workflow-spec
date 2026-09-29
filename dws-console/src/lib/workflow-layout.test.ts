import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { buildDefinitionGraph } from "./definition-graph-model";
import { layoutWorkflowGraph } from "./workflow-layout";

const tryOrderPath = path.resolve(
	__dirname,
	"../../../dws-orchestrator/src/test/resources/try-order.yaml",
);

const forForkYaml = `
document:
  dsl: '1.0.0'
  namespace: default
  name: for-fork
do:
  - loopTask:
      for:
        each: item
        in: '\${ .items }'
      do:
        - doItem:
            set:
              seen: true
  - parallelTask:
      fork:
        branches:
          - b1:
              do:
                - branch1Step:
                    set:
                      a: 1
          - b2:
              do:
                - branch2Step:
                    set:
                      b: 2
`;

async function layoutOf(text: string) {
	const built = buildDefinitionGraph(text, "yaml");
	if (!built.ok) throw new Error(built.error);
	return layoutWorkflowGraph(built.graph);
}

describe("layoutWorkflowGraph", () => {
	it("lays out try/catch containers with their nested tasks inside", async () => {
		const { nodes, edges } = await layoutOf(
			fs.readFileSync(tryOrderPath, "utf8"),
		);

		const guarded = nodes.find((n) => n.data.name === "guarded");
		expect(guarded?.type).toBe("containerNode");
		expect(guarded?.parentId).toBeUndefined();
		expect(guarded?.style?.width).toBeGreaterThan(0);

		const fetchOrder = nodes.find((n) => n.data.name === "fetchOrder");
		const tryBody = nodes.find((n) => n.data.name === "guarded (try)");
		expect(tryBody?.parentId).toBe(guarded?.id);
		expect(tryBody?.type).toBe("containerNode");
		expect(fetchOrder?.parentId).toBe(tryBody?.id);
		expect(fetchOrder?.extent).toBe("parent");

		const catchBody = nodes.find((n) => n.data.name === "guarded (catch)");
		expect(nodes.find((n) => n.data.name === "recordFailure")?.parentId).toBe(
			catchBody?.id,
		);
		expect(edges.length).toBeGreaterThan(0);
	});

	it("lays out for and fork containers", async () => {
		const { nodes } = await layoutOf(forForkYaml);

		for (const name of ["loopTask", "parallelTask"]) {
			expect(nodes.find((n) => n.data.name === name)?.type).toBe(
				"containerNode",
			);
		}
		const loopTask = nodes.find((n) => n.data.name === "loopTask");
		expect(nodes.find((n) => n.data.name === "doItem")?.parentId).toBe(
			loopTask?.id,
		);
		expect(nodes.find((n) => n.data.name === "branch1Step")?.parentId).toBe(
			nodes.find((n) => n.data.name === "b1")?.id,
		);
	});

	it("emits parents before their children, as xyflow requires", async () => {
		const { nodes } = await layoutOf(fs.readFileSync(tryOrderPath, "utf8"));

		const seen = new Set<string>();
		for (const node of nodes) {
			if (node.parentId) expect(seen.has(node.parentId)).toBe(true);
			seen.add(node.id);
		}
	});

	it("names task nodes '<name>, <type> task' and sizes them by kind", async () => {
		const { nodes } = await layoutOf(forForkYaml);

		const doItem = nodes.find((n) => n.data.name === "doItem");
		expect(doItem?.ariaLabel).toBe("doItem, set task");
		expect(doItem?.style).toEqual({ width: 200, height: 68 });
		const start = nodes.find((n) => n.data.kind === "start");
		expect(start?.type).toBe("startEndNode");
		expect(start?.style).toEqual({ width: 120, height: 44 });
	});
});
