// @vitest-environment jsdom
/**
 * Auth-state coverage for `admin-hooks.ts`'s TanStack Query gating and retry
 * policy (roadmap Phase 5, D6). `admin-client.test.ts` proves every request
 * carries the current bearer token; these tests prove the hook layer never
 * *issues* that request until the operator is actually signed in, and that a
 * token failure or a `401` response is treated as an authentication outcome
 * rather than something a transport retry could fix.
 *
 * Only `#/lib/oidc` is mocked — `admin-client.ts` and `admin-hooks.ts` run for
 * real, driven by a stubbed `fetch`, so "fetch issued with bearer" and "no
 * retry" are observed at the actual transport boundary rather than asserted
 * against a mock of the code under test.
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("#/lib/oidc", () => ({
	getAccessToken: vi.fn(),
	useOidc: vi.fn(),
}));

import { getAccessToken, useOidc } from "#/lib/oidc";
import { useWorkflows } from "./admin-hooks";

const mockedGetAccessToken = vi.mocked(getAccessToken);
const mockedUseOidc = vi.mocked(useOidc);
const originalFetch = globalThis.fetch;

/** Only `isUserLoggedIn` is read by `admin-hooks.ts`; the rest of the real shape is irrelevant here. */
function stubOidc(isUserLoggedIn: boolean | undefined) {
	mockedUseOidc.mockReturnValue({
		isUserLoggedIn,
	} as unknown as ReturnType<typeof useOidc>);
}

function createWrapper() {
	const queryClient = new QueryClient();
	return function Wrapper({ children }: { children: ReactNode }) {
		return (
			<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
		);
	};
}

const workflowsPage = {
	items: [
		{
			name: "order",
			latestVersion: "v1",
			status: "active",
			createdAt: "2024-01-01T00:00:00.000Z",
		},
	],
	nextCursor: null,
};

beforeEach(() => {
	mockedGetAccessToken.mockReset();
	mockedUseOidc.mockReset();
});

afterEach(() => {
	globalThis.fetch = originalFetch;
	vi.unstubAllEnvs();
});

describe("useWorkflows auth gating", () => {
	it("issues no request while signed out", async () => {
		stubOidc(false);
		const fetchMock = vi.fn();
		globalThis.fetch = fetchMock;

		const { result } = renderHook(() => useWorkflows(), {
			wrapper: createWrapper(),
		});

		// Give any (incorrect) fetch a tick to fire before asserting its absence.
		await new Promise((resolve) => setTimeout(resolve, 0));

		expect(fetchMock).not.toHaveBeenCalled();
		expect(result.current.fetchStatus).toBe("idle");
		expect(result.current.status).toBe("pending");
	});

	it("issues no request while auth is still initializing", async () => {
		// `oidc-spa` reports no `isUserLoggedIn` verdict yet as `undefined`.
		stubOidc(undefined);
		const fetchMock = vi.fn();
		globalThis.fetch = fetchMock;

		const { result } = renderHook(() => useWorkflows(), {
			wrapper: createWrapper(),
		});

		await new Promise((resolve) => setTimeout(resolve, 0));

		expect(fetchMock).not.toHaveBeenCalled();
		expect(result.current.fetchStatus).toBe("idle");
	});

	it("fetches with the current bearer token once signed in", async () => {
		stubOidc(true);
		mockedGetAccessToken.mockResolvedValue("fresh-token");
		const fetchMock = vi
			.fn()
			.mockResolvedValue(
				new Response(JSON.stringify(workflowsPage), { status: 200 }),
			);
		globalThis.fetch = fetchMock;

		const { result } = renderHook(() => useWorkflows(), {
			wrapper: createWrapper(),
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(fetchMock).toHaveBeenCalledTimes(1);
		const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
		const headers = new Headers(init.headers);
		expect(headers.get("Authorization")).toBe("Bearer fresh-token");
		expect(result.current.rows).toEqual([
			expect.objectContaining({ name: "order" }),
		]);
	});

	it("surfaces a token-renewal failure as an authentication outcome, without retrying", async () => {
		stubOidc(true);
		mockedGetAccessToken.mockRejectedValue(new Error("session expired"));
		const fetchMock = vi.fn();
		globalThis.fetch = fetchMock;

		const { result } = renderHook(() => useWorkflows(), {
			wrapper: createWrapper(),
		});

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toMatchObject({ name: "AuthenticationError" });
		// Token acquisition failed before any request was sent, and the retry
		// policy must not treat that as a retryable transport failure.
		expect(fetchMock).not.toHaveBeenCalled();
		expect(mockedGetAccessToken).toHaveBeenCalledTimes(1);
	});

	it("does not retry a 401 response", async () => {
		stubOidc(true);
		mockedGetAccessToken.mockResolvedValue("stale-token");
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(null, { status: 401 }));
		globalThis.fetch = fetchMock;

		const { result } = renderHook(() => useWorkflows(), {
			wrapper: createWrapper(),
		});

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toMatchObject({
			name: "ApiError",
			status: 401,
		});
		expect(fetchMock).toHaveBeenCalledTimes(1);
	});
});
