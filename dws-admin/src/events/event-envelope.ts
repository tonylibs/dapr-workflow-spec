/**
 * Two-level shape per docs/events.md: Dapr wraps the published bytes in its own
 * transport CloudEvent; the `@DaprPubSub` handler receives that transport
 * envelope's `data`, which is *our* documented envelope below. This module
 * decodes that outer level only — the per-type `data` payload is decoded by
 * each handler via the typed accessors in event-types.ts.
 */
export interface EventEnvelope<T = unknown> {
  id: string;
  source: string;
  type: string;
  time: string;
  datacontenttype: string;
  data: T;
}

export class InvalidEventEnvelopeError extends Error {
  constructor(reason: string) {
    super(`Invalid event envelope: ${reason}`);
  }
}

export function decodeEventEnvelope(raw: unknown): EventEnvelope {
  // @dapr/dapr's pubsub callback type documents the payload as "typically
  // string or object" (verified against the installed package's
  // DaprPubSubCallback.type.d.ts) — parse a raw JSON string defensively,
  // even though `datacontenttype: application/json` normally means the Dapr
  // HTTP server has already parsed it by the time it reaches @DaprPubSub.
  let value = raw;
  if (typeof value === 'string') {
    try {
      value = JSON.parse(value);
    } catch {
      throw new InvalidEventEnvelopeError('payload is a string but not valid JSON');
    }
  }

  if (typeof value !== 'object' || value === null) {
    throw new InvalidEventEnvelopeError('payload is not an object');
  }
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.id !== 'string' || candidate.id.length === 0) {
    throw new InvalidEventEnvelopeError('missing string field "id"');
  }
  if (typeof candidate.type !== 'string' || candidate.type.length === 0) {
    throw new InvalidEventEnvelopeError('missing string field "type"');
  }
  if (typeof candidate.source !== 'string') {
    throw new InvalidEventEnvelopeError('missing string field "source"');
  }
  if (typeof candidate.time !== 'string') {
    throw new InvalidEventEnvelopeError('missing string field "time"');
  }
  if (typeof candidate.data !== 'object' || candidate.data === null) {
    throw new InvalidEventEnvelopeError('missing object field "data"');
  }
  return {
    id: candidate.id,
    source: candidate.source,
    type: candidate.type,
    time: candidate.time,
    datacontenttype: typeof candidate.datacontenttype === 'string' ? candidate.datacontenttype : 'application/json',
    data: candidate.data,
  };
}
