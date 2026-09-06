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
