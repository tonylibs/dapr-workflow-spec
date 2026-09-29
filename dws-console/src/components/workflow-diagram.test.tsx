// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import WorkflowDiagram from "./workflow-diagram";

const sampleOrderYaml = `
document:
  dsl: '1.0.0'
  namespace: default
  name: order-workflow
do:
  - checkInventory:
      call: http
      with:
        method: post
        endpoint: http://inventory/check
  - decide:
      switch:
        - inStock:
            then: chargePayment
        - outOfStock:
            then: notifyOutOfStock
  - chargePayment:
      call: http
      with:
        method: post
        endpoint: http://billing/charge
  - notifyOutOfStock:
      call: http
      with:
        method: post
        endpoint: http://notifications/send
`;

beforeEach(() => {
	class MockDOMMatrixReadOnly {
		a = 1;
		b = 0;
		c = 0;
		d = 1;
		e = 0;
		f = 0;
		m41 = 0;
		m42 = 0;
		transformPoint(p: unknown) {
			return p;
		}
	}
	(window as unknown as { DOMMatrixReadOnly: unknown }).DOMMatrixReadOnly =
		MockDOMMatrixReadOnly;
	(globalThis as unknown as { DOMMatrixReadOnly: unknown }).DOMMatrixReadOnly =
		MockDOMMatrixReadOnly;

	Object.defineProperties(window.HTMLElement.prototype, {
		offsetWidth: {
			configurable: true,
			get() {
				return Number.parseFloat(this.style.width) || 200;
			},
		},
		offsetHeight: {
			configurable: true,
			get() {
				return Number.parseFloat(this.style.height) || 68;
			},
		},
	});

	globalThis.ResizeObserver = class {
		callback: (entries: unknown[]) => void;
		constructor(cb: (entries: unknown[]) => void) {
			this.callback = cb;
		}
		observe(target: Element) {
			setTimeout(() => {
				this.callback([
					{
						target,
						contentRect: {
							width: 200,
							height: 68,
							top: 0,
							left: 0,
							bottom: 68,
							right: 200,
							x: 0,
							y: 0,
							toJSON: () => {},
						},
						borderBoxSize: [],
						contentBoxSize: [],
						devicePixelContentBoxSize: [],
					},
				]);
			}, 0);
		}
		unobserve() {}
		disconnect() {}
	} as unknown as typeof ResizeObserver;
});

afterEach(() => {
	cleanup();
});

describe("WorkflowDiagram component", () => {
	it("renders zoom and fit view controls with accessible labels", async () => {
		render(<WorkflowDiagram definition={sampleOrderYaml} format="yaml" />);

		expect(
			screen.getByRole("toolbar", { name: /diagram zoom controls/i }),
		).toBeDefined();
		expect(screen.getByRole("button", { name: "Zoom in" })).toBeDefined();
		expect(screen.getByRole("button", { name: "Zoom out" })).toBeDefined();
		expect(screen.getByRole("button", { name: "Fit view" })).toBeDefined();
	});

	it("renders task nodes with accessible names and names matching definition", async () => {
		render(<WorkflowDiagram definition={sampleOrderYaml} format="yaml" />);

		await waitFor(() => {
			expect(screen.getByText("checkInventory")).toBeDefined();
			expect(screen.getByText("decide")).toBeDefined();
			expect(screen.getByText("chargePayment")).toBeDefined();
			expect(screen.getByText("notifyOutOfStock")).toBeDefined();
		});

		// Check accessible aria labels
		expect(screen.getByLabelText("checkInventory, call task")).toBeDefined();
		expect(screen.getByLabelText("decide, switch task")).toBeDefined();
	});

	it("keeps last good graph and shows stale banner after an invalid edit", async () => {
		const { rerender } = render(
			<WorkflowDiagram definition={sampleOrderYaml} format="yaml" />,
		);

		// Initial graph renders
		await waitFor(() => {
			expect(screen.getByText("checkInventory")).toBeDefined();
		});
		expect(screen.queryByTestId("stale-banner")).toBeNull();

		// Edited to broken syntax
		rerender(
			<WorkflowDiagram definition="document: [invalid yaml" format="yaml" />,
		);

		// Stale banner appears after debounce, but last good graph nodes stay
		await waitFor(
			() => {
				expect(screen.getByTestId("stale-banner")).toBeDefined();
				expect(screen.getByText("checkInventory")).toBeDefined();
			},
			{ timeout: 1500 },
		);
	});

	it("renders error badges on matching nodes when specErrors are provided and clears them", async () => {
		const specErrors = [
			{
				path: "/do/0/checkInventory/call",
				message: "Endpoint required",
			},
		];

		const { rerender } = render(
			<WorkflowDiagram
				definition={sampleOrderYaml}
				format="yaml"
				specErrors={specErrors}
			/>,
		);

		// Error badge appears on checkInventory
		await waitFor(() => {
			expect(
				screen.getByTestId("error-badge-/do/0/checkInventory"),
			).toBeDefined();
		});

		// Clear spec errors
		rerender(
			<WorkflowDiagram
				definition={sampleOrderYaml}
				format="yaml"
				specErrors={undefined}
			/>,
		);

		await waitFor(() => {
			expect(
				screen.queryByTestId("error-badge-/do/0/checkInventory"),
			).toBeNull();
		});
	});
});
