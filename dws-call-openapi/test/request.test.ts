/**
 * Contract tests over swagger-client `buildRequest` output for our config
 * mapping: jq-evaluated PARAMETERS -> buildRequest `parameters` -> the produced
 * {url, method, headers, body}. One representative case per parameter category,
 * plus URL-encoding and templated-server cases. We assert the mapping, not
 * swagger-client's internal serialization.
 */
import { describe, expect, it } from 'vitest';
import { buildEngine, type Engine } from '../src/openapi/engine.js';
import { loadConfig } from '../src/config/config.js';
import { prepareOutbound } from '../src/runner.js';
import { baseEnv, templatedEnv } from './helpers.js';

function engineFor(env: Record<string, string | undefined>): Promise<Engine> {
  return buildEngine(loadConfig(env));
}

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
});
