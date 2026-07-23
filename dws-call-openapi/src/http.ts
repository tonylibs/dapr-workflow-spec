/**
 * Executes an outbound request with undici, honoring the configured timeout.
 * Returns the upstream status and a best-effort JSON-parsed body. Network and
 * timeout failures propagate as thrown errors for the runner to classify.
 */
import { request, type Dispatcher } from 'undici';
import type { OutboundRequest } from './request.js';

export interface HttpResponse {
  readonly status: number;
  readonly body: unknown;
}

/** Performs the request; the timeout caps both header and body phases. */
export async function executeRequest(req: OutboundRequest, timeoutMs: number): Promise<HttpResponse> {
  const body = serializeBody(req.body);
  const res = await request(req.url, {
    method: req.method as Dispatcher.HttpMethod,
    headers: req.headers,
    ...(body !== undefined ? { body } : {}),
    headersTimeout: timeoutMs,
    bodyTimeout: timeoutMs,
  });

  const text = await res.body.text();
  return { status: res.statusCode, body: parseMaybeJson(text) };
}

/** JSON-encodes object bodies; passes strings through; drops empty bodies. */
function serializeBody(body: unknown): string | undefined {
  if (body === undefined || body === null) return undefined;
  if (typeof body === 'string') return body;
  return JSON.stringify(body);
}

/** Parses a response as JSON, falling back to the raw text (or undefined if empty). */
function parseMaybeJson(text: string): unknown {
  if (text === '') return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
