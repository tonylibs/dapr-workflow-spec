/**
 * Assembles the request-time engine at startup: fetch + verify + validate +
 * parse the AsyncAPI document, resolve the operation template, and compile the
 * payload validator. Everything the request path needs is cached here so nothing
 * is lazy per request. The outbound leg is a local Dapr output-binding call
 * (see binding.ts) executed with undici.
 */
import type { Config } from '../config/config.js';
import { fetchDocument, parseDocument, validateDocument, verifySha256 } from './document.js';
import { resolveOperation, type OperationTemplate } from './operation.js';
import { PayloadValidator } from './validator.js';

/** The immutable, request-ready engine. */
export interface Engine {
  readonly config: Config;
  readonly template: OperationTemplate;
  readonly validator: PayloadValidator;
}

/** Builds the engine from config. Throws on any failure so startup fails fast. */
export async function buildEngine(config: Config): Promise<Engine> {
  const raw = await fetchDocument(config.documentUrl);
  verifySha256(raw, config.documentSha256);
  await validateDocument(raw);

  const doc = parseDocument(raw);
  const template = resolveOperation(doc, config.operationId);
  const validator = new PayloadValidator(template.payloadSchema);

  return { config, template, validator };
}
