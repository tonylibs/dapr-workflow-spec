import type { AddressInfo } from 'node:net';
import { ConfigService } from '@nestjs/config';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { Test } from '@nestjs/testing';
import type { AppConfig } from '../config/configuration';
import { configureAppTokenGuard } from '../http/app-token-guard';
import { configureBodyParsers } from '../http/body-parsers';
import { ControllerRelayController } from './controller-relay.controller';
import { ControllerRelayService, FETCHER, type Fetcher } from './controller-relay.service';

// Captures every arg the relay hands to fetch so the test can assert the
// wire shape without a real sidecar.
interface Call {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: Buffer;
}

function makeService(overrides: Partial<AppConfig['dapr']> = {}, apiToken?: string): { service: ControllerRelayService; calls: Call[] } {
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
    dapr: {
      pubsubName: 'pubsub',
      topic: 'dws.events',
      daprHost: '127.0.0.1',
      daprPort: '3500',
      controllerAppId: 'dws-controller',
      ...overrides,
    },
    apiToken,
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

  describe('Dapr API token', () => {
    const DAPR_API_TOKEN = 'sidecar-api-token-0123456789';

    it('attaches dapr-api-token to the sidecar call when configured, alongside Authorization', async () => {
      const { service, calls } = makeService({}, DAPR_API_TOKEN);

      await service.relayDeploy('Bearer abc.def.ghi', 'application/json', Buffer.from('{}'), false);

      expect(calls[0].headers['dapr-api-token']).toBe(DAPR_API_TOKEN);
      expect(calls[0].headers.authorization).toBe('Bearer abc.def.ghi');
    });

    it('attaches dapr-api-token even when the caller sent no Authorization', async () => {
      const { service, calls } = makeService({}, DAPR_API_TOKEN);

      await service.relayDeploy(undefined, undefined, Buffer.alloc(0), false);

      expect(calls[0].headers['dapr-api-token']).toBe(DAPR_API_TOKEN);
      expect(calls[0].headers.authorization).toBeUndefined();
    });

    it('omits dapr-api-token when no Dapr API token is configured', async () => {
      const { service, calls } = makeService();

      await service.relayDeploy('Bearer x', 'application/json', Buffer.from('{}'), false);

      expect(Object.keys(calls[0].headers)).not.toContain('dapr-api-token');
    });

    it.each(['', '   '])('omits dapr-api-token when it is configured as %j', async (blank) => {
      const { service, calls } = makeService({}, blank);

      await service.relayDeploy('Bearer x', 'application/json', Buffer.from('{}'), false);

      expect(Object.keys(calls[0].headers)).not.toContain('dapr-api-token');
    });
  });
});

// The inbound `dapr-api-token` is the *app* token — it authenticates our own
// sidecar to this app. It must never be forwarded to the sidecar or on to the
// controller; the outbound header is the separately configured Dapr API token.
// Driven over HTTP through the real guard so the inbound header is really there.
describe('POST /workflows relay over HTTP', () => {
  const APP_TOKEN = 'app-api-token-fedcba9876';
  const DAPR_API_TOKEN = 'sidecar-api-token-0123456789';
  let app: NestExpressApplication;
  let origin: string;
  let calls: Call[];

  async function boot(apiToken: string | undefined): Promise<void> {
    calls = [];
    const fetcher: Fetcher = async (url, init) => {
      calls.push({ url, method: init.method, headers: init.headers, body: init.body });
      return {
        status: 201,
        headers: { get: () => null },
        arrayBuffer: async () => new Uint8Array([]).buffer,
      };
    };
    const moduleRef = await Test.createTestingModule({
      controllers: [ControllerRelayController],
      providers: [
        ControllerRelayService,
        { provide: FETCHER, useValue: fetcher },
        {
          provide: ConfigService,
          useValue: new ConfigService<AppConfig, true>({
            dapr: { pubsubName: 'pubsub', topic: 'dws.events', controllerAppId: 'dws-controller' },
            apiToken,
            appToken: APP_TOKEN,
          }),
        },
      ],
    }).compile();
    app = moduleRef.createNestApplication<NestExpressApplication>({ rawBody: true, logger: false });
    configureAppTokenGuard(app, APP_TOKEN);
    configureBodyParsers(app);
    await app.listen(0);
    origin = `http://127.0.0.1:${(app.getHttpServer().address() as AddressInfo).port}`;
  }

  const post = () =>
    fetch(`${origin}/workflows`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: 'Bearer caller.jwt.value', 'dapr-api-token': APP_TOKEN },
      body: '{}',
    });

  afterEach(() => app?.close());

  it('sends the Dapr API token, not the inbound app token, and keeps Authorization', async () => {
    await boot(DAPR_API_TOKEN);

    expect((await post()).status).toBe(201);

    expect(calls).toHaveLength(1);
    expect(calls[0].headers['dapr-api-token']).toBe(DAPR_API_TOKEN);
    expect(calls[0].headers.authorization).toBe('Bearer caller.jwt.value');
    expect(JSON.stringify(calls[0].headers)).not.toContain(APP_TOKEN);
  });

  it('sends no dapr-api-token at all when none is configured, rather than forwarding the inbound one', async () => {
    await boot(undefined);

    expect((await post()).status).toBe(201);

    expect(Object.keys(calls[0].headers)).not.toContain('dapr-api-token');
    expect(JSON.stringify(calls[0].headers)).not.toContain(APP_TOKEN);
  });
});
