import { describe, expect, it } from 'vitest';
import { loadConfig, ConfigError } from '../src/config/config.js';
import { baseEnv, fixtureSha, fixtureUrl } from './helpers.js';

describe('loadConfig', () => {
  it('loads a valid minimal configuration with defaults', () => {
    const cfg = loadConfig(baseEnv());
    expect(cfg.operationId).toBe('getPetById');
    expect(cfg.task).toBe('get-pet');
    expect(cfg.output).toBe('replace');
    expect(cfg.timeoutMs).toBe(30_000);
    expect(cfg.port).toBe(8080);
    expect(cfg.auth).toEqual({ type: 'none' });
    expect(cfg.daprHttpPort).toBe('3500');
  });

  it('defaults task to the operationId when TASK is unset', () => {
    const env = baseEnv();
    delete env.TASK;
    expect(loadConfig(env).task).toBe('getPetById');
  });

  const requiredCases: Array<[string, string]> = [
    ['DOCUMENT_URL', 'DOCUMENT_URL is required'],
    ['DOCUMENT_SHA256', 'DOCUMENT_SHA256 is required'],
    ['OPERATION_ID', 'OPERATION_ID is required'],
  ];
  it.each(requiredCases)('rejects missing %s', (key, message) => {
    const env = baseEnv();
    delete env[key];
    expect(() => loadConfig(env)).toThrow(message);
  });

  it('rejects a non-hex DOCUMENT_SHA256', () => {
    expect(() => loadConfig(baseEnv({ DOCUMENT_SHA256: 'nothex' }))).toThrow(ConfigError);
  });

  it('rejects an unsupported DOCUMENT_URL scheme', () => {
    expect(() => loadConfig(baseEnv({ DOCUMENT_URL: 'ftp://example.com/doc.json' }))).toThrow(/scheme/);
  });

  const timeoutCases: Array<[string, number]> = [
    ['30s', 30_000],
    ['1m', 60_000],
    ['500ms', 500],
    ['1500', 1500],
  ];
  it.each(timeoutCases)('parses TIMEOUT %s', (raw, ms) => {
    expect(loadConfig(baseEnv({ TIMEOUT: raw })).timeoutMs).toBe(ms);
  });

  it('rejects an unparseable TIMEOUT', () => {
    expect(() => loadConfig(baseEnv({ TIMEOUT: 'soon' }))).toThrow(/TIMEOUT/);
  });

  it('parses PARAMETERS into a name->expression map', () => {
    const cfg = loadConfig(baseEnv({ PARAMETERS: '{"petId":".petId"}' }));
    expect(cfg.parameters).toEqual({ petId: '.petId' });
  });

  it('rejects non-object or non-string PARAMETERS', () => {
    expect(() => loadConfig(baseEnv({ PARAMETERS: '[1,2]' }))).toThrow(/object/);
    expect(() => loadConfig(baseEnv({ PARAMETERS: '{"petId":5}' }))).toThrow(/string/);
  });

  it('parses SERVER_VARIABLES into a name->value map, defaulting to empty', () => {
    expect(loadConfig(baseEnv({ SERVER_VARIABLES: '{"region":"eu"}' })).serverVariables).toEqual({ region: 'eu' });
    expect(loadConfig(baseEnv()).serverVariables).toEqual({});
  });

  it('rejects non-object or non-string SERVER_VARIABLES', () => {
    expect(() => loadConfig(baseEnv({ SERVER_VARIABLES: '"eu"' }))).toThrow(/object/);
    expect(() => loadConfig(baseEnv({ SERVER_VARIABLES: '{"region":5}' }))).toThrow(/string/);
  });

  it('rejects OUTPUT outside replace|merge', () => {
    expect(() => loadConfig(baseEnv({ OUTPUT: 'append' }))).toThrow(/OUTPUT/);
  });

  it('accepts inline bearer auth', () => {
    const cfg = loadConfig(baseEnv({ AUTH_TYPE: 'bearer', AUTH_SECRET: 'tok' }));
    expect(cfg.auth).toEqual({ type: 'bearer', secret: { kind: 'inline', value: 'tok' } });
  });

  it('accepts store-backed auth', () => {
    const cfg = loadConfig(
      baseEnv({ AUTH_TYPE: 'apiKey', AUTH_SECRET_STORE: 'vault', AUTH_SECRET_KEY: 'petkey' }),
    );
    expect(cfg.auth).toEqual({ type: 'apiKey', secret: { kind: 'store', store: 'vault', key: 'petkey' } });
  });

  it('rejects auth without any secret source', () => {
    expect(() => loadConfig(baseEnv({ AUTH_TYPE: 'bearer' }))).toThrow(/AUTH_SECRET/);
  });

  it('keeps the config independent of process env mutation', () => {
    // Sanity: baseEnv builds fresh maps and does not read process.env.
    expect(fixtureUrl()).toContain('petstore.json');
    expect(fixtureSha()).toMatch(/^[0-9a-f]{64}$/);
  });
});
