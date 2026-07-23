/**
 * Resolves an auth secret. Inline secrets come straight from the env; store
 * secrets are fetched once at startup with a single HTTP GET to the Dapr
 * sidecar's secrets API. No Dapr SDK is used.
 */
import type { SecretRef } from './config/config.js';

/** Resolves a SecretRef to its plaintext value. */
export async function resolveSecret(ref: SecretRef, daprHttpPort: string): Promise<string> {
  if (ref.kind === 'inline') {
    return ref.value;
  }
  const url = `http://localhost:${daprHttpPort}/v1.0/secrets/${encodeURIComponent(ref.store)}/${encodeURIComponent(ref.key)}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`failed to fetch secret ${ref.store}/${ref.key} from Dapr: HTTP ${res.status}`);
  }
  const payload: unknown = await res.json();
  return extractSecret(payload, ref.key);
}

/**
 * The Dapr secrets API returns an object of key/value pairs. Prefer the value
 * keyed by the requested key; otherwise fall back to the single value present.
 */
function extractSecret(payload: unknown, key: string): string {
  if (payload === null || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('unexpected secret payload shape from Dapr');
  }
  const record = payload as Record<string, unknown>;
  const direct = record[key];
  if (typeof direct === 'string') return direct;
  const values = Object.values(record);
  if (values.length === 1 && typeof values[0] === 'string') {
    return values[0];
  }
  throw new Error(`secret ${key} not found in Dapr response`);
}
