/**
 * Builds the outbound HTTP request. Path/query/header/body serialization is
 * delegated entirely to swagger-client's `buildRequest` — we never hand-roll it.
 * Our only job is to map jq-evaluated, schema-coerced PARAMETERS into the
 * `parameters` object buildRequest expects, then layer on the resolved auth
 * material (headers, and apiKey query parameters).
 */
import SwaggerClient from 'swagger-client';
import type { AuthMaterial } from './auth.js';
import type { Engine } from './openapi/engine.js';
import type { Binding, BoundParam } from './openapi/validator.js';

/** A fully-resolved request descriptor ready for execution. */
export interface OutboundRequest {
  readonly url: string;
  readonly method: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body: unknown;
}

/** Maps coerced parameters + body into a buildRequest call, then applies auth. */
export function buildOutboundRequest(
  engine: Engine,
  params: readonly BoundParam[],
  binding: Binding,
): OutboundRequest {
  // buildRequest keys parameters by name and resolves each one's location
  // (path/query/header/cookie) from the spec.
  const parameters: Record<string, unknown> = {};
  for (const param of params) {
    parameters[param.name] = param.value;
  }

  const built = SwaggerClient.buildRequest({
    spec: engine.spec,
    operationId: engine.config.operationId,
    parameters,
    ...(binding.hasBody ? { requestBody: binding.body } : {}),
    ...(engine.server.url !== undefined
      ? { server: engine.server.url, serverVariables: { ...engine.server.variables } }
      : {}),
  });

  return applyAuth(built.url, built.method, built.headers, built.body, engine.auth);
}

function applyAuth(
  url: string,
  method: string,
  headers: Readonly<Record<string, string>>,
  body: unknown,
  auth: AuthMaterial,
): OutboundRequest {
  const mergedHeaders = { ...headers, ...auth.headers };

  let finalUrl = url;
  const queryEntries = Object.entries(auth.query);
  if (queryEntries.length > 0) {
    const parsed = new URL(url);
    for (const [name, value] of queryEntries) {
      parsed.searchParams.append(name, value);
    }
    finalUrl = parsed.href;
  }

  return { url: finalUrl, method, headers: mergedHeaders, body };
}
