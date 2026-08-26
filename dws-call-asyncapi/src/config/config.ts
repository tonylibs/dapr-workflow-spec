/**
 * Loads and validates step configuration from the environment. One generic
 * image serves every `call: asyncapi` step; all behavior is defined by the env
 * vars parsed here. Every invalid or missing required value produces a
 * descriptive error so the process can exit non-zero at startup (fail fast).
 */

export type OutputMode = 'replace' | 'merge';

/** Fully-resolved, validated step configuration. */
export interface Config {
  readonly port: number;
  readonly task: string;
  /** AsyncAPI document location (http|https|file), pinned by content hash. */
  readonly documentUrl: string;
  readonly documentSha256: string;
  /** AsyncAPI 3.0 operation key; its `action` must be `send`. */
  readonly operationId: string;
  /** Dapr output-binding component name the sidecar dispatches through. */
  readonly bindingName: string;
  /** Dapr binding operation verb (`create` is the publish verb for every supported binding). */
  readonly operation: string;
  /** jq expression evaluated against the input to build the message payload. */
  readonly payload: string;
  /** Static string metadata passed verbatim as the binding call's `metadata`. */
  readonly metadata: Readonly<Record<string, string>>;
  readonly output: OutputMode;
  readonly timeoutMs: number;
  readonly daprHttpPort: string;
}

const DEFAULT_PORT = 8080;
const DEFAULT_TIMEOUT_MS = 30_000;
const DEFAULT_DAPR_HTTP_PORT = '3500';
const DEFAULT_OPERATION = 'create';
const DEFAULT_PAYLOAD = '.';
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
  const documentUrl = required(env, 'DOC_ENDPOINT');
  assertScheme(documentUrl);

  const documentSha256 = required(env, 'DOC_SHA256');
  if (!SHA256_RE.test(documentSha256)) {
    throw new ConfigError('DOC_SHA256 must be a 64-character hex string');
  }

  const operationId = required(env, 'OPERATION_ID');
  const bindingName = required(env, 'BINDING_NAME');

  return {
    port: parsePort(env.PORT),
    task: nonEmpty(env.TASK) ?? operationId,
    documentUrl,
    documentSha256: documentSha256.toLowerCase(),
    operationId,
    bindingName,
    operation: nonEmpty(env.OPERATION) ?? DEFAULT_OPERATION,
    payload: nonEmpty(env.PAYLOAD) ?? DEFAULT_PAYLOAD,
    metadata: parseMetadata(env.METADATA),
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
    throw new ConfigError(`DOC_ENDPOINT is not a valid URL: ${url}`);
  }
  if (!ALLOWED_SCHEMES.includes(scheme as (typeof ALLOWED_SCHEMES)[number])) {
    throw new ConfigError(`DOC_ENDPOINT scheme must be http, https, or file, got ${scheme}`);
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

/** Parses the METADATA env var: a JSON object whose values must all be strings. */
function parseMetadata(raw: string | undefined): Record<string, string> {
  const value = nonEmpty(raw);
  if (value === undefined) return {};
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch (err) {
    throw new ConfigError(`METADATA must be valid JSON: ${(err as Error).message}`);
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new ConfigError('METADATA must be a JSON object');
  }
  const out: Record<string, string> = {};
  for (const [key, entry] of Object.entries(parsed)) {
    if (typeof entry !== 'string') {
      throw new ConfigError(`METADATA.${key} must be a string`);
    }
    out[key] = entry;
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
