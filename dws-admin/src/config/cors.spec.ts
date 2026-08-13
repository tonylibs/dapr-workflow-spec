import { Controller, Get, Module } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import type { INestApplication } from '@nestjs/common';
import { ANY_ORIGIN, corsOptions, parseCorsOrigins } from './cors';

describe('parseCorsOrigins', () => {
  it('allows any origin when unset or empty', () => {
    expect(parseCorsOrigins(undefined)).toEqual([ANY_ORIGIN]);
    expect(parseCorsOrigins('')).toEqual([ANY_ORIGIN]);
    expect(parseCorsOrigins('  ,  ')).toEqual([ANY_ORIGIN]);
  });

  it('splits a comma-separated allow-list and trims each entry', () => {
    expect(parseCorsOrigins('http://a.example, https://b.example')).toEqual(['http://a.example', 'https://b.example']);
  });
});

describe('corsOptions', () => {
  it('exposes only read methods and never enables credentials', () => {
    const options = corsOptions(['https://console.example']);

    expect(options.methods).toEqual(['GET', 'HEAD', 'OPTIONS']);
    // `*` and credentials cannot be combined; keeping credentials off is what
    // makes the permissive default legitimate.
    expect(options.credentials).toBe(false);
  });

  it('passes an allow-list through and collapses a wildcard entry', () => {
    expect(corsOptions(['https://console.example']).origin).toEqual(['https://console.example']);
    expect(corsOptions([ANY_ORIGIN, 'https://console.example']).origin).toBe(ANY_ORIGIN);
  });
});

// Exercises the policy over real HTTP: the header a browser actually checks is
// on the response, so asserting the options object alone would not prove the
// console can call this API.
@Controller('probe')
class ProbeController {
  @Get()
  read(): { ok: true } {
    return { ok: true };
  }
}

@Module({ controllers: [ProbeController] })
class ProbeModule {}

describe('CORS over HTTP', () => {
  let app: INestApplication;
  let baseUrl: string;

  async function start(origins: string[]): Promise<void> {
    app = await NestFactory.create(ProbeModule, { logger: false });
    app.enableCors(corsOptions(origins));
    await app.listen(0);
    baseUrl = await app.getUrl();
  }

  afterEach(async () => {
    await app?.close();
  });

  it('echoes an allowed origin so the browser delivers the response', async () => {
    await start(['http://localhost:3000']);

    const response = await fetch(`${baseUrl}/probe`, { headers: { Origin: 'http://localhost:3000' } });

    expect(response.status).toBe(200);
    expect(response.headers.get('access-control-allow-origin')).toBe('http://localhost:3000');
  });

  it('omits the header for an origin outside the allow-list', async () => {
    await start(['http://localhost:3000']);

    const response = await fetch(`${baseUrl}/probe`, { headers: { Origin: 'http://evil.example' } });

    expect(response.headers.get('access-control-allow-origin')).toBeNull();
  });

  it('allows any origin by default', async () => {
    await start(parseCorsOrigins(undefined));

    const response = await fetch(`${baseUrl}/probe`, { headers: { Origin: 'http://localhost:3000' } });

    expect(response.headers.get('access-control-allow-origin')).toBe(ANY_ORIGIN);
  });

  it('answers the preflight a browser sends before the real request', async () => {
    await start(['http://localhost:3000']);

    const response = await fetch(`${baseUrl}/probe`, {
      method: 'OPTIONS',
      headers: {
        Origin: 'http://localhost:3000',
        'Access-Control-Request-Method': 'GET',
      },
    });

    expect(response.status).toBeLessThan(300);
    expect(response.headers.get('access-control-allow-origin')).toBe('http://localhost:3000');
    expect(response.headers.get('access-control-allow-methods')).toContain('GET');
  });
});
