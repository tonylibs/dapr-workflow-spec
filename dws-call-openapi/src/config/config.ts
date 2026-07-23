/**
 * Loads and validates step configuration from the environment. One generic
 * image serves every `call: openapi` step; all behavior is defined by the env
 * vars parsed here. Every invalid or missing required value produces a
 * descriptive error so the process can exit non-zero at startup (fail fast).
 */

export type OutputMode = 'replace' | 'merge';
export type AuthType = 'none' | 'bearer' | 'basic' | 'apiKey';

/** Where an auth secret comes from: an inline env value or the Dapr secret store. */
export type SecretRef =
  | { readonly kind: 'inline'; readonly value: string }
  | { readonly kind: 'store'; readonly store: string; readonly key: string };

export type AuthConfig =
  | { readonly type: 'none' }
  | { readonly type: 'bearer'; readonly secret: SecretRef }
  | { readonly type: 'basic'; readonly secret: SecretRef }
  | { readonly type: 'apiKey'; readonly secret: SecretRef };

/** Fully-resolved, validated step configuration. */
export interface Config {
  readonly port: number;
  readonly task: string;
  readonly documentUrl: string;
  readonly documentSha256: string;
  readonly operationId: string;
  /** Parameter name -> jq expression evaluated against the input data. */
  readonly parameters: Readonly<Record<string, string>>;
  readonly auth: AuthConfig;
  readonly output: OutputMode;
  readonly timeoutMs: number;
  readonly daprHttpPort: string;
}

const DEFAULT_PORT = 8080;
const DEFAULT_TIMEOUT_MS = 30_000;
const DEFAULT_DAPR_HTTP_PORT = '3500';
const SHA256_RE = /^[0-9a-f]{64}$/i;
const ALLOWED_SCHEMES = ['http:', 'https:', 'file:'] as const;

/** A configuration error that should stop startup. */
export class ConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ConfigError';
  }
}

type Env = Record<string, string | undefined>;

/** Reads and validates configuration from the given environment map. */
export function loadConfig(env: Env): Config {
  const documentUrl = required(env, 'DOCUMENT_URL');
  assertScheme(documentUrl);

  const documentSha256 = required(env, 'DOCUMENT_SHA256');
  if (!SHA256_RE.test(documentSha256)) {
    throw new ConfigError('DOCUMENT_SHA256 must be a 64-character hex string');
  }

  const operationId = required(env, 'OPERATION_ID');

  return {
    port: parsePort(env.PORT),
    task: nonEmpty(env.TASK) ?? operationId,
    documentUrl,
    documentSha256: documentSha256.toLowerCase(),
    operationId,
    parameters: parseParameters(env.PARAMETERS),
    auth: parseAuth(env),
    output: parseOutput(env.OUTPUT),
    timeoutMs: parseTimeout(env.TIMEOUT),
    daprHttpPort: nonEmpty(env.DAPR_HTTP_PORT) ?? DEFAULT_DAPR_HTTP_PORT,
  };
}

function required(env: Env, key: string): string {
  const value = nonEmpty(env[key]);
  if (value === undefined) {
    throw new ConfigError(`${key} is required`);
  }
  return value;
}

function nonEmpty(value: string | undefined): string | undefined {
  if (value === undefined) return undefined;
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

function assertScheme(url: string): void {
  let scheme: string;
  try {
    scheme = new URL(url).protocol;
  } catch {
    throw new ConfigError(`DOCUMENT_URL is not a valid URL: ${url}`);
  }
  if (!ALLOWED_SCHEMES.includes(scheme as (typeof ALLOWED_SCHEMES)[number])) {
    throw new ConfigError(`DOCUMENT_URL scheme must be http, https, or file, got ${scheme}`);
  }
}

function parsePort(raw: string | undefined): number {
  const value = nonEmpty(raw);
  if (value === undefined) return DEFAULT_PORT;
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65_535) {
    throw new ConfigError(`PORT must be an integer between 1 and 65535, got ${value}`);
  }
  return port;
}

function parseParameters(raw: string | undefined): Record<string, string> {
  const value = nonEmpty(raw);
  if (value === undefined) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch (err) {
    throw new ConfigError(`PARAMETERS must be valid JSON: ${(err as Error).message}`);
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new ConfigError('PARAMETERS must be a JSON object');
  }
  const out: Record<string, string> = {};
  for (const [key, expr] of Object.entries(parsed)) {
    if (typeof expr !== 'string') {
      throw new ConfigError(`PARAMETERS.${key} must be a string (a jq expression)`);
    }
    out[key] = expr;
  }
  return out;
}

function parseOutput(raw: string | undefined): OutputMode {
  const value = (nonEmpty(raw) ?? 'replace').toLowerCase();
  if (value !== 'replace' && value !== 'merge') {
    throw new ConfigError(`OUTPUT must be one of replace|merge, got ${value}`);
  }
  return value;
}

/** Accepts `30s`, `1m`, `500ms`, or a bare integer (milliseconds). */
function parseTimeout(raw: string | undefined): number {
  const value = nonEmpty(raw);
  if (value === undefined) return DEFAULT_TIMEOUT_MS;
  const match = /^(\d+)(ms|s|m)?$/.exec(value);
  if (!match) {
    throw new ConfigError(`TIMEOUT must be like 30s, 1m, 500ms, or a bare millisecond count, got ${value}`);
  }
  const amount = Number(match[1]);
  const unit = match[2] ?? 'ms';
  const factor = unit === 'm' ? 60_000 : unit === 's' ? 1_000 : 1;
  const ms = amount * factor;
  if (ms <= 0) {
    throw new ConfigError(`TIMEOUT must be positive, got ${value}`);
  }
  return ms;
}

function parseAuth(env: Env): AuthConfig {
  const type = (nonEmpty(env.AUTH_TYPE) ?? 'none').toLowerCase();
  if (type === 'none') return { type: 'none' };
  if (type !== 'bearer' && type !== 'basic' && type !== 'apiKey' && type !== 'apikey') {
    throw new ConfigError(`AUTH_TYPE must be one of none|bearer|basic|apiKey, got ${type}`);
  }
  const secret = parseSecretRef(env);
  const normalized: Exclude<AuthType, 'none'> = type === 'apikey' ? 'apiKey' : (type as 'bearer' | 'basic' | 'apiKey');
  return { type: normalized, secret };
}

function parseSecretRef(env: Env): SecretRef {
  const inline = nonEmpty(env.AUTH_SECRET);
  const store = nonEmpty(env.AUTH_SECRET_STORE);
  const key = nonEmpty(env.AUTH_SECRET_KEY);
  if (inline !== undefined) {
    return { kind: 'inline', value: inline };
  }
  if (store !== undefined && key !== undefined) {
    return { kind: 'store', store, key };
  }
  throw new ConfigError('auth requires AUTH_SECRET, or both AUTH_SECRET_STORE and AUTH_SECRET_KEY');
}
