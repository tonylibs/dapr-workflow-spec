import type { FastifyInstance } from 'fastify';
import { MockAgent, getGlobalDispatcher, setGlobalDispatcher, type Dispatcher } from 'undici';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { baseEnv } from './helpers.js';

const UPSTREAM = 'http://upstream.test';

let app: FastifyInstance | undefined;
let mockAgent: MockAgent;
let originalDispatcher: Dispatcher;

async function startApp(env: Record<string, string | undefined>): Promise<FastifyInstance> {
  const instance = buildApp({ env, logger: false });
  await instance.ready();
  app = instance;
  return instance;
}

beforeEach(() => {
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

describe('POST /run', () => {
  it('executes the operation and returns the upstream body (replace)', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    mockAgent.get(UPSTREAM).intercept({ path: '/api/v3/pet/5', method: 'GET' }).reply(200, { id: 5, name: 'Rex' });

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ id: 5, name: 'Rex' });
  });

  it('shallow-merges the upstream body into the input (merge)', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}', OUTPUT: 'merge' }));
    mockAgent.get(UPSTREAM).intercept({ path: '/api/v3/pet/5', method: 'GET' }).reply(200, { name: 'Rex' });

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 5, keep: true } });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ petId: 5, keep: true, name: 'Rex' });
  });

  it('sends a jq-built request body for a POST operation', async () => {
    await startApp(baseEnv({ OPERATION_ID: 'addPet', TASK: 'add-pet', PARAMETERS: '{"requestBody":"."}' }));
    let received: string | undefined;
    mockAgent
      .get(UPSTREAM)
      .intercept({ path: '/api/v3/pet', method: 'POST' })
      .reply((opts) => {
        received = opts.body as string;
        return { statusCode: 200, data: { id: 10, name: 'Rex' } };
      });

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { name: 'Rex', status: 'available' } });

    expect(res.statusCode).toBe(200);
    expect(JSON.parse(received ?? '{}')).toEqual({ name: 'Rex', status: 'available' });
  });

  it('returns 400 with details on a schema violation', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 'abc' } });

    expect(res.statusCode).toBe(400);
    const body = res.json();
    expect(body.task).toBe('get-pet');
    expect(body.details.length).toBeGreaterThan(0);
  });

  it('maps an upstream non-2xx to a 502 error contract', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    mockAgent.get(UPSTREAM).intercept({ path: '/api/v3/pet/5', method: 'GET' }).reply(500, { error: 'boom' });

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(502);
    expect(res.json()).toEqual({ task: 'get-pet', status: 500, body: { error: 'boom' } });
  });

  it('maps a transport failure to a 502', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    mockAgent
      .get(UPSTREAM)
      .intercept({ path: '/api/v3/pet/5', method: 'GET' })
      .replyWithError(new Error('connection refused'));

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(502);
    expect(res.json().task).toBe('get-pet');
    expect(res.json().error).toContain('transport error');
  });

  it('treats an empty body as empty workflow data', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));

    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      headers: { 'content-type': 'application/json' },
      payload: '',
    });

    // petId cannot be resolved from {} -> required violation -> 400
    expect(res.statusCode).toBe(400);
  });

  it('injects a bearer token on the upstream request', async () => {
    await startApp(baseEnv({ PARAMETERS: '{"petId":".petId"}', AUTH_TYPE: 'bearer', AUTH_SECRET: 'tok123' }));
    mockAgent
      .get(UPSTREAM)
      .intercept({ path: '/api/v3/pet/5', method: 'GET', headers: { authorization: 'Bearer tok123' } })
      .reply(200, { id: 5, name: 'Rex' });

    const res = await app!.inject({ method: 'POST', url: '/run', payload: { petId: 5 } });

    expect(res.statusCode).toBe(200);
  });
});

describe('GET /healthz', () => {
  it('reports readiness after full initialization', async () => {
    await startApp(baseEnv());
    const res = await app!.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok', task: 'get-pet' });
  });
});
