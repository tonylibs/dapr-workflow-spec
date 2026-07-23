/**
 * Compiles ajv validators for an operation's bound parameters and requestBody
 * from the document schemas. Parameter values are coerced to their declared
 * types (query/path/header values may arrive as strings); the coerced values
 * are returned for the outbound request. The request body is validated as-is.
 */
import { Ajv, type ValidateFunction } from 'ajv';
import * as ajvFormats from 'ajv-formats';

// ajv-formats is a CommonJS module whose callable is the default export; under
// NodeNext esModuleInterop mistypes it, so reach it through a typed cast.
const addFormats = (ajvFormats as unknown as { default: (ajv: Ajv) => Ajv }).default;
import type { OperationTemplate, ParamLocation, ParamSpec } from './operation.js';

/** A single validation failure, flattened for the 400 response body. */
export interface ValidationIssue {
  readonly location: string;
  readonly message: string;
}

export interface BoundParam {
  readonly name: string;
  readonly location: ParamLocation;
  readonly value: unknown;
}

/** A bound request ready for validation. */
export interface Binding {
  readonly params: readonly BoundParam[];
  readonly body: unknown;
  readonly hasBody: boolean;
}

/** The outcome of validation: issues plus type-coerced parameters. */
export interface ValidationResult {
  readonly issues: readonly ValidationIssue[];
  readonly coercedParams: readonly BoundParam[];
}

interface CompiledParam {
  readonly spec: ParamSpec;
  /** Validates and coerces a `{ value }` wrapper in place. */
  readonly validate: ValidateFunction<{ value: unknown }>;
}

/** Holds the compiled validators for one operation. */
export class OperationValidator {
  private readonly paramValidators: Map<string, CompiledParam>;
  private readonly bodyValidator: ValidateFunction | undefined;
  private readonly requiredParams: readonly ParamSpec[];
  private readonly bodyRequired: boolean;

  constructor(template: OperationTemplate) {
    const paramAjv = buildAjv({ coerceTypes: true });
    const bodyAjv = buildAjv({ coerceTypes: false });

    this.paramValidators = new Map();
    for (const spec of template.parameters) {
      // Wrap in an object so ajv can coerce the primitive value in place.
      const wrapperSchema = { type: 'object', properties: { value: spec.schema } };
      this.paramValidators.set(key(spec.location, spec.name), {
        spec,
        validate: paramAjv.compile<{ value: unknown }>(wrapperSchema),
      });
    }
    this.requiredParams = template.parameters.filter((p) => p.required);
    this.bodyValidator =
      template.requestBodySchema !== undefined ? bodyAjv.compile(template.requestBodySchema) : undefined;
    this.bodyRequired = template.requestBodyRequired;
  }

  /** Validates a binding, returning all issues and the type-coerced parameters. */
  validate(binding: Binding): ValidationResult {
    const issues: ValidationIssue[] = [];
    const coercedParams: BoundParam[] = [];
    const present = new Set(binding.params.map((p) => key(p.location, p.name)));

    for (const spec of this.requiredParams) {
      if (!present.has(key(spec.location, spec.name))) {
        issues.push({ location: `${spec.location}.${spec.name}`, message: 'is required' });
      }
    }

    for (const param of binding.params) {
      const compiled = this.paramValidators.get(key(param.location, param.name));
      if (compiled === undefined) {
        coercedParams.push(param); // unknown params pass through, they are not rejected
        continue;
      }
      const holder = { value: param.value };
      if (compiled.validate(holder)) {
        coercedParams.push({ name: param.name, location: param.location, value: holder.value });
      } else {
        issues.push(...toIssues(`${param.location}.${param.name}`, compiled.validate.errors));
        coercedParams.push(param);
      }
    }

    if (this.bodyValidator !== undefined) {
      if (!binding.hasBody) {
        if (this.bodyRequired) {
          issues.push({ location: 'body', message: 'is required' });
        }
      } else if (!this.bodyValidator(binding.body)) {
        issues.push(...toIssues('body', this.bodyValidator.errors));
      }
    }

    return { issues, coercedParams };
  }
}

function buildAjv(opts: { coerceTypes: boolean }): Ajv {
  const ajv = new Ajv({
    strict: false,
    allErrors: true,
    coerceTypes: opts.coerceTypes,
    useDefaults: true,
  });
  addFormats(ajv);
  return ajv;
}

type AjvErrors = ValidateFunction['errors'];

function toIssues(location: string, errors: AjvErrors): ValidationIssue[] {
  if (!errors || errors.length === 0) {
    return [{ location, message: 'is invalid' }];
  }
  return errors.map((e) => {
    // Strip the `/value` wrapper segment from parameter instance paths.
    const path = (e.instancePath || '').replace(/^\/value/, '');
    return { location: location + path, message: e.message ?? 'is invalid' };
  });
}

function key(location: string, name: string): string {
  return `${location}:${name}`;
}
