/**
 * Executes the configured operation for one request: evaluate the PAYLOAD jq
 * expression against the input, validate it against the message payload schema,
 * dispatch it through the local Dapr output binding, then shape the output per
 * OUTPUT mode.
 */
import type { Engine } from './asyncapi/engine.js';
import { evaluatePayload } from './jq.js';
import { executeRequest } from './http.js';
import { buildBindingRequest, type OutboundRequest } from './binding.js';
import type { ValidationIssue } from './asyncapi/validator.js';

/**
 * Bad input: jq failure or payload schema violation. Maps to a 400. The message
 * opens with `validation failed:` so a schema violation classifies as a
 * validation error once it crosses the orchestrator's activity boundary (see
 * dws-orchestrator's WorkflowErrors); non-validation bad input (a jq failure)
 * carries no issues and stays a plain 400.
 */
export class BindingError extends Error {
  constructor(
    message: string,
    readonly issues: readonly ValidationIssue[] = [],
  ) {
    super(message);
    this.name = 'BindingError';
  }
}

/** The sidecar returned a non-2xx status. Maps to a 502 with {task, status, body}. */
export class UpstreamError extends Error {
  constructor(
    readonly task: string,
    readonly status: number,
    readonly body: unknown,
  ) {
    super(`binding dispatch for task "${task}" returned status ${status}`);
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

/**
 * Evaluates PAYLOAD, validates it, and builds the outbound binding request —
 * everything up to (but not including) the network call. Exposed so the
 * payload-to-binding contract can be tested without a live sidecar.
 */
export async function prepareOutbound(
  engine: Engine,
  input: Record<string, unknown>,
): Promise<OutboundRequest> {
  const payload = await evaluatePayload(engine.config.payload, input);

  const issues = engine.validator.validatePayload(payload);
  if (issues.length > 0) {
    throw new BindingError('validation failed: message payload failed schema validation', issues);
  }

  return buildBindingRequest(engine.config, payload);
}

/** Runs the operation and returns the value shaped per OUTPUT mode. */
export async function runOperation(engine: Engine, input: Record<string, unknown>): Promise<unknown> {
  const outbound = await prepareOutbound(engine, input);

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

/** Applies OUTPUT: replace returns the sidecar body; merge shallow-merges it. */
function shapeOutput(engine: Engine, input: Record<string, unknown>, upstream: unknown): unknown {
  if (engine.config.output === 'replace') {
    return upstream ?? {};
  }

  // merge
  if (upstream === null || upstream === undefined) {
    return { ...input };
  }
  if (typeof upstream !== 'object' || Array.isArray(upstream)) {
    throw new Error('OUTPUT=merge requires the sidecar response to be a JSON object');
  }
  return { ...input, ...(upstream as Record<string, unknown>) };
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
