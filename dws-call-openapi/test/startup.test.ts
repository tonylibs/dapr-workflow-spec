import { describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { baseEnv } from './helpers.js';

describe('startup fail-fast', () => {
  it('rejects readiness when DOCUMENT_SHA256 does not match', async () => {
    const app = buildApp({
      env: baseEnv({ DOCUMENT_SHA256: 'a'.repeat(64) }),
      logger: false,
    });
    await expect(app.ready()).rejects.toThrow(/SHA-256 mismatch/);
    await app.close();
  });

  it('rejects readiness when the operationId is not in the document', async () => {
    const app = buildApp({ env: baseEnv({ OPERATION_ID: 'noSuchOp' }), logger: false });
    await expect(app.ready()).rejects.toThrow(/not found/);
    await app.close();
  });

  it('rejects readiness on invalid configuration', async () => {
    const env = baseEnv();
    delete env.DOCUMENT_URL;
    const app = buildApp({ env, logger: false });
    await expect(app.ready()).rejects.toThrow(/DOCUMENT_URL is required/);
    await app.close();
  });
});
