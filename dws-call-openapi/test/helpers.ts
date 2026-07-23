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

/** Base env that points at the petstore fixture with a valid hash. */
export function baseEnv(overrides: Record<string, string | undefined> = {}): Record<string, string | undefined> {
  return {
    DOCUMENT_URL: fixtureUrl(),
    DOCUMENT_SHA256: fixtureSha(),
    OPERATION_ID: 'getPetById',
    TASK: 'get-pet',
    ...overrides,
  };
}

/** Env that points at any fixture document, with its hash computed from disk. */
export function fixtureEnv(
  fileName: string,
  defaults: Record<string, string | undefined>,
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  const url = new URL(`./fixtures/${fileName}`, import.meta.url);
  const raw = readFileSync(fileURLToPath(url), 'utf8');
  return {
    DOCUMENT_URL: url.href,
    DOCUMENT_SHA256: createHash('sha256').update(raw, 'utf8').digest('hex'),
    ...defaults,
    ...overrides,
  };
}

/** Env that points at the templated-server fixture with a valid hash. */
export function templatedEnv(
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  return fixtureEnv('templated.json', { OPERATION_ID: 'getThing', TASK: 'get-thing' }, overrides);
}

/** Env for the request-construction fixture (server `https://api.x.com/v2`). */
export function requestsEnv(
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  return fixtureEnv('requests.json', { OPERATION_ID: 'getPet', TASK: 'requests' }, overrides);
}

/** Env for the fixture whose server URL ends in a slash. */
export function trailingSlashEnv(
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  return fixtureEnv('server-trailing-slash.json', { OPERATION_ID: 'getPet', TASK: 'requests' }, overrides);
}

/** Env for the fixture whose server URL has a `{region}` variable and no default. */
export function templatedServerEnv(
  overrides: Record<string, string | undefined> = {},
): Record<string, string | undefined> {
  return fixtureEnv('server-templated.json', { OPERATION_ID: 'getPet', TASK: 'requests' }, overrides);
}
