/**
 * Resolves an OPERATION_ID from a dereferenced OpenAPI document into a cached
 * operation template: the method, path, parameter locations, and requestBody
 * schema needed to bind and validate a request. Also discovers the apiKey
 * security scheme (name + location) used by AUTH_TYPE=apiKey.
 */

export type ParamLocation = 'path' | 'query' | 'header' | 'cookie';

/** A JSON-schema-ish object. Kept loose because it comes straight from the doc. */
export type Schema = Record<string, unknown>;

export interface ParamSpec {
  readonly name: string;
  readonly location: ParamLocation;
  readonly required: boolean;
  readonly schema: Schema;
}

/** The cached, request-ready shape of one operation. */
export interface OperationTemplate {
  readonly operationId: string;
  readonly method: string;
  readonly path: string;
  readonly parameters: readonly ParamSpec[];
  readonly requestBodySchema: Schema | undefined;
  readonly requestBodyRequired: boolean;
}

/** An apiKey security scheme: which header/query parameter carries the key. */
export interface ApiKeyScheme {
  readonly name: string;
  readonly in: 'header' | 'query' | 'cookie';
}

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'] as const;

/** Detects whether the document declares OpenAPI 3.1 (vs 3.0). */
export function isOpenApi31(api: object): boolean {
  const version = (api as { openapi?: unknown }).openapi;
  return typeof version === 'string' && version.startsWith('3.1');
}

/** Finds the operation with the given operationId and builds its template. */
export function resolveOperation(api: object, operationId: string): OperationTemplate {
  const paths = (api as { paths?: Record<string, unknown> }).paths ?? {};
  for (const [path, pathItemRaw] of Object.entries(paths)) {
    const pathItem = pathItemRaw as Record<string, unknown>;
    const sharedParams = readParameters(pathItem.parameters);
    for (const method of HTTP_METHODS) {
      const op = pathItem[method] as Record<string, unknown> | undefined;
      if (op === undefined || op.operationId !== operationId) continue;

      const opParams = readParameters(op.parameters);
      const requestBody = op.requestBody as Record<string, unknown> | undefined;
      return {
        operationId,
        method: method.toUpperCase(),
        path,
        parameters: mergeParameters(sharedParams, opParams),
        requestBodySchema: readJsonBodySchema(requestBody),
        requestBodyRequired: requestBody?.required === true,
      };
    }
  }
  throw new Error(`operationId "${operationId}" not found in the OpenAPI document`);
}

/** Path-level params are overridden by operation-level params with the same (name, in). */
function mergeParameters(shared: ParamSpec[], own: ParamSpec[]): ParamSpec[] {
  const byKey = new Map<string, ParamSpec>();
  for (const p of shared) byKey.set(`${p.location}:${p.name}`, p);
  for (const p of own) byKey.set(`${p.location}:${p.name}`, p);
  return [...byKey.values()];
}

function readParameters(raw: unknown): ParamSpec[] {
  if (!Array.isArray(raw)) return [];
  const out: ParamSpec[] = [];
  for (const entry of raw) {
    const param = entry as Record<string, unknown>;
    const name = param.name;
    const location = param.in;
    if (typeof name !== 'string' || !isParamLocation(location)) continue;
    out.push({
      name,
      location,
      required: param.required === true || location === 'path',
      schema: (param.schema as Schema | undefined) ?? {},
    });
  }
  return out;
}

function readJsonBodySchema(requestBody: Record<string, unknown> | undefined): Schema | undefined {
  const content = requestBody?.content as Record<string, { schema?: Schema }> | undefined;
  return content?.['application/json']?.schema;
}

function isParamLocation(value: unknown): value is ParamLocation {
  return value === 'path' || value === 'query' || value === 'header' || value === 'cookie';
}

/** Returns the first apiKey security scheme declared in the document, if any. */
export function findApiKeyScheme(api: object): ApiKeyScheme | undefined {
  const schemes = (api as { components?: { securitySchemes?: Record<string, unknown> } }).components
    ?.securitySchemes;
  if (schemes === undefined) return undefined;
  for (const scheme of Object.values(schemes)) {
    const s = scheme as Record<string, unknown>;
    if (s.type === 'apiKey' && typeof s.name === 'string' && isApiKeyIn(s.in)) {
      return { name: s.name, in: s.in };
    }
  }
  return undefined;
}

function isApiKeyIn(value: unknown): value is ApiKeyScheme['in'] {
  return value === 'header' || value === 'query' || value === 'cookie';
}
