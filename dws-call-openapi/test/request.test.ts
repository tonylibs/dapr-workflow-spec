/**
 * Contract tests over swagger-client `buildRequest` output for our config
 * mapping: jq-evaluated PARAMETERS -> buildRequest `parameters` -> the produced
 * {url, method, headers, body}. One representative case per parameter category,
 * plus URL-encoding and templated-server cases. We assert the mapping, not
 * swagger-client's internal serialization.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildAuthMaterial } from '../src/auth.js';
import { buildEngine, type Engine } from '../src/openapi/engine.js';
import { loadConfig } from '../src/config/config.js';
import { applyAuth } from '../src/request.js';
import { prepareOutbound } from '../src/runner.js';
import { baseEnv, fixtureRawByName, httpFixtureEnv, templatedEnv } from './helpers.js';

function engineFor(env: Record<string, string | undefined>): Promise<Engine> {
  return buildEngine(loadConfig(env));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('buildRequest contract - parameter categories', () => {
  it('maps a jq-evaluated path parameter into the URL path', async () => {
    const engine = await engineFor(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    const req = await prepareOutbound(engine, { petId: 5 });
    expect(req.method).toBe('GET');
    expect(req.url).toBe('http://upstream.test/api/v3/pet/5');
    expect(req.body).toBeUndefined();
  });

  it('maps a jq-evaluated query parameter into the query string', async () => {
    const engine = await engineFor(
      baseEnv({ OPERATION_ID: 'findPetsByStatus', PARAMETERS: '{"status":".s"}' }),
    );
    const req = await prepareOutbound(engine, { s: 'pending' });
    expect(req.url).toBe('http://upstream.test/api/v3/pet/findByStatus?status=pending');
  });

  it('maps a jq-evaluated header parameter into request headers', async () => {
    const engine = await engineFor(templatedEnv({ PARAMETERS: '{"id":".id","X-Trace":".trace"}' }));
    const req = await prepareOutbound(engine, { id: 'abc', trace: 't-123' });
    expect(req.headers['X-Trace']).toBe('t-123');
    expect(req.url).toBe('https://api.test/api/v3/things/abc');
  });

  it('maps a jq-evaluated requestBody into the request body', async () => {
    const engine = await engineFor(baseEnv({ OPERATION_ID: 'addPet', PARAMETERS: '{"requestBody":".pet"}' }));
    const req = await prepareOutbound(engine, { pet: { name: 'Rex', status: 'available' } });
    expect(req.method).toBe('POST');
    expect(req.url).toBe('http://upstream.test/api/v3/pet');
    expect(req.body).toEqual({ name: 'Rex', status: 'available' });
    expect(req.headers['Content-Type']).toBe('application/json');
  });
});

describe('buildRequest contract - encoding and servers', () => {
  it('URL-encodes reserved characters in a path parameter', async () => {
    const engine = await engineFor(templatedEnv({ PARAMETERS: '{"id":".id"}' }));
    const req = await prepareOutbound(engine, { id: 'a b/c?d' });
    expect(req.url).toBe('https://api.test/api/v3/things/a%20b%2Fc%3Fd');
  });

  it('expands a templated server using its variable defaults', async () => {
    const engine = await engineFor(templatedEnv({ PARAMETERS: '{"id":".id"}' }));
    const req = await prepareOutbound(engine, { id: 'x' });
    expect(req.url.startsWith('https://api.test/api/v3/things/')).toBe(true);
  });
});

describe('buildRequest contract - auth layering', () => {
  it('replaces a lowercase prebuilt authorization header with generated basic auth', () => {
    const generated = buildAuthMaterial({ type: 'basic', username: 'alice', password: 'pw' }, undefined, undefined);
    const request = applyAuth(
      'https://upstream.test/orders',
      'GET',
      { authorization: 'static-credential', 'X-Trace': 'trace-1' },
      undefined,
      generated,
    );

    expect(request.headers.Authorization).toBe(`Basic ${Buffer.from('alice:pw').toString('base64')}`);
    expect(request.headers.authorization).toBeUndefined();
    expect(Object.keys(request.headers).filter((name) => name.toLowerCase() === 'authorization')).toHaveLength(1);
    expect(request.headers['X-Trace']).toBe('trace-1');
  });

  it('adds an apiKey query parameter from resolved auth material', async () => {
    const engine = await engineFor(
      baseEnv({ PARAMETERS: '{"petId":".petId"}', AUTH_TYPE: 'apiKey', AUTH_SECRET: 'k9' }),
    );
    const req = await prepareOutbound(engine, { petId: 5 });
    // fixture apiKey scheme is in header -> header, not query
    expect(req.headers.api_key).toBe('k9');
  });

  it('adds a bearer Authorization header from resolved auth material', async () => {
    const engine = await engineFor(
      baseEnv({ PARAMETERS: '{"petId":".petId"}', AUTH_TYPE: 'bearer', AUTH_SECRET: 'tok' }),
    );
    const req = await prepareOutbound(engine, { petId: 5 });
    expect(req.headers.Authorization).toBe('Bearer tok');
  });

  it('routes oauth through the local sidecar without a static Authorization header', async () => {
    const engine = await engineFor(
      baseEnv({
        OPERATION_ID: 'findPetsByStatus',
        PARAMETERS: '{"status":".s"}',
        AUTH_SCHEME: 'oauth2',
        OAUTH_ENDPOINT: 'accounts-oauth',
        DAPR_HTTP_PORT: '3600',
      }),
    );
    // Simulate a static Authorization value from a document or caller. OAuth
    // must let the Dapr middleware supply the external credential instead.
    const req = await prepareOutbound(
      { ...engine, auth: { ...engine.auth, headers: { authorization: 'must-not-reach-upstream' } } },
      { s: 'pending' },
    );
    expect(req.url).toBe('http://localhost:3600/v1.0/invoke/accounts-oauth/method/api/v3/pet/findByStatus?status=pending');
    expect(req.headers.Authorization).toBeUndefined();
    expect(req.headers.authorization).toBeUndefined();
  });

  it('resolves an HTTP-loaded relative server to an absolute direct request', async () => {
    const documentUrl = 'https://definitions.test/catalog/openapi.json';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(fixtureRawByName('relative-server.json'), { status: 200 }));
    const engine = await engineFor(
      httpFixtureEnv(
        'relative-server.json',
        documentUrl,
        { OPERATION_ID: 'listOrders', TASK: 'list-orders' },
        { PARAMETERS: '{"limit":".limit"}' },
      ),
    );

    const req = await prepareOutbound(engine, { limit: 1 });
    expect(req.url).toBe('https://definitions.test/inventory/v1/orders?limit=1');
  });

  it('resolves an HTTP-loaded relative server before OAuth sidecar routing', async () => {
    const documentUrl = 'https://definitions.test/catalog/openapi.json';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(fixtureRawByName('relative-server.json'), { status: 200 }));
    const engine = await engineFor(
      httpFixtureEnv(
        'relative-server.json',
        documentUrl,
        { OPERATION_ID: 'listOrders', TASK: 'list-orders' },
        {
          PARAMETERS: '{"limit":".limit"}',
          AUTH_SCHEME: 'oauth2',
          OAUTH_ENDPOINT: 'accounts-oauth',
          DAPR_HTTP_PORT: '3600',
        },
      ),
    );

    const req = await prepareOutbound(engine, { limit: 1 });
    expect(req.url).toBe('http://localhost:3600/v1.0/invoke/accounts-oauth/method/inventory/v1/orders?limit=1');
    expect(req.headers.Authorization).toBeUndefined();
  });
});
