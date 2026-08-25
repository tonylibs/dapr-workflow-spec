import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/** file:// URL string for a fixture document. */
export function fixtureUrl(fileName: string): string {
  return new URL(`./fixtures/${fileName}`, import.meta.url).href;
}

/** Raw fixture bytes. */
export function fixtureRaw(fileName: string): string {
  return readFileSync(fileURLToPath(new URL(`./fixtures/${fileName}`, import.meta.url)), 'utf8');
}

/** Parsed fixture document object. */
export function fixtureDoc(fileName: string): Record<string, unknown> {
  return JSON.parse(fixtureRaw(fileName)) as Record<string, unknown>;
}

/** SHA-256 of a fixture, for DOC_SHA256. */
export function fixtureSha(fileName: string): string {
  return createHash('sha256').update(fixtureRaw(fileName), 'utf8').digest('hex');
}

/** Base env that points at the kafka-orders fixture with a valid hash. */
export function baseEnv(overrides: Record<string, string | undefined> = {}): Record<string, string | undefined> {
  return {
    DOC_ENDPOINT: fixtureUrl('kafka-orders.json'),
    DOC_SHA256: fixtureSha('kafka-orders.json'),
    OPERATION_ID: 'publishOrder',
    BINDING_NAME: 'orders-binding',
    TASK: 'publish-order',
    ...overrides,
  };
}

/** Env for an arbitrary fixture document, with its hash computed from disk. */
export function fixtureEnv(
  fileName: string,
  defaults: Record<string, string | undefined>,
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  return {
    DOC_ENDPOINT: fixtureUrl(fileName),
    DOC_SHA256: fixtureSha(fileName),
    ...defaults,
    ...overrides,
  };
}
