import { describe, expect, it } from 'vitest';
import { loadConfig, ConfigError } from '../src/config/config.js';
import { baseEnv } from './helpers.js';

describe('loadConfig', () => {
  it('loads a valid minimal configuration with defaults', () => {
    const cfg = loadConfig(baseEnv());
    expect(cfg.operationId).toBe('publishOrder');
    expect(cfg.bindingName).toBe('orders-binding');
    expect(cfg.task).toBe('publish-order');
    expect(cfg.operation).toBe('create');
    expect(cfg.payload).toBe('.');
    expect(cfg.metadata).toEqual({});
    expect(cfg.output).toBe('replace');
    expect(cfg.timeoutMs).toBe(30_000);
    expect(cfg.port).toBe(8080);
    expect(cfg.daprHttpPort).toBe('3500');
  });

  it('defaults task to the operationId when TASK is unset', () => {
    const env = baseEnv();
    delete env.TASK;
    expect(loadConfig(env).task).toBe('publishOrder');
  });

  const requiredCases: Array<[string, string]> = [
    ['DOC_ENDPOINT', 'DOC_ENDPOINT is required'],
    ['DOC_SHA256', 'DOC_SHA256 is required'],
    ['OPERATION_ID', 'OPERATION_ID is required'],
    ['BINDING_NAME', 'BINDING_NAME is required'],
  ];
  it.each(requiredCases)('rejects missing %s', (key, message) => {
    const env = baseEnv();
    delete env[key];
    expect(() => loadConfig(env)).toThrow(message);
  });

  it('rejects a non-hex DOC_SHA256', () => {
    expect(() => loadConfig(baseEnv({ DOC_SHA256: 'nothex' }))).toThrow(ConfigError);
  });

  it('rejects an unsupported DOC_ENDPOINT scheme', () => {
    expect(() => loadConfig(baseEnv({ DOC_ENDPOINT: 'ftp://example.com/doc.json' }))).toThrow(/scheme/);
  });

  it('accepts a custom OPERATION and PAYLOAD', () => {
    const cfg = loadConfig(baseEnv({ OPERATION: 'publish', PAYLOAD: '.order' }));
    expect(cfg.operation).toBe('publish');
    expect(cfg.payload).toBe('.order');
  });

  it('parses METADATA into a string map', () => {
    const cfg = loadConfig(baseEnv({ METADATA: '{"partitionKey":"k1"}' }));
    expect(cfg.metadata).toEqual({ partitionKey: 'k1' });
  });

  it('rejects non-string METADATA values', () => {
    expect(() => loadConfig(baseEnv({ METADATA: '{"n":1}' }))).toThrow(/must be a string/);
  });

  it('rejects non-object METADATA', () => {
    expect(() => loadConfig(baseEnv({ METADATA: '[1,2]' }))).toThrow(/must be a JSON object/);
  });

  it('rejects an out-of-range PORT', () => {
    expect(() => loadConfig(baseEnv({ PORT: '70000' }))).toThrow(/PORT/);
  });

  it('rejects an invalid TIMEOUT', () => {
    expect(() => loadConfig(baseEnv({ TIMEOUT: 'soon' }))).toThrow(/TIMEOUT/);
  });
});
