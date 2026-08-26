import type { FastifyInstance } from 'fastify';
import { MockAgent, getGlobalDispatcher, setGlobalDispatcher, type Dispatcher } from 'undici';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { baseEnv } from './helpers.js';

const SIDECAR = 'http://localhost:3500';
const BINDING_PATH = '/v1.0/bindings/orders-binding';

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

describe('GET /healthz', () => {
  it('reports ready once initialized', async () => {
    await startApp(baseEnv());
    const res = await app!.inject({ method: 'GET', url: '/healthz' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ status: 'ok', task: 'publish-order' });
  });
});

describe('POST /run', () => {
  it('dispatches the whole input as the payload (default PAYLOAD) and replaces output', async () => {
    await startApp(baseEnv());
    let received: unknown;
    mockAgent
      .get(SIDECAR)
      .intercept({ path: BINDING_PATH, method: 'POST' })
      .reply((opts) => {
        received = JSON.parse(opts.body as string);
        return { statusCode: 200, data: { accepted: true } };
      });

    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      payload: { orderId: 'o1', amount: 5 },
    });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ accepted: true });
    expect(received).toEqual({
      data: { orderId: 'o1', amount: 5 },
      operation: 'create',
      metadata: {},
    });
  });

  it('merges the sidecar body into the input (merge)', async () => {
    await startApp(baseEnv({ OUTPUT: 'merge' }));
    mockAgent
      .get(SIDECAR)
      .intercept({ path: BINDING_PATH, method: 'POST' })
      .reply(200, { messageId: 'm1' });

    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      payload: { orderId: 'o1', amount: 5 },
    });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ orderId: 'o1', amount: 5, messageId: 'm1' });
  });

  it('applies a PAYLOAD jq expression to build the message', async () => {
    await startApp(baseEnv({ PAYLOAD: '.order' }));
    let received: { data: unknown } | undefined;
    mockAgent
      .get(SIDECAR)
      .intercept({ path: BINDING_PATH, method: 'POST' })
      .reply((opts) => {
        received = JSON.parse(opts.body as string);
        return { statusCode: 200, data: {} };
      });

    await app!.inject({
      method: 'POST',
      url: '/run',
      payload: { order: { orderId: 'o9', amount: 3 }, other: 'x' },
    });

    expect(received!.data).toEqual({ orderId: 'o9', amount: 3 });
  });

  it('returns 400 with a validation marker on a schema violation', async () => {
    await startApp(baseEnv());
    // No sidecar interception: a valid dispatch would fail netConnect, proving none happened.
    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      payload: { orderId: 'o1' }, // missing required `amount`
    });

    expect(res.statusCode).toBe(400);
    const body = res.json();
    expect(body.error).toMatch(/^validation failed:/);
    expect(body.details.length).toBeGreaterThan(0);
  });

  it('maps a non-2xx sidecar response to 502', async () => {
    await startApp(baseEnv());
    mockAgent
      .get(SIDECAR)
      .intercept({ path: BINDING_PATH, method: 'POST' })
      .reply(500, { error: 'broker down' });

    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      payload: { orderId: 'o1', amount: 5 },
    });

    expect(res.statusCode).toBe(502);
    expect(res.json()).toMatchObject({ task: 'publish-order', status: 500 });
  });

  it('treats an empty body as empty data', async () => {
    // Whole-input payload against the empty object violates the schema, so this
    // exercises the empty-body path without a live sidecar.
    await startApp(baseEnv());
    const res = await app!.inject({
      method: 'POST',
      url: '/run',
      headers: { 'content-type': 'application/json' },
      payload: '',
    });
    expect(res.statusCode).toBe(400);
  });
});
