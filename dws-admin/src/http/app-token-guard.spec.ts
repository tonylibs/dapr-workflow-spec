import { readFileSync } from 'node:fs';
import type { AddressInfo } from 'node:net';
import { join } from 'node:path';
import { Controller, Get, Logger, type INestApplication, Post } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { Test } from '@nestjs/testing';
import type { AppConfig } from '../config/configuration';
import { DaprSubscriptionController } from '../events/dapr-subscription.controller';
import { DwsEventsSubscriber } from '../events/dws-events.subscriber';
import { configureAppTokenGuard } from './app-token-guard';
import { configureBodyParsers } from './body-parsers';

const APP_TOKEN = 'app-token-0123456789abcdef';

/** Stand-ins for the routes the real app serves; the guard is route-agnostic. */
@Controller('health')
class HealthStub {
  @Get()
  check() {
    return { status: 'ok' };
  }
}

@Controller('instances')
class InstancesStub {
  @Get()
  list() {
    return { items: [] };
  }

  @Post()
  create() {
    return { created: true };
  }
}

/**
 * Drives the real HTTP stack, not the middleware function in isolation: what
 * matters is that the check runs before routing and before body parsing, and
 * that Express's lenient route matching (`/health/`, `/Health`) cannot slip past
 * the exact-match exemption. Boots through the same `configureAppTokenGuard`
 * and `configureBodyParsers` that `bootstrap` calls.
 */
describe('app API token guard over HTTP', () => {
  const processEvent = jest.fn<Promise<void>, [unknown]>();
  let app: INestApplication;
  let origin: string;

  async function boot(token: string | undefined): Promise<void> {
    const moduleRef = await Test.createTestingModule({
      controllers: [HealthStub, InstancesStub, DaprSubscriptionController],
      providers: [
        { provide: DwsEventsSubscriber, useValue: { process: processEvent } },
        {
          provide: ConfigService,
          useValue: new ConfigService<AppConfig, true>({
            dapr: { pubsubName: 'pubsub', topic: 'dws.events', controllerAppId: 'dws-controller' },
          }),
        },
      ],
    }).compile();

    app = moduleRef.createNestApplication<NestExpressApplication>({ rawBody: true, logger: false });
    // Same order as `bootstrap`: guard first, then the body parsers.
    configureAppTokenGuard(app as NestExpressApplication, token);
    configureBodyParsers(app as NestExpressApplication);
    SwaggerModule.setup('docs', app, SwaggerModule.createDocument(app, new DocumentBuilder().setTitle('t').build()));
    await app.listen(0);
    const { port } = app.getHttpServer().address() as AddressInfo;
    origin = `http://127.0.0.1:${port}`;
  }

  const get = (path: string, headers: Record<string, string> = {}) => fetch(`${origin}${path}`, { headers });

  beforeEach(() => processEvent.mockReset().mockResolvedValue(undefined));
  afterEach(() => app?.close());

  describe('with a token configured', () => {
    beforeEach(() => boot(APP_TOKEN));

    it('rejects a request with no dapr-api-token header', async () => {
      const response = await get('/instances');

      expect(response.status).toBe(401);
    });

    it('rejects a request with a wrong token, identically to a missing one', async () => {
      const missing = await get('/instances');
      const wrong = await get('/instances', { 'dapr-api-token': 'wrong' });
      const wrongSameLength = await get('/instances', { 'dapr-api-token': 'x'.repeat(APP_TOKEN.length) });

      expect(wrong.status).toBe(401);
      expect(wrongSameLength.status).toBe(401);
      const missingBody = await missing.text();
      expect(await wrong.text()).toBe(missingBody);
      expect(await wrongSameLength.text()).toBe(missingBody);
      expect(wrong.headers.get('content-type')).toBe(missing.headers.get('content-type'));
    });

    it('does not echo either token in the rejection', async () => {
      const response = await get('/instances', { 'dapr-api-token': 'supplied-secret-value' });

      const text = JSON.stringify([await response.text(), [...response.headers.entries()]]);
      expect(text).not.toContain('supplied-secret-value');
      expect(text).not.toContain(APP_TOKEN);
    });

    it('does not log either token when rejecting', async () => {
      const spies = (['log', 'warn', 'error', 'debug', 'verbose'] as const).map((level) => jest.spyOn(Logger.prototype, level));
      const consoleSpies = (['log', 'warn', 'error'] as const).map((level) => jest.spyOn(globalThis.console, level).mockImplementation());

      await get('/instances', { 'dapr-api-token': 'supplied-secret-value' });

      const logged = JSON.stringify([...spies, ...consoleSpies].flatMap((spy) => spy.mock.calls));
      expect(logged).not.toContain('supplied-secret-value');
      expect(logged).not.toContain(APP_TOKEN);
      [...spies, ...consoleSpies].forEach((spy) => spy.mockRestore());
    });

    it('serves a request carrying the correct token', async () => {
      const response = await get('/instances', { 'dapr-api-token': APP_TOKEN });

      expect(response.status).toBe(200);
      expect(await response.json()).toEqual({ items: [] });
    });

    it('serves GET /health without a token', async () => {
      const response = await get('/health');

      expect(response.status).toBe(200);
      expect(await response.json()).toEqual({ status: 'ok' });
    });

    // Express routes `/health/` and `/Health` to the same handler as `/health`,
    // so a looser exemption would silently open them.
    it.each(['/health/', '/Health', '/HEALTH', '/health//', '/health/x', '/healthz'])(
      'still requires the token on %s',
      async (path) => {
        const response = await get(path);

        expect(response.status).toBe(401);
      },
    );

    it('only exempts the GET method on /health', async () => {
      const response = await fetch(`${origin}/health`, { method: 'POST' });

      expect(response.status).toBe(401);
    });

    it('requires the token on /docs', async () => {
      expect((await get('/docs')).status).toBe(401);
      expect((await get('/docs', { 'dapr-api-token': APP_TOKEN })).status).toBe(200);
    });

    it('requires the token on GET /dapr/subscribe, and serves it with the token', async () => {
      expect((await get('/dapr/subscribe')).status).toBe(401);

      const response = await get('/dapr/subscribe', { 'dapr-api-token': APP_TOKEN });
      expect(response.status).toBe(200);
      expect(await response.json()).toEqual([{ pubsubname: 'pubsub', topic: 'dws.events', routes: { default: '/dapr/events/dws' } }]);
    });

    describe('POST /dapr/events/dws', () => {
      const post = (headers: Record<string, string> = {}) =>
        fetch(`${origin}/dapr/events/dws`, {
          method: 'POST',
          headers: { 'content-type': 'application/cloudevents+json', ...headers },
          body: JSON.stringify({ data: { id: 'evt-1' } }),
        });

      it('never reaches the event subscriber without the token', async () => {
        expect((await post()).status).toBe(401);
        expect((await post({ 'dapr-api-token': 'wrong' })).status).toBe(401);

        expect(processEvent).not.toHaveBeenCalled();
      });

      it('delivers to the event subscriber with the token', async () => {
        const response = await post({ 'dapr-api-token': APP_TOKEN });

        expect(response.status).toBe(201);
        expect(processEvent).toHaveBeenCalledTimes(1);
        expect(processEvent).toHaveBeenCalledWith({ id: 'evt-1' });
      });
    });

    // The guard must run ahead of the body parsers, otherwise an unauthenticated
    // peer makes the process buffer and parse up to MAX_BODY_BYTES before being
    // told 401. An over-cap body is 413 and malformed JSON is 400 if (and only
    // if) the parser reads the body, so 401 proves it never did.
    describe('ordering against the body parsers', () => {
      const postJson = (body: string, headers: Record<string, string> = {}) =>
        fetch(`${origin}/dapr/events/dws`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body });

      it('answers 401, not 413, for an oversized unauthenticated body', async () => {
        const oversized = ' '.repeat(1024 * 1024 + 1);

        expect((await postJson(oversized)).status).toBe(401);
      });

      it('answers 401, not 400, for a malformed unauthenticated JSON body', async () => {
        expect((await postJson('{"broken":')).status).toBe(401);
      });

      it('still lets the parser run once authenticated', async () => {
        expect((await postJson('{"broken":', { 'dapr-api-token': APP_TOKEN })).status).toBe(400);
        expect((await postJson(' '.repeat(1024 * 1024 + 1), { 'dapr-api-token': APP_TOKEN })).status).toBe(413);
      });
    });
  });

  describe.each([undefined, '', '   '])('with the token configured as %j', (token) => {
    beforeEach(() => boot(token));

    it('applies no check, as before the feature existed', async () => {
      expect((await get('/instances')).status).toBe(200);
      expect((await get('/health/')).status).toBe(200);
      expect((await get('/docs')).status).toBe(200);
      expect((await get('/dapr/subscribe')).status).toBe(200);
    });

    it('lets an unauthenticated event delivery through', async () => {
      const response = await fetch(`${origin}/dapr/events/dws`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ data: { id: 'evt-1' } }),
      });

      expect(response.status).toBe(201);
      expect(processEvent).toHaveBeenCalledTimes(1);
    });
  });
});

// A source assertion, like the enableCors one in configuration.spec.ts:
// `bootstrap` itself cannot be booted without Postgres, and the ordering is the
// whole point of registering this as middleware.
describe('main.ts bootstrap', () => {
  const source = readFileSync(join(__dirname, '..', 'main.ts'), 'utf8');

  it('registers the app token guard before the body parsers', () => {
    const guard = source.indexOf('configureAppTokenGuard(');
    const parsers = source.indexOf('configureBodyParsers(app)');

    expect(guard).toBeGreaterThan(-1);
    expect(parsers).toBeGreaterThan(guard);
  });

  it('feeds the guard from the appToken config value', () => {
    expect(source).toMatch(/configureAppTokenGuard\(app,\s*config\.get\('appToken',\s*\{ infer: true \}\)\)/);
  });
});
