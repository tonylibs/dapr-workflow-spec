import { describe, expect, it } from 'vitest';
import { findApiKeyScheme, resolveOperation } from '../src/openapi/operation.js';
import { loadApi } from './helpers.js';

describe('resolveOperation', () => {
  it('resolves a GET operation with a path parameter', async () => {
    const api = await loadApi();
    const op = resolveOperation(api, 'getPetById');
    expect(op.method).toBe('GET');
    expect(op.path).toBe('/pet/{petId}');
    expect(op.parameters).toHaveLength(1);
    expect(op.parameters[0]).toMatchObject({ name: 'petId', location: 'path', required: true });
    expect(op.requestBodySchema).toBeUndefined();
  });

  it('resolves a query-parameter operation', async () => {
    const api = await loadApi();
    const op = resolveOperation(api, 'findPetsByStatus');
    expect(op.method).toBe('GET');
    expect(op.parameters[0]).toMatchObject({ name: 'status', location: 'query', required: false });
  });

  it('resolves a POST operation with a required requestBody', async () => {
    const api = await loadApi();
    const op = resolveOperation(api, 'addPet');
    expect(op.method).toBe('POST');
    expect(op.path).toBe('/pet');
    expect(op.requestBodyRequired).toBe(true);
    expect(op.requestBodySchema).toMatchObject({ type: 'object' });
    expect(op.requestBodySchema?.required).toEqual(['name']);
  });

  it('throws for an unknown operationId', async () => {
    const api = await loadApi();
    expect(() => resolveOperation(api, 'deletePet')).toThrow(/not found/);
  });
});

describe('findApiKeyScheme', () => {
  it('finds the apiKey security scheme', async () => {
    const api = await loadApi();
    expect(findApiKeyScheme(api)).toEqual({ name: 'api_key', in: 'header' });
  });
});
