import { describe, expect, it } from 'vitest';
import { PayloadValidator } from '../src/asyncapi/validator.js';

const schema = {
  type: 'object',
  required: ['orderId', 'amount'],
  properties: {
    orderId: { type: 'string' },
    amount: { type: 'number' },
  },
  additionalProperties: false,
};

describe('PayloadValidator', () => {
  it('accepts a valid payload', () => {
    const v = new PayloadValidator(schema);
    expect(v.validatePayload({ orderId: 'o1', amount: 5 })).toEqual([]);
  });

  it('reports a missing required property', () => {
    const v = new PayloadValidator(schema);
    const issues = v.validatePayload({ amount: 5 });
    expect(issues.length).toBeGreaterThan(0);
    expect(issues.some((i) => /required/.test(i.message))).toBe(true);
  });

  it('does not coerce types (a numeric string is a violation)', () => {
    const v = new PayloadValidator(schema);
    const issues = v.validatePayload({ orderId: 'o1', amount: '5' });
    expect(issues.some((i) => i.location.includes('/amount'))).toBe(true);
  });

  it('rejects additional properties', () => {
    const v = new PayloadValidator(schema);
    expect(v.validatePayload({ orderId: 'o1', amount: 5, extra: true }).length).toBeGreaterThan(0);
  });

  it('accepts any payload when no schema is declared', () => {
    const v = new PayloadValidator(undefined);
    expect(v.validatePayload({ anything: [1, 2, 3] })).toEqual([]);
    expect(v.validatePayload(null)).toEqual([]);
  });
});
