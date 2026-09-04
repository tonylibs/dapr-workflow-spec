// @vitest-environment jsdom
// The leading dash keeps TanStack Router from treating this as a route module.
import {
	cleanup,
	fireEvent,
	render,
	screen,
	waitFor,
} from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
	definitionDraftDefaults,
	useDefinitionDraftStore,
} from "#/lib/definition-draft-store";

const { submitDefinitionMock, useOidcMock } = vi.hoisted(() => ({
	submitDefinitionMock: vi.fn(),
	useOidcMock: vi.fn(),
}));

vi.mock("@tanstack/react-router", async (importOriginal) => {
	const actual =
		await importOriginal<typeof import("@tanstack/react-router")>();
	return {
		...actual,
		Link: ({ children }: { children: ReactNode }) => (
			<a href="/workflows">{children}</a>
		),
	};
});

vi.mock("@uiw/react-codemirror", () => ({
	default: ({
		onChange,
		value,
	}: {
		onChange: (value: string) => void;
		value: string;
	}) => (
		<textarea
			aria-label="Workflow definition"
			onChange={(event) => onChange(event.target.value)}
			value={value}
		/>
	),
}));

vi.mock("#/components/app-layout", () => ({
	AppLayout: ({ children }: { children: ReactNode }) => <main>{children}</main>,
}));

vi.mock("#/components/states", () => ({
	Banner: ({ children }: { children: ReactNode }) => (
		<output>{children}</output>
	),
}));

vi.mock("#/lib/admin-client", () => ({
	ApiError: class ApiError extends Error {},
	AuthenticationError: class AuthenticationError extends Error {},
	submitDefinition: submitDefinitionMock,
}));

vi.mock("#/lib/oidc", () => ({ useOidc: useOidcMock }));

import { DefinitionEditor } from "./definition-editor";

function resetDraft() {
	localStorage.clear();
	useDefinitionDraftStore.setState(definitionDraftDefaults);
}

beforeEach(() => {
	resetDraft();
	submitDefinitionMock.mockReset();
	useOidcMock.mockReturnValue({ isUserLoggedIn: true });
});

afterEach(() => {
	cleanup();
	resetDraft();
});

describe("DefinitionEditor file import", () => {
	it("loads a JSON file into the editable definition buffer and selects JSON", async () => {
		render(<DefinitionEditor />);

		const fileInput = screen.getByLabelText("Import definition");
		expect((fileInput as HTMLInputElement).accept).toBe(".yaml,.yml,.json");

		Object.defineProperty(fileInput, "files", {
			configurable: true,
			value: [
				{
					name: "shipping.JSON",
					text: vi.fn().mockResolvedValue('{"document":{"name":"shipping"}}'),
				},
			],
		});
		fireEvent.change(fileInput);

		const editor = screen.getByLabelText("Workflow definition");
		await waitFor(() =>
			expect((editor as HTMLTextAreaElement).value).toBe(
				'{"document":{"name":"shipping"}}',
			),
		);
		expect((screen.getByLabelText("Format") as HTMLSelectElement).value).toBe(
			"json",
		);

		fireEvent.change(editor, {
			target: { value: '{"document":{"name":"edited-shipping"}}' },
		});
		expect((editor as HTMLTextAreaElement).value).toBe(
			'{"document":{"name":"edited-shipping"}}',
		);
	});

	it("retains the existing draft and reports an unreadable selected file", async () => {
		useDefinitionDraftStore.getState().setDefinition("existing: draft");
		render(<DefinitionEditor />);

		const fileInput = screen.getByLabelText("Import definition");
		Object.defineProperty(fileInput, "files", {
			configurable: true,
			value: [
				{
					name: "unreadable.yaml",
					text: vi.fn().mockRejectedValue(new Error("Permission denied")),
				},
			],
		});
		fireEvent.change(fileInput);

		await waitFor(() =>
			expect(screen.getByRole("status").textContent).toContain(
				"Could not read the selected file: Permission denied",
			),
		);
		expect(
			(screen.getByLabelText("Workflow definition") as HTMLTextAreaElement)
				.value,
		).toBe("existing: draft");
	});
});
