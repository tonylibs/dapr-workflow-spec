import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dereference } from '@readme/openapi-parser';

export const fixtureUrlObject = new URL('./fixtures/petstore.json', import.meta.url);
export const fixtureFsPath = fileURLToPath(fixtureUrlObject);

/** file:// URL string for DOCUMENT_URL. */
export function fixtureUrl(): string {
  return fixtureUrlObject.href;
}

/** Raw fixture bytes. */
export function fixtureRaw(): string {
  return readFileSync(fixtureFsPath, 'utf8');
}

/** SHA-256 of the fixture, for DOCUMENT_SHA256. */
export function fixtureSha(): string {
  return createHash('sha256').update(fixtureRaw(), 'utf8').digest('hex');
}

/** Dereferenced fixture API object. */
export async function loadApi(): Promise<object> {
  return dereference(JSON.parse(fixtureRaw()) as Parameters<typeof dereference>[0]);
}

/** Base env that points at the fixture with a valid hash. */
export function baseEnv(overrides: Record<string, string | undefined> = {}): Record<string, string | undefined> {
  return {
    DOCUMENT_URL: fixtureUrl(),
    DOCUMENT_SHA256: fixtureSha(),
    OPERATION_ID: 'getPetById',
    TASK: 'get-pet',
    ...overrides,
  };
}
