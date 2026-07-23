import type { FastifyInstance } from 'fastify';
import nock from 'nock';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { baseEnv } from './helpers.js';

const UPSTREAM = 'http://upstream.test';

async function startApp(env: Record<string, string | undefined>): Promise<FastifyInstance> {
  const app = buildApp({ env, logger: false });
  await app.ready();
  return app;
}

describe('POST /run', () => {
  let app: FastifyInstance;

  afterEach(async () => {
    nock.cleanAll();
    if (app) await app.close();
  });

  it('executes the operation and returns the upstream body (replace)', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    nock(UPSTREAM).get('/api/v3/pet/5').reply(200, { id: 5, name: 'Rex' });

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ id: 5, name: 'Rex' });
  });

  it('shallow-merges the upstream body into the input (merge)', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}', OUTPUT: 'merge' }));
    nock(UPSTREAM).get('/api/v3/pet/5').reply(200, { name: 'Rex' });

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 5, keep: true } });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ petId: 5, keep: true, name: 'Rex' });
  });

  it('sends a jq-built request body for a POST operation', async () => {
    app = await startApp(baseEnv({ OPERATION_ID: 'addPet', TASK: 'add-pet', PARAMETERS: '{"requestBody":"."}' }));
    let received: unknown;
    nock(UPSTREAM)
      .post('/api/v3/pet', (body) => {
        received = body;
        return true;
      })
      .reply(200, { id: 10, name: 'Rex' });

    const res = await app.inject({ method: 'POST', url: '/run', payload: { name: 'Rex', status: 'available' } });

    expect(res.statusCode).toBe(200);
    expect(received).toEqual({ name: 'Rex', status: 'available' });
  });

  it('returns 400 with details on a schema violation', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 'abc' } });

    expect(res.statusCode).toBe(400);
    const body = res.json();
    expect(body.task).toBe('get-pet');
    expect(body.details.length).toBeGreaterThan(0);
    expect(nock.pendingMocks()).toEqual([]); // upstream was never called
  });

  it('maps an upstream non-2xx to a 502 error contract', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    nock(UPSTREAM).get('/api/v3/pet/5').reply(500, { error: 'boom' });

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(502);
    expect(res.json()).toEqual({ task: 'get-pet', status: 500, body: { error: 'boom' } });
  });

  it('maps a transport failure to a 502', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    nock(UPSTREAM).get('/api/v3/pet/5').replyWithError('connection refused');

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(502);
    expect(res.json().task).toBe('get-pet');
    expect(res.json().error).toContain('transport error');
  });

  it('treats an empty body as empty workflow data', async () => {
    app = await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));

    const res = await app.inject({
      method: 'POST',
      url: '/run',
      headers: { 'content-type': 'application/json' },
      payload: '',
    });

    // petId cannot be resolved from {} -> required violation -> 400
    expect(res.statusCode).toBe(400);
  });

  it('injects a bearer token on the upstream request', async () => {
    app = await startApp(
      baseEnv({ PARAMETERS: '{"petId":".petId"}', AUTH_TYPE: 'bearer', AUTH_SECRET: 'tok123' }),
    );
    nock(UPSTREAM, { reqheaders: { authorization: 'Bearer tok123' } })
      .get('/api/v3/pet/5')
      .reply(200, { id: 5, name: 'Rex' });

    const res = await app.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(200);
  });
});

describe('GET /healthz', () => {
  let app: FastifyInstance;
  beforeEach(async () => {
    app = await startApp(baseEnv());
  });
  afterEach(async () => {
    await app.close();
  });

  it('reports readiness after full initialization', async () => {
    const res = await app.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok', task: 'get-pet' });
  });
});
