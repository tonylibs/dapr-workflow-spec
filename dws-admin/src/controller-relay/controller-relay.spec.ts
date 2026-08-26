import { ConfigService } from '@nestjs/config';
import type { AppConfig } from '../config/configuration';
import { ControllerRelayService, type Fetcher } from './controller-relay.service';

// Captures every arg the relay hands to fetch so the test can assert the
// wire shape without a real sidecar.
interface Call {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: Buffer;
}

function makeService(overrides: Partial<AppConfig['dapr']> = {}): { service: ControllerRelayService; calls: Call[] } {
  const calls: Call[] = [];
  const fetcher: Fetcher = async (url, init) => {
    calls.push({ url, method: init.method, headers: init.headers, body: init.body });
    return {
      status: 201,
      headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null) },
      arrayBuffer: async () => new Uint8Array([]).buffer,
    };
  };
  const config = new ConfigService<AppConfig, true>({
    port: 3000,
    databaseUrl: 'postgres://ignored',
    runMigrationsOnBoot: false,
    corsOrigins: [],
    dapr: {
      pubsubName: 'pubsub',
      topic: 'dws.events',
      appPort: '3001',
      daprHost: '127.0.0.1',
      daprPort: '3500',
      controllerAppId: 'dws-controller',
      ...overrides,
    },
  });
  return { service: new ControllerRelayService(config, fetcher), calls };
}

describe('ControllerRelayService', () => {
  it('forwards the Authorization header verbatim', async () => {
    const { service, calls } = makeService();
    const token = 'Bearer eyJhbGciOi.some-signed.jwt-value';

    await service.relayDeploy(token, 'application/json', Buffer.from('{}'), false);

    expect(calls).toHaveLength(1);
    // The bearer token must reach the controller's sidecar unchanged — that
    // sidecar is the sole verifier (Phase 2 middleware.http.bearer).
    expect(calls[0].headers.authorization).toBe(token);
  });

  it('does not modify the request body', async () => {
    const { service, calls } = makeService();
    // Deliberately not valid JSON and includes YAML-only syntax + a trailing
    // newline; a JSON parser + re-serialiser would mutate every one of these.
    const raw = Buffer.from('document:\n  dsl: 1.0.0\n  name: order-flow\ndo:\n  - a: {call: http}\n');

    await service.relayDeploy('Bearer x', 'application/yaml', raw, false);

    expect(calls[0].body).toBeDefined();
    expect(calls[0].body!.equals(raw)).toBe(true);
    expect(calls[0].headers['content-type']).toBe('application/yaml');
  });

  it('invokes the controller via the local sidecar, not directly', async () => {
    const { service, calls } = makeService();

    await service.relayDeploy(undefined, undefined, Buffer.alloc(0), false);

    // v1.0/invoke/<app-id>/method/... is Dapr's service-invocation shape;
    // hitting the controller's own port would bypass its bearer middleware.
    expect(calls[0].url).toBe('http://127.0.0.1:3500/v1.0/invoke/dws-controller/method/workflows');
    expect(calls[0].method).toBe('POST');
  });

  it('omits the Authorization header when the caller did not send one', async () => {
    const { service, calls } = makeService();

    await service.relayDeploy(undefined, 'application/json', Buffer.from('{}'), false);

    // Rather than substituting our own — this route stays dumb. The sidecar
    // will 401 the missing header, which is the intended behavior.
    expect(calls[0].headers.authorization).toBeUndefined();
  });

  it('passes dryRun through as a query parameter', async () => {
    const { service, calls } = makeService();

    await service.relayDeploy('Bearer x', 'application/json', Buffer.from('{}'), true);

    expect(calls[0].url.endsWith('/method/workflows?dryRun=true')).toBe(true);
  });
});
