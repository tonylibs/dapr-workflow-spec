import type { AddressInfo } from 'node:net';
import type { INestApplication } from '@nestjs/common';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { Test } from '@nestjs/testing';
import { configureBodyParsers } from '../http/body-parsers';
import { DefinitionValidationModule } from './definition-validation.module';
import type { ValidationReport } from './validation-report';

const VALID_JSON = JSON.stringify({
  document: { dsl: '1.0.0', namespace: 'default', name: 'x', version: '1.0.0' },
  do: [{ a: { set: { k: 1 } } }],
});

/**
 * Drives the real HTTP stack, not the controller method, because the defect this
 * covers lives entirely in middleware ordering: called directly with a rawBody
 * Buffer the controller always behaved correctly, while over HTTP Nest's json
 * parser answered 400 for malformed `application/json` before the route ran.
 */
describe('POST /definitions/validate over HTTP', () => {
  let app: INestApplication;
  let origin: string;

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({
      imports: [DefinitionValidationModule],
    }).compile();

    // Same options and same parser wiring as `bootstrap` — the ordering under
    // test is production's, not a re-declaration of it.
    app = moduleRef.createNestApplication<NestExpressApplication>({ rawBody: true });
    configureBodyParsers(app as NestExpressApplication);
    await app.listen(0);
    const { port } = app.getHttpServer().address() as AddressInfo;
    origin = `http://127.0.0.1:${port}`;
  });

  afterAll(() => app?.close());

  const post = (body: string | Buffer, contentType?: string) =>
    fetch(`${origin}/definitions/validate`, {
      method: 'POST',
      headers: contentType ? { 'content-type': contentType } : {},
      body: body as unknown as BodyInit,
    });

  it('reports malformed JSON as an invalid document, not a bad request', async () => {
    const response = await post('{"document":', 'application/json');

    expect(response.status).toBe(200);
    const report = (await response.json()) as ValidationReport;
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors[0].line).toBeGreaterThan(0);
    expect(report.errors[0].column).toBeGreaterThan(0);
  });

  it('reports malformed YAML the same way', async () => {
    const response = await post('document:\n  name: [unclosed\n', 'application/yaml');

    expect(response.status).toBe(200);
    const report = (await response.json()) as ValidationReport;
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors[0].line).toBeGreaterThan(0);
    expect(report.errors[0].column).toBeGreaterThan(0);
  });

  it('accepts a well-formed JSON definition', async () => {
    const response = await post(VALID_JSON, 'application/json');

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ valid: true });
  });

  it('accepts a well-formed YAML definition', async () => {
    const yaml = "document:\n  dsl: '1.0.0'\n  namespace: default\n  name: x\n  version: '1.0.0'\ndo:\n  - a:\n      set:\n        k: 1\n";
    const response = await post(yaml, 'application/yaml');

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ valid: true });
  });

  it('still rejects an empty body with 400', async () => {
    expect((await post('', 'application/json')).status).toBe(400);
  });

  it('still rejects an unsupported content type with 400', async () => {
    expect((await post(VALID_JSON, 'text/html')).status).toBe(400);
  });

  it('still rejects a body over the cap with 413', async () => {
    const huge = Buffer.alloc(1024 * 1024 + 1, 0x20);
    expect((await post(huge, 'application/yaml')).status).toBe(413);
  });

  // The raw parser is mounted on this one path, so every other route must still
  // get Nest's parsed JSON body — the Dapr delivery endpoints depend on it. A
  // globally-mounted raw parser would consume the body instead, the json parser
  // would skip it, and this would 404 (routing) rather than 400 (parsing).
  it('leaves the global JSON parser in front of every other route', async () => {
    const response = await fetch(`${origin}/not-a-route`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{"broken":',
    });

    expect(response.status).toBe(400);
  });
});
