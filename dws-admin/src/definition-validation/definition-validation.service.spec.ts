import { DefinitionValidationService } from './definition-validation.service';

const VALID = `document:
  dsl: '1.0.0'
  namespace: default
  name: orderflow
  version: '1.0.0'
do:
  - fetchOrder:
      call: http
      with:
        method: get
        endpoint: https://example.test/orders/1
`;

describe('DefinitionValidationService', () => {
  const service = new DefinitionValidationService();

  it('accepts a well-formed definition', () => {
    expect(service.validate(VALID)).toEqual({ valid: true });
  });

  it('reports a YAML parse failure with a source position', () => {
    const report = service.validate('document:\n  name: [unclosed\n');
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors[0].line).toBeGreaterThan(0);
    expect(report.errors[0].column).toBeGreaterThan(0);
  });

  it('reports a missing required member with a pointer', () => {
    const report = service.validate("document:\n  name: x\n  version: '1'\n");
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors.some((e) => e.keyword === 'required' && /do/.test(e.message))).toBe(true);
  });

  it('reports a structural violation with the offending pointer', () => {
    const bad = VALID.replace('method: get', 'method: 42');
    const report = service.validate(bad);
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors.some((e) => e.path.startsWith('/do/0/fetchOrder'))).toBe(true);
  });

  it('reports duplicate task names found by the uniqueness walk', () => {
    const dup = `${VALID}  - fetchOrder:\n      set:\n        done: true\n`;
    const report = service.validate(dup);
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors.some((e) => /duplicate/i.test(e.message))).toBe(true);
  });

  it('caps the number of reported errors', () => {
    const noisy = `document:\n  name: x\n  version: '1'\ndo:\n${'  - t: {}\n'.repeat(80)}`;
    const report = service.validate(noisy);
    expect(report.valid).toBe(false);
    if (report.valid) return;
    expect(report.errors.length).toBeLessThanOrEqual(50);
    expect(report.truncated).toBe(true);
  });
});
