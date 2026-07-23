import { describe, expect, it } from 'vitest';
import { resolveOperation } from '../src/openapi/operation.js';
import { OperationValidator, type Binding } from '../src/openapi/validator.js';
import { loadApi } from './helpers.js';

async function validatorFor(operationId: string): Promise<OperationValidator> {
  const api = await loadApi();
  return new OperationValidator(resolveOperation(api, operationId));
}

const noBody = { body: undefined, hasBody: false } as const;

describe('OperationValidator - parameters', () => {
  it('accepts a valid path parameter', async () => {
    const v = await validatorFor('getPetById');
    const binding: Binding = { params: [{ name: 'petId', location: 'path', value: 5 }], ...noBody };
    expect(v.validate(binding).issues).toEqual([]);
  });

  it('coerces a numeric string to the declared integer type', async () => {
    const v = await validatorFor('getPetById');
    const binding: Binding = { params: [{ name: 'petId', location: 'path', value: '5' }], ...noBody };
    const result = v.validate(binding);
    expect(result.issues).toEqual([]);
    expect(result.coercedParams[0]?.value).toBe(5);
  });

  it('rejects a non-numeric value for an integer parameter', async () => {
    const v = await validatorFor('getPetById');
    const binding: Binding = { params: [{ name: 'petId', location: 'path', value: 'abc' }], ...noBody };
    const issues = v.validate(binding).issues;
    expect(issues.length).toBeGreaterThan(0);
    expect(issues[0]?.location).toContain('path.petId');
  });

  it('reports a missing required parameter', async () => {
    const v = await validatorFor('getPetById');
    const issues = v.validate({ params: [], ...noBody }).issues;
    expect(issues).toEqual([{ location: 'path.petId', message: 'is required' }]);
  });

  it('rejects a value outside an enum', async () => {
    const v = await validatorFor('findPetsByStatus');
    const binding: Binding = { params: [{ name: 'status', location: 'query', value: 'flying' }], ...noBody };
    expect(v.validate(binding).issues.length).toBeGreaterThan(0);
  });
});

describe('OperationValidator - request body', () => {
  it('accepts a valid body', async () => {
    const v = await validatorFor('addPet');
    const binding: Binding = { params: [], body: { name: 'Rex', status: 'available' }, hasBody: true };
    expect(v.validate(binding).issues).toEqual([]);
  });

  it('reports a body missing a required property', async () => {
    const v = await validatorFor('addPet');
    const binding: Binding = { params: [], body: { status: 'available' }, hasBody: true };
    const issues = v.validate(binding).issues;
    expect(issues.length).toBeGreaterThan(0);
    expect(issues.some((i) => i.location.startsWith('body'))).toBe(true);
  });

  it('reports a missing required body', async () => {
    const v = await validatorFor('addPet');
    const issues = v.validate({ params: [], ...noBody }).issues;
    expect(issues).toEqual([{ location: 'body', message: 'is required' }]);
  });
});
