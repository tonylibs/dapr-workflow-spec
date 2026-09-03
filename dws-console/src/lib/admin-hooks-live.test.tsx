// @vitest-environment jsdom
/**
 * Auth-state coverage for the live-update effects in `admin-hooks.ts`
 * (roadmap Phase 5, D6). These do not run through TanStack Query's `enabled`
 * option — they are `useEffect`s that open a bearer-authenticated SSE
 * subscription — so the sign-in gate is asserted directly against
 * `admin-client.ts`'s subscribe calls instead of query `fetchStatus`.
 * `admin-client.test.ts` and `admin-hooks.test.tsx` already cover the
 * transport and query-gating layers, so `subscribeToInstance` is stubbed here
 * to isolate the effect's own auth-state gating.
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("#/lib/oidc", () => ({
	getAccessToken: vi.fn(),
	useOidc: vi.fn(),
}));

vi.mock("./admin-client", async (importOriginal) => {
	const actual = await importOriginal<typeof import("./admin-client")>();
	return {
		...actual,
		subscribeToInstance: vi.fn(),
	};
});

import { useOidc } from "#/lib/oidc";
import { subscribeToInstance } from "./admin-client";
import { useInstanceLiveUpdates } from "./admin-hooks";

const mockedUseOidc = vi.mocked(useOidc);
const mockedSubscribeToInstance = vi.mocked(subscribeToInstance);

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

beforeEach(() => {
	mockedUseOidc.mockReset();
	mockedSubscribeToInstance.mockReset();
	mockedSubscribeToInstance.mockReturnValue({ close: vi.fn() });
});

afterEach(() => {
	vi.unstubAllEnvs();
});

describe("useInstanceLiveUpdates auth gating", () => {
	it("opens no subscription while signed out", () => {
		stubOidc(false);

		renderHook(() => useInstanceLiveUpdates("instance-1", true), {
			wrapper: createWrapper(),
		});

		expect(mockedSubscribeToInstance).not.toHaveBeenCalled();
	});

	it("opens no subscription while auth is still initializing", () => {
		stubOidc(undefined);

		renderHook(() => useInstanceLiveUpdates("instance-1", true), {
			wrapper: createWrapper(),
		});

		expect(mockedSubscribeToInstance).not.toHaveBeenCalled();
	});

	it("opens an authenticated subscription once signed in", () => {
		stubOidc(true);

		renderHook(() => useInstanceLiveUpdates("instance-1", true), {
			wrapper: createWrapper(),
		});

		expect(mockedSubscribeToInstance).toHaveBeenCalledTimes(1);
		expect(mockedSubscribeToInstance).toHaveBeenCalledWith(
			"instance-1",
			expect.objectContaining({
				onOpen: expect.any(Function),
				onInstance: expect.any(Function),
				onTask: expect.any(Function),
			}),
		);
	});

	it("closes an open subscription when sign-in is lost across a rerender", () => {
		stubOidc(true);
		const close = vi.fn();
		mockedSubscribeToInstance.mockReturnValue({ close });

		const { rerender } = renderHook(
			({ isSignedIn }: { isSignedIn: boolean }) => {
				stubOidc(isSignedIn);
				return useInstanceLiveUpdates("instance-1", true);
			},
			{ wrapper: createWrapper(), initialProps: { isSignedIn: true } },
		);

		expect(mockedSubscribeToInstance).toHaveBeenCalledTimes(1);

		rerender({ isSignedIn: false });

		expect(close).toHaveBeenCalledTimes(1);
	});
});
