/// <reference types="vite/client" />

/**
 * The build-time environment variables the console reads.
 *
 * Vite's own `ImportMetaEnv` is an open index signature, which types every
 * `import.meta.env.X` as `any` and lets a typo through silently. Declaring the
 * console's variables here makes them checked and keeps this file the single
 * list of what a deployment can configure — mirror any change in `.env.example`.
 */
interface ImportMetaEnv {
	/** Base URL for the `dws-admin` read API. See `admin-client.ts`. */
	readonly VITE_DWS_ADMIN_URL?: string;

	/** OIDC issuer (Dex). Must match the chart's `dex.issuer`. See `oidc.ts`. */
	readonly VITE_OIDC_ISSUER_URI?: string;

	/** Public OIDC client id registered for the console in Dex. */
	readonly VITE_OIDC_CLIENT_ID?: string;

	/** Space-separated extra OIDC scopes; `openid` is added by the client. */
	readonly VITE_OIDC_SCOPES?: string;
}
