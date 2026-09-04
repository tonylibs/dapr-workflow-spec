# Validation Preview (dws-console Phase 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an operator check a DSL definition and see what it would deploy, without applying it — with field-precise structural errors from `dws-admin` and deployability errors from `dws-controller`'s existing dry run.

**Architecture:** Two validation layers, split by kind of rule. `dws-admin` gains `POST /definitions/validate`, which parses the raw buffer with `yaml` and validates it with ajv against a JSON Schema vendored out of the *same* `serverlessworkflow-types` jar `dws-controller` pins — so the two layers cannot disagree about document shape. `dws-controller` is untouched and remains the sole authority on deployability, reached through the existing `dws-admin` relay with `?dryRun=true`. `dws-console` adds a `Preview` action that runs the layers in order and renders either the `DeploymentPlan` or the rejecting layer's errors.

**Tech Stack:** NestJS 11 / Node 24 / pnpm / jest (dws-admin); ajv (`Ajv2020`) + `ajv-formats` + `yaml`; React 19 / TanStack Router / vitest / zod (dws-console).

## Global Constraints

- **Do not modify `dws-controller`.** Not one line. Deployability rules stay there.
- **Do not port `WorkflowCompiler.semanticErrors()`** into `dws-admin`.
- Schema source of truth: `io.serverlessworkflow:serverlessworkflow-types`, version read from `dws-controller/pom.xml`'s `<serverlessworkflow.version>` (today `7.26.0.Final`, schema `$id` `https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml`). **Never** vendor `open-workflow-specification.org/schemas/1.0.3/workflow.yaml` — it types `run.shell.arguments` as an array while the controller's model and `run-shell.yaml` use an object.
- `dws-admin` gate: `pnpm lint`, `pnpm test`, `pnpm build`. `dws-console` gate: `pnpm lint`, `pnpm test`, `pnpm build`. Run from the component directory; there is no top-level build.
- `dws-console` must never call `dws-controller` directly; every request goes through `admin-client.ts`, the only module allowed to call `getAccessToken`.
- `submitDefinition` in `admin-client.ts` stays byte-identical.
- Commit style: `<type>: <description>` (feat, fix, refactor, docs, test, chore).

---

## File Structure

**dws-admin (new module `src/definition-validation/`)**
- `definition-validation.module.ts` — wires controller + service.
- `definition-validation.controller.ts` — `POST /definitions/validate`, raw body in, report out.
- `definition-validation.service.ts` — parse → ajv → uniqueness walk → report.
- `validation-report.ts` — the report/error types shared by both.
- `task-names.ts` — the nested task-name walk (own file: it is the one rule not expressible in JSON Schema, and it is tested independently).
- `schema/workflow-schema.json`, `schema/provenance.json` — generated, checked in.
- `definition-validation.service.spec.ts`, `definition-validation.controller.spec.ts`, `task-names.spec.ts`, `schema-provenance.spec.ts`, `fixture-parity.spec.ts`.
- `scripts/vendor-dsl-schema.mjs` — regenerates the two `schema/` files.

**dws-console**
- `src/lib/admin-client.ts` — add `validateDefinitionSpec`, `previewDefinition`, `deploymentPlanSchema`, the `DefinitionPreview` union.
- `src/lib/admin-client.test.ts` — extend.
- `src/components/deployment-plan-view.tsx` — renders a plan (own file so the route stays readable).
- `src/routes/workflows/new.tsx` — add the preview control and handler.
- `src/routes/workflows/new.test.tsx` — new.

---

## Task 1: Vendor the DSL schema with a provenance record and a drift guard

**Files:**
- Create: `dws-admin/scripts/vendor-dsl-schema.mjs`
- Create (generated): `dws-admin/src/definition-validation/schema/workflow-schema.json`
- Create (generated): `dws-admin/src/definition-validation/schema/provenance.json`
- Create: `dws-admin/src/definition-validation/schema-provenance.spec.ts`
- Modify: `dws-admin/package.json` (add `yaml` dep, `vendor:schema` script)

**Interfaces:**
- Consumes: nothing.
- Produces: `schema/workflow-schema.json` (a JSON Schema draft 2020-12 document) and `schema/provenance.json` with exactly `{ sdkVersion: string, schemaId: string, sourceJar: string, sha256: string }`. Task 2 imports both.

- [ ] **Step 1: Add the dependency and script entry**

In `dws-admin`, run:

```bash
pnpm add yaml
```

Then add to `package.json`'s `scripts`:

```json
"vendor:schema": "node scripts/vendor-dsl-schema.mjs"
```

- [ ] **Step 2: Write the vendor script**

Create `dws-admin/scripts/vendor-dsl-schema.mjs`. Node 24 has `fetch` and
`node:zlib`'s raw inflate, which is all a jar (a zip) needs for stored/deflated
entries:

```js
// Regenerates the vendored DSL JSON Schema from the exact serverlessworkflow SDK
// jar that dws-controller pins. Run manually (`pnpm vendor:schema`) after a
// controller-side SDK bump — never during `pnpm build`, which must not need
// network access.
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';
import { parse } from 'yaml';

const here = dirname(fileURLToPath(import.meta.url));
const POM = join(here, '..', '..', 'dws-controller', 'pom.xml');
const OUT_DIR = join(here, '..', 'src', 'definition-validation', 'schema');
const ENTRY = 'schema/workflow.yaml';

/** Reads <serverlessworkflow.version> out of dws-controller's pom. */
async function pinnedSdkVersion() {
  const pom = await readFile(POM, 'utf8');
  const match = pom.match(/<serverlessworkflow\.version>([^<]+)<\/serverlessworkflow\.version>/);
  if (!match) throw new Error(`No <serverlessworkflow.version> in ${POM}`);
  return match[1];
}

/** Extracts one entry from a zip buffer via its central directory. */
function readZipEntry(buf, name) {
  // End of central directory record: signature 0x06054b50, scanned from the tail.
  let eocd = buf.length - 22;
  while (eocd >= 0 && buf.readUInt32LE(eocd) !== 0x06054b50) eocd -= 1;
  if (eocd < 0) throw new Error('Not a zip archive: no EOCD record');
  let offset = buf.readUInt32LE(eocd + 16);
  const count = buf.readUInt16LE(eocd + 10);
  for (let i = 0; i < count; i += 1) {
    const nameLen = buf.readUInt16LE(offset + 28);
    const extraLen = buf.readUInt16LE(offset + 30);
    const commentLen = buf.readUInt16LE(offset + 32);
    const localOffset = buf.readUInt32LE(offset + 42);
    const entryName = buf.toString('utf8', offset + 46, offset + 46 + nameLen);
    if (entryName === name) {
      const method = buf.readUInt16LE(localOffset + 8);
      const localNameLen = buf.readUInt16LE(localOffset + 26);
      const localExtraLen = buf.readUInt16LE(localOffset + 28);
      const start = localOffset + 30 + localNameLen + localExtraLen;
      const compressedSize = buf.readUInt32LE(offset + 20);
      const body = buf.subarray(start, start + compressedSize);
      return method === 0 ? body : inflateRawSync(body);
    }
    offset += 46 + nameLen + extraLen + commentLen;
  }
  throw new Error(`Entry ${name} not found in archive`);
}

const sdkVersion = await pinnedSdkVersion();
const sourceJar =
  `https://repo1.maven.org/maven2/io/serverlessworkflow/serverlessworkflow-types/` +
  `${sdkVersion}/serverlessworkflow-types-${sdkVersion}.jar`;

const response = await fetch(sourceJar);
if (!response.ok) throw new Error(`GET ${sourceJar} → ${response.status}`);
const jar = Buffer.from(await response.arrayBuffer());

const yamlText = readZipEntry(jar, ENTRY).toString('utf8');
const schema = parse(yamlText);
if (typeof schema?.$id !== 'string') throw new Error('Extracted schema has no $id');

await mkdir(OUT_DIR, { recursive: true });
await writeFile(join(OUT_DIR, 'workflow-schema.json'), `${JSON.stringify(schema, null, 2)}\n`);
await writeFile(
  join(OUT_DIR, 'provenance.json'),
  `${JSON.stringify(
    {
      sdkVersion,
      schemaId: schema.$id,
      sourceJar,
      sha256: createHash('sha256').update(yamlText).digest('hex'),
    },
    null,
    2,
  )}\n`,
);

console.log(`Vendored ${schema.$id} from ${sdkVersion}`);
```

- [ ] **Step 3: Run it and inspect the output**

Run: `cd dws-admin && pnpm vendor:schema`
Expected: prints `Vendored https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml from 7.26.0.Final`.

Then confirm the guard-rail fact this whole design rests on:

```bash
node -e "const s=require('./src/definition-validation/schema/workflow-schema.json');console.log(JSON.stringify(s.\$defs.runTask.properties.run.oneOf.find(o=>o.title==='RunShell')?.properties?.shell?.properties?.arguments?.type ?? 'check-manually'))"
```

Expected: `"object"` (not `"array"`). If this prints `"array"`, the wrong schema was vendored — stop and re-read Global Constraints.

- [ ] **Step 4: Write the drift-guard test**

Create `dws-admin/src/definition-validation/schema-provenance.spec.ts`:

```ts
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import provenance from './schema/provenance.json';
import schema from './schema/workflow-schema.json';

const POM = join(__dirname, '..', '..', '..', 'dws-controller', 'pom.xml');

describe('vendored DSL schema provenance', () => {
  it('records the schema identity it was generated from', () => {
    expect((schema as { $id: string }).$id).toBe(provenance.schemaId);
  });

  it('matches the SDK version dws-controller pins', () => {
    if (!existsSync(POM)) {
      // dws-admin can be checked out without its sibling components; a packaging
      // choice must not turn into a red build.
      console.warn(`Skipping: ${POM} not present in this checkout`);
      return;
    }
    const pom = readFileSync(POM, 'utf8');
    const pinned = pom.match(/<serverlessworkflow\.version>([^<]+)</)?.[1];
    expect(pinned).toBeDefined();
    expect(provenance.sdkVersion).toBe(pinned);
  });
});
```

`resolveJsonModule` must be on for the JSON imports; check `tsconfig.json` and add
`"resolveJsonModule": true` under `compilerOptions` if it is absent.

- [ ] **Step 5: Run the test**

Run: `cd dws-admin && pnpm test -- schema-provenance`
Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add dws-admin/package.json dws-admin/pnpm-lock.yaml dws-admin/tsconfig.json \
        dws-admin/scripts/vendor-dsl-schema.mjs dws-admin/src/definition-validation/schema \
        dws-admin/src/definition-validation/schema-provenance.spec.ts
git commit -m "chore: vendor the DSL JSON Schema from the SDK dws-controller pins"
```

---

## Task 2: Nested task-name uniqueness walk

**Files:**
- Create: `dws-admin/src/definition-validation/task-names.ts`
- Create: `dws-admin/src/definition-validation/task-names.spec.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `duplicateTaskNames(document: unknown): { name: string; path: string }[]` — one entry per repeated occurrence (not per distinct duplicated name), `path` a JSON pointer to the repeated task object.

- [ ] **Step 1: Write the failing tests**

Create `dws-admin/src/definition-validation/task-names.spec.ts`:

```ts
import { duplicateTaskNames } from './task-names';

describe('duplicateTaskNames', () => {
  it('returns nothing when every task name is distinct', () => {
    const doc = { do: [{ a: { set: {} } }, { b: { set: {} } }] };
    expect(duplicateTaskNames(doc)).toEqual([]);
  });

  it('finds a duplicate between the top level and a nested catch body', () => {
    const doc = {
      do: [
        { retryIt: { try: { do: [{ a: { set: {} } }] }, catch: { do: [{ a: { set: {} } }] } } },
      ],
    };
    expect(duplicateTaskNames(doc)).toEqual([
      { name: 'a', path: '/do/0/retryIt/catch/do/0/a' },
    ]);
  });

  it('descends into for bodies and fork branches', () => {
    const doc = {
      do: [
        { each: { for: { each: 'i', in: '${ .xs }' }, do: [{ dup: { set: {} } }] } },
        { split: { fork: { branches: [{ dup: { set: {} } }] } } },
      ],
    };
    expect(duplicateTaskNames(doc)).toEqual([
      { name: 'dup', path: '/do/1/split/fork/branches/0/dup' },
    ]);
  });

  it('tolerates a malformed document without throwing', () => {
    expect(duplicateTaskNames({ do: 'not-a-list' })).toEqual([]);
    expect(duplicateTaskNames(null)).toEqual([]);
  });
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd dws-admin && pnpm test -- task-names`
Expected: FAIL — `Cannot find module './task-names'`.

- [ ] **Step 3: Implement the walk**

Create `dws-admin/src/definition-validation/task-names.ts`:

```ts
/**
 * Task names must be unique across the whole definition at every depth: a
 * call/run task's name becomes its Dapr app-id and therefore its deployed
 * Knative Service name, and the orchestrator resolves tasks by name at runtime.
 *
 * JSON Schema cannot express this (it is cross-referential across sibling and
 * nested lists), so ajv will not catch it. Mirrors dws-controller's
 * WorkflowCompiler.duplicateTaskNames/collectTaskNames so both layers give the
 * same verdict — this one just gives it earlier, and with a pointer.
 */
export interface DuplicateTaskName {
  name: string;
  /** JSON pointer to the repeated occurrence (not the first one). */
  path: string;
}

/** Bodies that hold a nested task list, by the key that reaches them. */
const NESTED_LISTS: readonly (readonly string[])[] = [
  ['try', 'do'],
  ['catch', 'do'],
  ['for', 'do'],
  ['do'],
  ['fork', 'branches'],
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** JSON-pointer escaping: `~` → `~0`, `/` → `~1` (RFC 6901). */
function escapePointer(segment: string): string {
  return segment.replace(/~/g, '~0').replace(/\//g, '~1');
}

export function duplicateTaskNames(document: unknown): DuplicateTaskName[] {
  const seen = new Set<string>();
  const duplicates: DuplicateTaskName[] = [];
  if (isRecord(document)) walkList(document.do, '/do', seen, duplicates);
  return duplicates;
}

function walkList(
  list: unknown,
  pointer: string,
  seen: Set<string>,
  duplicates: DuplicateTaskName[],
): void {
  if (!Array.isArray(list)) return;
  list.forEach((entry, index) => {
    if (!isRecord(entry)) return;
    // A task item is a single-key map: { <taskName>: <taskDefinition> }.
    for (const [name, definition] of Object.entries(entry)) {
      const taskPointer = `${pointer}/${index}/${escapePointer(name)}`;
      if (!seen.add(name)) duplicates.push({ name, path: taskPointer });
      walkNested(definition, taskPointer, seen, duplicates);
    }
  });
}

function walkNested(
  definition: unknown,
  pointer: string,
  seen: Set<string>,
  duplicates: DuplicateTaskName[],
): void {
  if (!isRecord(definition)) return;
  for (const keys of NESTED_LISTS) {
    let node: unknown = definition;
    let nested = pointer;
    for (const key of keys) {
      if (!isRecord(node)) {
        node = undefined;
        break;
      }
      node = node[key];
      nested += `/${key}`;
    }
    if (Array.isArray(node)) walkList(node, nested, seen, duplicates);
  }
}
```

Note on `Set.add`: it returns the set, not a boolean, so `!seen.add(name)` is
wrong. Use the explicit form instead — replace that line with:

```ts
      if (seen.has(name)) duplicates.push({ name, path: taskPointer });
      else seen.add(name);
```

- [ ] **Step 4: Run the tests**

Run: `cd dws-admin && pnpm test -- task-names`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add dws-admin/src/definition-validation/task-names.ts dws-admin/src/definition-validation/task-names.spec.ts
git commit -m "feat: detect duplicate task names across nested definition bodies"
```

---

## Task 3: The validation service (parse → ajv → uniqueness)

**Files:**
- Create: `dws-admin/src/definition-validation/validation-report.ts`
- Create: `dws-admin/src/definition-validation/definition-validation.service.ts`
- Create: `dws-admin/src/definition-validation/definition-validation.service.spec.ts`
- Create: `dws-admin/src/definition-validation/fixture-parity.spec.ts`
- Modify: `dws-admin/package.json` (add `ajv`, `ajv-formats`)

**Interfaces:**
- Consumes: `duplicateTaskNames` (Task 2); `schema/workflow-schema.json` (Task 1).
- Produces: `ValidationReport = { valid: true } | { valid: false; errors: ValidationError[]; truncated: boolean }` with `ValidationError = { path: string; message: string; keyword?: string; line?: number; column?: number }`, and `DefinitionValidationService.validate(source: string): ValidationReport`.

- [ ] **Step 1: Add dependencies**

```bash
cd dws-admin && pnpm add ajv ajv-formats
```

- [ ] **Step 2: Write the report types**

Create `dws-admin/src/definition-validation/validation-report.ts`:

```ts
/** One problem with a submitted definition. */
export interface ValidationError {
  /** JSON pointer to the offending location; `''` when the whole document failed. */
  path: string;
  message: string;
  /** ajv keyword (`required`, `const`, …) when the error came from the schema. */
  keyword?: string;
  /** 1-based source position; present only for parse failures. */
  line?: number;
  column?: number;
}

export type ValidationReport =
  | { valid: true }
  | { valid: false; errors: ValidationError[]; truncated: boolean };

/** Reporting cap: the DSL schema is one big union, so a broken document can
 *  otherwise produce hundreds of anyOf-branch errors. */
export const MAX_REPORTED_ERRORS = 50;
```

- [ ] **Step 3: Write the failing service tests**

Create `dws-admin/src/definition-validation/definition-validation.service.spec.ts`:

```ts
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
```

- [ ] **Step 4: Run to verify they fail**

Run: `cd dws-admin && pnpm test -- definition-validation.service`
Expected: FAIL — module not found.

- [ ] **Step 5: Implement the service**

Create `dws-admin/src/definition-validation/definition-validation.service.ts`:

```ts
import { Injectable } from '@nestjs/common';
import Ajv2020, { type ErrorObject, type ValidateFunction } from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { parse, YAMLParseError } from 'yaml';
import schema from './schema/workflow-schema.json';
import { duplicateTaskNames } from './task-names';
import {
  MAX_REPORTED_ERRORS,
  type ValidationError,
  type ValidationReport,
} from './validation-report';

/**
 * Spec-conformance validation: is this a valid DSL document at all?
 *
 * Deliberately NOT deployability — task-kind support, DNS-1123 secret naming,
 * image resolution and OAuth wiring stay in dws-controller, reached through the
 * relay's `?dryRun=true`. The schema here is vendored from the same SDK jar
 * dws-controller parses with, so the two layers agree by construction.
 */
@Injectable()
export class DefinitionValidationService {
  private readonly validateSchema: ValidateFunction;

  constructor() {
    // strict: false — the schema is generated upstream and is not written to
    // ajv's strict-mode rules; strict mode would fail compiling the schema
    // itself, not the user's input.
    const ajv = new Ajv2020({ allErrors: true, strict: false });
    addFormats(ajv);
    this.validateSchema = ajv.compile(schema);
  }

  validate(source: string): ValidationReport {
    let document: unknown;
    try {
      // `parse` accepts JSON too — JSON is a subset of YAML.
      document = parse(source);
    } catch (error) {
      return failure([parseError(error)]);
    }

    const errors: ValidationError[] = [];
    if (!this.validateSchema(document)) {
      errors.push(...(this.validateSchema.errors ?? []).map(schemaError));
    }
    for (const duplicate of duplicateTaskNames(document)) {
      errors.push({
        path: duplicate.path,
        message:
          `Duplicate task name '${duplicate.name}': task names must be unique across the ` +
          'whole definition, including nested try/catch/for/fork bodies',
        keyword: 'uniqueTaskName',
      });
    }

    return errors.length === 0 ? { valid: true } : failure(errors);
  }
}

function failure(errors: ValidationError[]): ValidationReport {
  return {
    valid: false,
    errors: errors.slice(0, MAX_REPORTED_ERRORS),
    truncated: errors.length > MAX_REPORTED_ERRORS,
  };
}

function schemaError(error: ErrorObject): ValidationError {
  return {
    path: error.instancePath,
    message: error.message ?? 'is invalid',
    keyword: error.keyword,
  };
}

function parseError(error: unknown): ValidationError {
  if (error instanceof YAMLParseError) {
    const [line, column] = error.linePos?.[0] ? [error.linePos[0].line, error.linePos[0].col] : [];
    return { path: '', message: error.message, line, column };
  }
  return { path: '', message: error instanceof Error ? error.message : String(error) };
}
```

- [ ] **Step 6: Run the tests**

Run: `cd dws-admin && pnpm test -- definition-validation.service`
Expected: PASS, 6 tests. If the `required`-message assertion fails because ajv
words it differently, read the actual message from the failure output and adjust
the assertion — do not weaken it to `toBeTruthy()`.

- [ ] **Step 7: Write the fixture-parity test**

This is the regression net for the whole D1 decision: every definition the
controller compiles today must pass spec validation.

Create `dws-admin/src/definition-validation/fixture-parity.spec.ts`:

```ts
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { DefinitionValidationService } from './definition-validation.service';

const FIXTURES = join(
  __dirname, '..', '..', '..', 'dws-controller', 'src', 'test', 'resources', 'fixtures',
);

// Fixtures the controller itself rejects — they exist to prove a deployability
// rule fires, so they are not expected to be spec-valid-and-deployable. They must
// still PARSE and match the DSL shape; only the controller's own rules reject them.
const DEPLOYABILITY_REJECTS = new Set(['run-script-bad-language.yaml', 'run-container.yaml']);

describe('spec validation agrees with the controller on its own fixtures', () => {
  if (!existsSync(FIXTURES)) {
    it('skips when dws-controller is not in this checkout', () => {
      console.warn(`Skipping: ${FIXTURES} not present`);
    });
    return;
  }

  const service = new DefinitionValidationService();
  const files = readdirSync(FIXTURES).filter((f) => f.endsWith('.yaml') || f.endsWith('.json'));

  it.each(files)('%s is spec-valid', (file) => {
    const report = service.validate(readFileSync(join(FIXTURES, file), 'utf8'));
    if (!report.valid) {
      throw new Error(
        `${file} failed spec validation:\n` +
          report.errors.map((e) => `  ${e.path || '(root)'}: ${e.message}`).join('\n'),
      );
    }
  });
});
```

`DEPLOYABILITY_REJECTS` is declared for the reader's benefit; those fixtures are
still expected to be *spec*-valid (their defect is a deployability rule), so the
test asserts every fixture passes. If one genuinely is malformed DSL, exclude it
here with a one-line comment explaining why — do not loosen the assertion.

- [ ] **Step 8: Run it**

Run: `cd dws-admin && pnpm test -- fixture-parity`
Expected: PASS for every fixture. **A failure here is the important signal**: it
means the vendored schema disagrees with the compiler. Diagnose before proceeding
— the likely cause is the wrong schema version (see Task 1 Step 3).

- [ ] **Step 9: Commit**

```bash
git add dws-admin/package.json dws-admin/pnpm-lock.yaml dws-admin/src/definition-validation
git commit -m "feat: validate definitions against the vendored DSL schema"
```

---

## Task 4: The `POST /definitions/validate` endpoint

**Files:**
- Create: `dws-admin/src/definition-validation/definition-validation.controller.ts`
- Create: `dws-admin/src/definition-validation/definition-validation.module.ts`
- Create: `dws-admin/src/definition-validation/definition-validation.controller.spec.ts`
- Modify: `dws-admin/src/app.module.ts`
- Modify: `dws-admin/README.md`

**Interfaces:**
- Consumes: `DefinitionValidationService.validate` (Task 3).
- Produces: `POST /definitions/validate` → 200 `ValidationReport`; 400 empty body / unsupported content type; 413 over 1 MiB. Task 5's `validateDefinitionSpec` calls it.

- [ ] **Step 1: Write the failing controller tests**

Create `dws-admin/src/definition-validation/definition-validation.controller.spec.ts`:

```ts
import { BadRequestException, PayloadTooLargeException } from '@nestjs/common';
import { DefinitionValidationController } from './definition-validation.controller';
import { DefinitionValidationService } from './definition-validation.service';

const VALID = "document:\n  name: x\n  version: '1'\ndo:\n  - a:\n      set:\n        k: 1\n";

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
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd dws-admin && pnpm test -- definition-validation.controller`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the controller**

Create `dws-admin/src/definition-validation/definition-validation.controller.ts`:

```ts
import {
  BadRequestException,
  Controller,
  Headers,
  PayloadTooLargeException,
  Post,
  Req,
} from '@nestjs/common';
import { ApiExcludeEndpoint } from '@nestjs/swagger';
import { DefinitionValidationService } from './definition-validation.service';
import type { ValidationReport } from './validation-report';

/** Same minimal raw-request shape the controller relay uses. */
interface RawRequest {
  rawBody?: Buffer;
  body?: unknown;
}

/** Content types a definition may be submitted as — mirrors main.ts's raw parser. */
const ACCEPTED = new Set([
  'application/yaml',
  'application/x-yaml',
  'text/yaml',
  'application/json',
]);

/** CPU-bound endpoint on an operator-supplied body; cap it explicitly. */
const MAX_BODY_BYTES = 1024 * 1024;

/**
 * Spec-conformance check for a raw definition. Local to dws-admin: it never
 * contacts dws-controller and changes nothing. A well-formed request always
 * answers 200 — the document's validity is in the body, not the status.
 */
@Controller('definitions')
export class DefinitionValidationController {
  constructor(private readonly validation: DefinitionValidationService) {}

  @Post('validate')
  @ApiExcludeEndpoint()
  validate(
    @Req() req: RawRequest,
    @Headers('content-type') contentType: string | undefined,
  ): ValidationReport {
    const mediaType = (contentType ?? '').split(';')[0].trim().toLowerCase();
    if (!ACCEPTED.has(mediaType)) {
      throw new BadRequestException(`Unsupported content type: ${contentType ?? '(none)'}`);
    }

    const body = req.rawBody ?? (Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0));
    if (body.byteLength === 0) throw new BadRequestException('Definition body is empty');
    if (body.byteLength > MAX_BODY_BYTES) {
      throw new PayloadTooLargeException(`Definition exceeds ${MAX_BODY_BYTES} bytes`);
    }

    return this.validation.validate(body.toString('utf8'));
  }
}
```

- [ ] **Step 4: Add the module and register it**

Create `dws-admin/src/definition-validation/definition-validation.module.ts`:

```ts
import { Module } from '@nestjs/common';
import { DefinitionValidationController } from './definition-validation.controller';
import { DefinitionValidationService } from './definition-validation.service';

@Module({
  controllers: [DefinitionValidationController],
  providers: [DefinitionValidationService],
})
export class DefinitionValidationModule {}
```

Then in `dws-admin/src/app.module.ts` add the import and list it in `imports`,
after `ControllerRelayModule`.

- [ ] **Step 5: Run the tests**

Run: `cd dws-admin && pnpm test -- definition-validation.controller`
Expected: PASS, 5 tests.

- [ ] **Step 6: Full gate**

Run: `cd dws-admin && pnpm lint && pnpm test && pnpm build`
Expected: all green.

- [ ] **Step 7: Document the endpoint**

In `dws-admin/README.md`, add a short section covering `POST /definitions/validate`
(purpose, accepted content types, 200-report contract, the 1 MiB cap) and the
`pnpm vendor:schema` workflow, stating plainly that the schema is vendored from
the SDK version `dws-controller`'s `pom.xml` pins and must be regenerated when
that version moves.

- [ ] **Step 8: Commit**

```bash
git add dws-admin/src/app.module.ts dws-admin/src/definition-validation dws-admin/README.md
git commit -m "feat: expose POST /definitions/validate on dws-admin"
```

---

## Task 5: Console transport — spec check and dry-run preview

**Files:**
- Modify: `dws-console/src/lib/admin-client.ts`
- Modify: `dws-console/src/lib/admin-client.test.ts`

**Interfaces:**
- Consumes: `POST /definitions/validate` (Task 4); `POST /workflows?dryRun=true` (existing relay).
- Produces:
  - `validateDefinitionSpec(definition: string, format: "yaml" | "json", signal?: AbortSignal): Promise<SpecValidationReport>` where `SpecValidationReport = { valid: true } | { valid: false; errors: SpecError[]; truncated: boolean }` and `SpecError = { path: string; message: string; keyword?: string; line?: number; column?: number }`.
  - `previewDefinition(definition: string, signal?: AbortSignal): Promise<DefinitionPreview>` where `DefinitionPreview = { kind: "plan"; plan: DeploymentPlan } | { kind: "deploy-error"; errors: string[] }`.
  - `DeploymentPlan` (zod-inferred) with `workflow`, `versionId`, `version`, `definitionResource`, `specText`, `steps: {name, kind, image}[]`, `bindings: {task, direction, topic}[]`, `orchestrator: {name, image, appId, appPort, replicas}`.

- [ ] **Step 1: Write the failing transport tests**

Append to `dws-console/src/lib/admin-client.test.ts`:

```ts
describe("validateDefinitionSpec", () => {
	it("posts the raw buffer with the format's content type and the bearer token", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify({ valid: true }), { status: 200 }));
		globalThis.fetch = fetchMock;

		const report = await validateDefinitionSpec("document: {}", "yaml");

		expect(report).toEqual({ valid: true });
		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe("/dws-admin/definitions/validate");
		expect(init.method).toBe("POST");
		expect(init.body).toBe("document: {}");
		expect(new Headers(init.headers).get("content-type")).toBe("application/yaml");
		expect(new Headers(init.headers).get("authorization")).toBe("Bearer test-token");
	});

	it("uses the JSON content type for a JSON buffer", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify({ valid: true }), { status: 200 }));
		globalThis.fetch = fetchMock;

		await validateDefinitionSpec("{}", "json");

		expect(new Headers(fetchMock.mock.calls[0][1].headers).get("content-type")).toBe(
			"application/json",
		);
	});

	it("returns the reported errors verbatim", async () => {
		const body = {
			valid: false,
			truncated: false,
			errors: [{ path: "/do/0", message: "must have required property 'call'" }],
		};
		globalThis.fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 200 }));

		const report = await validateDefinitionSpec("x", "yaml");

		expect(report).toEqual(body);
	});

	it("throws ApiError on a non-2xx response", async () => {
		globalThis.fetch = vi.fn().mockResolvedValue(new Response("", { status: 413 }));
		await expect(validateDefinitionSpec("x", "yaml")).rejects.toBeInstanceOf(ApiError);
	});
});

describe("previewDefinition", () => {
	const plan = {
		workflow: "order",
		versionId: "v12345678",
		version: "order@v12345678",
		definitionResource: "dws-def-order-v12345678",
		specText: "document: {}",
		steps: [{ name: "fetch-order", kind: "CALL_HTTP", image: "ghcr.io/dws/call-http:1" }],
		bindings: [{ task: "notify", direction: "EMIT", topic: "orders" }],
		orchestrator: {
			name: "dws-orch-order-v12345678",
			image: "ghcr.io/dws/orchestrator:1",
			appId: "order",
			appPort: 8080,
			replicas: 1,
		},
		oauthEndpoints: [],
		bindingComponents: [],
	};

	it("requests a dry run and returns the parsed plan", async () => {
		const fetchMock = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify(plan), { status: 200 }));
		globalThis.fetch = fetchMock;

		const outcome = await previewDefinition("document: {}");

		expect(fetchMock.mock.calls[0][0]).toBe("/dws-admin/workflows?dryRun=true");
		expect(outcome).toEqual({ kind: "plan", plan: expect.objectContaining({ workflow: "order" }) });
	});

	it("returns the controller's flat errors on a 400", async () => {
		globalThis.fetch = vi.fn().mockResolvedValue(
			new Response(JSON.stringify({ message: "invalid", errors: ["task 'a': boom"] }), {
				status: 400,
			}),
		);

		await expect(previewDefinition("x")).resolves.toEqual({
			kind: "deploy-error",
			errors: ["task 'a': boom"],
		});
	});

	it("throws ApiError when the plan shape is unexpected", async () => {
		globalThis.fetch = vi
			.fn()
			.mockResolvedValue(new Response(JSON.stringify({ workflow: 1 }), { status: 200 }));
		await expect(previewDefinition("x")).rejects.toBeInstanceOf(ApiError);
	});
});
```

Add `validateDefinitionSpec` and `previewDefinition` to the file's existing import
from `./admin-client`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd dws-console && pnpm test -- admin-client`
Expected: FAIL — the two functions are not exported.

- [ ] **Step 3: Implement in `admin-client.ts`**

Add below `submitDefinition` (leave `submitDefinition` unchanged):

```ts
/** Content type per editor format; the admin validator accepts both. */
const CONTENT_TYPES = {
	yaml: "application/yaml",
	json: "application/json",
} as const;

/** One spec-conformance problem. `line`/`column` appear only for parse failures. */
export interface SpecError {
	path: string;
	message: string;
	keyword?: string;
	line?: number;
	column?: number;
}

export type SpecValidationReport =
	| { valid: true }
	| { valid: false; errors: SpecError[]; truncated: boolean };

/**
 * Layer 1 of preview: does this parse, and is it valid DSL?
 *
 * Answered inside dws-admin against the JSON Schema vendored from the same SDK
 * dws-controller parses with — no controller hop, so an invalid draft never
 * costs a compile. Unlike the submit path, an invalid *document* is a 200 here;
 * a non-2xx means the *request* was wrong.
 */
export async function validateDefinitionSpec(
	definition: string,
	format: "yaml" | "json",
	signal?: AbortSignal,
): Promise<SpecValidationReport> {
	const response = await adminFetch(
		"/definitions/validate",
		{
			method: "POST",
			headers: { Accept: "application/json", "Content-Type": CONTENT_TYPES[format] },
			body: definition,
		},
		signal,
	);

	if (!response.ok) {
		throw new ApiError(
			response.status,
			`POST /definitions/validate failed: ${response.status} ${response.statusText}`,
		);
	}

	return (await response.json()) as SpecValidationReport;
}

/**
 * Mirrors dws-controller's `DeploymentPlan` record. Parsed, not cast, for the
 * same reason `applyResultSchema` is: a silent shape drift would otherwise reach
 * the plan table as "undefined".
 *
 * `oauthEndpoints`/`bindingComponents` are accepted permissively — Phase 2 does
 * not render them and their shape is not console-facing.
 */
const deploymentPlanSchema = z.object({
	workflow: z.string(),
	versionId: z.string(),
	version: z.string(),
	definitionResource: z.string(),
	specText: z.string(),
	steps: z.array(z.object({ name: z.string(), kind: z.string(), image: z.string() })),
	bindings: z.array(
		z.object({ task: z.string(), direction: z.string(), topic: z.string() }),
	),
	orchestrator: z.object({
		name: z.string(),
		image: z.string(),
		appId: z.string(),
		appPort: z.number(),
		replicas: z.number(),
	}),
	oauthEndpoints: z.array(z.unknown()).optional(),
	bindingComponents: z.array(z.unknown()).optional(),
});

export type DeploymentPlan = z.infer<typeof deploymentPlanSchema>;

/** Layer 2 of preview: what would this actually deploy, per dws-controller? */
export type DefinitionPreview =
	| { kind: "plan"; plan: DeploymentPlan }
	| { kind: "deploy-error"; errors: string[] };

/**
 * Compile-only request through the same relay the real submit uses. Applies
 * nothing: `dryRun=true` makes dws-controller return the plan without touching
 * the cluster.
 */
export async function previewDefinition(
	definition: string,
	signal?: AbortSignal,
): Promise<DefinitionPreview> {
	const response = await adminFetch(
		"/workflows?dryRun=true",
		{
			method: "POST",
			headers: { Accept: "application/json", "Content-Type": "application/yaml" },
			body: definition,
		},
		signal,
	);

	if (response.status === 400) {
		const payload = (await response.json()) as { errors?: unknown };
		if (
			Array.isArray(payload.errors) &&
			payload.errors.every((error) => typeof error === "string")
		) {
			return { kind: "deploy-error", errors: payload.errors };
		}
		throw new ApiError(
			400,
			`POST /workflows?dryRun=true failed: invalid validation response: ${summarize(payload)}`,
		);
	}

	if (!response.ok) {
		throw new ApiError(
			response.status,
			`POST /workflows?dryRun=true failed: ${response.status} ${response.statusText}`,
		);
	}

	const parsed = deploymentPlanSchema.safeParse(await response.json());
	if (!parsed.success) {
		throw new ApiError(
			response.status,
			`POST /workflows?dryRun=true failed: unexpected plan: ${parsed.error.message}`,
		);
	}

	return { kind: "plan", plan: parsed.data };
}
```

- [ ] **Step 4: Run the tests**

Run: `cd dws-console && pnpm test -- admin-client`
Expected: PASS, including the pre-existing `submitDefinition` tests unchanged.

- [ ] **Step 5: Commit**

```bash
git add dws-console/src/lib/admin-client.ts dws-console/src/lib/admin-client.test.ts
git commit -m "feat: add spec-validation and dry-run preview calls to the admin client"
```

---

## Task 6: Editor preview action and plan rendering

**Files:**
- Create: `dws-console/src/components/deployment-plan-view.tsx`
- Modify: `dws-console/src/routes/workflows/new.tsx`
- Create: `dws-console/src/routes/workflows/new.test.tsx`

**Interfaces:**
- Consumes: `validateDefinitionSpec`, `previewDefinition`, `DeploymentPlan`, `SpecError` (Task 5).
- Produces: nothing downstream.

- [ ] **Step 1: Write the plan view component**

Create `dws-console/src/components/deployment-plan-view.tsx`:

```tsx
import type { DeploymentPlan } from "#/lib/admin-client";

/**
 * Renders what a definition would deploy. Note what is deliberately absent: the
 * DSL's control-flow tasks (`switch`/`set`/`wait`/`try`/`for`/`fork`) never
 * appear in a DeploymentPlan — it is the deployable-resource view, not the task
 * graph. `specText` is not re-rendered; the operator is looking at it.
 */
export function DeploymentPlanView({ plan }: { plan: DeploymentPlan }) {
	return (
		<div className="pane" style={{ gap: 12 }}>
			<div>
				<h3 className="pane-title">Would deploy {plan.version}</h3>
				<p className="pane-lede">
					Nothing was applied. {plan.steps.length} step
					{plan.steps.length === 1 ? "" : "s"}, {plan.bindings.length} binding
					{plan.bindings.length === 1 ? "" : "s"}.
				</p>
			</div>

			{plan.steps.length > 0 && (
				<table>
					<caption className="muted">Step services</caption>
					<thead>
						<tr>
							<th scope="col">Task</th>
							<th scope="col">Kind</th>
							<th scope="col">Image</th>
						</tr>
					</thead>
					<tbody>
						{plan.steps.map((step) => (
							<tr key={step.name}>
								<td>{step.name}</td>
								<td>{step.kind}</td>
								<td>{step.image}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}

			{plan.bindings.length > 0 && (
				<table>
					<caption className="muted">Topic bindings</caption>
					<thead>
						<tr>
							<th scope="col">Task</th>
							<th scope="col">Direction</th>
							<th scope="col">Topic</th>
						</tr>
					</thead>
					<tbody>
						{plan.bindings.map((binding) => (
							<tr key={`${binding.task}:${binding.topic}`}>
								<td>{binding.task}</td>
								<td>{binding.direction}</td>
								<td>{binding.topic}</td>
							</tr>
						))}
					</tbody>
				</table>
			)}

			<p className="muted">
				Orchestrator {plan.orchestrator.name} · {plan.orchestrator.image}
			</p>
		</div>
	);
}
```

Check `dws-console/src/components/data-table.tsx` first: if it exposes a plain
presentational table that fits, use it instead of raw `<table>` markup so the
plan view inherits existing console styling. Keep the same columns either way.

- [ ] **Step 2: Write the failing route tests**

Create `dws-console/src/routes/workflows/new.test.tsx`. Follow the mocking style
already used in `dws-console/src/components/auth-control.test.tsx` for `useOidc`
and in `admin-hooks.test.tsx` for rendering; mock `#/lib/admin-client` so no
network is involved:

```tsx
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("#/lib/oidc", () => ({ useOidc: () => ({ isUserLoggedIn: true }) }));
vi.mock("#/lib/admin-client", async (importOriginal) => ({
	...(await importOriginal<object>()),
	validateDefinitionSpec: vi.fn(),
	previewDefinition: vi.fn(),
	submitDefinition: vi.fn(),
}));

import { previewDefinition, validateDefinitionSpec } from "#/lib/admin-client";
// Import the component the route file exports for testing — export
// `DefinitionEditor` by name from new.tsx as part of Step 3.
import { DefinitionEditor } from "./new";

const type = async (text: string) => {
	await userEvent.click(screen.getByLabelText("Workflow definition"));
	await userEvent.paste(text);
};

beforeEach(() => {
	vi.mocked(validateDefinitionSpec).mockReset();
	vi.mocked(previewDefinition).mockReset();
});

describe("definition editor preview", () => {
	it("disables preview while the buffer is empty", () => {
		render(<DefinitionEditor />);
		expect(screen.getByRole("button", { name: /preview/i })).toBeDisabled();
	});

	it("renders spec errors with their path and never calls the dry run", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({
			valid: false,
			truncated: false,
			errors: [{ path: "/do/0", message: "must have required property 'call'" }],
		});
		render(<DefinitionEditor />);
		await type("document: {}");
		await userEvent.click(screen.getByRole("button", { name: /preview/i }));

		expect(await screen.findByText(/must have required property/)).toBeInTheDocument();
		expect(screen.getByText("/do/0")).toBeInTheDocument();
		expect(previewDefinition).not.toHaveBeenCalled();
	});

	it("renders a parse error's line and column", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({
			valid: false,
			truncated: false,
			errors: [{ path: "", message: "Flow map must end with }", line: 2, column: 9 }],
		});
		render(<DefinitionEditor />);
		await type("x");
		await userEvent.click(screen.getByRole("button", { name: /preview/i }));

		expect(await screen.findByText(/line 2, column 9/i)).toBeInTheDocument();
	});

	it("renders the plan when both layers pass", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({
			kind: "plan",
			plan: {
				workflow: "order",
				versionId: "v1",
				version: "order@v1",
				definitionResource: "dws-def-order-v1",
				specText: "",
				steps: [{ name: "fetch-order", kind: "CALL_HTTP", image: "img:1" }],
				bindings: [{ task: "notify", direction: "EMIT", topic: "orders" }],
				orchestrator: {
					name: "orch", image: "orch:1", appId: "order", appPort: 8080, replicas: 1,
				},
			},
		});
		render(<DefinitionEditor />);
		await type("document: {}");
		await userEvent.click(screen.getByRole("button", { name: /preview/i }));

		expect(await screen.findByText(/would deploy order@v1/i)).toBeInTheDocument();
		expect(screen.getByText("fetch-order")).toBeInTheDocument();
		expect(screen.getByText("orders")).toBeInTheDocument();
	});

	it("renders controller rejections as flat strings", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({
			kind: "deploy-error",
			errors: ["task 'a': run: container is not supported"],
		});
		render(<DefinitionEditor />);
		await type("document: {}");
		await userEvent.click(screen.getByRole("button", { name: /preview/i }));

		expect(await screen.findByText(/run: container is not supported/)).toBeInTheDocument();
	});

	it("clears a rendered plan when the buffer changes", async () => {
		vi.mocked(validateDefinitionSpec).mockResolvedValue({ valid: true });
		vi.mocked(previewDefinition).mockResolvedValue({
			kind: "plan",
			plan: {
				workflow: "order", versionId: "v1", version: "order@v1",
				definitionResource: "d", specText: "", steps: [], bindings: [],
				orchestrator: { name: "o", image: "i", appId: "a", appPort: 8080, replicas: 1 },
			},
		});
		render(<DefinitionEditor />);
		await type("document: {}");
		await userEvent.click(screen.getByRole("button", { name: /preview/i }));
		await screen.findByText(/would deploy/i);

		await type("\nmore: text");

		await waitFor(() =>
			expect(screen.queryByText(/would deploy/i)).not.toBeInTheDocument(),
		);
	});
});
```

If `@testing-library/react` / `@testing-library/user-event` are not already dev
dependencies, check how `auth-control.test.tsx` renders components and match that
setup rather than introducing a second testing approach.

- [ ] **Step 3: Run to verify they fail**

Run: `cd dws-console && pnpm test -- workflows/new`
Expected: FAIL — no `Preview` button / `DefinitionEditor` not exported.

- [ ] **Step 4: Implement the preview action**

In `dws-console/src/routes/workflows/new.tsx`:

1. Export the component: `export function DefinitionEditor() {` (the route
   registration stays as it is).
2. Add imports: `previewDefinition`, `validateDefinitionSpec`, and the types
   `DefinitionPreview`, `SpecError` from `#/lib/admin-client`;
   `DeploymentPlanView` from `#/components/deployment-plan-view`.
3. Add state and the handler:

```tsx
	const [preview, setPreview] = useState<DefinitionPreview | undefined>();
	const [specErrors, setSpecErrors] = useState<SpecError[] | undefined>();
	const [isPreviewing, setIsPreviewing] = useState(false);

	/**
	 * Two layers, in order: spec conformance in dws-admin (fast, local, gives
	 * field paths), then — only if that passes — dws-controller's compile-only
	 * dry run for deployability. A document that fails layer 1 would fail layer 2
	 * too, with strictly worse feedback, so the controller is never asked.
	 */
	const runPreview = async () => {
		if (!oidc.isUserLoggedIn || !definition.trim()) return;
		setIsPreviewing(true);
		setPreview(undefined);
		setSpecErrors(undefined);
		setRequestError(undefined);
		try {
			const report = await validateDefinitionSpec(definition, format);
			if (!report.valid) {
				setSpecErrors(report.errors);
				return;
			}
			setPreview(await previewDefinition(definition));
		} catch (error) {
			if (error instanceof AuthenticationError) {
				setRequestError("Your session has expired. Sign in again to preview.");
			} else {
				setRequestError(
					error instanceof ApiError ? error.message : "Could not reach dws-admin.",
				);
			}
		} finally {
			setIsPreviewing(false);
		}
	};

	// A plan describes the buffer that produced it; once the text moves, showing
	// it would assert something no longer true.
	const onDefinitionChange = (next: string) => {
		setDefinition(next);
		setPreview(undefined);
		setSpecErrors(undefined);
	};
```

4. Point CodeMirror at the new handler: `onChange={onDefinitionChange}`.
5. Add the button next to `Submit definition`:

```tsx
					<button
						type="button"
						className="btn-sm"
						disabled={!canSubmit || isPreviewing || isSubmitting}
						onClick={runPreview}
					>
						{isPreviewing ? "Checking…" : "Preview"}
					</button>
```

6. Render the three outcomes below the existing banners:

```tsx
				{specErrors && (
					<Banner>
						<p>This is not a valid DSL 1.0 definition.</p>
						<ul>
							{specErrors.map((error) => (
								<li key={`${error.path}:${error.message}:${error.line ?? ""}`}>
									<code>{error.path || "(document)"}</code> — {error.message}
									{error.line !== undefined &&
										` (line ${error.line}, column ${error.column})`}
								</li>
							))}
						</ul>
					</Banner>
				)}
				{preview?.kind === "deploy-error" && (
					<Banner>
						<p>Valid DSL, but this cluster cannot deploy it.</p>
						<ul>
							{preview.errors.map((error) => (
								<li key={error}>{error}</li>
							))}
						</ul>
					</Banner>
				)}
				{preview?.kind === "plan" && <DeploymentPlanView plan={preview.plan} />}
```

- [ ] **Step 5: Run the tests**

Run: `cd dws-console && pnpm test -- workflows/new`
Expected: PASS, 6 tests.

- [ ] **Step 6: Full gate**

Run: `cd dws-console && pnpm lint && pnpm test && pnpm build`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add dws-console/src/routes/workflows/new.tsx dws-console/src/routes/workflows/new.test.tsx \
        dws-console/src/components/deployment-plan-view.tsx
git commit -m "feat: preview a definition's deployment plan before submitting it"
```

---

## Task 7: Documentation

**Files:**
- Modify: `docs/roadmaps/dws-console-submission.md`
- External: Notion mirror `https://app.notion.com/p/3c92f73e4fd981988252dcbff0736f60`

- [ ] **Step 1: Update the phase table**

In §3, change the Phase 2 row's status from
`❌ not started — design decided 2026-09-03 (two-layer validation, see §6)` to
`✅ done — see §6` and update the `Updated` date in the status legend.

- [ ] **Step 2: Rewrite §6's open questions as resolved**

Replace the "Open questions before this is buildable, not yet resolved" block
with a resolved record covering, in this order:

1. **DSL version — resolved, and it changed the plan.** `dws-controller` has no
   hand-written model: it parses via `serverlessworkflow-api` `7.26.0.Final`,
   whose `serverlessworkflow-types` jar ships the schema its types were generated
   from — `$id: https://serverlessworkflow.io/schemas/1.0.1/workflow.yaml`. Not
   1.0.3. The two differ materially: 1.0.3 types `run.shell`/`run.script`
   `arguments` as an array of strings while 1.0.1 types it as an object, which is
   the form `WorkflowCompiler` reads (`Map<String,Object>`) and the form
   `run-shell.yaml` uses — so a 1.0.3 validator would reject a definition DWS
   deploys today. Four further divergences (`emit.event.with` required set,
   inline `oauth2` required set, `for.in` union, `uriTemplate` pattern) run the
   other way. **Decision: vendor the SDK's schema, not the spec repo's.**
2. **Task-name uniqueness — confirmed needed, with a correction.** Still not
   expressible in JSON Schema, so `dws-admin` carries a custom walk over `do`,
   `try.do`, `catch.do`, `for.do`, and `fork.branches`. But `dws-controller`
   already enforces it (`WorkflowCompiler.duplicateTaskNames`), so this is
   early, path-precise **parity**, not new coverage.
3. **Vendoring — resolved as neither listed option.** `pnpm vendor:schema` in
   `dws-admin` reads `<serverlessworkflow.version>` from
   `dws-controller/pom.xml`, downloads that jar, and writes a checked-in
   `workflow-schema.json` plus `provenance.json`. A `dws-admin` test asserts the
   provenance still matches the pom, so a controller-side SDK bump fails
   `pnpm test` instead of drifting silently.

Also record what shipped: `POST /definitions/validate` in `dws-admin` (200 report,
1 MiB cap, ajv `Ajv2020` + `ajv-formats`, 50-error cap), and the console's
sequential `Preview` action rendering the `DeploymentPlan` or the rejecting
layer's errors. State the deliberate non-goal that held: **`dws-controller` was
not modified**.

- [ ] **Step 3: Update the "Current progress" note**

Replace the 2026-09-03 progress block with a dated note that Phase 2 shipped,
naming the files added (`dws-admin/src/definition-validation/`,
`dws-console/src/components/deployment-plan-view.tsx`) and that Phases 3–5 remain
as described.

- [ ] **Step 4: Mirror to Notion**

Update `https://app.notion.com/p/3c92f73e4fd981988252dcbff0736f60` so the phase
table and §6 match the file. If the Notion connector is unavailable in this
session, stop and report that the mirror is outstanding rather than marking the
task done.

- [ ] **Step 5: Commit**

```bash
git add docs/roadmaps/dws-console-submission.md
git commit -m "docs: record Phase 2 validation preview as shipped"
```

---

## Self-Review Notes

- **Spec coverage.** `admin-definition-validation`'s five requirements map to
  Tasks 1 (provenance/drift), 2 (uniqueness), 3 (parse, schema, cap, fixture
  parity), 4 (endpoint, status codes, size cap).
  `console-definition-submission`'s five added requirements map to Tasks 5
  (transport, plan parsing, deployability errors) and 6 (control, ordering, path
  rendering, plan rendering, distinct outcomes).
- **Known follow-ups, deliberately not in this plan:** ajv `anyOf` error pruning
  if the raw list proves noisy in practice; surfacing the checked DSL version in
  the UI; mapping JSON pointers to CodeMirror source positions (needs a YAML CST).
