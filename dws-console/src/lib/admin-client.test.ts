import { afterEach, describe, expect, it, vi } from "vitest";
import { submitDefinition } from "./admin-client";

const originalFetch = globalThis.fetch;

afterEach(() => {
	globalThis.fetch = originalFetch;
	vi.unstubAllEnvs();
});

describe("submitDefinition", () => {
	it("posts the unchanged source and OIDC token to the configured relay URL", async () => {
		vi.stubEnv("VITE_DWS_ADMIN_URL", "https://admin.example.test/api");
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					workflow: "order",
					versionId: "order@v12345678",
					version: "v12345678",
					created: true,
				}),
				{ status: 200 },
			),
		);
		globalThis.fetch = fetchMock;
		const definition = "document:\n  dsl: '1.0.0'\n";

		await expect(submitDefinition(definition, "oidc-token")).resolves.toEqual({
			kind: "applied",
			result: {
				workflow: "order",
				versionId: "order@v12345678",
				version: "v12345678",
				created: true,
			},
		});

		expect(fetchMock).toHaveBeenCalledWith(
			"https://admin.example.test/api/workflows?dryRun=false",
			{
				method: "POST",
				headers: {
					Accept: "application/json",
					Authorization: "Bearer oidc-token",
					"Content-Type": "application/yaml",
				},
				body: definition,
			},
		);
	});

	it("preserves an idempotent apply as a success", async () => {
		globalThis.fetch = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					workflow: "order",
					versionId: "order@v12345678",
					version: "v12345678",
					created: false,
				}),
				{ status: 200 },
			),
		);

		await expect(submitDefinition("document: {}", "oidc-token")).resolves.toMatchObject({
			kind: "applied",
			result: { created: false },
		});
	});

	it("returns raw controller errors from a 400 response", async () => {
		globalThis.fetch = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					message: "Definition is invalid",
					errors: ["task name is required", "unsupported DSL version"],
				}),
				{ status: 400 },
			),
		);

		await expect(submitDefinition("invalid", "oidc-token")).resolves.toEqual({
			kind: "validation-error",
			errors: ["task name is required", "unsupported DSL version"],
		});
	});

	it("rejects non-validation failures with the response status", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(new Response("unavailable", { status: 503 }));

		await expect(submitDefinition("document: {}", "oidc-token")).rejects.toMatchObject({
			status: 503,
		});
	});
});
