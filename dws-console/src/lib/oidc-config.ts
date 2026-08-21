/**
 * Pure helpers behind the console's OIDC login (see `oidc.ts`).
 *
 * Deliberately free of imports with side effects: `oidc.ts` bootstraps the OIDC
 * client at module scope, so the logic worth testing lives here instead, where a
 * test can import it without starting a login.
 */

/** Fallback issuer, matching the chart's own `dex.issuer` default. */
export const DEFAULT_ISSUER_URI = "http://dex.dws.local/dex";

/** Fallback client id, matching Dex's `staticClients` entry for the console. */
export const DEFAULT_CLIENT_ID = "dws-console";

/** `openid` is added by the OIDC client itself, so it is not listed here. */
export const DEFAULT_SCOPES = "profile email";

export interface OidcConfig {
	issuerUri: string;
	clientId: string;
	scopes: string[];
}

/**
 * Resolves the OIDC client configuration from environment values.
 *
 * Every field falls back to a working default so a console built without OIDC
 * environment variables still starts; an unreachable issuer surfaces later as an
 * initialization error (sign-in unavailable) rather than a boot failure.
 */
export function resolveOidcConfig(env: {
	VITE_OIDC_ISSUER_URI?: string;
	VITE_OIDC_CLIENT_ID?: string;
	VITE_OIDC_SCOPES?: string;
}): OidcConfig {
	return {
		issuerUri: env.VITE_OIDC_ISSUER_URI || DEFAULT_ISSUER_URI,
		clientId: env.VITE_OIDC_CLIENT_ID || DEFAULT_CLIENT_ID,
		scopes: (env.VITE_OIDC_SCOPES || DEFAULT_SCOPES).split(" ").filter(Boolean),
	};
}

/**
 * The console's view of who is signed in.
 *
 * `unavailable` is deliberately distinct from `signed-out`: it means the IdP
 * could not be reached or configured (bad issuer, no DNS, discovery failure), so
 * offering a sign-in button would only fail. Reads stay unauthenticated either
 * way, so the rest of the console is unaffected.
 *
 * Note there is no token field in any variant, and none should be added: the
 * access token stays inside the OIDC client, in memory. Nothing consumes it yet
 * (roadmap Phase 5).
 */
export type AuthState =
	| { status: "initializing" }
	| { status: "unavailable" }
	| { status: "signed-out"; signIn: () => void }
	| {
			status: "signed-in";
			/** Best available human label for the operator; falls back to the subject claim. */
			displayName: string;
			email: string | undefined;
			subject: string;
			signOut: () => void;
	  };

/** The subset of the OIDC client's state `toAuthState` reads. */
export interface OidcSnapshot {
	isOidcReady: boolean;
	isUserLoggedIn?: boolean;
	oidcInitializationError?: unknown;
	decodedIdToken?: {
		sub: string;
		name?: string;
		preferred_username?: string;
		email?: string;
	};
	login?: () => Promise<never>;
	logout?: (params: { redirectTo: "home" }) => Promise<never>;
}

/**
 * Maps the OIDC client's state onto the console's `AuthState`.
 *
 * Signing out goes through the OIDC client's `logout`, which performs
 * RP-initiated logout against the IdP's `end_session_endpoint` — clearing local
 * state alone would leave the IdP session live and silently sign the operator
 * back in on the next visit.
 */
export function toAuthState(oidc: OidcSnapshot): AuthState {
	if (oidc.oidcInitializationError !== undefined) {
		return { status: "unavailable" };
	}

	if (!oidc.isOidcReady) {
		return { status: "initializing" };
	}

	if (!oidc.isUserLoggedIn) {
		const { login } = oidc;
		return { status: "signed-out", signIn: () => void login?.() };
	}

	const claims = oidc.decodedIdToken;
	const { logout } = oidc;

	// `isUserLoggedIn` implies claims are present; treat their absence as a
	// programming error rather than rendering a blank identity.
	if (claims === undefined) {
		throw new Error("Signed in but the ID token claims are missing");
	}

	return {
		status: "signed-in",
		displayName:
			claims.name ?? claims.preferred_username ?? claims.email ?? claims.sub,
		email: claims.email,
		subject: claims.sub,
		signOut: () => void logout?.({ redirectTo: "home" }),
	};
}
