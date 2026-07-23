/**
 * Table-driven request-construction suite. Every case runs a real POST /run
 * against a mocked upstream (undici MockAgent, net connect disabled) and asserts
 * the exact final URL — plus method, headers, and body where they matter. One
 * fixture (`requests.json`) covers the path/query/header/body cases; the two
 * server-shape cases need their own `servers` entry and so have their own doc.
 */
import type { FastifyInstance } from 'fastify';
import { MockAgent, getGlobalDispatcher, setGlobalDispatcher, type Dispatcher } from 'undici';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { requestsEnv, templatedServerEnv, trailingSlashEnv } from './helpers.js';

interface Captured {
  readonly url: string;
  readonly method: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body: string | undefined;
}

interface Case {
  readonly name: string;
  readonly env: Record<string, string | undefined>;
  readonly input: Record<string, unknown>;
  readonly url: string;
  readonly method?: string;
  readonly headers?: Readonly<Record<string, string>>;
  readonly body?: unknown;
}

let app: FastifyInstance | undefined;
let mockAgent: MockAgent;
let originalDispatcher: Dispatcher;
let captured: Captured | undefined;

beforeEach(() => {
  captured = undefined;
  originalDispatcher = getGlobalDispatcher();
  mockAgent = new MockAgent();
  mockAgent.disableNetConnect();
  setGlobalDispatcher(mockAgent);
});

afterEach(async () => {
  if (app) {
    await app.close();
    app = undefined;
  }
  await mockAgent.close();
  setGlobalDispatcher(originalDispatcher);
});

/** Starts the step, mocks the origin, runs one request, and returns what went out. */
async function run(testCase: Case): Promise<Captured> {
  const status = await runExpectingStatus(testCase, 200);
  expect(status).toBe(200);
  if (captured === undefined) {
    throw new Error('no upstream request was captured');
  }
  return captured;
}

/** Runs one request without asserting success; returns the /run status code. */
async function runExpectingStatus(testCase: Case, upstreamStatus: number | undefined): Promise<number> {
  const instance = buildApp({ env: testCase.env, logger: false });
  await instance.ready();
  app = instance;

  if (upstreamStatus !== undefined) {
    mockAgent
      .get(new URL(testCase.url).origin)
      .intercept({ path: /.*/, method: /.*/ })
      .reply((opts) => {
        captured = {
          url: new URL(testCase.url).origin + String(opts.path),
          method: String(opts.method ?? 'GET'),
          headers: lowercaseHeaders(opts.headers),
          body: typeof opts.body === 'string' ? opts.body : undefined,
        };
        return { statusCode: upstreamStatus, data: { ok: true } };
      });
  }

  const res = await instance.inject({ method: 'POST', url: '/run', payload: testCase.input });
  return res.statusCode;
}

function lowercaseHeaders(raw: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (raw === null || typeof raw !== 'object') return out;
  for (const [name, value] of Object.entries(raw as Record<string, unknown>)) {
    out[name.toLowerCase()] = String(value);
  }
  return out;
}

/** Asserts every field a case declares against the captured request. */
function assertCase(testCase: Case, actual: Captured): void {
  expect(actual.url).toBe(testCase.url);
  if (testCase.method !== undefined) {
    expect(actual.method).toBe(testCase.method);
  }
  for (const [name, value] of Object.entries(testCase.headers ?? {})) {
    expect(actual.headers[name.toLowerCase()]).toBe(value);
  }
  if (testCase.body !== undefined) {
    expect(JSON.parse(actual.body ?? 'null')).toEqual(testCase.body);
  }
}

const pathCases: readonly Case[] = [
  {
    name: 'single path parameter',
    env: requestsEnv({ OPERATION_ID: 'getPet', PARAMETERS: '{"petId":".petId"}' }),
    input: { petId: 42 },
    url: 'https://api.x.com/v2/pets/42',
    method: 'GET',
  },
  {
    name: 'multiple path parameters across segments',
    env: requestsEnv({
      OPERATION_ID: 'getOrder',
      PARAMETERS: '{"storeId":".store","orderId":".order"}',
    }),
    input: { store: 'north', order: 7 },
    url: 'https://api.x.com/v2/stores/north/orders/7',
  },
  {
    name: 'adjacent path parameters in neighbouring segments',
    env: requestsEnv({ OPERATION_ID: 'getFile', PARAMETERS: '{"dir":".dir","file":".file"}' }),
    input: { dir: 'reports', file: 'q1.pdf' },
    url: 'https://api.x.com/v2/files/reports/q1.pdf',
  },
  {
    name: 'percent-encodes reserved characters and never treats "/" as a separator',
    env: requestsEnv({ OPERATION_ID: 'getName', PARAMETERS: '{"name":".name"}' }),
    input: { name: 'a b/c&d' },
    url: 'https://api.x.com/v2/names/a%20b%2Fc%26d',
  },
  {
    name: 'renders an integer parameter without quotes or a decimal point',
    env: requestsEnv({ OPERATION_ID: 'getPet', PARAMETERS: '{"petId":".petId"}' }),
    input: { petId: '42' },
    url: 'https://api.x.com/v2/pets/42',
  },
  {
    name: 'renders a boolean parameter as a bare true/false',
    env: requestsEnv({ OPERATION_ID: 'getFlag', PARAMETERS: '{"flag":".flag"}' }),
    input: { flag: true },
    url: 'https://api.x.com/v2/flags/true',
  },
  {
    name: 'ignores a PARAMETERS key that names no declared parameter',
    env: requestsEnv({ OPERATION_ID: 'getPet', PARAMETERS: '{"petId":".petId","bogus":".bogus"}' }),
    input: { petId: 42, bogus: 'dropped' },
    url: 'https://api.x.com/v2/pets/42',
  },
];

const queryCases: readonly Case[] = [
  {
    name: 'scalar query parameter',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"limit":".limit"}' }),
    input: { limit: 10 },
    url: 'https://api.x.com/v2/search?limit=10',
  },
  {
    name: 'array query parameter, style form + explode true',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"tag":".tags"}' }),
    input: { tags: ['a', 'b'] },
    url: 'https://api.x.com/v2/search?tag=a&tag=b',
  },
  {
    name: 'array query parameter, style form + explode false',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"tags":".tags"}' }),
    input: { tags: ['a', 'b'] },
    url: 'https://api.x.com/v2/search?tags=a,b',
  },
  {
    // deepObject keys are `filter[color]`; the brackets go out percent-encoded,
    // which is the equivalent, always-safe form of the same query string.
    name: 'object query parameter, style deepObject',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"filter":".filter"}' }),
    input: { filter: { color: 'red', size: 'xl' } },
    url: 'https://api.x.com/v2/search?filter%5Bcolor%5D=red&filter%5Bsize%5D=xl',
  },
  {
    name: 'omits a query parameter whose jq expression resolves to nothing',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"limit":".limit","strict":".absent"}' }),
    input: { limit: 10 },
    url: 'https://api.x.com/v2/search?limit=10',
  },
  {
    name: 'encodes reserved characters in a query parameter by default',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"strict":".value"}' }),
    input: { value: 'a/b:c' },
    url: 'https://api.x.com/v2/search?strict=a%2Fb%3Ac',
  },
  {
    name: 'leaves reserved characters intact when allowReserved is true',
    env: requestsEnv({ OPERATION_ID: 'searchItems', PARAMETERS: '{"q":".value"}' }),
    input: { value: 'a/b:c' },
    url: 'https://api.x.com/v2/search?q=a/b:c',
  },
];

const serverCases: readonly Case[] = [
  {
    name: 'prefixes the server base path',
    env: requestsEnv({ OPERATION_ID: 'getPet', PARAMETERS: '{"petId":".petId"}' }),
    input: { petId: 5 },
    url: 'https://api.x.com/v2/pets/5',
  },
  {
    name: 'does not double the slash when the server URL ends in one',
    env: trailingSlashEnv({ PARAMETERS: '{"petId":".petId"}' }),
    input: { petId: 5 },
    url: 'https://api.x.com/v2/pets/5',
  },
  {
    name: 'expands a templated server URL from SERVER_VARIABLES',
    env: templatedServerEnv({ PARAMETERS: '{"petId":".petId"}', SERVER_VARIABLES: '{"region":"eu"}' }),
    input: { petId: 5 },
    url: 'https://eu.api.x.com/v1/pets/5',
  },
];

const combinedCases: readonly Case[] = [
  {
    name: 'binds path, query, header, and body for one operation',
    env: requestsEnv({
      OPERATION_ID: 'createNote',
      PARAMETERS:
        '{"petId":".petId","tag":".tag","X-Request-Id":".requestId","requestBody":"{text: .text}"}',
    }),
    input: { petId: 42, tag: 'urgent', requestId: 'req-1', text: 'hello' },
    url: 'https://api.x.com/v2/pets/42/notes?tag=urgent',
    method: 'POST',
    headers: { 'X-Request-Id': 'req-1', 'content-type': 'application/json' },
    body: { text: 'hello' },
  },
  {
    name: 'takes the request content type from the document',
    env: requestsEnv({ OPERATION_ID: 'createDoc', PARAMETERS: '{"requestBody":"{title: .title}"}' }),
    input: { title: 'spec' },
    url: 'https://api.x.com/v2/docs',
    method: 'POST',
    headers: { 'content-type': 'application/vnd.acme+json' },
    body: { title: 'spec' },
  },
];

describe('path parameters', () => {
  it.each(pathCases)('$name', async (testCase) => {
    assertCase(testCase, await run(testCase));
  });

  it('rejects a missing required parameter with a 400 before any HTTP call', async () => {
    const testCase: Case = {
      name: 'missing required',
      env: requestsEnv({ OPERATION_ID: 'getPet', PARAMETERS: '{"petId":".petId"}' }),
      input: { somethingElse: 1 },
      url: 'https://api.x.com/v2/pets/42',
    };

    expect(await runExpectingStatus(testCase, undefined)).toBe(400);
    expect(captured).toBeUndefined();
  });
});

describe('query parameters', () => {
  it.each(queryCases)('$name', async (testCase) => {
    assertCase(testCase, await run(testCase));
  });
});

describe('server / base URL', () => {
  it.each(serverCases)('$name', async (testCase) => {
    assertCase(testCase, await run(testCase));
  });

  it('fails startup when a server variable is left unresolved', async () => {
    const instance = buildApp({ env: templatedServerEnv({ PARAMETERS: '{"petId":".petId"}' }), logger: false });
    app = instance;
    await expect(instance.ready()).rejects.toThrow(/unresolved variable/);
  });
});

describe('header and body binding', () => {
  it.each(combinedCases)('$name', async (testCase) => {
    assertCase(testCase, await run(testCase));
  });
});
