import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Mocked at the OIDC boundary so admin-client tests never start a real login;
// this is also what proves every operation goes through the centralized
// `getAccessToken` rather than reading a token some other way.
vi.mock("#/lib/oidc", () => ({
	getAccessToken: vi.fn(),
}));

import { getAccessToken } from "#/lib/oidc";
import {
	ApiError,
	fetchWorkflows,
	getJson,
	previewDefinition,
	submitDefinition,
	subscribeToInstance,
	validateDefinitionSpec,
} from "./admin-client";

const originalFetch = globalThis.fetch;
const mockedGetAccessToken = vi.mocked(getAccessToken);

beforeEach(() => {
	mockedGetAccessToken.mockReset();
	mockedGetAccessToken.mockResolvedValue("test-token");
});

afterEach(() => {
	globalThis.fetch = originalFetch;
	vi.unstubAllEnvs();
	vi.useRealTimers();
});

describe("submitDefinition", () => {
	it("posts the unchanged source to the configured relay URL with the current bearer token", async () => {
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

		await expect(submitDefinition(definition)).resolves.toEqual({
			kind: "applied",
			result: {
				workflow: "order",
				versionId: "order@v12345678",
				version: "v12345678",
				created: true,
			},
		});

		expect(fetchMock).toHaveBeenCalledTimes(1);
		const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		expect(url).toBe("https://admin.example.test/api/workflows?dryRun=false");
		expect(init.method).toBe("POST");
		expect(init.body).toBe(definition);
		const headers = new Headers(init.headers);
		expect(headers.get("Authorization")).toBe("Bearer test-token");
		expect(headers.get("Accept")).toBe("application/json");
		expect(headers.get("Content-Type")).toBe("application/yaml");
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

		await expect(submitDefinition("document: {}")).resolves.toMatchObject({
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

		await expect(submitDefinition("invalid")).resolves.toEqual({
			kind: "validation-error",
			errors: ["task name is required", "unsupported DSL version"],
		});
	});

	it("rejects a 400 whose body is not a well-formed error list, quoting the payload", async () => {
		globalThis.fetch = vi.fn().mockResolvedValue(
			new Response(JSON.stringify({ message: "Definition is invalid" }), {
				status: 400,
			}),
		);

		await expect(submitDefinition("invalid")).rejects.toMatchObject({
			status: 400,
			message: expect.stringContaining("Definition is invalid"),
		});
	});

	it("rejects an apply result that does not match the controller's shape", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(
				new Response(JSON.stringify({ workflow: "order" }), { status: 200 }),
			);

		await expect(submitDefinition("document: {}")).rejects.toMatchObject({
			status: 200,
			message: expect.stringContaining("unexpected apply result"),
		});
	});

	it("rejects non-validation failures with the response status", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(new Response("unavailable", { status: 503 }));

		await expect(submitDefinition("document: {}")).rejects.toMatchObject({
			status: 503,
		});
	});
});

describe("centralized token acquisition", () => {
	function jsonResponse(body: unknown): Response {
		return new Response(JSON.stringify(body), {
			status: 200,
			headers: { "Content-Type": "application/json" },
		});
	}

	it("acquires the current access token for every JSON read", async () => {
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
		globalThis.fetch = fetchMock;

		await getJson("/workflows");

		expect(mockedGetAccessToken).toHaveBeenCalledTimes(1);
		const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		const headers = new Headers(init.headers);
		expect(headers.get("Authorization")).toBe("Bearer test-token");
	});

	it("sends two sequential reads with two distinct, freshly-acquired tokens", async () => {
		mockedGetAccessToken
			.mockReset()
			.mockResolvedValueOnce("token-1")
			.mockResolvedValueOnce("token-2");
		const fetchMock = vi
			.fn()
			.mockImplementation(() =>
				Promise.resolve(jsonResponse({ items: [], nextCursor: null })),
			);
		globalThis.fetch = fetchMock;

		await fetchWorkflows();
		await fetchWorkflows();

		expect(mockedGetAccessToken).toHaveBeenCalledTimes(2);
		const headersOf = (index: number) =>
			new Headers(
				(fetchMock.mock.calls[index] as [string, RequestInit])[1].headers,
			);
		expect(headersOf(0).get("Authorization")).toBe("Bearer token-1");
		expect(headersOf(1).get("Authorization")).toBe("Bearer token-2");
	});

	it("never places the access token in the request URL or query string", async () => {
		mockedGetAccessToken.mockResolvedValue("super-secret-token");
		const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
		globalThis.fetch = fetchMock;

		await getJson("/workflows", { limit: 20 });

		const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
		expect(url).not.toContain("super-secret-token");
	});
});

// ── Fetch-based SSE transport ────────────────────────────────────────────

/** Encodes a small named-event SSE fixture as a one-shot readable byte stream. */
function sseStream(frames: string): ReadableStream<Uint8Array> {
	const encoder = new TextEncoder();
	return new ReadableStream({
		start(controller) {
			controller.enqueue(encoder.encode(frames));
			controller.close();
		},
	});
}

const FIXTURE_FRAMES =
	'event: instance\ndata: {"instanceId":"i-1","status":"completed"}\n\n' +
	'event: task\ndata: {"id":"t-1","instanceId":"i-1"}\n\n';

function streamResponse(
	body: ReadableStream<Uint8Array> | null,
	status = 200,
): Response {
	return new Response(body, { status });
}

describe("subscribeToInstance (fetch/ReadableStream SSE)", () => {
	it("opens with Accept: text/event-stream and the current bearer token, and dispatches named events", async () => {
		mockedGetAccessToken.mockResolvedValueOnce("token-1");
		const fetchMock = vi
			.fn()
			.mockResolvedValue(streamResponse(sseStream(FIXTURE_FRAMES)));
		globalThis.fetch = fetchMock;

		const onInstance = vi.fn();
		const onTask = vi.fn();
		const subscription = subscribeToInstance("i-1", { onInstance, onTask });

		await vi.waitFor(() => {
			expect(onInstance).toHaveBeenCalledWith({
				instanceId: "i-1",
				status: "completed",
			});
			expect(onTask).toHaveBeenCalledWith({ id: "t-1", instanceId: "i-1" });
		});

		expect(fetchMock).toHaveBeenCalledTimes(1);
		const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		expect(url).toContain("/instances/i-1/events");
		const headers = new Headers(init.headers);
		expect(headers.get("Accept")).toBe("text/event-stream");
		expect(headers.get("Authorization")).toBe("Bearer token-1");

		subscription.close();
		expect((init.signal as AbortSignal).aborted).toBe(true);
	});

	it("calls onOpen before reading frames, reconnects with a renewed token, and resyncs", async () => {
		vi.useFakeTimers();
		mockedGetAccessToken
			.mockResolvedValueOnce("token-1")
			.mockResolvedValueOnce("token-2");

		// First connection ends the stream after one event (a disconnect), the
		// second stays open with the fixture frames.
		const fetchMock = vi
			.fn()
			.mockResolvedValueOnce(
				streamResponse(
					sseStream(
						'event: instance\ndata: {"instanceId":"i-1","status":"started"}\n\n',
					),
				),
			)
			.mockResolvedValueOnce(streamResponse(sseStream(FIXTURE_FRAMES)));
		globalThis.fetch = fetchMock;

		const onOpen = vi.fn();
		const onInstance = vi.fn();
		const onTask = vi.fn();
		const subscription = subscribeToInstance("i-1", {
			onOpen,
			onInstance,
			onTask,
		});

		await vi.waitFor(() => expect(onOpen).toHaveBeenCalledTimes(1));
		await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));

		// Let exactly the first scheduled reconnect fire, then close before the
		// second connection's own end-of-stream reconnect would be due — this
		// test is about one reconnect using a renewed token, not the backoff loop.
		await vi.advanceTimersByTimeAsync(1_000);
		await vi.waitFor(() => expect(onOpen).toHaveBeenCalledTimes(2));
		await vi.waitFor(() =>
			expect(onTask).toHaveBeenCalledWith({ id: "t-1", instanceId: "i-1" }),
		);
		subscription.close();

		expect(mockedGetAccessToken).toHaveBeenCalledTimes(2);
		expect(fetchMock).toHaveBeenCalledTimes(2);
		const secondHeaders = new Headers(
			(fetchMock.mock.calls[1] as [string, RequestInit])[1].headers,
		);
		expect(secondHeaders.get("Authorization")).toBe("Bearer token-2");
	});

	it("closes on a terminal instance event and never reconnects", async () => {
		vi.useFakeTimers();
		const fetchMock = vi
			.fn()
			.mockResolvedValue(
				streamResponse(
					sseStream(
						'event: instance\ndata: {"instanceId":"i-1","status":"completed"}\n\n',
					),
				),
			);
		globalThis.fetch = fetchMock;

		let subscription!: { close(): void };
		const onInstance = vi.fn((event: { status: string }) => {
			if (event.status === "completed") subscription.close();
		});
		subscription = subscribeToInstance("i-1", { onInstance, onTask: vi.fn() });

		await vi.waitFor(() => expect(onInstance).toHaveBeenCalledTimes(1));
		await vi.advanceTimersByTimeAsync(60_000);

		expect(fetchMock).toHaveBeenCalledTimes(1);
	});

	it("treats a 401 as terminal and does not retry anonymously", async () => {
		vi.useFakeTimers();
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(null, { status: 401 }));
		globalThis.fetch = fetchMock;

		const onError = vi.fn();
		const subscription = subscribeToInstance("i-1", {
			onError,
			onInstance: vi.fn(),
			onTask: vi.fn(),
		});

		await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
		await vi.advanceTimersByTimeAsync(60_000);

		expect(fetchMock).toHaveBeenCalledTimes(1);
		expect(onError).toHaveBeenCalled();

		subscription.close();
	});
});

describe("validateDefinitionSpec", () => {
	it("posts the raw buffer with the format's content type and the bearer token", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(
				new Response(JSON.stringify({ valid: true }), { status: 200 }),
			);
		globalThis.fetch = fetchMock;

		const report = await validateDefinitionSpec("document: {}", "yaml");

		expect(report).toEqual({ valid: true });
		const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		expect(url).toBe("/dws-admin/definitions/validate");
		expect(init.method).toBe("POST");
		expect(init.body).toBe("document: {}");
		expect(new Headers(init.headers).get("content-type")).toBe(
			"application/yaml",
		);
		expect(new Headers(init.headers).get("authorization")).toBe(
			"Bearer test-token",
		);
	});

	it("uses the JSON content type for a JSON buffer", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(
				new Response(JSON.stringify({ valid: true }), { status: 200 }),
			);
		globalThis.fetch = fetchMock;

		await validateDefinitionSpec("{}", "json");

		const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		expect(new Headers(init.headers).get("content-type")).toBe(
			"application/json",
		);
	});

	it("returns the reported errors verbatim", async () => {
		const body = {
			valid: false,
			truncated: false,
			errors: [
				{ path: "/do/0", message: "must have required property 'call'" },
			],
		};
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify(body), { status: 200 }));

		const report = await validateDefinitionSpec("x", "yaml");

		expect(report).toEqual(body);
	});

	it("throws ApiError on a non-2xx response", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(new Response("", { status: 413 }));
		await expect(validateDefinitionSpec("x", "yaml")).rejects.toBeInstanceOf(
			ApiError,
		);
	});
});

describe("previewDefinition", () => {
	const plan = {
		workflow: "order",
		versionId: "v12345678",
		version: "order@v12345678",
		definitionResource: "dws-def-order-v12345678",
		specText: "document: {}",
		steps: [
			{
				name: "fetch-order",
				kind: "CALL_HTTP",
				image: "ghcr.io/dws/call-http:1",
			},
		],
		bindings: [{ task: "notify", direction: "EMIT", topic: "orders" }],
		orchestrator: {
			name: "dws-orch-order-v12345678",
			image: "ghcr.io/dws/orchestrator:1",
			appId: "order",
			appPort: 8080,
			replicas: 1,
		},
		oauthEndpoints: [],
		bindingComponents: [],
	};

	it("requests a dry run and returns the parsed plan", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify(plan), { status: 200 }));
		globalThis.fetch = fetchMock;

		const outcome = await previewDefinition("document: {}");

		expect(fetchMock.mock.calls[0][0]).toBe("/dws-admin/workflows?dryRun=true");
		expect(outcome).toEqual({
			kind: "plan",
			plan: expect.objectContaining({ workflow: "order" }),
		});
	});

	it("returns the controller's flat errors on a 400", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(
				new Response(
					JSON.stringify({ message: "invalid", errors: ["task 'a': boom"] }),
					{ status: 400 },
				),
			);

		await expect(previewDefinition("x")).resolves.toEqual({
			kind: "deploy-error",
			errors: ["task 'a': boom"],
		});
	});

	it("throws ApiError when the plan shape is unexpected", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(
				new Response(JSON.stringify({ workflow: 1 }), { status: 200 }),
			);
		await expect(previewDefinition("x")).rejects.toBeInstanceOf(ApiError);
	});
});
