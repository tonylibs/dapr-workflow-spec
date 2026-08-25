/**
 * Builds the outbound Dapr output-binding request. `call: asyncapi` is a single
 * `send` dispatch, which Dapr's Bindings building block models directly: a POST
 * to the local sidecar at `/v1.0/bindings/<BINDING_NAME>` with a body of
 * `{ data, operation, metadata }`. The sidecar's binding Component (synthesized
 * by the controller) holds every broker specific — this runner is broker-agnostic
 * and holds no credentials.
 */
import type { Config } from './config/config.js';

/** A fully-resolved request descriptor ready for execution. */
export interface OutboundRequest {
  readonly url: string;
  readonly method: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body: unknown;
}

/** The Dapr output-binding request body shape. */
export interface BindingBody {
  readonly data: unknown;
  readonly operation: string;
  readonly metadata: Readonly<Record<string, string>>;
}

/** Builds the local Dapr output-binding POST for the validated, interpolated payload. */
export function buildBindingRequest(config: Config, payload: unknown): OutboundRequest {
  const body: BindingBody = {
    data: payload,
    operation: config.operation,
    metadata: config.metadata,
  };
  return {
    url: bindingUrl(config.daprHttpPort, config.bindingName),
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body,
  };
}

/** The Dapr sidecar output-binding invocation URL. */
export function bindingUrl(port: string, bindingName: string): string {
  return `http://localhost:${port}/v1.0/bindings/${encodeURIComponent(bindingName)}`;
}
