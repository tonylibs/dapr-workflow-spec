import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import configuration from './configuration';

// After consolidating dws-admin on one Nest listener (port 3000, design D1/D5),
// AppConfig.dapr must describe only the sidecar wiring a thin HTTP adapter needs
// (pubsub name/topic, sidecar host/port, controller app-id) — no second
// Dapr-application-server fields (`serverHost`, `appPort`) and no browser CORS
// configuration (`corsOrigins`), since the public path is same-origin through
// the Gateway and local dev uses the console's dev-server proxy instead.
describe('configuration', () => {
  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV, DATABASE_URL: 'postgres://ignored' };
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  it('does not expose a second Dapr-application-server port or host', () => {
    const config = configuration();

    expect(config.dapr).not.toHaveProperty('appPort');
    expect(config.dapr).not.toHaveProperty('serverHost');
  });

  it('does not expose browser CORS configuration', () => {
    const config = configuration();

    expect(config).not.toHaveProperty('corsOrigins');
  });

  it('still exposes the sidecar wiring a thin Dapr HTTP adapter needs', () => {
    process.env.DAPR_PUBSUB_NAME = 'pubsub';
    process.env.DAPR_PUBSUB_TOPIC = 'dws.events';
    process.env.DAPR_HOST = '127.0.0.1';
    process.env.DAPR_HTTP_PORT = '3500';
    process.env.DAPR_CONTROLLER_APP_ID = 'dws-controller';

    const config = configuration();

    expect(config.dapr).toEqual({
      pubsubName: 'pubsub',
      topic: 'dws.events',
      daprHost: '127.0.0.1',
      daprPort: '3500',
      controllerAppId: 'dws-controller',
    });
  });
});

// The chart sets these two names on the dws-admin Deployment (secretKeyRef from
// the token Secrets, auth.enabled only). A rename on either side is a silent
// runtime failure — the feature just turns off — so the exact names are pinned
// here and by the chart's api-tokens render test.
describe('configuration machine-auth tokens', () => {
  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV, DATABASE_URL: 'postgres://ignored' };
    delete process.env.DAPR_API_TOKEN;
    delete process.env.APP_API_TOKEN;
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  it('reads the Dapr API token from DAPR_API_TOKEN and the app token from APP_API_TOKEN', () => {
    process.env.DAPR_API_TOKEN = 'sidecar-token';
    process.env.APP_API_TOKEN = 'app-token';

    const config = configuration();

    expect(config.apiToken).toBe('sidecar-token');
    expect(config.appToken).toBe('app-token');
  });

  it('does not read the tokens from any other name', () => {
    process.env.DAPR_APP_TOKEN = 'wrong-name';
    process.env.API_TOKEN = 'wrong-name';

    const config = configuration();

    expect(config.apiToken).toBeUndefined();
    expect(config.appToken).toBeUndefined();
  });

  it('is off by default', () => {
    const config = configuration();

    expect(config.apiToken).toBeUndefined();
    expect(config.appToken).toBeUndefined();
  });

  it('treats blank values as unset', () => {
    process.env.DAPR_API_TOKEN = '';
    process.env.APP_API_TOKEN = '   ';

    const config = configuration();

    expect(config.apiToken).toBeUndefined();
    expect(config.appToken).toBeUndefined();
  });
});

// A source assertion rather than a request-level one: `enableCors` gone from
// bootstrap is the actual removal this step performs, and the fastest way to
// pin that down is to assert it is not present in the compiled source text.
describe('main.ts bootstrap', () => {
  it('does not call app.enableCors(...)', () => {
    const source = readFileSync(join(__dirname, '..', 'main.ts'), 'utf8');

    expect(source).not.toMatch(/enableCors/);
  });
});
