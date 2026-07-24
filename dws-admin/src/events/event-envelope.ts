import { CloudEvent, ValidationError } from 'cloudevents';
import type { CloudEventV1 } from 'cloudevents';

/**
 * Two-level shape per docs/events.md: Dapr wraps the published bytes in its own
 * transport CloudEvent; the `@DaprPubSub` handler receives that transport
 * envelope's `data`, which is *our* documented envelope — itself a CloudEvent.
 * This module decodes that inner CloudEvent with the CloudEvents JS SDK
 * (`cloudevents`), so spec conformance (`specversion`, `source`, `type`, an
 * RFC 3339 `time`, attribute-name rules) is enforced by the SDK instead of by
 * hand-rolled field checks. The per-type `data` payload is still decoded by
 * each handler via the typed accessors in event-types.ts.
 */

/**
 * A DWS lifecycle event: a spec-valid CloudEvent narrowed by the extra
 * guarantees docs/events.md makes for every DWS event — `time` and `data` are
 * always present, where the CloudEvents spec leaves both optional.
 */
export type DwsEvent<T = unknown> = CloudEvent<T> & { time: string; data: T };

export class InvalidEventEnvelopeError extends Error {
  constructor(reason: string) {
    super(`Invalid event envelope: ${reason}`);
  }
}

export function decodeEventEnvelope<T = unknown>(raw: CloudEventV1<T> | string | unknown): DwsEvent<T> {
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

  // `id` and `time` are checked here rather than left to the SDK: the
  // CloudEvent constructor *fabricates* a random uuid `id` and a "now" `time`
  // when they are absent, so a malformed event would validate cleanly and —
  // a fresh id on every delivery — defeat the id-keyed idempotency guard.
  if (typeof candidate.id !== 'string' || candidate.id.length === 0) {
    throw new InvalidEventEnvelopeError('missing string field "id"');
  }
  if (typeof candidate.time !== 'string' || candidate.time.length === 0) {
    throw new InvalidEventEnvelopeError('missing string field "time"');
  }
  // The CloudEvents spec allows any JSON type as `data`; docs/events.md
  // narrows that to an object for every DWS event type.
  if (typeof candidate.data !== 'object' || candidate.data === null) {
    throw new InvalidEventEnvelopeError('missing object field "data"');
  }

  try {
    // Strict mode (the constructor's default) validates against the
    // CloudEvents v1 JSON schema and throws ValidationError on a violation.
    return new CloudEvent<T>({
      ...candidate,
      datacontenttype: typeof candidate.datacontenttype === 'string' ? candidate.datacontenttype : 'application/json',
    }) as DwsEvent<T>;
  } catch (err) {
    if (err instanceof ValidationError || err instanceof TypeError) {
      throw new InvalidEventEnvelopeError(err.message);
    }
    throw err;
  }
}
