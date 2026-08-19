import { describe, expect, it, vi } from "vitest";
import {
	type AuthState,
	DEFAULT_CLIENT_ID,
	DEFAULT_ISSUER_URI,
	type OidcSnapshot,
	resolveOidcConfig,
	toAuthState,
} from "./oidc-config";

describe("resolveOidcConfig", () => {
	it("reads issuer, client id and scopes from the environment", () => {
		expect(
			resolveOidcConfig({
				VITE_OIDC_ISSUER_URI: "https://dex.example.com/dex",
				VITE_OIDC_CLIENT_ID: "other-client",
				VITE_OIDC_SCOPES: "profile email groups",
			}),
		).toEqual({
			issuerUri: "https://dex.example.com/dex",
			clientId: "other-client",
			scopes: ["profile", "email", "groups"],
		});
	});

	it("falls back to working defaults when nothing is configured", () => {
		expect(resolveOidcConfig({})).toEqual({
			issuerUri: DEFAULT_ISSUER_URI,
			clientId: DEFAULT_CLIENT_ID,
			scopes: ["profile", "email"],
		});
	});

	it("does not request openid explicitly — the client adds it", () => {
		expect(resolveOidcConfig({}).scopes).not.toContain("openid");
	});

	it("ignores stray whitespace between scopes", () => {
		expect(
			resolveOidcConfig({ VITE_OIDC_SCOPES: "profile  email " }).scopes,
		).toEqual(["profile", "email"]);
	});
});

/** A signed-in snapshot; individual tests override the claims they care about. */
function loggedIn(
	claims: Partial<NonNullable<OidcSnapshot["decodedIdToken"]>> = {},
	logout: OidcSnapshot["logout"] = vi.fn(),
): OidcSnapshot {
	return {
		isOidcReady: true,
		isUserLoggedIn: true,
		decodedIdToken: { sub: "dws-bootstrap-admin", ...claims },
		logout,
	};
}

describe("toAuthState", () => {
	it("reports initializing before the client is ready", () => {
		expect(toAuthState({ isOidcReady: false })).toEqual({
			status: "initializing",
		});
	});

	it("reports unavailable when the client failed to initialize", () => {
		// An unreachable or misconfigured issuer must not look like "signed out",
		// because a sign-in button would only fail.
		expect(
			toAuthState({
				isOidcReady: false,
				oidcInitializationError: new Error("discovery failed"),
			}),
		).toEqual({ status: "unavailable" });
	});

	it("reports signed-out with a sign-in trigger", () => {
		const login = vi.fn();
		const state = toAuthState({
			isOidcReady: true,
			isUserLoggedIn: false,
			login,
		});

		expect(state.status).toBe("signed-out");
		assertStatus(state, "signed-out");
		state.signIn();
		expect(login).toHaveBeenCalledOnce();
	});

	it("prefers name, then preferred_username, then email, then sub as the label", () => {
		const label = (
			claims: Partial<NonNullable<OidcSnapshot["decodedIdToken"]>>,
		) => {
			const state = toAuthState(loggedIn(claims));
			assertStatus(state, "signed-in");
			return state.displayName;
		};

		expect(
			label({ name: "Ada", preferred_username: "ada", email: "a@b.c" }),
		).toBe("Ada");
		expect(label({ preferred_username: "ada", email: "a@b.c" })).toBe("ada");
		expect(label({ email: "a@b.c" })).toBe("a@b.c");
		expect(label({})).toBe("dws-bootstrap-admin");
	});

	it("signs out through the IdP rather than only clearing local state", () => {
		// The roadmap's ground rule: logout must be RP-initiated so the Dex session
		// ends too. `redirectTo` is what drives the end_session_endpoint redirect.
		const logout = vi.fn();
		const state = toAuthState(loggedIn({}, logout));

		assertStatus(state, "signed-in");
		state.signOut();

		expect(logout).toHaveBeenCalledWith({ redirectTo: "home" });
	});

	it("never exposes a token on the auth state", () => {
		// Guards the "access token in memory only" ground rule at the boundary this
		// code owns: the token stays inside the OIDC client and must not be copied
		// onto app-wide state where a component could persist it.
		for (const state of [
			toAuthState({ isOidcReady: false }),
			toAuthState({ isOidcReady: true, isUserLoggedIn: false, login: vi.fn() }),
			toAuthState(loggedIn({ email: "admin@dws.local" })),
		]) {
			const keys = Object.keys(state).join(" ").toLowerCase();
			expect(keys).not.toMatch(/token|credential|secret/);
		}
	});
});

/** Narrows `AuthState` in a test, failing loudly instead of silently skipping assertions. */
function assertStatus<S extends AuthState["status"]>(
	state: AuthState,
	status: S,
): asserts state is Extract<AuthState, { status: S }> {
	if (state.status !== status) {
		throw new Error(`Expected status ${status} but got ${state.status}`);
	}
}
