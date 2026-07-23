/**
 * Assembles the request-time engine at startup: fetch + verify + validate +
 * dereference the document, resolve the operation template, compile validators,
 * resolve auth, and capture the server for request building. Everything the
 * request path needs is cached here so nothing is lazy per request. Requests
 * are built with swagger-client's `buildRequest` (no hand-rolled serialization)
 * and executed with undici.
 */
import { dereference, validate, compileErrors } from '@readme/openapi-parser';
import type { Config } from '../config/config.js';
import { buildAuthMaterial, type AuthMaterial } from '../auth.js';
import { resolveSecret } from '../secrets.js';
import { fetchDocument, parseDocument, verifySha256 } from './document.js';
import { findApiKeyScheme, resolveOperation, type OperationTemplate } from './operation.js';
import { OperationValidator } from './validator.js';

/** The selected server: its (possibly templated) URL and variable defaults. */
export interface ServerConfig {
  readonly url: string | undefined;
  readonly variables: Readonly<Record<string, string>>;
}

/** The immutable, request-ready engine. */
export interface Engine {
  readonly config: Config;
  readonly spec: object;
  readonly template: OperationTemplate;
  readonly validator: OperationValidator;
  readonly server: ServerConfig;
  readonly auth: AuthMaterial;
}

/** Builds the engine from config. Throws on any failure so startup fails fast. */
export async function buildEngine(config: Config): Promise<Engine> {
  const raw = await fetchDocument(config.documentUrl);
  verifySha256(raw, config.documentSha256);
  const doc = parseDocument(raw);

  const result = await validate(structuredClone(doc) as Parameters<typeof validate>[0]);
  if (!result.valid) {
    throw new Error(`invalid OpenAPI document:\n${compileErrors(result)}`);
  }

  const spec = await dereference(structuredClone(doc) as Parameters<typeof dereference>[0]);
  const template = resolveOperation(spec, config.operationId);
  const validator = new OperationValidator(template);
  const apiKeyScheme = findApiKeyScheme(spec);
  const server = readServer(spec);

  const secret =
    config.auth.type === 'none' ? undefined : await resolveSecret(config.auth.secret, config.daprHttpPort);
  const auth = buildAuthMaterial(config.auth, secret, apiKeyScheme);

  return { config, spec, template, validator, server, auth };
}

/** Reads the first server's URL and its variable defaults for buildRequest. */
function readServer(spec: object): ServerConfig {
  const servers = (spec as { servers?: unknown }).servers;
  const first = Array.isArray(servers) ? (servers[0] as Record<string, unknown> | undefined) : undefined;
  const url = typeof first?.url === 'string' ? first.url : undefined;

  const variables: Record<string, string> = {};
  const rawVars = first?.variables;
  if (rawVars !== null && typeof rawVars === 'object') {
    for (const [name, def] of Object.entries(rawVars as Record<string, unknown>)) {
      const value = (def as { default?: unknown })?.default;
      if (typeof value === 'string') variables[name] = value;
    }
  }
  return { url, variables };
}
