import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
	buildDefinitionGraph,
	countErrorsByNode,
	findNodeForErrorPath,
} from "./definition-graph-model";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const controllerFixturesDir = path.resolve(
	__dirname,
	"../../../dws-controller/src/test/resources/fixtures",
);
const orchestratorFixturesDir = path.resolve(
	__dirname,
	"../../../dws-orchestrator/src/test/resources",
);

describe("definition-graph-model", () => {
	describe("error-path -> node helper (findNodeForErrorPath)", () => {
		const nodes = [
			{ id: "/do/0/checkInventory" },
			{ id: "/do/1/approve" },
			{ id: "/do/0/guarded" },
			{ id: "/do/0/guarded/try/0/fetchOrder" },
			{ id: "/do/0/guarded/catch/do/0/recordFailure" },
		];

		it("matches task-level path to closest enclosing task", () => {
			const match = findNodeForErrorPath(
				"/do/1/approve/call/with/endpoint",
				nodes,
			);
			expect(match).toBeDefined();
			expect(match?.id).toBe("/do/1/approve");
		});

		it("matches exact task reference path", () => {
			const match = findNodeForErrorPath("/do/0/checkInventory", nodes);
			expect(match?.id).toBe("/do/0/checkInventory");
		});

		it("matches nested path to the deepest enclosing task node", () => {
			const match = findNodeForErrorPath(
				"/do/0/guarded/try/0/fetchOrder/call",
				nodes,
			);
			expect(match?.id).toBe("/do/0/guarded/try/0/fetchOrder");
		});

		it("returns undefined for document-level path", () => {
			const match = findNodeForErrorPath("/document/name", nodes);
			expect(match).toBeUndefined();
		});

		it("returns undefined for non-matching or empty paths", () => {
			expect(findNodeForErrorPath("", nodes)).toBeUndefined();
			expect(findNodeForErrorPath("/", nodes)).toBeUndefined();
			expect(findNodeForErrorPath("/other/path", nodes)).toBeUndefined();
		});
	});

	describe("countErrorsByNode", () => {
		const nodes = [{ id: "/do/0/first" }, { id: "/do/1/approve" }];

		it("counts errors per closest enclosing node and ignores unmatched paths", () => {
			const counts = countErrorsByNode(
				[
					{ path: "/do/1/approve/call" },
					{ path: "/do/1/approve/with/endpoint" },
					{ path: "/do/0/first" },
					{ path: "/document/name" },
					{ path: "" },
				],
				nodes,
			);
			expect(Object.fromEntries(counts)).toEqual({
				"/do/1/approve": 2,
				"/do/0/first": 1,
			});
		});

		it("does not match a sibling whose name only shares a string prefix", () => {
			const counts = countErrorsByNode(
				[{ path: "/do/1/approved/call" }],
				nodes,
			);
			expect(counts.size).toBe(0);
		});
	});

	describe("fixture parity over dws-controller fixtures", () => {
		const fixtureFiles = fs
			.readdirSync(controllerFixturesDir)
			.filter((f) => f.endsWith(".yaml"));

		it("finds fixtures in controller directory", () => {
			expect(fixtureFiles.length).toBeGreaterThanOrEqual(10);
		});

		for (const file of fixtureFiles) {
			if (file === "broken.yaml") {
				it(`broken.yaml yields a handled error without throwing`, () => {
					const content = fs.readFileSync(
						path.join(controllerFixturesDir, file),
						"utf8",
					);
					const result = buildDefinitionGraph(content, "yaml");
					expect(result.ok).toBe(false);
					if (!result.ok) {
						expect(result.error).toBeTruthy();
					}
				});
			} else {
				it(`${file} yields a non-empty graph`, () => {
					const content = fs.readFileSync(
						path.join(controllerFixturesDir, file),
						"utf8",
					);
					const result = buildDefinitionGraph(content, "yaml");
					expect(result.ok).toBe(true);
					if (result.ok) {
						expect(result.graph.nodes.length).toBeGreaterThan(0);
						const taskOrContainers = result.graph.nodes.filter(
							(n) => n.kind === "task" || n.kind === "container",
						);
						expect(taskOrContainers.length).toBeGreaterThan(0);
					}
				});
			}
		}
	});

	describe("control-flow tests", () => {
		it("order.yaml produces task nodes and switch jump edges", () => {
			const content = fs.readFileSync(
				path.join(controllerFixturesDir, "order.yaml"),
				"utf8",
			);
			const result = buildDefinitionGraph(content, "yaml");
			expect(result.ok).toBe(true);
			if (!result.ok) return;

			const { nodes, edges } = result.graph;
			const nodeNames = nodes.map((n) => n.name);
			expect(nodeNames).toContain("checkInventory");
			expect(nodeNames).toContain("decide");
			expect(nodeNames).toContain("chargePayment");
			expect(nodeNames).toContain("notifyOutOfStock");

			// Verify switch edges with labels
			const switchNode = nodes.find((n) => n.name === "decide");
			expect(switchNode).toBeDefined();
			const switchEdges = edges.filter((e) => e.source === switchNode?.id);
			expect(switchEdges.length).toBe(2);
			expect(switchEdges.map((e) => e.label)).toContain("inStock");
			expect(switchEdges.map((e) => e.label)).toContain("outOfStock");
		});

		it("try-order.yaml produces container nodes for try/catch and nested tasks", () => {
			const content = fs.readFileSync(
				path.join(orchestratorFixturesDir, "try-order.yaml"),
				"utf8",
			);
			const result = buildDefinitionGraph(content, "yaml");
			expect(result.ok).toBe(true);
			if (!result.ok) return;

			const { nodes } = result.graph;
			const containers = nodes.filter((n) => n.kind === "container");
			expect(containers.length).toBeGreaterThanOrEqual(1);

			const guardedContainer = nodes.find(
				(n) =>
					n.kind === "container" &&
					(n.taskType === "try-catch" || n.name === "guarded"),
			);
			expect(guardedContainer).toBeDefined();

			const fetchOrderNode = nodes.find((n) => n.name === "fetchOrder");
			expect(fetchOrderNode).toBeDefined();
			expect(fetchOrderNode?.parentId).toBeDefined();

			const recordFailureNode = nodes.find((n) => n.name === "recordFailure");
			expect(recordFailureNode).toBeDefined();
			expect(recordFailureNode?.parentId).toBeDefined();
		});

		it("handles for loop and fork containers", () => {
			const workflowWithForAndFork = `
document:
  dsl: '1.0.0'
  namespace: default
  name: for-fork-test
do:
  - loopTask:
      for:
        each: item
        in: '\${ .items }'
      do:
        - doItem:
            call: http
            with:
              method: get
              endpoint: http://example.com/item
  - parallelTask:
      fork:
        branches:
          - b1:
              do:
                - branch1Step:
                    call: http
                    with:
                      method: get
                      endpoint: http://example.com/1
          - b2:
              do:
                - branch2Step:
                    call: http
                    with:
                      method: get
                      endpoint: http://example.com/2
`;
			const result = buildDefinitionGraph(workflowWithForAndFork, "yaml");
			expect(result.ok).toBe(true);
			if (!result.ok) return;

			const { nodes } = result.graph;
			const forNode = nodes.find((n) => n.name === "loopTask");
			expect(forNode).toBeDefined();
			expect(forNode?.kind).toBe("container");

			const forkNode = nodes.find((n) => n.name === "parallelTask");
			expect(forkNode).toBeDefined();
			expect(forkNode?.kind).toBe("container");
		});
	});

	describe("validation, parsing and format tests", () => {
		it("returns error with line/column for YAML parse error", () => {
			const invalidYaml = `
document:
  dsl: '1.0.0'
  name: test: [unclosed
do:
  - task1:
      call: http
`;
			const result = buildDefinitionGraph(invalidYaml, "yaml");
			expect(result.ok).toBe(false);
			if (!result.ok) {
				expect(result.error).toMatch(/line|col/i);
			}
		});

		it("returns error for invalid JSON syntax", () => {
			const invalidJson = `{"document": {"dsl": "1.0.0"}, "do": [}`;
			const result = buildDefinitionGraph(invalidJson, "json");
			expect(result.ok).toBe(false);
			if (!result.ok) {
				expect(result.error).toBeTruthy();
			}
		});

		it("handles valid JSON input", () => {
			const validJson = JSON.stringify({
				document: { dsl: "1.0.0", namespace: "default", name: "json-test" },
				do: [
					{
						step1: {
							call: "http",
							with: { method: "get", endpoint: "http://example.com" },
						},
					},
				],
			});
			const result = buildDefinitionGraph(validJson, "json");
			expect(result.ok).toBe(true);
			if (result.ok) {
				expect(result.graph.nodes.some((n) => n.name === "step1")).toBe(true);
			}
		});

		it("reports missing do list", () => {
			const noDo = `
document:
  dsl: '1.0.0'
  name: no-do
`;
			const result = buildDefinitionGraph(noDo, "yaml");
			expect(result.ok).toBe(false);
			if (!result.ok) {
				expect(result.error).toContain('"do" task list');
			}
		});

		it("reports missing document object", () => {
			const noDoc = `
do:
  - step:
      call: http
`;
			const result = buildDefinitionGraph(noDoc, "yaml");
			expect(result.ok).toBe(false);
			if (!result.ok) {
				expect(result.error).toContain("document object");
			}
		});
	});

	describe("module purity check", () => {
		it("has no React import in definition-graph-model.ts", () => {
			const modelSource = fs.readFileSync(
				path.join(__dirname, "definition-graph-model.ts"),
				"utf8",
			);
			expect(modelSource).not.toMatch(/from\s+['"]react['"]/);
			expect(modelSource).not.toMatch(/import\s+.*['"]react['"]/);
		});
	});
});
