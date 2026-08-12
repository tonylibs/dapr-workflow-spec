import type { CorsOptions } from '@nestjs/common/interfaces/external/cors-options.interface';

// Browsers refuse a cross-origin fetch unless the response carries
// Access-Control-Allow-Origin, so dws-console cannot call this API from a page
// served by another origin until CORS is enabled here. Same-origin deployments
// (console and API behind one ingress) never reach this code — it exists for
// the split-origin case, including local development.

/** Allows any origin. The read API is unauthenticated, so this grants a browser nothing a direct request would not. */
export const ANY_ORIGIN = '*';

/**
 * Reads the allow-list from `CORS_ORIGINS` (comma-separated). Unset or empty
 * means any origin: the read surface exposes no per-caller data and carries no
 * credentials, so a narrower default would only break the console without
 * protecting anything. Narrow it explicitly once the API is authenticated —
 * an allow-list is what makes `credentials` safe to turn on later.
 */
export function parseCorsOrigins(raw: string | undefined): string[] {
  const origins = (raw ?? '')
    .split(',')
    .map((origin) => origin.trim())
    .filter((origin) => origin.length > 0);

  return origins.length > 0 ? origins : [ANY_ORIGIN];
}

/**
 * CORS policy for the read API.
 *
 * Deliberately read-only and credential-free: this listener serves only `GET`
 * endpoints (Dapr's pubsub callbacks arrive on a separate port), and
 * `credentials: false` keeps cookies out of cross-origin requests — which is
 * also what makes the `*` default legitimate, since the two cannot be combined.
 */
export function corsOptions(origins: string[]): CorsOptions {
  return {
    origin: origins.includes(ANY_ORIGIN) ? ANY_ORIGIN : origins,
    methods: ['GET', 'HEAD', 'OPTIONS'],
    credentials: false,
    // Lets a browser cache the preflight for a day instead of re-asking before
    // every navigation in the console.
    maxAge: 86_400,
  };
}
