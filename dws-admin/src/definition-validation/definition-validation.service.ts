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
    const position = error.linePos?.[0];
    return {
      path: '',
      message: error.message,
      line: position?.line,
      column: position?.col,
    };
  }
  return { path: '', message: error instanceof Error ? error.message : String(error) };
}
