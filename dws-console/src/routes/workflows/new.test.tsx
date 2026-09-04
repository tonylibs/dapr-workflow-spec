// @vitest-environment jsdom
/**
 * Behaviour coverage for the definition editor's `Preview` action.
 *
 * Only the module boundaries are mocked — `#/lib/admin-client` (so no request
 * is issued and the two-layer ordering can be observed directly), `#/lib/oidc`
 * (so no login starts), and `@tanstack/react-router` (the component is rendered
 * outside a router, and `Link` would otherwise demand its context). CodeMirror
 * itself runs for real, so the `aria-label` these tests type through is the one
 * `new.tsx` actually publishes to assistive tech, not a test double's.
 */

import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("#/lib/oidc", () => ({
	useOidc: () => ({ isUserLoggedIn: true }),
	useAuth: () => ({ status: "unavailable" }),
	getAccessToken: vi.fn(),
}));

vi.mock("@tanstack/react-router", async (importOriginal) => ({
	...(await importOriginal<object>()),
	// The route registration is a module-level side effect of importing new.tsx;
	// it only has to not throw for the component under test to be reachable.
	createFileRoute: () => (options: unknown) => options,
	Link: ({ to, children, ...rest }: { to: string; children: ReactNode }) => (
		<a href={to} {...rest}>
			{children}
		</a>
	),
}));

vi.mock("#/lib/admin-client", async (importOriginal) => ({
	...(await importOriginal<object>()),
	validateDefinitionSpec: vi.fn(),
	previewDefinition: vi.fn(),
	submitDefinition: vi.fn(),
}));

import { previewDefinition, validateDefinitionSpec } from "#/lib/admin-client";
import { DefinitionEditor } from "./new";

// CodeMirror measures itself through Range client rects on every layout pass;
// jsdom implements neither, and the unhandled rejection would otherwise fail
// the run from inside a requestAnimationFrame callback.
Range.prototype.getClientRects = () => ({
	length: 0,
	item: () => null,
	[Symbol.iterator]: function* () {},
}) as unknown as DOMRectList;
Range.prototype.getBoundingClientRect = () => new DOMRect();

/**
 * Enters text into the editor.
 *
 * A paste, not keystrokes: CodeMirror's `beforeinput`/composition path needs
 * browser APIs jsdom does not provide, but its paste handler only reads
 * `clipboardData.getData`, which is enough to drive the real editor's document
 * (and therefore the component's `onChange`).
 */
const type = (text: string) => {
	const editor = screen.getByLabelText("Workflow definition");
	fireEvent.paste(editor, {
		clipboardData: { getData: () => text, types: ["text/plain"] },
	});
};

const previewButton = () =>
	screen.getByRole("button", { name: /preview/i }) as HTMLButtonElement;

const PLAN = {
	workflow: "order",
	versionId: "v1",
	version: "order@v1",
	definitionResource: "dws-def-order-v1",
	specText: "",
	steps: [{ name: "fetch-order", kind: "CALL_HTTP", image: "img:1" }],
	bindings: [{ task: "notify", direction: "EMIT", topic: "orders" }],
	orchestrator: {
		name: "orch",
		image: "orch:1",
		appId: "order",
		appPort: 8080,
		replicas: 1,
	},
};

beforeEach(() => {
	vi.mocked(validateDefinitionSpec).mockReset();
	vi.mocked(previewDefinition).mockReset();
});

afterEach(cleanup);

describe("definition editor preview", () => {
	it("disables preview while the buffer is empty", () => {
		render(<DefinitionEditor />);
		expect(previewButton().disabled).toBe(true);
	});

	it("renders spec errors with their path and never calls the dry run", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({
			valid: false,
			truncated: false,
			errors: [{ path: "/do/0", message: "must have required property 'call'" }],
		});
		render(<DefinitionEditor />);
		type("document: {}");
		fireEvent.click(previewButton());

		expect(await screen.findByText(/must have required property/)).toBeTruthy();
		expect(screen.getByText("/do/0")).toBeTruthy();
		expect(previewDefinition).not.toHaveBeenCalled();
	});

	it("renders a parse error's line and column", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({
			valid: false,
			truncated: false,
			errors: [
				{ path: "", message: "Flow map must end with }", line: 2, column: 9 },
			],
		});
		render(<DefinitionEditor />);
		type("x");
		fireEvent.click(previewButton());

		expect(await screen.findByText(/line 2, column 9/i)).toBeTruthy();
	});

	it("renders the plan when both layers pass", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({ kind: "plan", plan: PLAN });
		render(<DefinitionEditor />);
		type("document: {}");
		fireEvent.click(previewButton());

		expect(await screen.findByText(/would deploy order@v1/i)).toBeTruthy();
		expect(screen.getByText("fetch-order")).toBeTruthy();
		expect(screen.getByText("orders")).toBeTruthy();
		expect(validateDefinitionSpec).toHaveBeenCalledWith("document: {}", "yaml");
	});

	it("renders controller rejections as flat strings", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({
			kind: "deploy-error",
			errors: ["task 'a': run: container is not supported"],
		});
		render(<DefinitionEditor />);
		type("document: {}");
		fireEvent.click(previewButton());

		expect(
			await screen.findByText(/run: container is not supported/),
		).toBeTruthy();
	});

	it("clears a rendered plan when the buffer changes", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({
			kind: "plan",
			plan: { ...PLAN, steps: [], bindings: [] },
		});
		render(<DefinitionEditor />);
		type("document: {}");
		fireEvent.click(previewButton());
		await screen.findByText(/would deploy/i);

		type("\nmore: text");

		await waitFor(() =>
			expect(screen.queryByText(/would deploy/i)).toBeNull(),
		);
	});
});
