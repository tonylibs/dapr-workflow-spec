/**
 * Turns the resolved auth secret into request material: a set of headers and/or
 * query parameters injected on every outbound call. Computed once at startup.
 */
import type { AuthConfig } from './config/config.js';
import type { ApiKeyScheme } from './openapi/operation.js';

const DEFAULT_API_KEY_HEADER = 'X-API-Key';

/** Constant auth material applied to every request. */
export interface AuthMaterial {
  readonly headers: Readonly<Record<string, string>>;
  readonly query: Readonly<Record<string, string>>;
  /** OAuth is handled by Dapr's sidecar, never by a runner-managed token. */
  readonly oauth?: { readonly endpoint: string; readonly daprHttpPort: string };
}

const EMPTY: AuthMaterial = { headers: {}, query: {} };

/**
 * Builds auth material from the config, resolved secret, and (for apiKey) the
 * document's apiKey security scheme. `secret` is required for every type except
 * `none`; callers resolve it before calling this.
 */
export function buildAuthMaterial(
  auth: AuthConfig,
  secret: string | undefined,
  apiKeyScheme: ApiKeyScheme | undefined,
): AuthMaterial {
  switch (auth.type) {
    case 'none':
      return EMPTY;
    case 'bearer':
      return { headers: { Authorization: `Bearer ${'token' in auth ? auth.token : req(secret)}` }, query: {} };
    case 'basic':
      // Legacy secrets contain raw `user:password`; generated env vars keep the
      // username and password independently secret-backed.
      return {
        headers: {
          Authorization: `Basic ${base64('username' in auth ? `${auth.username}:${auth.password}` : req(secret))}`,
        },
        query: {},
      };
    case 'apiKey':
      return apiKeyMaterial(req(secret), apiKeyScheme);
    case 'oauth2':
      return { headers: {}, query: {}, oauth: { endpoint: auth.endpoint, daprHttpPort: auth.daprHttpPort } };
  }
}

function apiKeyMaterial(secret: string, scheme: ApiKeyScheme | undefined): AuthMaterial {
  const name = scheme?.name ?? DEFAULT_API_KEY_HEADER;
  const location = scheme?.in ?? 'header';
  if (location === 'query') {
    return { headers: {}, query: { [name]: secret } };
  }
  // header and cookie both ride as headers (cookie -> Cookie header).
  const headerName = location === 'cookie' ? 'Cookie' : name;
  const value = location === 'cookie' ? `${name}=${secret}` : secret;
  return { headers: { [headerName]: value }, query: {} };
}

function req(secret: string | undefined): string {
  if (secret === undefined) {
    throw new Error('auth secret is required but was not resolved');
  }
  return secret;
}

function base64(value: string): string {
  return Buffer.from(value, 'utf8').toString('base64');
}
