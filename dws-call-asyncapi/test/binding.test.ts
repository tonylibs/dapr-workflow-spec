import { describe, expect, it } from 'vitest';
import { buildBindingRequest, bindingUrl } from '../src/binding.js';
import { loadConfig } from '../src/config/config.js';
import { baseEnv } from './helpers.js';

describe('bindingUrl', () => {
  it('targets the local sidecar output-binding path', () => {
    expect(bindingUrl('3500', 'orders-binding')).toBe(
      'http://localhost:3500/v1.0/bindings/orders-binding',
    );
  });

  it('url-encodes the binding name', () => {
    expect(bindingUrl('3500', 'a b')).toBe('http://localhost:3500/v1.0/bindings/a%20b');
  });
});

describe('buildBindingRequest', () => {
  it('builds a POST with the {data, operation, metadata} body', () => {
    const cfg = loadConfig(baseEnv({ OPERATION: 'create', METADATA: '{"partitionKey":"k"}' }));
    const req = buildBindingRequest(cfg, { orderId: 'o1', amount: 5 });

    expect(req.method).toBe('POST');
    expect(req.url).toBe('http://localhost:3500/v1.0/bindings/orders-binding');
    expect(req.headers).toMatchObject({ 'content-type': 'application/json' });
    expect(req.body).toEqual({
      data: { orderId: 'o1', amount: 5 },
      operation: 'create',
      metadata: { partitionKey: 'k' },
    });
  });

  it('honors a custom DAPR_HTTP_PORT and OPERATION', () => {
    const cfg = loadConfig(baseEnv({ DAPR_HTTP_PORT: '3600', OPERATION: 'publish' }));
    const req = buildBindingRequest(cfg, { x: 1 });
    expect(req.url).toBe('http://localhost:3600/v1.0/bindings/orders-binding');
    expect((req.body as { operation: string }).operation).toBe('publish');
  });
});
