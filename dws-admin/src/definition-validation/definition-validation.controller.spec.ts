import { BadRequestException, PayloadTooLargeException } from '@nestjs/common';
import { DefinitionValidationController } from './definition-validation.controller';
import { DefinitionValidationService } from './definition-validation.service';

// A minimally complete definition: the DSL schema requires document.dsl,
// document.namespace and a semver document.version, so a shorter stub would fail
// validation for reasons that have nothing to do with the controller under test.
const VALID = `document:
  dsl: '1.0.0'
  namespace: default
  name: x
  version: '1.0.0'
do:
  - a:
      set:
        k: 1
`;

function makeController() {
  return new DefinitionValidationController(new DefinitionValidationService());
}

function req(body: string) {
  return { rawBody: Buffer.from(body) };
}

describe('DefinitionValidationController', () => {
  it('returns a valid report for a well-formed definition', () => {
    expect(makeController().validate(req(VALID), 'application/yaml')).toEqual({ valid: true });
  });

  it('returns an invalid report rather than an error status', () => {
    const report = makeController().validate(req('document: {}\n'), 'application/yaml');
    expect(report.valid).toBe(false);
  });

  it('rejects an empty body as a request error', () => {
    expect(() => makeController().validate(req(''), 'application/yaml')).toThrow(BadRequestException);
  });

  it('rejects an unsupported content type', () => {
    expect(() => makeController().validate(req(VALID), 'text/html')).toThrow(BadRequestException);
  });

  it('rejects a body over the size cap before parsing', () => {
    const huge = { rawBody: Buffer.alloc(1024 * 1024 + 1, 0x20) };
    expect(() => makeController().validate(huge, 'application/yaml')).toThrow(PayloadTooLargeException);
  });
});
