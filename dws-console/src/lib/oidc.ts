/**
 * OIDC login against Dex (auth roadmap Phase 1, docs/roadmaps/dws-auth.md).
 *
 * The console is a public Authorization Code + PKCE client. `oidc-spa` keeps the
 * tokens in memory — never `localStorage`/`sessionStorage` — runs silent renew in
 * a hidden `prompt=none` iframe, and logs out through Dex's own RP-initiated
 * `end_session_endpoint`, which are the three ground rules the roadmap fixes.
 *
 * There is deliberately no redirect-URI setting: `oidc-spa` always uses the app's
 * own root URL as the OIDC redirect URI and restores the route the operator
 * started from afterwards. That is why there is no `/callback` route, and why the
 * chart must register the console's *root* URL as `dex.consoleRedirectURI`.
 *
 * Auth state is a module-level singleton (v10 has no provider component), so
 * `useAuth` works anywhere under the app without wiring a context. Importing this
 * module starts the OIDC client; the logic worth unit-testing lives in the
 * side-effect-free `oidc-config.ts` beside it.
 */

import { oidcSpa } from "oidc-spa/react-tanstack-start";
import { z } from "zod";
import { type AuthState, resolveOidcConfig, toAuthState } from "./oidc-config";

/**
 * The ID-token claims the console actually reads.
 *
 * Only `sub` is required — it is the one identifier every OIDC provider must
 * issue. The display claims are optional on purpose: which of them Dex emits
 * depends on its connector, and a missing `name` must degrade the label rather
 * than fail the login.
 */
const decodedIdTokenSchema = z.object({
	sub: z.string(),
	name: z.string().optional(),
	preferred_username: z.string().optional(),
	email: z.string().optional(),
});

export const { bootstrapOidc, useOidc, getOidc, enforceLogin } = oidcSpa
	.withExpectedDecodedIdTokenShape({ decodedIdTokenSchema })
	.createUtils();

bootstrapOidc({
	implementation: "real",
	...resolveOidcConfig(import.meta.env),
});

/**
 * Reads auth state anywhere in the app without touching OIDC internals.
 *
 * Prefer this over `useOidc` in components so the token/library surface stays in
 * this module — nothing else should need to know which OIDC client is in use.
 */
export function useAuth(): AuthState {
	return toAuthState(useOidc());
}

export type { AuthState };
