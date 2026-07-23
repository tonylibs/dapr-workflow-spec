/**
 * Executes the configured operation for one request: evaluate PARAMETERS jq
 * expressions, bind values by location, validate against the document schemas,
 * build the request with swagger-client, execute it with undici, then shape the
 * output per OUTPUT mode.
 */
import type { Engine } from './openapi/engine.js';
import { evaluateParameters } from './jq.js';
import { executeRequest } from './http.js';
import { buildOutboundRequest } from './request.js';
import type { Binding, BoundParam, ValidationIssue } from './openapi/validator.js';

/** The special PARAMETERS key whose evaluated value becomes the JSON body. */
const REQUEST_BODY_KEY = 'requestBody';

/** Bad input: jq failure or schema violation. Maps to a 400. */
export class BindingError extends Error {
  constructor(
    message: string,
    readonly issues: readonly ValidationIssue[] = [],
  ) {
    super(message);
    this.name = 'BindingError';
  }
}

/** Upstream returned a non-2xx status. Maps to a 502 with {task, status, body}. */
export class UpstreamError extends Error {
  constructor(
    readonly task: string,
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`upstream call for task "${task}" returned status ${status}`);
    this.name = 'UpstreamError';
  }
}

/** The request never produced a response (network, DNS, timeout). Maps to a 502. */
export class TransportError extends Error {
  constructor(
    readonly task: string,
    cause: unknown,
  ) {
    super(`transport error for task "${task}": ${errorMessage(cause)}`);
    this.name = 'TransportError';
  }
}

const SUCCESS_MIN = 200;
const SUCCESS_MAX = 300;

/** Runs the operation and returns the value shaped per OUTPUT mode. */
export async function runOperation(engine: Engine, input: Record<string, unknown>): Promise<unknown> {
  const evaluated = await evaluateParameters(engine.config.parameters, input);
  const binding = bind(engine, evaluated);

  const { issues, coercedParams } = engine.validator.validate(binding);
  if (issues.length > 0) {
    throw new BindingError('request failed schema validation', issues);
  }

  const outbound = buildOutboundRequest(engine, coercedParams, binding);

  let status: number;
  let body: unknown;
  try {
    ({ status, body } = await executeRequest(outbound, engine.config.timeoutMs));
  } catch (cause) {
    throw new TransportError(engine.config.task, cause);
  }

  if (status < SUCCESS_MIN || status >= SUCCESS_MAX) {
    throw new UpstreamError(engine.config.task, status, body);
  }
  return shapeOutput(engine, input, body);
}

/** Splits evaluated PARAMETERS into located params and the optional body. */
function bind(engine: Engine, evaluated: Record<string, unknown>): Binding {
  const params: BoundParam[] = [];
  let body: unknown;
  let hasBody = false;

  for (const [name, value] of Object.entries(evaluated)) {
    // A jq expression that matches nothing yields null; treat that as absent so
    // required-parameter validation fires instead of sending an empty value.
    if (value === null || value === undefined) continue;
    if (name === REQUEST_BODY_KEY) {
      body = value;
      hasBody = true;
      continue;
    }
    // A PARAMETERS key names an operation parameter; place it at every location
    // that parameter is declared for (usually exactly one).
    for (const spec of engine.template.parameters) {
      if (spec.name === name) {
        params.push({ name, location: spec.location, value });
      }
    }
  }

  return { params, body, hasBody };
}

/** Applies OUTPUT: replace returns the upstream body; merge shallow-merges it. */
function shapeOutput(engine: Engine, input: Record<string, unknown>, upstream: unknown): unknown {
  if (engine.config.output === 'replace') {
    return upstream ?? {};
  }

  // merge
  if (upstream === null || upstream === undefined) {
    return { ...input };
  }
  if (typeof upstream !== 'object' || Array.isArray(upstream)) {
    throw new Error('OUTPUT=merge requires the upstream response to be a JSON object');
  }
  return { ...input, ...(upstream as Record<string, unknown>) };
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
