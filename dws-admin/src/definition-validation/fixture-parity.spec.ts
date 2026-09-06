import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { DefinitionValidationService } from './definition-validation.service';

const FIXTURES = join(
  __dirname, '..', '..', '..', 'dws-controller', 'src', 'test', 'resources', 'fixtures',
);

// Fixtures the controller itself rejects — they exist to prove a deployability
// rule fires, so they are not expected to be spec-valid-and-deployable. They must
// still PARSE and match the DSL shape; only the controller's own rules reject them.
const DEPLOYABILITY_REJECTS = ['run-script-bad-language.yaml', 'run-container.yaml'];

// broken.yaml is not a deployability case: it is the controller's own malformed-DSL
// fixture (no document.version, empty do), asserted to throw CompilationException in
// WorkflowCompilerTest.invalidDefinitionThrows. Layer 1 must reject it too, so it is
// asserted below rather than merely skipped.
const SPEC_INVALID = ['broken.yaml'];

describe('spec validation agrees with the controller on its own fixtures', () => {
  if (!existsSync(FIXTURES)) {
    it('skips when dws-controller is not in this checkout', () => {
      console.warn(`Skipping: ${FIXTURES} not present`);
    });
    return;
  }

  const service = new DefinitionValidationService();
  const files = readdirSync(FIXTURES).filter((f) => f.endsWith('.yaml') || f.endsWith('.json'));
  const specValid = files.filter((f) => !SPEC_INVALID.includes(f));

  it('sees every fixture this suite claims to classify', () => {
    // Guards against a fixture being renamed out from under the lists above.
    for (const file of [...SPEC_INVALID, ...DEPLOYABILITY_REJECTS]) {
      expect(files).toContain(file);
    }
  });

  it.each(specValid)('%s is spec-valid', (file) => {
    const report = service.validate(readFileSync(join(FIXTURES, file), 'utf8'));
    if (!report.valid) {
      throw new Error(
        `${file} failed spec validation:\n` +
          report.errors.map((e) => `  ${e.path || '(root)'}: ${e.message}`).join('\n'),
      );
    }
  });

  // The layer boundary, stated as a test: a definition the controller refuses to
  // deploy is still a well-formed DSL document, and layer 1 must not pre-empt it.
  it.each(DEPLOYABILITY_REJECTS)('%s is spec-valid even though the controller rejects it', (file) => {
    expect(service.validate(readFileSync(join(FIXTURES, file), 'utf8'))).toEqual({ valid: true });
  });

  it.each(SPEC_INVALID)('%s is rejected as malformed DSL', (file) => {
    const report = service.validate(readFileSync(join(FIXTURES, file), 'utf8'));
    expect(report.valid).toBe(false);
  });
});
