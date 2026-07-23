import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildAuthMaterial } from '../src/auth.js';
import { resolveSecret } from '../src/secrets.js';

describe('buildAuthMaterial', () => {
  it('produces no material for auth type none', () => {
    expect(buildAuthMaterial({ type: 'none' }, undefined, undefined)).toEqual({ headers: {}, query: {} });
  });

  it('builds a bearer Authorization header', () => {
    const m = buildAuthMaterial({ type: 'bearer', secret: { kind: 'inline', value: 'tok' } }, 'tok', undefined);
    expect(m.headers).toEqual({ Authorization: 'Bearer tok' });
  });

  it('base64-encodes basic credentials', () => {
    const m = buildAuthMaterial({ type: 'basic', secret: { kind: 'inline', value: 'u:p' } }, 'u:p', undefined);
    expect(m.headers.Authorization).toBe(`Basic ${Buffer.from('u:p').toString('base64')}`);
  });

  it('places an apiKey in the scheme-defined header', () => {
    const m = buildAuthMaterial(
      { type: 'apiKey', secret: { kind: 'inline', value: 'k' } },
      'k',
      { name: 'api_key', in: 'header' },
    );
    expect(m.headers).toEqual({ api_key: 'k' });
  });

  it('places an apiKey in the query when the scheme says so', () => {
    const m = buildAuthMaterial(
      { type: 'apiKey', secret: { kind: 'inline', value: 'k' } },
      'k',
      { name: 'token', in: 'query' },
    );
    expect(m.query).toEqual({ token: 'k' });
  });

  it('falls back to X-API-Key when no scheme is known', () => {
    const m = buildAuthMaterial({ type: 'apiKey', secret: { kind: 'inline', value: 'k' } }, 'k', undefined);
    expect(m.headers).toEqual({ 'X-API-Key': 'k' });
  });
});

describe('resolveSecret', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns inline secrets without a network call', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const value = await resolveSecret({ kind: 'inline', value: 's3cr3t' }, '3500');
    expect(value).toBe('s3cr3t');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('fetches a store secret from the Dapr sidecar', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ petkey: 'from-vault' }), { status: 200 }),
    );
    const value = await resolveSecret({ kind: 'store', store: 'vault', key: 'petkey' }, '3500');
    expect(value).toBe('from-vault');
    expect(fetchSpy).toHaveBeenCalledWith('http://localhost:3500/v1.0/secrets/vault/petkey');
  });

  it('throws when the Dapr secrets API returns non-2xx', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('nope', { status: 404 }));
    await expect(resolveSecret({ kind: 'store', store: 'vault', key: 'petkey' }, '3500')).rejects.toThrow(
      /HTTP 404/,
    );
  });
});
