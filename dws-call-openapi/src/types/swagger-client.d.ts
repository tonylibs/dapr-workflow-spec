/**
 * Minimal ambient types for `swagger-client`. The package ships no declarations;
 * we only use the static `buildRequest`, which turns a spec + operationId +
 * parameters into a plain HTTP request descriptor without touching the network.
 */
declare module 'swagger-client' {
  export interface BuildRequestOptions {
    spec: object;
    operationId: string;
    parameters?: Record<string, unknown>;
    requestBody?: unknown;
    server?: string;
    serverVariables?: Record<string, string>;
    requestContentType?: string;
    responseContentType?: string;
  }

  export interface BuiltRequest {
    url: string;
    method: string;
    headers: Record<string, string>;
    body?: unknown;
    credentials?: string;
  }

  export interface SwaggerClientStatic {
    buildRequest(options: BuildRequestOptions): BuiltRequest;
  }

  const SwaggerClient: SwaggerClientStatic;
  export default SwaggerClient;
}
