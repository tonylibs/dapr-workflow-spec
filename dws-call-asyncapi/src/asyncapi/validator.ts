/**
 * Compiles a single ajv validator for the resolved message `payload` schema and
 * validates the jq-interpolated payload against it. `message.payload` is the
 * same JSON-Schema dialect `dws-call-openapi` validates a request body with, so
 * this reuses the same ajv shape (`allErrors`, `coerceTypes: false`) — the
 * payload is validated as-is, never coerced. When no payload schema is declared,
 * every payload is accepted.
 */
import { Ajv, type ValidateFunction } from 'ajv';
import * as ajvFormats from 'ajv-formats';

// ajv-formats is a CommonJS module whose callable is the default export; under
// NodeNext esModuleInterop mistypes it, so reach it through a typed cast.
const addFormats = (ajvFormats as unknown as { default: (ajv: Ajv) => Ajv }).default;
import type { Schema } from './operation.js';

/** A single validation failure, flattened for the 400 response body. */
export interface ValidationIssue {
  readonly location: string;
  readonly message: string;
}

/** Holds the compiled validator for one operation's message payload. */
export class PayloadValidator {
  private readonly validate: ValidateFunction | undefined;

  constructor(payloadSchema: Schema | undefined) {
    if (payloadSchema === undefined) {
      this.validate = undefined;
      return;
    }
    const ajv = new Ajv({
      strict: false,
      allErrors: true,
      coerceTypes: false,
      useDefaults: true,
    });
    addFormats(ajv);
    this.validate = ajv.compile(payloadSchema);
  }

  /** Validates the payload, returning all issues (empty when valid or unconstrained). */
  validatePayload(payload: unknown): readonly ValidationIssue[] {
    if (this.validate === undefined) return [];
    if (this.validate(payload)) return [];
    return toIssues(this.validate.errors);
  }
}

type AjvErrors = ValidateFunction['errors'];

function toIssues(errors: AjvErrors): ValidationIssue[] {
  if (!errors || errors.length === 0) {
    return [{ location: 'payload', message: 'is invalid' }];
  }
  return errors.map((e) => ({
    location: 'payload' + (e.instancePath || ''),
    message: e.message ?? 'is invalid',
  }));
}
