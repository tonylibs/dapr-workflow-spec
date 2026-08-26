import { describe, expect, it } from 'vitest';
import {
  DocumentHashError,
  parseDocument,
  validateDocument,
  verifySha256,
} from '../src/asyncapi/document.js';
import { fixtureRaw, fixtureSha } from './helpers.js';

describe('verifySha256', () => {
  it('accepts a matching digest', () => {
    const raw = fixtureRaw('kafka-orders.json');
    expect(() => verifySha256(raw, fixtureSha('kafka-orders.json'))).not.toThrow();
  });

  it('rejects a mismatching digest', () => {
    expect(() => verifySha256('{}', 'a'.repeat(64))).toThrow(DocumentHashError);
  });
});

describe('parseDocument', () => {
  it('parses JSON', () => {
    expect(parseDocument('{"asyncapi":"3.0.0"}')).toEqual({ asyncapi: '3.0.0' });
  });

  it('parses YAML', () => {
    expect(parseDocument('asyncapi: "3.0.0"\ninfo:\n  title: t')).toMatchObject({ asyncapi: '3.0.0' });
  });

  it('rejects a non-object document', () => {
    expect(() => parseDocument('[1,2]')).toThrow(/must be a JSON\/YAML object/);
  });
});

describe('validateDocument', () => {
  it('accepts a valid AsyncAPI 3.0 document', async () => {
    await expect(validateDocument(fixtureRaw('kafka-orders.json'))).resolves.toBeUndefined();
  });

  it('rejects a document that is not valid AsyncAPI', async () => {
    await expect(validateDocument('{"not":"asyncapi"}')).rejects.toThrow(/invalid AsyncAPI document/);
  });
});
